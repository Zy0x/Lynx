package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.data.*

@Composable
internal fun HudOverview(snapshot: HudSnapshot, action: HudActionState, onProfile: (String) -> Unit,
    onCapture: () -> Unit, onTools: () -> Unit, onBoostRam: () -> Unit = {}) {
    HudSection(hudString(R.string.hud_group_frame)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fps = snapshot[HudMetric.FPS]
            val fpsWidth = hudLabelWidth(listOf(fps.text()), 26, numeric = true) + hudLabelWidth(listOf(hudString(R.string.hud_metric_fps))) + 14f
            val readings: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HudReadingLine(hudString(HudMetric.FRAME_TIME.label), snapshot[HudMetric.FRAME_TIME])
                    HudReadingLine(hudString(HudMetric.DISPLAY_HZ.label), snapshot[HudMetric.DISPLAY_HZ])
                }
            }
            if (maxWidth.value >= fpsWidth + 156f) Row(verticalAlignment = Alignment.CenterVertically) {
                HudFpsValue(fps, Modifier.width(fpsWidth.dp))
                Box(Modifier.weight(1f)) { readings() }
            } else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HudFpsValue(fps)
                readings()
            }
        }
    }
    HudSection(hudString(R.string.hud_group_processor)) {
        HudProcessorRow(hudString(R.string.hud_cpu), snapshot[HudMetric.CPU_LOAD], snapshot[HudMetric.CPU_CLOCK])
        HudProcessorRow(hudString(R.string.hud_gpu), snapshot[HudMetric.GPU_LOAD], snapshot[HudMetric.GPU_CLOCK])
    }
    HudProfiles(snapshot.profile, action.busy, onProfile)
    val labels = listOf(hudString(R.string.hud_capture_short), hudString(R.string.hud_tools))
    HudAdaptiveGrid(labels, maximum = 2) { index ->
        HudAction(labels[index], !action.busy, if (index == 0) onCapture else onTools, Modifier.weight(1f))
    }
}

@Composable
private fun HudFpsValue(fps: HudReading, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(fps.text(), fontSize = 26.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            color = if (fps.status == "valid") Color.White else HudMuted)
        Text(hudString(R.string.hud_metric_fps), fontSize = 12.sp, color = HudMuted)
    }
}

@Composable
private fun HudProcessorRow(label: String, load: HudReading, clock: HudReading) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        HudReadingLine(label, load)
        HudReadingLine(hudString(R.string.hud_clock_short), clock)
        LinearProgressIndicator(progress = ((load.value?.takeIf { load.status == "valid" } ?: 0.0) / 100).toFloat().coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth().height(2.dp), color = HudCyan, trackColor = HudElevated)
    }
}
