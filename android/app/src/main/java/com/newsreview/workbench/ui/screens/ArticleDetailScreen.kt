package com.newsreview.workbench.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newsreview.workbench.WorkbenchViewModel
import com.newsreview.workbench.domain.check.CheckReport
import com.newsreview.workbench.domain.model.Article
import com.newsreview.workbench.domain.model.ArticleStatus
import com.newsreview.workbench.domain.model.CommentStatus
import com.newsreview.workbench.domain.model.ReviewComment
import com.newsreview.workbench.domain.model.ReviewStep
import com.newsreview.workbench.domain.model.Role
import com.newsreview.workbench.ui.ScreenTopBar
import com.newsreview.workbench.ui.SectionCard
import com.newsreview.workbench.ui.Tag
import com.newsreview.workbench.ui.components.AnchoredNoteDialog
import com.newsreview.workbench.ui.components.ChannelPickerDialog
import com.newsreview.workbench.ui.components.WordUploadDialog
import com.newsreview.workbench.ui.components.CommentCard
import com.newsreview.workbench.ui.components.EmptyHint
import com.newsreview.workbench.ui.components.InfoBanner
import com.newsreview.workbench.ui.components.NoteInputDialog
import com.newsreview.workbench.ui.components.ParagraphBlock
import com.newsreview.workbench.ui.components.SegmentedRow
import com.newsreview.workbench.ui.components.SignerLineBlock
import com.newsreview.workbench.ui.components.StepProgress
import com.newsreview.workbench.ui.components.TimelineRow
import com.newsreview.workbench.ui.components.VersionRow
import com.newsreview.workbench.ui.relativeTime
import com.newsreview.workbench.ui.shareDocument
import com.newsreview.workbench.ui.theme.AppColors

/**
 * 稿件详情。三个页签对应审核人真正会做的三件事：处理意见、看原文、查流转。
 * 底部操作条按"当前角色 × 稿件状态"决定给哪些按钮，规则集中在 ActionBar 里。
 */
