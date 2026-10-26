package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.data.*

/** Landscape prioritizes live readings over a tall launcher/action grid. */
@Composable
internal fun HudSummaryContent(
    landscape: Boolean, snapshot: HudSnapshot, action: HudActionState,
    onProfile: (String) -> Unit, onCapture: () -> Unit, onDetails: () -> Unit,
    onBoostRam: () -> Unit, quickAccess: @Composable () -> Unit
) {
    if (!landscape) {
        quickAccess()
        HudOverview(snapshot, action, onProfile, onCapture, onDetails, onBoostRam)
        return
    }
    LandscapeFrameReadings(snapshot)
    val clockWidth = hudLabelWidth(listOf("9999 MHz"), 14, numeric = true) + 20f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (HudLayoutPolicy.fits(maxWidth.value, listOf(clockWidth, clockWidth))) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LandscapeProcessor(snapshot, true, Modifier.weight(1f))
                LandscapeProcessor(snapshot, false, Modifier.weight(1f))
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LandscapeProcessor(snapshot, true, Modifier.fillMaxWidth())
            LandscapeProcessor(snapshot, false, Modifier.fillMaxWidth())
        }
    }
    HudProfiles(snapshot.profile, action.busy, onProfile)
    HudExpandableSection(hudString(R.string.hud_quick_actions)) { quickAccess() }
}

@Composable
private fun LandscapeFrameReadings(snapshot: HudSnapshot) {
    val metrics = listOf(HudMetric.FPS, HudMetric.FRAME_TIME, HudMetric.DISPLAY_HZ)
    val labels = metrics.map { hudString(it.label) }
    val values = metrics.map { metric -> snapshot[metric].let {
        if (it.status == "valid") hudString(R.string.hud_value, it.text(), it.unit) else "—"
    } }
    val widths = labels.indices.map { maxOf(hudLabelWidth(listOf(labels[it])), hudLabelWidth(listOf(values[it]), 14, numeric = true)) }
    HudSection(hudString(R.string.hud_group_frame)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell: @Composable (Int, Modifier) -> Unit = { i, modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(labels[i], fontSize = 12.sp, color = HudMuted)
                    Text(values[i], fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = if (i == 0) HudCyan else Color.White)
                }
            }
            if (HudLayoutPolicy.fits(maxWidth.value, widths, 8f)) {
                // Weighted by measured width so a long frame-time value cannot steal its neighbour's space.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    metrics.indices.forEach { cell(it, Modifier.weight(widths[it].coerceAtLeast(1f))) }
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                metrics.indices.forEach { cell(it, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun LandscapeProcessor(snapshot: HudSnapshot, cpu: Boolean, modifier: Modifier) {
    val clock = snapshot[if (cpu) HudMetric.CPU_CLOCK else HudMetric.GPU_CLOCK]
    val load = snapshot[if (cpu) HudMetric.CPU_LOAD else HudMetric.GPU_LOAD]
    val temp = snapshot[if (cpu) HudMetric.CPU_TEMP else HudMetric.GPU_TEMP]
    Column(modifier) {
        HudSection(hudString(if (cpu) R.string.hud_cpu else R.string.hud_gpu), accentColor = if (cpu) HudBlue else HudCyan) {
            Text(if (clock.status == "valid") hudString(R.string.hud_value, clock.text(), clock.unit) else "—",
                fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = Color.White)
            HudReadingLine(hudString(R.string.hud_load_short), load)
            // Reserve the same two lines even when one sensor is unavailable.
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(hudString(R.string.hud_temperature_short), fontSize = 12.sp, color = HudMuted)
                Text(if (temp.status == "valid") hudString(R.string.hud_value, temp.text(), temp.unit) else "—",
                    fontSize = 12.sp, color = Color.White)
            }
        }
    }
}
