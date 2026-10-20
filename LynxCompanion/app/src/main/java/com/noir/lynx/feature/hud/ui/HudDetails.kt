package com.noir.lynx.feature.hud.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.*

@Composable
internal fun HudDetails(snapshot: HudSnapshot) {
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HudSection(hudString(R.string.hud_group_frame)) {
            HudFrameChart(snapshot)
            listOf(HudMetric.FPS, HudMetric.FRAME_TIME, HudMetric.DISPLAY_HZ, HudMetric.FRAME_AVERAGE, HudMetric.FPS_LOW).forEach {
                HudReadingLine(hudString(it.label), snapshot[it])
            }
        }
        HudSection(hudString(R.string.hud_group_processor)) {
            listOf(HudMetric.CPU_LOAD, HudMetric.CPU_CLOCK, HudMetric.GPU_LOAD, HudMetric.GPU_CLOCK).forEach {
                HudReadingLine(hudString(it.label), snapshot[it])
            }
            snapshot.policies.forEach { (id, clock) -> HudReadingLine(id, clock) }
        }
        HudSection(hudString(R.string.hud_group_memory_temperature)) {
            listOf(HudMetric.RAM_USED, HudMetric.CPU_TEMP, HudMetric.SOC_TEMP, HudMetric.GPU_TEMP).forEach {
                HudReadingLine(hudString(it.label), snapshot[it])
            }
        }
        HudSection(hudString(R.string.hud_group_battery)) {
            listOf(HudMetric.BATTERY_LEVEL, HudMetric.BATTERY_TEMP, HudMetric.BATTERY_CURRENT, HudMetric.BATTERY_POWER, HudMetric.BATTERY_VOLTAGE).forEach {
                HudReadingLine(hudString(it.label), snapshot[it])
            }
            Text(hudString(when (snapshot.charging) {
                true -> R.string.hud_charging; false -> R.string.hud_discharging; else -> R.string.hud_unsupported
            }), fontSize = 12.sp, color = HudMuted)
        }
        HudDisclosure(hudString(R.string.hud_diagnostics), diagnostics) { diagnostics = !diagnostics }
        if (diagnostics) HudSection(hudString(R.string.hud_diagnostics)) {
            Text(hudString(R.string.hud_app, snapshot.packageName.ifBlank { "—" }), fontSize = 12.sp)
            Text(hudString(R.string.hud_layer, snapshot.layer.ifBlank { "—" }), fontSize = 12.sp, color = HudMuted)
            Text(hudString(R.string.hud_collection, snapshot.collectionMs.toString()), fontSize = 12.sp, color = HudMuted)
            Text(hudString(R.string.hud_frame_note), fontSize = 12.sp, color = HudMuted)
            Text(hudString(R.string.hud_clock_note), fontSize = 12.sp, color = HudMuted)
            Text(hudString(R.string.hud_sensor_note), fontSize = 12.sp, color = HudMuted)
            HudMetric.values().forEach { metric -> HudSourceDetails(hudString(metric.label), snapshot[metric]) }
            snapshot.policies.forEach { (id, clock) -> HudSourceDetails(id, clock) }
        }
    }
}

@Composable
private fun HudFrameChart(snapshot: HudSnapshot) {
    val history = snapshot.history
    if (history.size > 1) {
        val low = history.minOrNull() ?: 0f
        val high = history.maxOrNull() ?: 0f
        Text(hudString(R.string.hud_frame_chart), fontSize = 12.sp, color = HudMuted)
        Text(hudString(R.string.hud_chart_range, "%.1f".format(low), "%.1f".format(high)), fontSize = 12.sp, color = HudMuted)
        val description = hudString(R.string.hud_frame_chart)
        Canvas(Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = description }) {
            val max = high.coerceAtLeast(1f)
            val path = Path()
            history.forEachIndexed { i, v ->
                val x = i.toFloat() / history.lastIndex * size.width
                val y = size.height - v / max * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, HudCyan, style = Stroke(1.5.dp.toPx()))
        }
    }
}

@Composable
private fun HudSourceDetails(label: String, reading: HudReading) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        HorizontalDivider(color = HudMuted.copy(alpha = 0.12f))
        Text(label, fontSize = 12.sp)
        Text(statusText(reading.status), fontSize = 12.sp, color = HudMuted)
        if (reading.source.isNotBlank()) Text(hudString(R.string.hud_source, reading.source), fontSize = 12.sp, color = HudMuted)
        if (reading.atMs > 0) Text(hudString(R.string.hud_age, (SystemClock.elapsedRealtime() - reading.atMs).coerceAtLeast(0).toString()),
            fontSize = 12.sp, color = HudMuted)
    }
}