@Composable
fun ArticleDetailScreen(
    vm: WorkbenchViewModel,
    articleId: String,
    onBack: () -> Unit,
    onOpenAi: () -> Unit,
    onEdit: () -> Unit,
    initialParagraph: Int = -1
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val article = state.article(articleId)
    if (article == null) {
        MissingArticleScreen(onBack = onBack)
        return
    }

    val context = LocalContext.current
    val report = remember(article) { vm.reportOf(article.id) }
    var tab by remember { mutableIntStateOf(if (initialParagraph >= 0) 1 else 0) }
    var paragraph by remember { mutableIntStateOf(initialParagraph) }
    var showChannelPicker by remember { mutableStateOf(false) }
    var showReturnDialog by remember { mutableStateOf(false) }
    var showCommentDialog by remember { mutableStateOf(false) }
    var showRevisionUpload by remember { mutableStateOf(false) }
    var historical by remember { mutableStateOf<Pair<ReviewComment, String>?>(null) }

    val role = state.role
    val canComment = "COMMENT" in article.allowedActions
    val exportAndShare: () -> Unit = {
        vm.exportSigned(article) { file ->
            runCatching { shareDocument(context, file) }
                .onFailure { vm.notify("没有可用的分享应用，文件已导出：${file.name}") }
        }
    }

    fun jumpToComment(comment: ReviewComment) {
        val target = comment.paragraphIndex ?: return
        if (comment.sourceVersion != null && comment.sourceVersion != article.currentVersion?.no) {
            vm.openHistoricalParagraph(article.id, comment.sourceVersion, target) { original ->
                historical = comment to original
            }
            return
        }
        paragraph = target
        tab = 1
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.PageBg)
    ) {
        ScreenTopBar(
            title = article.title,
            subtitle = subtitleOf(article),
            onBack = onBack,
            actions = {
                TextButton(onClick = onOpenAi) { Text("AI 检查") }
            }
        )

        StepProgress(
            currentStep = if (article.status == ArticleStatus.RETURNED) article.returnedAt else article.currentStep,
            passedSteps = article.passedSteps,
            status = article.status,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        SegmentedRow(
            options = listOf("批注与意见", "稿件原文", "流转记录"),
            selected = tab,
            onSelect = { tab = it }
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> CommentsTab(
                    article = article,
                    report = report,
                    canToggle = !state.connected && role != null && role != Role.ADMIN,
                    canAdd = !state.connected || canComment,
                    onJump = ::jumpToComment,
                    onOpenAi = onOpenAi,
                    onToggleComment = { comment ->
                        vm.setCommentStatus(
                            article.id,
                            comment.id,
                            if (comment.status == CommentStatus.OPEN) CommentStatus.DONE else CommentStatus.OPEN
                        )
                    },
                    onAddComment = { showCommentDialog = true }
                )

                1 -> OriginalTab(article = article, target = paragraph, connected = state.connected)

                else -> TimelineTab(article = article)
            }
        }

        if (state.connected && role == Role.WRITER && article.status == ArticleStatus.RETURNED) {
            TextButton(
                onClick = { showRevisionUpload = true }, enabled = !state.busy,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("上传新版 Word") }
        }
        ActionBar(
            article = article,
            role = role,
            connected = state.connected,
            onEdit = onEdit,
            onSend = { showChannelPicker = true },
            onReturn = { showReturnDialog = true },
            onPass = { vm.passToNext(article.id) },
            onSign = { vm.sign(article.id) },
            onExport = exportAndShare,
            onPublish = { vm.markPublished(article.id) }
        )
    }

    if (showChannelPicker) {
        ChannelPickerDialog(
            onDismiss = { showChannelPicker = false },
            onConfirm = { channel ->
                showChannelPicker = false
                vm.submit(article.id, channel)
            }
        )
    }

    historical?.let { (comment, original) ->
        AlertDialog(
            onDismissRequest = { historical = null },
            title = { Text("第 ${comment.sourceVersion} 版 · 第 ${(comment.paragraphIndex ?: 0) + 1} 段") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(original)
                    comment.anchorText?.let { Text("批注原句：$it", color = AppColors.Warn) }
                    Text(comment.text)
                }
            },
            confirmButton = { TextButton(onClick = { historical = null }) { Text("关闭") } }
        )
    }

    if (showRevisionUpload) {
        WordUploadDialog(revision = true, busy = state.busy, onDismiss = { showRevisionUpload = false }) {
            file, materials ->
            vm.uploadWord(file, materials, article.id) { showRevisionUpload = false }
        }
    }

    if (state.connected && (showReturnDialog || showCommentDialog)) {
        val selected = paragraph.takeIf { it in article.paragraphs.indices }
        val finding = article.findings.firstOrNull { selected == null || it.paragraphIndex == selected }
        val returning = showReturnDialog
        AnchoredNoteDialog(
            paragraphs = article.paragraphs,
            initialParagraph = selected ?: finding?.paragraphIndex ?: 0,
            initialQuote = finding?.quote,
            returning = returning,
            onDismiss = {
                showReturnDialog = false
                showCommentDialog = false
            },
            onConfirm = { index, quote, note ->
                showReturnDialog = false
                showCommentDialog = false
                if (returning) vm.returnToWriter(article.id, note, index, quote)
                else vm.addComment(article.id, index, quote, note)
            }
        )
    }

    if (showReturnDialog && !state.connected) {
        NoteInputDialog(
            title = "退回修改",
            label = "退改意见（必填）",
            confirmText = "退回给写稿人",
            onDismiss = { showReturnDialog = false },
            onConfirm = { note ->
                showReturnDialog = false
                vm.returnToWriter(article.id, note)
            }
        )
    }

    if (showCommentDialog && !state.connected) {
        NoteInputDialog(
            title = "添加批注",
            label = "批注内容",
            confirmText = "添加",
            onDismiss = { showCommentDialog = false },
            onConfirm = { text ->
                showCommentDialog = false
                vm.addComment(article.id, null, null, text)
            }
        )
    }
}

private fun subtitleOf(article: Article): String = when (article.status) {
    ArticleStatus.DRAFT -> "草稿 · 未送审"
    ArticleStatus.IN_REVIEW -> "审核中 · 当前：${article.currentStep?.label ?: "—"}"
    ArticleStatus.RETURNED -> "已退回 · 退回至${article.returnedAt?.label ?: "—"}（${article.returnedBy ?: "—"}）"
    ArticleStatus.SIGNED -> "已签发 · 三审齐全"
    ArticleStatus.PUBLISHED -> "已发布归档"
}

// ------------------------------------------------------------------ 页签一：批注与意见

