package com.noir.lynx.feature.hud.ui

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.*

@Composable
internal fun HudDetails(snapshot: HudSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 1. Frame & Display Telemetry Card
        val displayHz = snapshot[HudMetric.DISPLAY_HZ]
        HudSection(
            title = hudString(R.string.hud_group_frame),
            badge = if (displayHz.status == "valid") "${displayHz.text()} ${displayHz.unit}" else null,
            accentColor = HudCyan
        ) {
            HudFrameChart(snapshot)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HudMetricTile(
                    label = hudString(HudMetric.FPS.label),
                    reading = snapshot[HudMetric.FPS],
                    modifier = Modifier.weight(1f),
                    accentColor = HudCyan
                )
                HudMetricTile(
                    label = hudString(HudMetric.FPS_LOW.label),
                    reading = snapshot[HudMetric.FPS_LOW],
                    modifier = Modifier.weight(1f),
                    accentColor = HudOrange
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HudMetricTile(
                    label = hudString(HudMetric.FRAME_TIME.label),
                    reading = snapshot[HudMetric.FRAME_TIME],
                    modifier = Modifier.weight(1f)
                )
                HudMetricTile(
                    label = hudString(HudMetric.FRAME_AVERAGE.label),
                    reading = snapshot[HudMetric.FRAME_AVERAGE],
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 2. CPU & GPU Telemetry Card
        HudSection(
            title = hudString(R.string.hud_group_processor),
            accentColor = HudBlue
        ) {
            val cpuLoad = snapshot[HudMetric.CPU_LOAD]
            val gpuLoad = snapshot[HudMetric.GPU_LOAD]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HudMetricTile(
                    label = hudString(HudMetric.CPU_LOAD.label),
                    reading = cpuLoad,
                    modifier = Modifier.weight(1f),
                    accentColor = HudCyan,
                    progress = ((cpuLoad.value?.takeIf { cpuLoad.status == "valid" } ?: 0.0) / 100.0).toFloat()
                )
                HudMetricTile(
                    label = hudString(HudMetric.CPU_CLOCK.label),
                    reading = snapshot[HudMetric.CPU_CLOCK],
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HudMetricTile(
                    label = hudString(HudMetric.GPU_LOAD.label),
                    reading = gpuLoad,
                    modifier = Modifier.weight(1f),
                    accentColor = HudBlue,
                    progress = ((gpuLoad.value?.takeIf { gpuLoad.status == "valid" } ?: 0.0) / 100.0).toFloat()
                )
                HudMetricTile(
                    label = hudString(HudMetric.GPU_CLOCK.label),
                    reading = snapshot[HudMetric.GPU_CLOCK],
                    modifier = Modifier.weight(1f)
                )
            }
            if (snapshot.policies.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    snapshot.policies.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            pair.forEach { (id, clock) ->
                                HudMetricTile(
                                    label = id.uppercase(),
                                    reading = clock,
                                    modifier = Modifier.weight(1f),
                                    accentColor = Color(0xFFE2E8F0)
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 3. RAM & Thermal Matrix Card
        HudSection(
            title = hudString(R.string.hud_group_memory_temperature),
            accentColor = HudOrange
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HudMetricTile(
                    label = hudString(HudMetric.RAM_USED.label),
                    reading = snapshot[HudMetric.RAM_USED],
                    modifier = Modifier.weight(1f),
                    accentColor = HudPurple
                )
                val cpuTemp = snapshot[HudMetric.CPU_TEMP]
                HudMetricTile(
                    label = hudString(HudMetric.CPU_TEMP.label),
                    reading = cpuTemp,
                    modifier = Modifier.weight(1f),
                    accentColor = tempAccent(cpuTemp.value)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val socTemp = snapshot[HudMetric.SOC_TEMP]
                HudMetricTile(
                    label = hudString(HudMetric.SOC_TEMP.label),
                    reading = socTemp,
                    modifier = Modifier.weight(1f),
                    accentColor = tempAccent(socTemp.value)
                )
                val gpuTemp = snapshot[HudMetric.GPU_TEMP]
                HudMetricTile(
                    label = hudString(HudMetric.GPU_TEMP.label),
                    reading = gpuTemp,
                    modifier = Modifier.weight(1f),
                    accentColor = tempAccent(gpuTemp.value)
                )
            }
        }

        // 4. Battery & Power Flow Card
        val chargingBadge = hudString(
            when (snapshot.charging) {
                true -> R.string.hud_charging
                false -> R.string.hud_discharging
                else -> R.string.hud_unsupported
            }
        )
        HudSection(
            title = hudString(R.string.hud_group_battery),
            badge = chargingBadge,
            accentColor = if (snapshot.charging == true) HudCyan else HudBlue
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HudMetricTile(
                    label = hudString(HudMetric.BATTERY_LEVEL.label),
                    reading = snapshot[HudMetric.BATTERY_LEVEL],
                    modifier = Modifier.weight(1f),
                    accentColor = HudCyan
                )
                val batTemp = snapshot[HudMetric.BATTERY_TEMP]
                HudMetricTile(
                    label = hudString(HudMetric.BATTERY_TEMP.label),
                    reading = batTemp,
                    modifier = Modifier.weight(1f),
                    accentColor = tempAccent(batTemp.value)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HudMetricTile(
                    label = hudString(HudMetric.BATTERY_CURRENT.label),
                    reading = snapshot[HudMetric.BATTERY_CURRENT],
                    modifier = Modifier.weight(1f)
                )
                HudMetricTile(
                    label = hudString(HudMetric.BATTERY_POWER.label),
                    reading = snapshot[HudMetric.BATTERY_POWER],
                    modifier = Modifier.weight(1f)
                )
            }
            HudReadingLine(
                label = hudString(HudMetric.BATTERY_VOLTAGE.label),
                reading = snapshot[HudMetric.BATTERY_VOLTAGE]
            )
        }

        // 5. Collapsible Source & Diagnostics Section
        HudExpandableSection(
            title = hudString(R.string.hud_diagnostics),
            detail = "${snapshot.collectionMs} ms",
            initiallyOpen = false,
            accentColor = HudMuted
        ) {
            Text(hudString(R.string.hud_app, snapshot.packageName.ifBlank { "—" }), fontSize = 11.5.sp)
            Text(hudString(R.string.hud_layer, snapshot.layer.ifBlank { "—" }), fontSize = 11.sp, color = HudMuted)
            Text(hudString(R.string.hud_collection, snapshot.collectionMs.toString()), fontSize = 11.sp, color = HudMuted)
            Text(hudString(R.string.hud_frame_note), fontSize = 10.5.sp, color = HudMuted)
            Text(hudString(R.string.hud_clock_note), fontSize = 10.5.sp, color = HudMuted)
            Text(hudString(R.string.hud_sensor_note), fontSize = 10.5.sp, color = HudMuted)
            HudMetric.values().forEach { metric ->
                HudSourceDetails(hudString(metric.label), snapshot[metric])
            }
            snapshot.policies.forEach { (id, clock) ->
                HudSourceDetails(id, clock)
            }
        }
    }
}

private fun tempAccent(tempC: Double?): Color = when {
    tempC == null -> Color.White
    tempC >= 48.0 -> HudRed
    tempC >= 42.0 -> HudOrange
    else -> HudCyan
}

@Composable
private fun HudFrameChart(snapshot: HudSnapshot) {
    val history = snapshot.history
    if (history.size > 1) {
        val low = history.minOrNull() ?: 0f
        val high = history.maxOrNull() ?: 0f
        val description = hudString(R.string.hud_frame_chart)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = HudElevated,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(0.7.dp, HudBorder)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = hudString(R.string.hud_frame_chart),
                        fontSize = 10.5.sp,
                        color = HudMuted
                    )
                    Text(
                        text = hudString(R.string.hud_chart_range, "%.1f".format(low), "%.1f".format(high)),
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = HudCyan
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .semantics { contentDescription = description }
                ) {
                    val max = high.coerceAtLeast(1f)
                    // Subtle horizontal reference grid lines
                    val midY = size.height * 0.5f
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f),
                        start = Offset(0f, midY),
                        end = Offset(size.width, midY),
                        strokeWidth = 1.dp.toPx()
                    )
                    val path = Path()
                    val fillPath = Path()
                    history.forEachIndexed { i, v ->
                        val x = i.toFloat() / history.lastIndex.coerceAtLeast(1) * size.width
                        val y = size.height - (v / max).coerceIn(0f, 1f) * (size.height - 4.dp.toPx()) - 2.dp.toPx()
                        if (i == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, size.height)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }
                    fillPath.lineTo(size.width, size.height)
                    fillPath.close()
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(HudCyan.copy(alpha = 0.25f), Color.Transparent)
                        )
                    )
                    drawPath(path, HudCyan, style = Stroke(1.8.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun HudSourceDetails(label: String, reading: HudReading) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        HorizontalDivider(color = HudMuted.copy(alpha = 0.12f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE2E8F0))
            Text(statusText(reading.status), fontSize = 10.5.sp, color = HudCyan)
        }
        if (reading.source.isNotBlank()) {
            Text(hudString(R.string.hud_source, reading.source), fontSize = 10.5.sp, color = HudMuted)
        }
        if (reading.atMs > 0) {
            Text(
                hudString(R.string.hud_age, (SystemClock.elapsedRealtime() - reading.atMs).coerceAtLeast(0).toString()),
                fontSize = 10.5.sp,
                color = HudMuted
            )
        }
    }
}
