package com.newsreview.workbench.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newsreview.workbench.WorkbenchViewModel
import com.newsreview.workbench.data.WorkbenchState
import com.newsreview.workbench.domain.model.Article
import com.newsreview.workbench.domain.model.ArticleStatus
import com.newsreview.workbench.domain.model.Role
import com.newsreview.workbench.ui.ScreenTopBar
import com.newsreview.workbench.ui.SectionCard
import com.newsreview.workbench.ui.StatusChip
import com.newsreview.workbench.ui.Tag
import com.newsreview.workbench.ui.components.EmptyHint
import com.newsreview.workbench.ui.components.InfoBanner
import com.newsreview.workbench.ui.components.SegmentedRow
import com.newsreview.workbench.ui.components.WordUploadDialog
import com.newsreview.workbench.ui.relativeTime
import com.newsreview.workbench.ui.theme.AppColors

private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

/** 工作台首页：今天轮到我干什么、我是谁、最近有哪些稿子。 */
@Composable
fun HomeScreen(
    vm: WorkbenchViewModel,
    onOpenArticle: (String) -> Unit,
    onImportWord: () -> Unit,
    onNewArticle: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenMaterials: () -> Unit,
    onSwitchRole: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pickDocx = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importDocx(uri) { id -> if (id != null) onOpenArticle(id) }
    }
    val role = state.role
    var showFlow by remember { mutableStateOf(false) }
    var upload by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(0) }
    val writerStates = listOf(ArticleStatus.DRAFT, ArticleStatus.IN_REVIEW, ArticleStatus.RETURNED, ArticleStatus.SIGNED)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.PageBg)
    ) {
        ScreenTopBar(
            title = "审稿工作台",
            subtitle = "${state.userName}　·　${role?.label ?: "未登录"}",
            actions = {
                if (state.connected) TextButton(onClick = vm::refresh, enabled = !state.busy) { Text("刷新") }
                TextButton(onClick = onSwitchRole, enabled = !state.busy) { Text("切换角色") }
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { StatRow(state) }

            item {
                SectionCard(title = "待我处理") {
                    if (state.pendingForMe.isEmpty()) {
                        EmptyHint("当前没有轮到你这一级的稿件。")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.pendingForMe.forEach { article ->
                                ArticleRow(article = article, onClick = onOpenArticle)
                            }
                        }
                    }
                }
            }

            if (role == Role.WRITER) {
                item {
                    SectionCard(title = "写稿人操作") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (state.connected) upload = true
                                    else pickDocx.launch(arrayOf(DOCX_MIME, "application/octet-stream"))
                                },
                                modifier = Modifier.fillMaxWidth(), enabled = !state.busy
                            ) {
                                Text(if (state.connected) "选择并上传 Word 稿件" else "从手机选择 Word 稿件")
                            }
                            if (!state.connected) {
                                OutlinedButton(
                                    onClick = onImportWord,
                                    modifier = Modifier.fillMaxWidth(), enabled = !state.busy
                                ) {
                                    Text("导入内置样例")
                                }
                                OutlinedButton(onClick = onNewArticle, modifier = Modifier.fillMaxWidth()) {
                                    Text("新建空白稿件")
                                }
                            }
                        }
                    }
                }
            }

            if (!state.connected) item {
                SectionCard(title = "工具") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoBanner(
                            "流程：写稿人提交 → 编辑初审 → 指导老师二审 → 负责人签发 → 发布归档。" +
                                "AI 预检不是一级审核，它是每一级都能调用的辅助证据层。"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onOpenMaterials, modifier = Modifier.weight(1f)) {
                                Text("参考资料")
                            }
                            OutlinedButton(onClick = onOpenLibrary, modifier = Modifier.weight(1f)) {
                                Text("敏感词与规范用语")
                            }
                        }
                        TextButton(onClick = { showFlow = true }) { Text("三审制要求与演示脚本") }
                    }
                }
            }

            item {
                SectionCard(title = if (role == Role.WRITER) "我的稿件" else "最近稿件") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val filtered = if (role == Role.WRITER && state.connected) {
                            SegmentedRow(listOf("全部") + writerStates.map { it.label }, filter, onSelect = { filter = it })
                            if (filter == 0) state.myArticles else state.myArticles.filter {
                                it.status == writerStates[filter - 1]
                            }
                        } else state.myArticles
                        if (filtered.isEmpty()) EmptyHint("暂无稿件")
                        filtered.forEach { article ->
                            ArticleRow(article = article, onClick = onOpenArticle)
                        }
                    }
                }
            }
        }
    }

    if (upload) {
        WordUploadDialog(revision = false, busy = state.busy, onDismiss = { upload = false }) { file, materials ->
            vm.uploadWord(file, materials, null) { upload = false }
        }
    }

    if (showFlow) {
        AlertDialog(
            onDismissRequest = { showFlow = false },
            title = { Text("三审制要求") },
            text = {
                Text(
                    "1. 三级审核必须是三个不同的人，三级姓名都要标在文末。\n" +
                        "2. 审核范围：语言文字、图片、视频的准确性；信息是否客观真实；发布依据与版权归属；政治导向与是否涉密。\n" +
                        "3. 退改意见必填，退回后回到被打回的那一级，已通过的环节不重审。\n" +
                        "4. 签发时工作台自动把三审署名行写进文末，并锁定签发版本。\n\n" +
                        "演示脚本：以写稿人进入 → 打开发布状态为“已退回”的开放日稿件 → 看 AI 批注 → 采纳错别字建议、" +
                        "逐条处理二审意见 → 重新送审 → 依次用一审、二审、终审角色进入并放行 → 终审签发后看文末署名行 → " +
                        "切到发布人员导出并分享签发版。",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Ink
                )
            },
            confirmButton = { TextButton(onClick = { showFlow = false }) { Text("知道了") } }
        )
    }
}

@Composable
private fun StatRow(state: WorkbenchState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatTile("待我审核", state.pendingForMe.size.toString(), AppColors.Primary, Modifier.weight(1f))
        StatTile("全部稿件", state.articles.size.toString(), AppColors.Ink, Modifier.weight(1f))
        StatTile(
            "已签发",
            state.articles.count {
                it.status == ArticleStatus.SIGNED || it.status == ArticleStatus.PUBLISHED
            }.toString(),
            AppColors.Success,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = AppColors.CardBg,
        border = BorderStroke(1.dp, AppColors.Line)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.InkSoft)
        }
    }
}

@Composable
private fun ArticleRow(article: Article, onClick: (String) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick(article.id) },
        color = AppColors.PageBg
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    article.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(article.status)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    meta(article),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (article.confirmCount > 0) {
                    Tag("${article.confirmCount} 条待核实", AppColors.WarnBg, AppColors.Warn)
                    Spacer(Modifier.width(6.dp))
                }
                if (article.pendingCount > 0) {
                    Tag("${article.pendingCount} 条意见", AppColors.DangerBg, AppColors.Danger)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    relativeTime(article.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.InkSoft
                )
            }
        }
    }
}

private fun meta(article: Article): String = buildString {
    append("第 ${article.versions.size} 版")
    article.channel?.let { append(" · ${it.label}") }
    when (article.status) {
        ArticleStatus.IN_REVIEW -> article.currentStep?.let { append(" · 当前：${it.label}") }
        ArticleStatus.RETURNED -> article.returnedAt?.let { append(" · 退回至：${it.label}") }
        ArticleStatus.SIGNED -> append(" · 已含三审署名行")
        ArticleStatus.PUBLISHED -> append(" · 已发布归档")
        ArticleStatus.DRAFT -> append(" · 草稿未送审")
    }
}
