package com.newsreview.workbench

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.newsreview.workbench.data.DemoData
import com.newsreview.workbench.data.WorkbenchRepository
import com.newsreview.workbench.data.WorkbenchApi
import com.newsreview.workbench.data.WordUpload
import com.newsreview.workbench.data.WorkbenchState
import com.newsreview.workbench.domain.docx.DocxReader
import com.newsreview.workbench.domain.docx.DocxWriter
import com.newsreview.workbench.domain.flow.ReviewFlow
import com.newsreview.workbench.domain.model.Article
import com.newsreview.workbench.domain.model.ArticleStatus
import com.newsreview.workbench.domain.model.Channel
import com.newsreview.workbench.domain.model.CommentStatus
import com.newsreview.workbench.domain.model.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 界面与业务之间的唯一接口。状态放在仓库里（StateFlow），这里只做转发与"需要 Context 的动作"。
 * 整个界面树共用一个实例（在 MainActivity 里创建），避免各页面各自 new 一份导致状态不同步。
 */
class WorkbenchViewModel(private val app: Application) : AndroidViewModel(app) {

    private val repo: WorkbenchRepository = WorkbenchRepository.shared

    val state: StateFlow<WorkbenchState> = repo.state
    private val settings = app.getSharedPreferences("workbench", 0)
    private var server: WorkbenchApi? = null
    val serverAddress: String get() = settings.getString("server_address", "").orEmpty()

    /** 演示用"角色 → 姓名"映射。接入账号系统后由后端给出。 */
    val roleNames: Map<Role, String> = DemoData.roleNames

    // ---------------------------------------------------------------- 会话

    fun login(role: Role) = repo.login(role)

