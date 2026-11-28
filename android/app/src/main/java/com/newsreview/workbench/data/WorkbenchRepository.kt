package com.newsreview.workbench.data

import com.newsreview.workbench.domain.check.ArticleChecker
import com.newsreview.workbench.domain.check.CheckReport
import com.newsreview.workbench.domain.flow.ReviewFlow
import com.newsreview.workbench.domain.model.ActorAction
import com.newsreview.workbench.domain.model.Article
import com.newsreview.workbench.domain.model.ArticleStatus
import com.newsreview.workbench.domain.model.ArticleVersion
import com.newsreview.workbench.domain.model.Channel
import com.newsreview.workbench.domain.model.CommentStatus
import com.newsreview.workbench.domain.model.ReviewComment
import com.newsreview.workbench.domain.model.ReviewStep
import com.newsreview.workbench.domain.model.ReferenceMaterial
import com.newsreview.workbench.domain.model.Role
import com.newsreview.workbench.domain.model.TimelineEntry
import com.newsreview.workbench.domain.model.TranscriptCue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WorkbenchState(
    val role: Role? = null,
    val articles: List<Article> = emptyList(),
    val materials: List<ReferenceMaterial> = DemoData.materials(),
    val transcript: List<TranscriptCue> = DemoData.transcript(),
    val notice: String? = null,
    val connected: Boolean = false,
    val busy: Boolean = false
) {
    val userName: String get() = role?.let { DemoData.roleNames[it] } ?: ""

    /** "我的待审"：轮到我这一级、还在审核中的稿件。 */
    val pendingForMe: List<Article>
        get() = role?.let { r ->
            articles.filter { it.status == ArticleStatus.IN_REVIEW && it.currentStep?.role == r }
        } ?: emptyList()

    /** "我的稿件"：写稿人视角是我提交的，审核人视角是我经手过的。演示里统一给全部。 */
    val myArticles: List<Article> get() = articles.sortedByDescending { it.updatedAt }

    fun article(id: String): Article? = articles.firstOrNull { it.id == id }
}

/**
 * 演示用仓库：状态在内存里（StateFlow），进程存活期间全局共享。
 *
 * 说明：MVP 阶段不做持久化，重启应用会回到初始演示数据。
 * 下一步换成本地数据库（Room）即可，UI 层不用改。
 */
class WorkbenchRepository {

    companion object {
        /** 演示用单例：进程内共享同一份工作台状态。 */
        val shared: WorkbenchRepository by lazy { WorkbenchRepository() }
    }

    private val checker = ArticleChecker.default()
    private val _state = MutableStateFlow(WorkbenchState())
    private var demoBeforeServer: WorkbenchState? = null
    val state: StateFlow<WorkbenchState> = _state.asStateFlow()

    init {
        val seeded = DemoData.articles().map { checked(it) }
        _state.value = WorkbenchState(
            role = null,
            articles = seeded,
            materials = DemoData.materials(),
            transcript = DemoData.transcript()
        )
    }

    // ------------------------------------------------------------ 会话

    fun login(role: Role) = mutate { it.copy(role = role) }

    fun logout() = mutate { it.copy(role = null) }

    fun setBusy(busy: Boolean) = mutate { it.copy(busy = busy) }

    fun useServer(role: Role, articles: List<Article>) = mutate {
        demoBeforeServer = it.copy(role = null, busy = false, notice = null)
        WorkbenchState(role = role, articles = articles, materials = emptyList(),
            transcript = emptyList(), connected = true, busy = it.busy)
    }

    fun leaveServer() {
        demoBeforeServer?.let { _state.value = it }
        demoBeforeServer = null
    }

    fun refreshRemote(articles: List<Article>) = mutate { it.copy(articles = articles) }

    fun updateRemote(article: Article) = mutate { state ->
        state.copy(articles = state.articles.filterNot { it.id == article.id } + article)
    }

    fun notify(message: String) = mutate { it.copy(notice = message) }

    fun consumeNotice() = mutate { it.copy(notice = null) }

    // ------------------------------------------------------------ 查询

    fun reportOf(id: String): CheckReport? {
        val article = _state.value.article(id) ?: return null
        return if (_state.value.connected) CheckReport(article.findings, emptyList()) else checkOf(article)
    }

