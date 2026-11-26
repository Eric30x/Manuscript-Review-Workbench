package com.newsreview.workbench.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newsreview.workbench.domain.flow.ReviewFlow
import com.newsreview.workbench.domain.model.ArticleStatus
import com.newsreview.workbench.domain.model.ReviewStep
import com.newsreview.workbench.ui.theme.AppColors

/**
 * 初审 → 复审 → 签发 的进度条。
 * 绿色=已通过，深蓝=当前停留，灰色=还没走到。被退回时当前环节会重新变回深蓝。
 */
@Composable
fun StepProgress(
    currentStep: ReviewStep?,
    passedSteps: Set<ReviewStep>,
    status: ArticleStatus,
    modifier: Modifier = Modifier
) {
    val finished = status == ArticleStatus.SIGNED || status == ArticleStatus.PUBLISHED
    Row(
        modifier = modifier
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ReviewFlow.order.forEachIndexed { index, step ->
            val done = finished || step in passedSteps
            val active = !done && currentStep == step
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                done -> AppColors.Success
                                active -> AppColors.Primary
                                else -> AppColors.Line
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (done) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) Color.White else AppColors.InkSoft,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    step.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (done || active) AppColors.Ink else AppColors.InkSoft
                )
            }
            if (index < ReviewFlow.order.size - 1) {
                Box(
                    Modifier
                        .padding(bottom = 16.dp)
                        .width(24.dp)
                        .height(2.dp)
                        .background(if (finished || ReviewFlow.order[index] in passedSteps) AppColors.Success else AppColors.Line)
                )
            }
        }
    }
}
