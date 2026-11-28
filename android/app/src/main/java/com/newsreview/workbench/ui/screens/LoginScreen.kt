package com.newsreview.workbench.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newsreview.workbench.WorkbenchViewModel
import com.newsreview.workbench.domain.model.Role
import com.newsreview.workbench.ui.SectionCard
import com.newsreview.workbench.ui.Tag
import com.newsreview.workbench.ui.components.InfoBanner
import com.newsreview.workbench.ui.theme.AppColors

/** 可选本地样例，或使用后端配置的演示口令进入。 */
@Composable
fun LoginScreen(
    vm: WorkbenchViewModel,
    onEnter: (Role) -> Unit,
    onServerEnter: (Role, String, String) -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var connected by remember { mutableStateOf(vm.serverAddress.isNotBlank()) }
    var address by remember { mutableStateOf(vm.serverAddress) }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.PageBg)
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(36.dp))
        Text(
            "审稿工作台",
            style = MaterialTheme.typography.headlineMedium,
            color = AppColors.Ink,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "学校网络平台宣传报道 · 三审流程工作台",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.InkSoft
        )
        Spacer(Modifier.height(18.dp))

        SectionCard(title = "通知里的那条硬要求") {
            Text(
                "对学校网络平台宣传报道内容，需按初审——复审——终审流程由不同审核人员把关，" +
                    "三审人员姓名须标注在每篇报道的文末。语言文字、图片、视频是否准确，信息是否客观真实，" +
                    "发布依据与版权归属，政治导向与是否涉密，都在审核范围之内。",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Ink
            )
        }
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("连接团队后端", modifier = Modifier.weight(1f))
            Switch(checked = connected, onCheckedChange = { connected = it }, enabled = !state.busy)
        }
        if (connected) {
            OutlinedTextField(address, { address = it }, label = { Text("后端地址") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
            OutlinedTextField(password, { password = it }, label = { Text("演示口令") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !state.busy,
                visualTransformation = PasswordVisualTransformation())
        } else {
            InfoBanner("本地演示使用内置样例。打开「连接团队后端」即可上传自己的稿件并运行真实审核。")
        }
        Spacer(Modifier.height(20.dp))

        Text(
            "选择进入角色",
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.Ink,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Spacer(Modifier.height(10.dp))
        Role.entries.filter { !connected || it !in listOf(Role.PUBLISHER, Role.ADMIN) }.forEach { role ->
            RoleCard(
                role = role,
                name = vm.roleNames[role].orEmpty(),
                enabled = !state.busy && (!connected || (address.isNotBlank() && password.isNotBlank())),
                onClick = { if (connected) onServerEnter(role, address, password) else onEnter(role) }
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun RoleCard(role: Role, name: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = AppColors.CardBg,
        border = BorderStroke(1.dp, AppColors.Line)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        role.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.Ink,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Tag(name, AppColors.PrimaryContainer, AppColors.Primary)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    role.duty,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.InkSoft
                )
            }
            Spacer(Modifier.width(10.dp))
            Text("进入", style = MaterialTheme.typography.labelLarge, color = AppColors.Primary)
        }
    }
}