    fun loginServer(role: Role, address: String, password: String, onReady: () -> Unit) {
        val url = address.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            notify("请输入完整的后端地址，例如 http://192.168.1.10:8000")
            return
        }
        val api = WorkbenchApi(url)
        runNetwork(
            call = {
                api.login(role, password)
                api.articles()
            },
            onSuccess = { articles ->
                settings.edit().putString("server_address", url).apply()
                server = api
                repo.useServer(role, articles)
                onReady()
            }
        )
    }

    fun logout() {
        if (server == null) repo.logout() else repo.leaveServer()
        server = null
    }

    fun refresh() {
        val api = server ?: return
        runNetwork(api::articles, repo::refreshRemote)
    }

    fun openArticle(id: String, onReady: () -> Unit) {
        val api = server
        if (api == null) {
            onReady()
        } else {
            runNetwork({ api.article(id) }) { article ->
                repo.updateRemote(article)
                onReady()
            }
        }
    }

    fun openHistoricalParagraph(id: String, version: Int, index: Int, onReady: (String) -> Unit) {
        val api = server ?: return
        runNetwork({ api.originalParagraph(id, version, index) }, onReady)
    }

    private fun <T> runNetwork(call: () -> T, onSuccess: (T) -> Unit) {
        if (state.value.busy) return
        repo.setBusy(true)
        viewModelScope.launch {
            try {
                onSuccess(withContext(Dispatchers.IO) { call() })
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                notify(error.message ?: "请求失败，请重试")
            } finally {
                repo.setBusy(false)
            }
        }
    }
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

    /** 从手机里选中的真实 .docx 导入（离线模式）。解析链路与内置样例一致：zip + OOXML。 */
    fun importDocx(uri: Uri, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val displayName = app.contentResolver.query(
                        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                    )?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "导入稿件.docx"
                    require(displayName.endsWith(".docx", ignoreCase = true)) { "请选择 .docx 文件：$displayName" }
                    val stream = app.contentResolver.openInputStream(uri) ?: error("无法读取：$displayName")
                    val content = DocxReader.read(stream)
                    Triple(displayName, content.title, content)
                }
            }
            outcome.onSuccess { (displayName, docTitle, content) ->
                repo.notify(
                    if (content.revisions.isEmpty()) "已导入 $displayName：${content.paragraphs.size} 段"
                    else "已导入 $displayName：${content.paragraphs.size} 段，识别到 ${content.revisions.size} 处修订（默认接受并留痕）"
                )
                onDone(repo.importFromDocx(docTitle ?: displayName.removeSuffix(".docx"), content.paragraphs))
            }.onFailure {
                repo.notify("解析失败：${it.message}")
                onDone(null)
            }
        }
    }

    fun uploadWord(manuscript: Uri, materials: List<Uri>, id: String?, onReady: (String) -> Unit) {
        val api = server ?: return
        runNetwork({
            val articleId = api.upload(word(manuscript), materials.map(::word), id)
            api.article(articleId)
        }) { article ->
            repo.updateRemote(article)
            onReady(article.id)
        }
    }

    private fun word(uri: Uri): WordUpload {
        val resolver = app.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "稿件.docx"
        require(name.endsWith(".docx", ignoreCase = true)) { "请选择 .docx 文件：$name" }
        return WordUpload(name) { resolver.openInputStream(uri) ?: error("无法读取：$name") }
    }

    fun saveRevision(id: String, title: String, pushText: String, paragraphs: List<String>,
                     note: String, onSaved: () -> Unit) {
        val api = server
        if (api == null) {
            repo.saveRevision(id, title, pushText, paragraphs, note)
            onSaved()
            return
        }
        runNetwork({
            val file = File.createTempFile("revision-", ".docx", app.cacheDir)
            try {
                DocxWriter.write(file, "", paragraphs, null, null)
                api.upload(WordUpload("修改稿.docx") { file.inputStream() }, emptyList(), id, title, note)
                api.article(id)
            } finally {
                file.delete()
            }
        }) { article ->
            repo.updateRemote(article)
            onSaved()
        }
    }

    /** 新建空白稿件，建完直接进编辑页。 */
    fun createBlankArticle(): String = repo.importFromDocx("未命名稿件", listOf(""))

    fun submit(id: String, channel: Channel, onSent: () -> Unit = {}) {
        val api = server
        if (api == null) {
            repo.submit(id, channel)
            onSent()
        } else {
            val article = state.value.article(id) ?: return
            val action = if (article.status == ArticleStatus.RETURNED) "RESUBMIT" else "SUBMIT"
            runNetwork({ api.action(article, action, channel = channel) }) { updated ->
                repo.updateRemote(updated)
                onSent()
            }
        }
    }

    fun check(id: String) {
        val api = server ?: return
        val article = state.value.article(id) ?: return
        runNetwork({
            api.check(article)
            api.article(id)
        }, repo::updateRemote)
    }

    // ---------------------------------------------------------------- 审核人

    fun passToNext(id: String) {
        if (server == null) repo.passToNext(id) else reviewAction(id, "PASS")
    }

    fun returnToWriter(id: String, note: String, paragraph: Int? = null, quote: String? = null) {
        if (server == null) repo.returnToWriter(id, note)
        else reviewAction(id, "RETURN", note, paragraph, quote)
    }

    fun sign(id: String) {
        if (server == null) repo.sign(id) else reviewAction(id, "SIGN")
    }

    private fun reviewAction(id: String, action: String, note: String = "",
                             paragraph: Int? = null, quote: String? = null) {
        val api = server ?: return
        val article = state.value.article(id) ?: return
        runNetwork({ api.action(article, action, note, paragraph, quote) }, repo::updateRemote)
    }
    fun markPublished(id: String) = repo.markPublished(id)

    fun addComment(id: String, paragraphIndex: Int?, anchor: String?, text: String) {
        if (server == null) repo.addComment(id, paragraphIndex, anchor, text)
        else reviewAction(id, "COMMENT", text, paragraphIndex, anchor)
    }

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
    fun exportSigned(article: Article, onReady: (File) -> Unit) {
        val api = server
        if (api == null) {
            exportDemo(article)?.let(onReady)
        } else {
            runNetwork({
                val dir = File(app.getExternalFilesDir(null), "export").apply { mkdirs() }
                val name = article.title.map { if (it in "\\/:*?\"<>|") '_' else it }.joinToString("").take(80)
                File(dir, "$name-签发版.docx").also { api.downloadSigned(article, it) }
            }, onReady)
        }
    }

    private fun exportDemo(article: Article): File? = runCatching {
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
