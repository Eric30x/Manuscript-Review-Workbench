package com.newsreview.workbench.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 界面配色取自设计稿：白底、浅灰卡片、深蓝黑主色。 */
object AppColors {
    val Ink = Color(0xFF1B1D21)
    val InkSoft = Color(0xFF6B7280)
    val Primary = Color(0xFF1F3A5F)
    val PrimaryContainer = Color(0xFFDCE7F5)
    val Line = Color(0xFFE3E7EE)
    val CardBg = Color(0xFFFFFFFF)
    val PageBg = Color(0xFFF6F7F9)

    val Success = Color(0xFF2F9E68)
    val SuccessBg = Color(0xFFE4F4EC)
    val Warn = Color(0xFFB5761A)
    val WarnBg = Color(0xFFFDF3E2)
    val Danger = Color(0xFFC0453F)
    val DangerBg = Color(0xFFFBE9E7)
    val MarkRed = Color(0xFFD9534F)

    // 版面分层的补充色：主色仍是深蓝黑，下面几个用来把「原文区 / 批注区 / 操作区」分开。
    val PrimaryDeep = Color(0xFF16293F)
    val Accent = Color(0xFF2F6E8F)
    val AccentSoft = Color(0xFFE7F0F5)
    val SurfaceSunken = Color(0xFFEFF2F6)
    val NoteBg = Color(0xFFF2F6FA)
}

private val LightColors = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color.White,
    primaryContainer = AppColors.PrimaryContainer,
    onPrimaryContainer = AppColors.Primary,
    secondary = AppColors.Accent,
    background = AppColors.PageBg,
    onBackground = AppColors.Ink,
    surface = AppColors.CardBg,
    onSurface = AppColors.Ink,
    surfaceVariant = AppColors.SurfaceSunken,
    secondaryContainer = AppColors.AccentSoft,
    onSecondaryContainer = AppColors.Accent,
    onSurfaceVariant = AppColors.InkSoft,
    outline = AppColors.Line,
    error = AppColors.Danger
)

@Composable
fun NewsReviewTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // 演示阶段先固定浅色：刊物审稿讲究版面稳定，与设计稿一致
    MaterialTheme(colorScheme = LightColors, content = content)
}
