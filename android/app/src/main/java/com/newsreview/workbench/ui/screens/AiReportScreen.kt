package com.newsreview.workbench.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newsreview.workbench.WorkbenchViewModel
import com.newsreview.workbench.domain.model.FindingLevel
import com.newsreview.workbench.domain.model.Role
import com.newsreview.workbench.ui.ScreenTopBar
import com.newsreview.workbench.ui.SectionCard
import com.newsreview.workbench.ui.Tag
import com.newsreview.workbench.ui.components.AgreementRow
import com.newsreview.workbench.ui.components.EmptyHint
import com.newsreview.workbench.ui.components.FindingCard
import com.newsreview.workbench.ui.components.InfoBanner
import com.newsreview.workbench.ui.components.SegmentedRow
import com.newsreview.workbench.ui.theme.AppColors

/**
 * AI 预检报告。这里要讲清楚三件事：
 * ① AI 不是一级审核，它不签字、不改变稿件状态；
 * ② 每条结论都给证据链，把握度是依据增量之和，不是凭空给的分数；
 * ③ 拿不准就标"待核实"，绝不替审核人下结论。
 */
@Composable
fun AiReportScreen(
    vm: WorkbenchViewModel,
    articleId: String,
    onBack: () -> Unit,
    onOpenArticle: () -> Unit,
    onOpenMaterials: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val article = state.article(articleId)
    if (article == null) {
        MissingArticleScreen(onBack = onBack)
        return
    }
    val report = remember(article) { vm.reportOf(article.id) }
    val role = state.role
    var filter by remember { mutableIntStateOf(0) }

    val all = report?.findings.orEmpty()
    val shown = all.filter { finding ->
        when (filter) {
            1 -> finding.level == FindingLevel.L1
            2 -> finding.level == FindingLevel.L2
            3 -> finding.level == FindingLevel.L3
            else -> true
        }
    }.sortedWith(compareBy({ !it.needsConfirm }, { it.level.ordinal }))

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        ScreenTopBar(
            title = "AI 预检报告",
            subtitle = article.title,
            onBack = onBack,
            actions = {
                TextButton(onClick = onOpenMaterials) { Text("材料") }
                TextButton(onClick = onOpenArticle) { Text("看原文") }
            }
        )

        SegmentedRow(
            options = listOf("全部", "文字层", "一致性", "判断层"),
            selected = filter,
            onSelect = { filter = it }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                InfoBanner(
                    "AI 预检不是一级审核：它不签字、不改状态，只是每一级都能调用的辅助证据层。" +
                        "下面每一条都带证据链，拿不准的会标成「待核实」。"
                )
            }

            item {
                SectionCard(title = "本次预检") {
                    Column {
                        Row {
                            Text(
                                "共 ${all.size} 条批注",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppColors.Ink
                            )
                            Spacer(Modifier.weight(1f))
                            Tag(
                                "${all.count { it.needsConfirm && !it.ignored && !it.resolved }} 条待核实",
                                AppColors.WarnBg,
                                AppColors.Warn
                            )
                            Spacer(Modifier.width(6.dp))
                            Tag(
                                "${all.count { it.resolved }} 条已采纳",
                                AppColors.SuccessBg,
                                AppColors.Success
                            )
                            Spacer(Modifier.width(6.dp))
                            Tag(
                                "${all.count { it.ignored }} 条已忽略",
                                AppColors.Line,
                                AppColors.InkSoft
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "L1 文字层 ${all.count { it.level == FindingLevel.L1 }} 条 · " +
                                "L2 一致性 ${all.count { it.level == FindingLevel.L2 }} 条 · " +
                                "L3 判断层 ${all.count { it.level == FindingLevel.L3 }} 条",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.InkSoft
                        )
                    }
                }
            }

            if (shown.isEmpty()) {
                item { EmptyHint("这一层没有待处理的批注。") }
            }

            items(items = shown) { finding ->
                FindingCard(
                    finding = finding,
                    showActions = role != null && role != Role.ADMIN,
                    onApply = { vm.applySuggestion(article.id, finding.id) },
                    onToggleIgnore = { vm.setFindingIgnored(article.id, finding.id, !finding.ignored) },
                    onComment = {
                        vm.addComment(
                            article.id,
                            finding.paragraphIndex,
                            finding.quote,
                            "AI 建议：${finding.title}" +
                                (finding.suggestions.firstOrNull()?.let { "，可改为「$it」" } ?: "")
                        )
                    }
                )
            }

            val agreements = report?.agreements.orEmpty()
            if (agreements.isNotEmpty()) {
                item {
                    SectionCard(title = "已核对一致（${agreements.size} 项）") {
                        Column {
                            agreements.forEach { agreement -> AgreementRow(agreement) }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "三层怎么分工") {
                    Column {
                        LevelLine("L1 文字层", "错别字、敏感词、规范用语。词表多模式匹配，能直接定位到字，可一键替换。")
                        LevelLine("L2 一致性", "人名职务、时间、地点、统计口径，与通知 / 签到表 / 议程交叉核对。")
                        LevelLine("L3 判断层", "引语有没有丢限定条件、结论有没有事实支撑、篇幅结构够不够。")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "把握度 = 证据链各项增量之和；低于阈值的自动进「待核实」，由审核人定夺。",
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
private fun LevelLine(title: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.Ink,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(84.dp)
        )
        Text(detail, style = MaterialTheme.typography.bodySmall, color = AppColors.InkSoft)
    }
}