@Composable
private fun CommentsTab(
    article: Article,
    report: CheckReport?,
    canToggle: Boolean,
    canAdd: Boolean,
    onJump: (ReviewComment) -> Unit,
    onOpenAi: () -> Unit,
    onToggleComment: (ReviewComment) -> Unit,
    onAddComment: () -> Unit
) {
    val comments = article.comments.sortedWith(
        compareBy({ it.status != CommentStatus.OPEN }, { -it.createdAt })
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionCard(title = "AI 预检摘要") {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile(
                            label = "需人工核实",
                            value = "${report?.confirmRequired ?: 0}",
                            color = AppColors.Warn,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "AI 批注",
                            value = "${report?.findings?.size ?: 0}",
                            color = AppColors.Primary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "已核对一致",
                            value = "${report?.agreements?.size ?: 0}",
                            color = AppColors.Success,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onOpenAi, modifier = Modifier.fillMaxWidth()) {
                        Text("查看逐条证据链")
                    }
                }
            }
        }

        item {
            Text(
                "审核意见（${article.pendingCount} 条待处理 / 共 ${article.comments.size} 条）",
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.Ink,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (comments.isEmpty()) {
            item { EmptyHint("还没有人工意见。") }
        }

        items(comments, key = { it.id }) { comment ->
            CommentCard(
                comment = comment,
                canToggle = canToggle,
                onToggle = { onToggleComment(comment) },
                onJump = if (comment.paragraphIndex == null) null else { { onJump(comment) } }
            )
        }

        if (canAdd) item {
            TextButton(onClick = onAddComment) { Text("＋ 添加批注") }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = AppColors.PageBg,
        border = BorderStroke(1.dp, AppColors.Line)
    ) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 8.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.InkSoft,
                maxLines = 1
            )
        }
    }
}

// ------------------------------------------------------------------ 页签二：稿件原文

