package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.*

@Composable
internal fun HudDetails(snapshot: HudSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HudExpandableSection(
            title = hudString(R.string.hud_group_frame),
            initiallyOpen = true
        ) {
            HudFrameChart(snapshot)
            listOf(
                HudMetric.FPS,
                HudMetric.FRAME_TIME,
                HudMetric.DISPLAY_HZ,
                HudMetric.FRAME_AVERAGE,
                HudMetric.FPS_LOW
            ).forEach { metric ->
                HudReadingLine(hudString(metric.label), snapshot[metric])
            }
        }

        HudExpandableSection(
            title = hudString(R.string.hud_group_processor),
            initiallyOpen = true
        ) {
            listOf(
                HudMetric.CPU_LOAD,
                HudMetric.CPU_CLOCK,
                HudMetric.GPU_LOAD,
                HudMetric.GPU_CLOCK
            ).forEach { metric ->
                HudReadingLine(hudString(metric.label), snapshot[metric])
            }
            snapshot.policies.forEach { (id, clock) ->
                HudReadingLine(id, clock)
            }
        }

        HudExpandableSection(
            title = hudString(R.string.hud_group_memory_temperature),
            initiallyOpen = true
        ) {
            listOf(
                HudMetric.RAM_USED,
                HudMetric.CPU_TEMP,
                HudMetric.SOC_TEMP,
                HudMetric.GPU_TEMP
            ).forEach { metric ->
                HudReadingLine(hudString(metric.label), snapshot[metric])
            }
        }

        val chargingDetail = hudString(
            when (snapshot.charging) {
                true -> R.string.hud_charging
                false -> R.string.hud_discharging
                else -> R.string.hud_unsupported
            }
        )
        HudExpandableSection(
            title = hudString(R.string.hud_group_battery),
            detail = chargingDetail,
            initiallyOpen = false
        ) {
            listOf(
                HudMetric.BATTERY_LEVEL,
                HudMetric.BATTERY_TEMP,
                HudMetric.BATTERY_CURRENT,
                HudMetric.BATTERY_VOLTAGE,
                HudMetric.BATTERY_POWER
            ).forEach { metric ->
                HudReadingLine(hudString(metric.label), snapshot[metric])
            }
        }
    }
}

@Composable
private fun HudFrameChart(snapshot: HudSnapshot) {
    val history = snapshot.history
    if (history.size > 1) {
        val low = history.minOrNull() ?: 0f
        val high = history.maxOrNull() ?: 0f
        val description = hudString(R.string.hud_frame_chart)
        Text(
            text = hudString(R.string.hud_chart_range, "%.1f".format(low), "%.1f".format(high)),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = HudMuted
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .semantics { contentDescription = description }
        ) {
            val max = high.coerceAtLeast(1f)
            val path = Path()
            history.forEachIndexed { i, v ->
                val x = i.toFloat() / history.lastIndex.coerceAtLeast(1) * size.width
                val y = size.height - (v / max).coerceIn(0f, 1f) * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, HudCyan, style = Stroke(2.dp.toPx()))
        }
    }
}
