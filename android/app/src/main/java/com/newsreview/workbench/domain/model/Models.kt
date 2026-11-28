package com.newsreview.workbench.domain.model

/** 角色。管理员维护词库与账号，不参与稿件审核。 */
enum class Role(val label: String, val duty: String) {
    WRITER("写稿人", "创建稿件、在线编辑、上传稿件、提交前跑 AI 检查"),
    REVIEW_L1("一审 · 编辑初审", "查看 AI 批注、逐条处理、退回或送下一级"),
    REVIEW_L2("二审 · 指导老师复审", "查看 AI 批注与上轮意见、退回或送终审"),
    FINAL("终审 · 负责人", "复核并确认签发，锁定签发版本"),
    PUBLISHER("发布人员", "下载签发版、标记已发布并归档"),
    ADMIN("管理员", "维护敏感词与规范用词词库、账号配置")
}

/** 人工审核的三个环节。AI 预检不是环节，是贯穿每一级的辅助证据层。 */
enum class ReviewStep(val label: String, val role: Role, val order: Int) {
    FIRST("编辑初审", Role.REVIEW_L1, 1),
    SECOND("指导老师二审", Role.REVIEW_L2, 2),
    FINAL("负责人签发", Role.FINAL, 3)
}

enum class ArticleStatus(val label: String) {
    DRAFT("草稿"),
    IN_REVIEW("审核中"),
    RETURNED("已退回"),
    SIGNED("已签发"),
    PUBLISHED("已发布")
}

enum class Channel(val label: String, val hint: String) {
    COLLEGE_WECHAT("学院公众号", "三审齐全后才能发布"),
    SCHOOL_SITE("学校官网", "需终审签发版 + 三审署名"),
    INNER_NOTICE("院内通知", "按院系流程发布")
}

enum class FindingLevel(val label: String) {
    L1("L1 文字层"),
    L2("L2 一致性"),
    L3("L3 判断层")
}

enum class FindingKind(val label: String) {
    TYPO("错别字"),
    BANNED_WORD("敏感词 / 禁用词"),
    STYLE("规范用语"),
    CONSISTENCY("一致性核对"),
    FACT_SUPPORT("事实支撑"),
    CLARITY("表述清晰度"),
    STRUCTURE("篇幅结构")
}

/** 证据链的一环：一条可核查的依据。confidence 是这些增量之和，不是拍出来的数。 */
data class Evidence(
    val label: String,
    val delta: Double,
    val source: String? = null,
    val quote: String? = null
)

/** AI 批注。每一条都必须带依据（evidence），拿不准就 needsConfirm。 */
data class Finding(
    val id: String,
    val level: FindingLevel,
    val kind: FindingKind,
    val paragraphIndex: Int,
    val start: Int,
    val end: Int,
    val quote: String,
    val title: String,
    val detail: String,
    val evidence: List<Evidence>,
    val confidence: Double,
    val suggestions: List<String> = emptyList(),
    val needsConfirm: Boolean = false,
    val resolved: Boolean = false,
    val ignored: Boolean = false
)

enum class CommentStatus(val label: String) { OPEN("待处理"), DONE("已处理") }

/** 人工批注 / 退改意见。可以来自 AI 建议，也可以是审核人自己写的。 */
data class ReviewComment(
    val id: String,
    val author: String,
    val authorRole: Role,
    val paragraphIndex: Int?,
    val anchorText: String?,
    val text: String,
    val createdAt: Long,
    val status: CommentStatus = CommentStatus.OPEN,
    val fromFindingId: String? = null,
    val sourceVersion: Int? = null
)

enum class RevisionKind(val label: String) { INSERT("插入"), DELETE("删除") }

data class TrackedRevision(val kind: RevisionKind, val text: String)

/** 一个版本。Word 自带修订默认接受，但把改动记下来留痕。 */
data class ArticleVersion(
    val no: Int,
    val createdAt: Long,
    val author: String,
    val note: String,
    val paragraphs: List<String>,
    val acceptedRevisions: List<TrackedRevision> = emptyList()
)

enum class ActorAction(val label: String) {
    SUBMIT("提交送审"),
    PASS("通过并送下一审"),
    RETURN("退回修改"),
    COMMENT("添加批注"),
    RESUBMIT("上传新版并重新送审"),
    SIGN("确认签发"),
    PUBLISH("标记已发布")
}

data class TimelineEntry(
    val at: Long,
    val actor: String,
    val role: Role,
    val action: ActorAction,
    val step: ReviewStep?,
    val note: String? = null
)

data class Signers(val first: String, val second: String, val final: String)

data class Attachment(val name: String, val type: String, val size: String)

enum class MaterialKind(val label: String) {
    NOTICE("活动通知"),
    SIGN_IN("签到表"),
    AGENDA("活动议程"),
    TRANSCRIPT("采访录音转写"),
    OTHER("其它参考")
}

/** 参考资料。AI 的所有"依据"都必须能指到这些材料的具体行。 */
data class ReferenceMaterial(
    val id: String,
    val name: String,
    val kind: MaterialKind,
    val lines: List<String>
)

/** 录音转写的单句，用于"回听原话"。 */
data class TranscriptCue(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val speaker: String,
    val text: String
)

data class ArticleImage(
    val caption: String,
    val placeholder: String
)

data class Article(
    val id: String,
    val title: String,
    val pushText: String,
    val paragraphs: List<String>,
    val images: List<ArticleImage> = emptyList(),
    val status: ArticleStatus = ArticleStatus.DRAFT,
    val channel: Channel? = null,
    val currentStep: ReviewStep? = null,
    /** 被退回时所在的环节；改稿重新送审后回到这一级，已通过的环节不重审。 */
    val returnedAt: ReviewStep? = null,
    val returnNote: String? = null,
    val returnedBy: String? = null,
    val passedSteps: Set<ReviewStep> = emptySet(),
    val versions: List<ArticleVersion> = emptyList(),
    val findings: List<Finding> = emptyList(),
    val comments: List<ReviewComment> = emptyList(),
    val timeline: List<TimelineEntry> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val referenceIds: List<String> = emptyList(),
    val signers: Signers? = null,
    val updatedAt: Long = 0L,
    val serverChecked: Boolean = false,
    val reviewedReturnNote: String? = null,
    val allowedActions: Set<String> = emptySet()
) {
    val currentVersion: ArticleVersion? get() = versions.maxByOrNull { it.no }
    val pendingCount: Int get() = comments.count { it.status == CommentStatus.OPEN }
    val confirmCount: Int get() = findings.count { it.needsConfirm && !it.ignored && !it.resolved }
    val signedLine: String?
        get() = signers?.let { "初审：${it.first}　复审：${it.second}　终审：${it.final}" }
}