    private fun checkOf(article: Article): CheckReport = checker.check(
        article = article,
        materials = _state.value.materials.filter { it.id in article.referenceIds },
        transcript = if (article.referenceIds.contains(DemoData.MATERIAL_TRANSCRIPT)) _state.value.transcript else emptyList()
    )

    private fun checked(article: Article): Article = article.copy(findings = checkOf(article).findings)

    // ------------------------------------------------------------ 写稿人动作

    /** 导入 Word：新建一篇草稿并立即跑一遍 AI 预检。 */
    fun importFromDocx(title: String, paragraphs: List<String>): String {
        val id = "a-imported-${System.currentTimeMillis()}"
        val now = System.currentTimeMillis()
        val article = Article(
            id = id,
            title = title,
            pushText = "",
            paragraphs = paragraphs,
            status = ArticleStatus.DRAFT,
            versions = listOf(ArticleVersion(1, now, writerName(), "由 Word 导入", paragraphs)),
            referenceIds = emptyList(),
            updatedAt = now
        )
        mutate { it.copy(articles = it.articles + checked(article)) }
        return id
    }

    /** 确认送审：草稿 / 已退回 → 审核中。 */
    fun submit(id: String, channel: Channel) = updateArticle(id) { article ->
        val step = if (article.status == ArticleStatus.RETURNED) ReviewFlow.stepForResubmit(article) else ReviewStep.FIRST
        val action = if (article.status == ArticleStatus.RETURNED) ActorAction.RESUBMIT else ActorAction.SUBMIT
        article.copy(
            channel = channel,
            status = ArticleStatus.IN_REVIEW,
            currentStep = step,
            returnedAt = null,
            returnedBy = null,
            returnNote = null,
            timeline = article.timeline + TimelineEntry(
                at = System.currentTimeMillis(),
                actor = writerName(),
                role = Role.WRITER,
                action = action,
                step = step,
                note = if (action == ActorAction.RESUBMIT) "按退改意见修改后重新送审" else "提交送审"
            ),
            updatedAt = System.currentTimeMillis()
        )
    }

    /** 改稿：产出新版本（正文、标题、推送文案）。 */
    fun saveRevision(id: String, title: String, pushText: String, paragraphs: List<String>, note: String) =
        updateArticle(id) { article ->
            val nextNo = (article.versions.maxOfOrNull { it.no } ?: 0) + 1
            article.copy(
                title = title,
                pushText = pushText,
                paragraphs = paragraphs,
                versions = article.versions + ArticleVersion(
                    no = nextNo,
                    createdAt = System.currentTimeMillis(),
                    author = writerName(),
                    note = note,
                    paragraphs = paragraphs
                ),
                updatedAt = System.currentTimeMillis()
            )
        }

    // ------------------------------------------------------------ 审核人动作

    fun passToNext(id: String) = updateArticle(id) { article ->
        val step = article.currentStep ?: ReviewStep.FIRST
        val (status, next) = ReviewFlow.afterPass(step)
        article.copy(
            status = status,
            currentStep = next,
            passedSteps = article.passedSteps + step,
            timeline = article.timeline + TimelineEntry(
                at = System.currentTimeMillis(),
                actor = reviewerName(),
                role = step.role,
                action = if (next == null) ActorAction.SIGN else ActorAction.PASS,
                step = step,
                note = if (next == null) "复核完毕，同意签发" else "转${next.label}"
            ),
            updatedAt = System.currentTimeMillis()
        )
    }

