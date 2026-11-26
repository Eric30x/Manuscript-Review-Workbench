package com.newsreview.workbench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newsreview.workbench.ui.WorkbenchApp
import com.newsreview.workbench.ui.theme.NewsReviewTheme

/**
 * 工程里唯一的 Activity。
 *
 * 分层：Activity → WorkbenchApp（Compose 导航）→ 各 Screen → WorkbenchViewModel → WorkbenchRepository → domain（纯 Kotlin）。
 * 审核规则、流程状态机、词库匹配全部待在 domain / data 包，不碰任何 Android API，
 * 所以核心逻辑可以脱离模拟器直接跑单元测试。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NewsReviewTheme {
                // 整个界面树共用一个 ViewModel，避免各页面各持一份状态导致不同步
                val viewModel: WorkbenchViewModel = viewModel()
                WorkbenchApp(viewModel)
            }
        }
    }
}

