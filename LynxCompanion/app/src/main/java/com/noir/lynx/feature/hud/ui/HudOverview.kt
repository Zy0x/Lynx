package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.data.*

@Composable
internal fun HudOverview(
    snapshot: HudSnapshot,
    action: HudActionState,
    onProfile: (String) -> Unit,
    onCapture: () -> Unit,
    onTools: () -> Unit,
    onBoostRam: () -> Unit = {}
) {
    HudSection(hudString(R.string.hud_group_frame)) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HudValue(snapshot[HudMetric.FPS], large = true)
            Text(
                text = hudString(R.string.hud_metric_fps),
                fontSize = 12.sp,
                color = HudMuted,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        HudReadingLine(
            label = hudString(HudMetric.FRAME_TIME.label),
            reading = snapshot[HudMetric.FRAME_TIME]
        )
        HudReadingLine(
            label = hudString(HudMetric.DISPLAY_HZ.label),
            reading = snapshot[HudMetric.DISPLAY_HZ]
        )
    }

    HudSection(hudString(R.string.hud_group_processor)) {
        HudProcessorBar(
            label = hudString(R.string.hud_cpu),
            load = snapshot[HudMetric.CPU_LOAD],
            clock = snapshot[HudMetric.CPU_CLOCK]
        )
        HorizontalDivider(color = HudBorder, thickness = 0.6.dp)
        HudProcessorBar(
            label = hudString(R.string.hud_gpu),
            load = snapshot[HudMetric.GPU_LOAD],
            clock = snapshot[HudMetric.GPU_CLOCK]
        )
    }

    HudProfiles(
        current = snapshot.profile,
        busy = action.busy,
        onProfile = onProfile
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HudAction(
            label = hudString(R.string.hud_capture_short),
            enabled = !action.busy,
            onClick = onCapture,
            modifier = Modifier.weight(1f)
        )
        HudAction(
            label = hudString(R.string.hud_tools),
            enabled = !action.busy,
            onClick = onTools,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HudProcessorBar(label: String, load: HudReading, clock: HudReading) {
    val progress = ((load.value?.takeIf { load.status == "valid" } ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HudReadingLine(label, load)
        HudReadingLine(hudString(R.string.hud_clock_short), clock)
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(3.5.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = HudCyan,
            trackColor = HudElevated
        )
    }
}