    /** 确认签发：锁定版本 + 生成文末三审署名行（学校通知的硬要求）。 */
    fun sign(id: String) = updateArticle(id) { article ->
        val timeline = article.timeline + TimelineEntry(
            at = System.currentTimeMillis(),
            actor = reviewerName(),
            role = Role.FINAL,
            action = ActorAction.SIGN,
            step = ReviewStep.FINAL,
            note = "确认签发"
        )
        val signers = ReviewFlow.signersFrom(timeline) ?: return@updateArticle article
        val signedParagraphs = ReviewFlow.appendSignerLine(article.paragraphs, signers)
        val nextNo = (article.versions.maxOfOrNull { it.no } ?: 0) + 1
        article.copy(
            status = ArticleStatus.SIGNED,
            currentStep = null,
            passedSteps = ReviewFlow.order.toSet(),
            signers = signers,
            paragraphs = signedParagraphs,
            versions = article.versions + ArticleVersion(
                no = nextNo,
                createdAt = System.currentTimeMillis(),
                author = reviewerName(),
                note = "签发版（含三审署名行）",
                paragraphs = signedParagraphs
            ),
            timeline = timeline,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun returnToWriter(id: String, note: String) = updateArticle(id) { article ->
        val step = article.currentStep ?: ReviewStep.FIRST
        article.copy(
            status = ArticleStatus.RETURNED,
            currentStep = step,
            returnedAt = step,
            returnedBy = reviewerName(),
            returnNote = note,
            timeline = article.timeline + TimelineEntry(
                at = System.currentTimeMillis(),
                actor = reviewerName(),
                role = step.role,
                action = ActorAction.RETURN,
                step = step,
                note = note
            ),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun addComment(id: String, paragraphIndex: Int?, anchor: String?, text: String) =
        updateArticle(id) { article ->
            val comment = ReviewComment(
                id = "c-${System.currentTimeMillis()}",
                author = currentUserName(),
                authorRole = article.currentStep?.role ?: Role.WRITER,
                paragraphIndex = paragraphIndex,
                anchorText = anchor,
                text = text,
                createdAt = System.currentTimeMillis()
            )
            article.copy(comments = article.comments + comment, updatedAt = System.currentTimeMillis())
        }

    fun setCommentStatus(id: String, commentId: String, status: CommentStatus) =
        updateArticle(id) { article ->
            article.copy(
                comments = article.comments.map { if (it.id == commentId) it.copy(status = status) else it },
                updatedAt = System.currentTimeMillis()
            )
        }

    /** 采纳 AI 建议：能定位到原文的直接替换，定位不到的原位标"已处理"。 */
    fun applySuggestion(id: String, findingId: String) = updateArticle(id) { article ->
        val finding = article.findings.firstOrNull { it.id == findingId } ?: return@updateArticle article
        val suggestion = finding.suggestions.firstOrNull()
        val paragraph = article.paragraphs.getOrNull(finding.paragraphIndex)
        val replaced = if (suggestion != null && paragraph != null && finding.start >= 0 && finding.end > finding.start) {
            article.paragraphs.toMutableList().also { list ->
                list[finding.paragraphIndex] = paragraph.replaceRange(finding.start, finding.end, suggestion)
            }
        } else {
            article.paragraphs
        }
        article.copy(
            paragraphs = replaced,
            findings = article.findings.map { if (it.id == findingId) it.copy(resolved = true) else it },
            updatedAt = System.currentTimeMillis()
        )
    }

    fun setFindingIgnored(id: String, findingId: String, ignored: Boolean) =
        updateArticle(id) { article ->
            article.copy(
                findings = article.findings.map { if (it.id == findingId) it.copy(ignored = ignored) else it },
                updatedAt = System.currentTimeMillis()
            )
        }

    fun markPublished(id: String) = updateArticle(id) { article ->
        article.copy(
            status = ArticleStatus.PUBLISHED,
            timeline = article.timeline + TimelineEntry(
                at = System.currentTimeMillis(),
                actor = currentUserName(),
                role = Role.PUBLISHER,
                action = ActorAction.PUBLISH,
                step = null,
                note = "已发布并归档"
            ),
            updatedAt = System.currentTimeMillis()
        )
    }

    /** 重新跑一遍 AI 检查（改稿之后调用）。 */
    fun recheck(id: String) = updateArticle(id) { checked(it) }

    // ------------------------------------------------------------ 内部

    private fun currentUserName(): String =
        _state.value.role?.let { DemoData.roleNames[it] } ?: "演示用户"

    private fun writerName(): String = DemoData.roleNames.getValue(Role.WRITER)

    private fun reviewerName(): String = currentUserName()

    private fun updateArticle(id: String, transform: (Article) -> Article) = mutate { state ->
        state.copy(articles = state.articles.map { if (it.id == id) checked(transform(it)) else it })
    }

    private fun mutate(transform: (WorkbenchState) -> WorkbenchState) {
        _state.value = transform(_state.value)
    }
}
