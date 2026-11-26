package com.newsreview.workbench.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newsreview.workbench.WorkbenchViewModel
import com.newsreview.workbench.domain.model.MaterialKind
import com.newsreview.workbench.domain.model.ReferenceMaterial
import com.newsreview.workbench.domain.model.TranscriptCue
import com.newsreview.workbench.ui.ScreenTopBar
import com.newsreview.workbench.ui.SectionCard
import com.newsreview.workbench.ui.Tag
import com.newsreview.workbench.ui.components.InfoBanner
import com.newsreview.workbench.ui.theme.AppColors

/**
 * 参考资料库。AI 的每条依据都指向这里的某一行，审核人可以顺着依据回溯到原始材料。
 * 演示数据全部虚构，且只存在本机。
 */
@Composable
fun MaterialsScreen(vm: WorkbenchViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.PageBg)
    ) {
        ScreenTopBar(
            title = "参考资料",
            subtitle = "${state.materials.size} 份材料 · ${state.transcript.size} 条录音转写",
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                InfoBanner("AI 的每条依据都能指回下面的具体行；引语还会跟录音逐句比对。材料只在本机，不出设备。")
            }

            items(state.materials, key = { it.id }) { material ->
                MaterialCard(material)
            }

            if (state.transcript.isNotEmpty()) {
                item {
                    SectionCard(title = "采访录音转写（${state.transcript.size} 条）") {
                        Column {
                            state.transcript.forEach { cue ->
                                Text(
                                    "[${cueTime(cue)}] ${cue.speaker}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AppColors.Primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    cue.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.Ink
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                            Text(
                                "稿件里的引语会跟这里逐句比对：丢掉了「如果评估通过」这类限定条件，会被单独标出来。",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.InkSoft
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialCard(material: ReferenceMaterial) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag(material.kind.label, AppColors.PrimaryContainer, AppColors.Primary)
            Spacer(Modifier.width(8.dp))
            Text(
                material.name,
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.Ink,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            material.lines.forEachIndexed { index, line ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.PageBg,
                    border = BorderStroke(1.dp, AppColors.Line),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.InkSoft,
                            modifier = Modifier.width(18.dp)
                        )
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.Ink
                        )
                    }
                }
            }
        }
    }
}

private fun cueTime(cue: TranscriptCue): String {
    val totalSeconds = cue.startMs / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