@Composable
private fun OriginalTab(article: Article, target: Int, connected: Boolean) {
    val list = rememberLazyListState()
    LaunchedEffect(target, article.currentVersion?.no) {
        if (target in article.paragraphs.indices) list.scrollToItem(target + 3)
    }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard {
                Text(
                    article.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = AppColors.Ink,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "正文 ${article.paragraphs.size} 段 · 第 ${article.versions.size} 版 · " +
                        "${article.channel?.label ?: "未选渠道"} · 更新于 ${relativeTime(article.updatedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.InkSoft
                )
                if (article.pushText.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "推送文案：${article.pushText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.InkSoft
                    )
                }
            }
        }

        item {
            InfoBanner(if (connected) "标色原句需要核实，可在 AI 检查里查看材料原文。"
                else "红底是词库建议，橙底是待人工核实。点右上角 AI 检查查看依据。")
        }

        item { Text("正文", fontWeight = FontWeight.SemiBold) }
        itemsIndexed(article.paragraphs) { index, text ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (index == target) AppColors.WarnBg else AppColors.CardBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(Modifier.padding(12.dp)) {
                    ParagraphBlock(index = index, text = text, findings = article.findings)
                }
            }
        }

        article.signedLine?.let { signedLine ->
            item { SignerLineBlock(signedLine = signedLine) }
        }

        if (article.images.isNotEmpty()) {
            item {
                SectionCard(title = "配图占位") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        article.images.forEach { image ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .width(56.dp)
                                        .height(40.dp)
                                        .background(AppColors.Line, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "图",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AppColors.InkSoft
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(image.caption, style = MaterialTheme.typography.bodyMedium, color = AppColors.Ink)
                                    Text(
                                        "图片同属审核范围：需核对画面是否准确、有无不宜公开信息",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AppColors.InkSoft
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "附件与参考资料") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    article.attachments.forEach { attachment ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Tag(attachment.type, AppColors.PrimaryContainer, AppColors.Primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${attachment.name}　${attachment.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.Ink
                            )
                        }
                    }
                    Text(
                        "AI 给出的每一条依据，都能指回上面这些材料的具体行。",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.InkSoft
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 页签三：流转记录

@Composable
private fun TimelineTab(article: Article) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        article.returnNote?.let { note ->
            item {
                SectionCard(title = "退改说明") {
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = AppColors.Danger)
                    Text(
                        "退回人：${article.returnedBy ?: "—"}　退回环节：${article.returnedAt?.label ?: "—"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.InkSoft
                    )
                }
            }
        }

        item {
            SectionCard(title = "流转记录") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    article.timeline.forEachIndexed { index, entry ->
                        TimelineRow(entry = entry, isLast = index == article.timeline.lastIndex)
                    }
                }
            }
        }

        item {
            SectionCard(title = "版本记录") {
                Column {
                    article.versions.sortedByDescending { it.no }.forEach { version ->
                        VersionRow(
                            version = version,
                            isCurrent = version.no == article.currentVersion?.no
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 底部操作条

@Composable
private fun ActionBar(
    article: Article,
    role: Role?,
    connected: Boolean,
    onEdit: () -> Unit,
    onSend: () -> Unit,
    onReturn: () -> Unit,
    onPass: () -> Unit,
    onSign: () -> Unit,
    onExport: () -> Unit,
    onPublish: () -> Unit
) {
    val step = article.currentStep
    val canReview = if (connected) "RETURN" in article.allowedActions
        else article.status == ArticleStatus.IN_REVIEW && step?.role == role && role != null
    val canSubmit = if (connected) article.allowedActions.any { it == "SUBMIT" || it == "RESUBMIT" }
        else role == Role.WRITER && article.status in listOf(ArticleStatus.DRAFT, ArticleStatus.RETURNED)

    Surface(color = Color.White, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                hintOf(article, role, canReview),
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.InkSoft
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    !connected && role == Role.WRITER && article.status == ArticleStatus.IN_REVIEW -> {
                        OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                            Text("继续修改并重提")
                        }
                    }

                    canSubmit -> {
                        OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                            Text("编辑稿件")
                        }
                        Button(onClick = onSend, modifier = Modifier.weight(1f)) {
                            Text(if (article.status == ArticleStatus.RETURNED) "重新送审" else "送审")
                        }
                    }

                    canReview && step == ReviewStep.FINAL -> {
                        OutlinedButton(onClick = onReturn, modifier = Modifier.weight(1f)) {
                            Text("退回修改")
                        }
                        Button(onClick = onSign, modifier = Modifier.weight(1f)) {
                            Text("确认签发")
                        }
                    }

                    canReview -> {
                        OutlinedButton(onClick = onReturn, modifier = Modifier.weight(1f)) {
                            Text("退回修改")
                        }
                        Button(onClick = onPass, modifier = Modifier.weight(1f)) {
                            Text("通过并送下一审")
                        }
                    }

                    role == Role.PUBLISHER && article.status == ArticleStatus.SIGNED -> {
                        OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                            Text("导出签发版")
                        }
                        Button(onClick = onPublish, modifier = Modifier.weight(1f)) {
                            Text("标记已发布")
                        }
                    }

                    article.status == ArticleStatus.SIGNED || article.status == ArticleStatus.PUBLISHED -> {
                        Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                            Text("导出签发版 .docx")
                        }
                    }

                    else -> {
                        Text(
                            "当前角色在这一步没有操作。",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.InkSoft
                        )
                    }
                }
            }
        }
    }
}

private fun hintOf(article: Article, role: Role?, canReview: Boolean): String = when {
    role == Role.WRITER && article.status == ArticleStatus.DRAFT ->
        "草稿不会进审核流程；选好发布渠道送审，才会到编辑初审。"

    role == Role.WRITER && article.status == ArticleStatus.RETURNED ->
        "退回至${article.returnedAt?.label ?: "—"}。改完重新送审，回到这一级，前面已通过的环节不重审。"

    role == Role.WRITER && article.status == ArticleStatus.IN_REVIEW ->
        "正在${article.currentStep?.label ?: "—"}，等待放行。需要改稿就先保存新版本，再重新送审。"

    canReview && article.currentStep == ReviewStep.FINAL ->
        "终审签发后会自动把三审署名行写进文末，并锁定签发版本。"

    canReview ->
        "处理完批注再放行；建议先过一遍 AI 检查里的待核实项。"

    role == Role.PUBLISHER && article.status == ArticleStatus.SIGNED ->
        "签发版已含三审署名行，导出后可直接用于发布。"

    article.status == ArticleStatus.PUBLISHED -> "已发布归档，流程结束。"

    else -> "当前角色在这一步是旁看；需要处理时请用对应角色的入口进入。"
}
