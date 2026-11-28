package com.newsreview.workbench.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newsreview.workbench.domain.check.Agreement
import com.newsreview.workbench.domain.model.ActorAction
import com.newsreview.workbench.domain.model.ArticleVersion
import com.newsreview.workbench.domain.model.CommentStatus
import com.newsreview.workbench.domain.model.Finding
import com.newsreview.workbench.domain.model.ReviewComment
import com.newsreview.workbench.domain.model.TimelineEntry
import com.newsreview.workbench.ui.Tag
import com.newsreview.workbench.ui.formatTime
import com.newsreview.workbench.ui.relativeTime
import com.newsreview.workbench.ui.theme.AppColors

/** 分段选择器（"批注与意见 / 稿件原文 / 流转记录"）。 */
@Composable
fun SegmentedRow(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Line)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(index) },
                color = if (active) Color.White else Color.Transparent
            ) {
                Text(
                    text = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) AppColors.Ink else AppColors.InkSoft,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/** 一句话说明条。用来说清"AI 不是审核环节"这类容易误解的事。 */
@Composable
fun InfoBanner(text: String, accent: Color = AppColors.Primary, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = AppColors.Ink)
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = AppColors.InkSoft
    )
}

/**
 * 稿件段落 + 命中高亮。红底=按词库能直接改的，橙底=需要人工核实的。
 * 高亮区间来自 Finding 的 start/end，和 AI 报告里点"采纳"改动的是同一段文字。
 */
@Composable
fun ParagraphBlock(
    index: Int,
    text: String,
    findings: List<Finding>,
    modifier: Modifier = Modifier
) {
    val marks = findings
        .filter {
            it.paragraphIndex == index && !it.ignored && !it.resolved &&
                it.start >= 0 && it.end > it.start && it.end <= text.length
        }
        .sortedBy { it.start }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.InkSoft,
            modifier = Modifier
                .width(22.dp)
                .padding(top = 3.dp)
        )
        when {
            text.isBlank() -> Text(
                text = "（空段落）",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.InkSoft
            )

            marks.isEmpty() -> Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Ink,
                lineHeight = 24.sp
            )

            else -> Text(
                text = highlight(text, marks),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Ink,
                lineHeight = 24.sp
            )
        }
    }
}

private fun highlight(text: String, marks: List<Finding>) = buildAnnotatedString {
    var cursor = 0
    marks.forEach { finding ->
        val start = maxOf(finding.start, cursor)
        if (start > cursor) append(text.substring(cursor, start))
        if (finding.end > start) {
            val background = if (finding.needsConfirm) AppColors.WarnBg else AppColors.DangerBg
            val foreground = if (finding.needsConfirm) AppColors.Warn else AppColors.Danger
            withStyle(SpanStyle(background = background, color = foreground)) {
                append(text.substring(start, finding.end))
            }
            cursor = finding.end
        }
    }
    if (cursor < text.length) append(text.substring(cursor))
}

/** 学校通知的硬要求：三审人员姓名要标在文末。这条是签发时自动写进去的。 */
@Composable
fun SignerLineBlock(signedLine: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.SuccessBg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.Success, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                "文末三审署名行（签发时自动写入）",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.Success,
                fontWeight = FontWeight.SemiBold
            )
            Text(signedLine, style = MaterialTheme.typography.bodyMedium, color = AppColors.Ink)
        }
    }
}

@Composable
fun CommentCard(
    comment: ReviewComment,
    canToggle: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onJump: (() -> Unit)? = null
) {
    val open = comment.status == CommentStatus.OPEN
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (open) AppColors.CardBg else AppColors.PageBg,
        border = BorderStroke(1.dp, AppColors.Line)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Ink,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(6.dp))
                Tag(comment.authorRole.label, AppColors.PrimaryContainer, AppColors.Primary)
                Spacer(Modifier.weight(1f))
                Text(
                    relativeTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.InkSoft
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(comment.text, style = MaterialTheme.typography.bodyMedium, color = AppColors.Ink)
            val paragraph = comment.paragraphIndex
            val anchor = comment.anchorText
            if (paragraph != null || anchor != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (paragraph != null) Tag("第 ${paragraph + 1} 段", AppColors.Line, AppColors.InkSoft)
                    if (anchor != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "“$anchor”",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.InkSoft
                        )
                    }
                }
            }
            comment.sourceVersion?.let {
                Text("第 $it 版意见", style = MaterialTheme.typography.labelSmall)
            }
            if (onJump != null) {
                TextButton(onClick = onJump) { Text("定位原文") }
            }
            if (canToggle) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onToggle, contentPadding = PaddingValues(0.dp)) {
                    Text(
                        if (open) "标记已处理" else "重新打开",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun TimelineRow(entry: TimelineEntry, isLast: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(22.dp)) {
            Box(
                Modifier
                    .padding(top = 5.dp)
                    .size(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(dotColor(entry))
            )
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(48.dp)
                        .background(AppColors.Line)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${entry.actor} · ${entry.role.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Ink,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                buildString {
                    append(entry.action.label)
                    entry.step?.let { append("（${it.label}）") }
                    append("　")
                    append(formatTime(entry.at))
                },
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.InkSoft
            )
            entry.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.InkSoft)
            }
        }
    }
}

private fun dotColor(entry: TimelineEntry): Color = when (entry.action) {
    ActorAction.RETURN -> AppColors.Danger
    ActorAction.SIGN -> AppColors.Success
    ActorAction.PUBLISH -> AppColors.Success
    else -> AppColors.Primary
}

@Composable
fun VersionRow(version: ArticleVersion, isCurrent: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Tag(
            "第 ${version.no} 版",
            if (isCurrent) AppColors.PrimaryContainer else AppColors.Line,
            if (isCurrent) AppColors.Primary else AppColors.InkSoft
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(version.note, style = MaterialTheme.typography.bodyMedium, color = AppColors.Ink)
            Text(
                "${version.author}　${formatTime(version.createdAt)}　${version.paragraphs.size} 段",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.InkSoft
            )
        }
    }
}

/** 已核对一致的正例。先看疑点，再看"这些地方查过了，没问题"。 */
@Composable
fun AgreementRow(agreement: Agreement, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = AppColors.Success,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(agreement.title, style = MaterialTheme.typography.bodyMedium, color = AppColors.Ink)
            Text(
                "${agreement.detail}　来源：${agreement.source}",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.InkSoft
            )
        }
    }
}

