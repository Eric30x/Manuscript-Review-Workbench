package com.newsreview.workbench.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newsreview.workbench.WorkbenchViewModel
import com.newsreview.workbench.domain.model.Role
import com.newsreview.workbench.domain.rule.WordLists
import com.newsreview.workbench.ui.ScreenTopBar
import com.newsreview.workbench.ui.SectionCard
import com.newsreview.workbench.ui.components.InfoBanner
import com.newsreview.workbench.ui.theme.AppColors

/**
 * 词表。三类词对应 L1 文字层的三种处理方式：
 * 错别字命中即可换；敏感词只提示不判定；规范用语给出建议说法。
 */
@Composable
fun LibraryScreen(vm: WorkbenchViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.PageBg)
    ) {
        ScreenTopBar(
            title = "敏感词与规范用语",
            subtitle = "内置词表 ${WordLists.size} 条",
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp)
        ) {
            item {
                InfoBanner("词表决定 L1 文字层报什么。命中的词在正文里标红，审核人可以直接一键替换成规范写法。")
            }
            if (state.role == Role.ADMIN) {
                item {
                    Spacer(Modifier.height(10.dp))
                    InfoBanner(
                        "你是管理员角色。真实版本里这张表可以增删改，并记录谁在什么时候改了什么。",
                        accent = AppColors.Success
                    )
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                WordGroupCard(
                    title = "错别字表",
                    hint = "命中即可替换，属于低级错误，发布前必须清零。",
                    rules = WordLists.typo,
                    accent = AppColors.Danger
                )
            }
            item {
                Spacer(Modifier.height(12.dp))
                WordGroupCard(
                    title = "敏感词 / 禁用词",
                    hint = "只提示，由审核人判定是否可以对外发布（例如涉及内部材料的表述）。",
                    rules = WordLists.banned,
                    accent = AppColors.Warn
                )
            }
            item {
                Spacer(Modifier.height(12.dp))
                WordGroupCard(
                    title = "规范用语",
                    hint = "口语或不适合公开稿件的说法，给出更稳妥的替换。",
                    rules = WordLists.style,
                    accent = AppColors.Primary
                )
            }
        }
    }
}

@Composable
private fun WordGroupCard(
    title: String,
    hint: String,
    rules: List<WordLists.WordRule>,
    accent: Color
) {
    SectionCard(title = "$title（${rules.size} 条）") {
        Column {
            Text(hint, style = MaterialTheme.typography.labelSmall, color = AppColors.InkSoft)
            Spacer(Modifier.height(10.dp))
            rules.forEach { rule ->
                Column(Modifier.padding(vertical = 5.dp)) {
                    Row {
                        Text(
                            rule.word,
                            style = MaterialTheme.typography.bodyMedium,
                            color = accent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(120.dp)
                        )
                        Text(
                            "→",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.InkSoft,
                            modifier = Modifier.width(22.dp)
                        )
                        Text(
                            rule.suggestion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.Ink
                        )
                    }
                    Text(
                        rule.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.InkSoft,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

