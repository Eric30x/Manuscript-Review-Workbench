package com.newsreview.workbench.ui.components

import androidx.compose.foundation.clickable
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
