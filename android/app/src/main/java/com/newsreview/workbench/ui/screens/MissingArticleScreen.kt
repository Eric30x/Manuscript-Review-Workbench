package com.newsreview.workbench.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.newsreview.workbench.ui.ScreenTopBar
import com.newsreview.workbench.ui.theme.AppColors

/** 稿件 id 找不到时的兜底页（例如演示单据被重置）。 */
@Composable
fun MissingArticleScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.PageBg)
    ) {
        ScreenTopBar(title = "稿件不存在", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "没有找到这篇稿件。演示数据在进程重启后会重置，可以先返回工作台重新选择。",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Ink
            )
            OutlinedButton(onClick = onBack) { Text("返回上一页") }
        }
    }
}
