package com.newsreview.workbench.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newsreview.workbench.domain.model.Finding
import com.newsreview.workbench.domain.model.FindingLevel
import com.newsreview.workbench.ui.ConfidenceBar
import com.newsreview.workbench.ui.EvidenceList
import com.newsreview.workbench.ui.SectionCard
import com.newsreview.workbench.ui.Tag
import com.newsreview.workbench.ui.theme.AppColors

/**
 * 一条 AI 批注。卡片顺序固定为：级别 / 类型 → 定位 → 结论 → 把握度 → 证据链 → 可执行动作。
 * 之所以把"证据链"摆在动作前面，是为了让审核人先看清依据再决定收不收。
 */
@Composable
fun FindingCard(
    finding: Finding,
    showActions: Boolean,
    onApply: () -> Unit,
    onToggleIgnore: () -> Unit,
    onComment: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (levelBg, levelFg) = when (finding.level) {
        FindingLevel.L1 -> AppColors.DangerBg to AppColors.Danger
        FindingLevel.L2 -> AppColors.WarnBg to AppColors.Warn
        FindingLevel.L3 -> AppColors.PrimaryContainer to AppColors.Primary
    }
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag(finding.level.label, levelBg, levelFg)
            Spacer(Modifier.width(6.dp))
            Tag(finding.kind.label, AppColors.Line, AppColors.InkSoft)
            Spacer(Modifier.weight(1f))
            when {
                finding.resolved -> Tag("已采纳", AppColors.SuccessBg, AppColors.Success)
                finding.ignored -> Tag("已忽略", AppColors.Line, AppColors.InkSoft)
                finding.needsConfirm -> Tag("待核实", AppColors.WarnBg, AppColors.Warn)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            finding.title,
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.Ink,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "定位：第 ${finding.paragraphIndex + 1} 段　“${finding.quote}”",
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.InkSoft
        )
        Spacer(Modifier.height(6.dp))
        Text(finding.detail, style = MaterialTheme.typography.bodySmall, color = AppColors.Ink)
        Spacer(Modifier.height(10.dp))
        ConfidenceBar(finding.confidence, finding.needsConfirm)
        Spacer(Modifier.height(10.dp))
        Text(
            "证据链（把握度 = 下列依据增量之和）",
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.InkSoft
        )
        Spacer(Modifier.height(6.dp))
        EvidenceList(finding.evidence)
        if (finding.suggestions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "建议改法：${finding.suggestions.joinToString("；")}",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Primary
            )
        }
        if (showActions) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (finding.suggestions.isNotEmpty() && !finding.resolved) {
                    FilledTonalButton(onClick = onApply) { Text("采纳建议") }
                }
                OutlinedButton(onClick = onToggleIgnore) {
                    Text(if (finding.ignored) "恢复" else "忽略")
                }
                if (!finding.resolved) {
                    TextButton(onClick = onComment) { Text("加批注") }
                }
            }
        }
    }
}

