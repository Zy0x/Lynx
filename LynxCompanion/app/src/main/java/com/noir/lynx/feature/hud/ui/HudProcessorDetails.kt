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
import com.noir.lynx.feature.hud.data.*

/** Keeps measured clock, utilization and sensor readings separate. No shell or guessed values. */
@Composable
internal fun HudProcessorDetails(snapshot: HudSnapshot) {
    ProcessorCard(hudString(R.string.hud_cpu), snapshot[HudMetric.CPU_CLOCK], snapshot[HudMetric.CPU_LOAD], snapshot[HudMetric.CPU_TEMP], HudBlue) {
        if (snapshot.policies.isNotEmpty()) HudExpandableSection(hudString(R.string.hud_cpu_policies)) {
            snapshot.policies.forEach { (id, clock) -> HudReadingLine(id, clock) }
        }
    }
    ProcessorCard(hudString(R.string.hud_gpu), snapshot[HudMetric.GPU_CLOCK], snapshot[HudMetric.GPU_LOAD], snapshot[HudMetric.GPU_TEMP], HudCyan)
}

@Composable
private fun ProcessorCard(title: String, clock: HudReading, load: HudReading, temp: HudReading, accent: Color, extra: @Composable () -> Unit = {}) {
    HudSection(title, accentColor = accent) {
        val clockText = if (clock.status == "valid") hudString(R.string.hud_value, clock.text(), clock.unit) else "—"
        val loadText = if (load.status == "valid") hudString(R.string.hud_value, load.text(), load.unit) else "—"
        val clockWidth = hudLabelWidth(listOf(clockText), 18, numeric = true)
        val loadWidth = maxOf(hudLabelWidth(listOf(loadText), 12, numeric = true), hudLabelWidth(listOf(hudString(R.string.hud_load_short))))
        val frequency: @Composable () -> Unit = {
            Text(clockText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = Color.White)
        }
        val utilization: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(hudString(R.string.hud_load_short), fontSize = 12.sp, color = HudMuted)
                Text(loadText, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = accent)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (HudLayoutPolicy.fits(maxWidth.value, listOf(clockWidth, loadWidth), 12f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    frequency(); utilization()
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { frequency(); utilization() }
        }
        if (load.status == "valid" && load.value != null) LinearProgressIndicator(
            progress = { (load.value / 100.0).toFloat().coerceIn(0f, 1f) }, color = accent,
            trackColor = HudElevated, modifier = Modifier.fillMaxWidth().height(3.dp))
        HudReadingLine(hudString(R.string.hud_temperature_short), temp)
        if (clock.status != "valid") Availability(hudString(R.string.hud_clock_short), clock)
        if (load.status != "valid") Availability(hudString(R.string.hud_load_short), load)
        extra()
    }
}

@Composable
private fun Availability(label: String, reading: HudReading) {
    Text(hudString(R.string.hud_metric_value_description, label, statusText(reading.status)), fontSize = 12.sp, color = HudOrange)
}
