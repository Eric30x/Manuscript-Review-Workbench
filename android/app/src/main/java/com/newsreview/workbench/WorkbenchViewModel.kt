package com.newsreview.workbench

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.newsreview.workbench.data.DemoData
import com.newsreview.workbench.data.WorkbenchRepository
import com.newsreview.workbench.data.WorkbenchState
import com.newsreview.workbench.domain.docx.DocxReader
import com.newsreview.workbench.domain.docx.DocxWriter
import com.newsreview.workbench.domain.flow.ReviewFlow
import com.newsreview.workbench.domain.model.Article
import com.newsreview.workbench.domain.model.Channel
import com.newsreview.workbench.domain.model.CommentStatus
import com.newsreview.workbench.domain.model.Role
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * 界面与业务之间的唯一接口。状态放在仓库里（StateFlow），这里只做转发与"需要 Context 的动作"。
 * 整个界面树共用一个实例（在 MainActivity 里创建），避免各页面各自 new 一份导致状态不同步。
 */
class WorkbenchViewModel(private val app: Application) : AndroidViewModel(app) {

    private val repo: WorkbenchRepository = WorkbenchRepository.shared

    val state: StateFlow<WorkbenchState> = repo.state

    /** 演示用"角色 → 姓名"映射。接入账号系统后由后端给出。 */
    val roleNames: Map<Role, String> = DemoData.roleNames

    // ---------------------------------------------------------------- 会话

    fun login(role: Role) = repo.login(role)
    fun logout() = repo.logout()
    fun consumeNotice() = repo.consumeNotice()

    // ---------------------------------------------------------------- 写稿人

    /** 从内置样例 .docx 导入：走一遍真实的解析流程（zip + OOXML）。 */
    fun importSampleDocx(): String? = runCatching {
        val stream = app.assets.open(com.newsreview.workbench.data.DemoData.SAMPLE_DOCX_ASSET)
        val content = DocxReader.read(stream)
        val title = content.title ?: "未命名稿件"
        repo.notify(
            if (content.revisions.isEmpty()) "已解析 Word：${content.paragraphs.size} 段"
            else "已解析 Word：${content.paragraphs.size} 段，识别到 ${content.revisions.size} 处修订（默认接受并留痕）"
        )
        repo.importFromDocx(title, content.paragraphs)
    }.getOrElse {
        repo.notify("解析失败：${it.message}")
        null
    }

    fun saveRevision(id: String, title: String, pushText: String, paragraphs: List<String>, note: String) =
        repo.saveRevision(id, title, pushText, paragraphs, note)

    /** 新建空白稿件，建完直接进编辑页。 */
    fun createBlankArticle(): String = repo.importFromDocx("未命名稿件", listOf(""))

    fun submit(id: String, channel: Channel) = repo.submit(id, channel)

    // ---------------------------------------------------------------- 审核人

    fun passToNext(id: String) = repo.passToNext(id)
    fun returnToWriter(id: String, note: String) = repo.returnToWriter(id, note)
    fun sign(id: String) = repo.sign(id)
    fun markPublished(id: String) = repo.markPublished(id)

    fun addComment(id: String, paragraphIndex: Int?, anchor: String?, text: String) =
        repo.addComment(id, paragraphIndex, anchor, text)

    fun setCommentStatus(id: String, commentId: String, status: CommentStatus) =
        repo.setCommentStatus(id, commentId, status)

    fun applySuggestion(id: String, findingId: String) = repo.applySuggestion(id, findingId)

    fun setFindingIgnored(id: String, findingId: String, ignored: Boolean) =
        repo.setFindingIgnored(id, findingId, ignored)

    // ---------------------------------------------------------------- 导出

    /**
     * 导出签发版 .docx：正文 + 文末三审署名行 + 版本信息。
     * 写到应用专属外部目录 export/，通过 FileProvider 分享出去。
     */
    fun exportSigned(article: Article): File? = runCatching {
        val dir = File(app.getExternalFilesDir(null), "export").apply { mkdirs() }
        val safeName = article.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val target = File(dir, "$safeName-签发版.docx")
        val signers = article.signers
        val signerLine = signers?.let { ReviewFlow.signerLine(it) }
        val meta = buildString {
            append("发布渠道：")
            append(article.channel?.label ?: "未指定")
            append("　版本：第 ")
            append(article.versions.size)
            append(" 版")
            article.timeline.lastOrNull { it.action.name == "SIGN" }?.let {
                append("　签发时间：")
                append(com.newsreview.workbench.ui.formatTime(it.at))
            }
        }
        DocxWriter.write(target, article.title, article.paragraphs, signerLine, meta)
        repo.notify("已导出：${target.name}（已含三审署名行）")
        target
    }.getOrElse {
        repo.notify("导出失败：${it.message}")
        null
    }

    fun notify(message: String) = repo.notify(message)

    // ---------------------------------------------------------------- 只读

    fun reportOf(id: String) = repo.reportOf(id)
}
