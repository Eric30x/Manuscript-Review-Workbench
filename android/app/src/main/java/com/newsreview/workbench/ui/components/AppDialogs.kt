package com.newsreview.workbench.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import com.newsreview.workbench.domain.model.Channel
import com.newsreview.workbench.ui.theme.AppColors

/** 送审前选发布渠道。渠道决定了后面要满足哪些硬性要求（例如官网必须有三审署名行）。 */
@Composable
fun ChannelPickerDialog(onDismiss: () -> Unit, onConfirm: (Channel) -> Unit) {
    var picked by remember { mutableStateOf(Channel.COLLEGE_WECHAT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择发布渠道") },
        text = {
            Column {
                Text(
                    "渠道决定后续要满足的硬性要求。",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.InkSoft
                )
                Spacer(Modifier.height(8.dp))
                Channel.entries.forEach { channel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { picked = channel }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = picked == channel, onClick = { picked = channel })
                        Column {
                            Text(channel.label, color = AppColors.Ink)
                            Text(
                                channel.hint,
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.InkSoft
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(picked) }) { Text("确认送审") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 通用"填一句话再确认"弹窗：退回修改要填意见，保存要填版本说明。 */
@Composable
fun NoteInputDialog(
    title: String,
    label: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(label) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note) }, enabled = note.isNotBlank()) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun WordUploadDialog(
    revision: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onUpload: (Uri, List<Uri>) -> Unit
) {
    val docx = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    var manuscript by remember { mutableStateOf<Uri?>(null) }
    var materials by remember { mutableStateOf(emptyList<Uri>()) }
    val openManuscript = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) manuscript = it
    }
    val openMaterials = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        if (it.isNotEmpty()) materials = it
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (revision) "上传新版 Word" else "上传稿件") },
        text = {
            Column {
                TextButton(onClick = { openManuscript.launch(arrayOf(docx, "application/octet-stream")) }, enabled = !busy) {
                    Text(if (manuscript == null) "选择稿件 .docx" else "稿件已选 · 重新选择")
                }
                TextButton(onClick = { openMaterials.launch(arrayOf(docx, "application/octet-stream")) }, enabled = !busy) {
                    Text("选择参考材料（已选 ${materials.size} 份）")
                }
                if (revision) Text("未选择新材料时会沿用上一版材料。")
            }
        },
        confirmButton = {
            TextButton(
                enabled = manuscript != null && !busy,
                onClick = { manuscript?.let { onUpload(it, materials) } }
            ) { Text(if (busy) "上传中…" else "上传") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } }
    )
}

@Composable
fun AnchoredNoteDialog(
    paragraphs: List<String>,
    initialParagraph: Int,
    initialQuote: String?,
    returning: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int, String, String) -> Unit
) {
    var index by remember { mutableStateOf(initialParagraph.coerceIn(0, paragraphs.lastIndex)) }
    var quote by remember { mutableStateOf(initialQuote ?: paragraphs[index]) }
    var note by remember { mutableStateOf("") }
    fun select(position: Int) {
        index = position
        quote = paragraphs[index]
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (returning) "针对原句退回" else "添加段落批注") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { select(index - 1) }, enabled = index > 0) { Text("上一段") }
                    Text("第 ${index + 1} 段", modifier = Modifier.weight(1f))
                    TextButton(onClick = { select(index + 1) }, enabled = index < paragraphs.lastIndex) {
                        Text("下一段")
                    }
                }
                Text(paragraphs[index], style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = quote, onValueChange = { quote = it }, label = { Text("引用原句或其中一部分") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("审核意见") },
                    minLines = 2, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(index, quote.trim(), note.trim()) },
                enabled = quote.isNotBlank() && quote.trim() in paragraphs[index] && note.isNotBlank()
            ) { Text(if (returning) "确认退回" else "保存批注") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
