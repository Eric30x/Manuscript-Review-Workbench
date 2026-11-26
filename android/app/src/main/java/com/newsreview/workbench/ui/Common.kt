package com.newsreview.workbench.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newsreview.workbench.domain.model.ArticleStatus
import com.newsreview.workbench.domain.model.Evidence
import com.newsreview.workbench.ui.theme.AppColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)

fun relativeTime(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "刚刚"
        diff < hour -> "${diff / minute} 分钟前"
        diff < day -> "${diff / hour} 小时前"
        diff < 30 * day -> "${diff / day} 天前"
        else -> formatTime(millis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.InkSoft
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White,
            titleContentColor = AppColors.Ink,
            navigationIconContentColor = AppColors.Ink
        )
    )
}

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AppColors.CardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Line)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (title != null) {
                Text(title, fontWeight = FontWeight.SemiBold, color = AppColors.Ink)
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
fun StatusChip(status: ArticleStatus) {
    val (bg, fg) = when (status) {
        ArticleStatus.DRAFT -> AppColors.Line to AppColors.InkSoft
        ArticleStatus.IN_REVIEW -> AppColors.PrimaryContainer to AppColors.Primary
        ArticleStatus.RETURNED -> AppColors.DangerBg to AppColors.Danger
        ArticleStatus.SIGNED -> AppColors.SuccessBg to AppColors.Success
        ArticleStatus.PUBLISHED -> AppColors.SuccessBg to AppColors.Success
    }
    Tag(status.label, bg, fg)
}

@Composable
fun Tag(text: String, background: Color, foreground: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = background
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = foreground
        )
    }
}

@Composable
fun ConfidenceBar(confidence: Double, needsConfirm: Boolean, modifier: Modifier = Modifier) {
    val color = when {
        needsConfirm -> AppColors.Warn
        confidence >= 0.85 -> AppColors.Success
        else -> AppColors.Primary
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { confidence.toFloat().coerceIn(0f, 1f) },
            color = color,
            trackColor = AppColors.Line,
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = (if (needsConfirm) "待核实 " else "把握 ") + "${(confidence * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.InkSoft
        )
    }
}

/** 证据链：每一项都给出增量，让审核人看见机器为什么这么想。 */
@Composable
fun EvidenceList(evidence: List<Evidence>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.PageBg)
            .border(1.dp, AppColors.Line, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        evidence.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "+${"%.2f".format(item.delta)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.Primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(46.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.label, style = MaterialTheme.typography.bodySmall, color = AppColors.Ink)
                    val source = item.source
                    val quote = item.quote
                    if (source != null || quote != null) {
                        Text(
                            text = listOfNotNull(
                                source?.let { "来源：$it" },
                                quote?.let { "原文：$it" }
                            ).joinToString("　"),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.InkSoft
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KV(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.InkSoft,
            modifier = Modifier.width(84.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.Ink,
            modifier = Modifier.weight(1f)
        )
    }
}
