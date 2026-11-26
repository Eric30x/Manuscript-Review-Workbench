package com.newsreview.workbench.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newsreview.workbench.WorkbenchViewModel
import com.newsreview.workbench.domain.model.Article
import com.newsreview.workbench.domain.model.ArticleStatus
import com.newsreview.workbench.ui.ScreenTopBar
import com.newsreview.workbench.ui.SectionCard
import com.newsreview.workbench.ui.components.ChannelPickerDialog
import com.newsreview.workbench.ui.components.InfoBanner
import com.newsreview.workbench.ui.theme.AppColors

/**
 * 写稿人编辑页。保存 = 生成一个新版本（留痕），并立即重跑一遍 AI 预检。
 * 送审会带上发布渠道，渠道决定了后续要满足哪些硬性要求。
 */
@Composable
fun EditScreen(
    vm: WorkbenchViewModel,
    articleId: String,
    onBack: () -> Unit,
    onSent: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val article = state.article(articleId)
    if (article == null) {
        MissingArticleScreen(onBack = onBack)
        return
    }

    var title by remember(articleId) { mutableStateOf(article.title) }
    var pushText by remember(articleId) { mutableStateOf(article.pushText) }
    var paragraphs by remember(articleId) { mutableStateOf(article.paragraphs) }
    var note by remember(articleId) { mutableStateOf(defaultNote(article)) }
    var showChannelPicker by remember { mutableStateOf(false) }

    fun save(): Boolean {
        val body = paragraphs.map { it.trim() }.filter { it.isNotEmpty() }
        if (title.isBlank()) {
            vm.notify("标题不能为空")
            return false
        }
        if (body.isEmpty()) {
            vm.notify("正文至少要有一段")
            return false
        }
        vm.saveRevision(article.id, title.trim(), pushText.trim(), body, note.ifBlank { "编辑稿件" })
        return true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.PageBg)
            .imePadding()
    ) {
        ScreenTopBar(
            title = "编辑稿件",
            subtitle = "保存生成新版本 · 保存后 AI 自动重检",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            InfoBanner(
                "当前 AI 批注 ${article.findings.size} 条，其中待核实 ${article.confirmCount} 条。" +
                    "保存后会按新正文重新跑一遍检查。"
            )
            Spacer(Modifier.height(12.dp))

            SectionCard(title = "基本信息") {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题（建议 24 字以内）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pushText,
                    onValueChange = { pushText = it },
                    label = { Text("推送文案（订阅号列表里独立出现的那句话）") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(12.dp))

            SectionCard(title = "正文") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    paragraphs.forEachIndexed { index, text ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "第 ${index + 1} 段",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppColors.InkSoft
                                )
                                Spacer(Modifier.weight(1f))
                                if (index > 0) {
                                    TextButton(onClick = { paragraphs = paragraphs.swap(index, index - 1) }) {
                                        Text("上移", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                if (index < paragraphs.lastIndex) {
                                    TextButton(onClick = { paragraphs = paragraphs.swap(index, index + 1) }) {
                                        Text("下移", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                if (paragraphs.size > 1) {
                                    TextButton(onClick = { paragraphs = paragraphs.removedAt(index) }) {
                                        Text("删除", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = text,
                                onValueChange = { paragraphs = paragraphs.replacedAt(index, it) },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { paragraphs = paragraphs + "" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("＋ 添加段落")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            SectionCard(title = "版本说明") {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("这一版改了什么（会记进版本记录）") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        Surface(color = Color.White, shadowElevation = 8.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { if (save()) onBack() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
                Button(
                    onClick = { if (save()) showChannelPicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (article.status == ArticleStatus.RETURNED) "保存并重新送审" else "保存并送审")
                }
            }
        }
    }

    if (showChannelPicker) {
        ChannelPickerDialog(
            onDismiss = { showChannelPicker = false },
            onConfirm = { channel ->
                showChannelPicker = false
                vm.submit(article.id, channel)
                onSent()
            }
        )
    }
}

private fun defaultNote(article: Article): String = when (article.status) {
    ArticleStatus.RETURNED -> "按退改意见修改"
    ArticleStatus.IN_REVIEW -> "修订"
    else -> "首次提交"
}

private fun List<String>.swap(from: Int, to: Int): List<String> =
    toMutableList().also { list ->
        val tmp = list[from]
        list[from] = list[to]
        list[to] = tmp
    }

private fun List<String>.replacedAt(index: Int, value: String): List<String> =
    toMutableList().also { it[index] = value }

private fun List<String>.removedAt(index: Int): List<String> =
    filterIndexed { i, _ -> i != index }
