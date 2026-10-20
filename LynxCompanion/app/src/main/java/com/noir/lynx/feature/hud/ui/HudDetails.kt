package com.noir.lynx.feature.hud.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.*

@Composable
internal fun HudDetails(snapshot: HudSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(hudString(R.string.hud_frame_chart), fontSize = 14.sp)
        val history = snapshot.history
        if (history.size > 1) {
            val low = history.minOrNull() ?: 0f
            val high = history.maxOrNull() ?: 0f
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(hudString(R.string.hud_value, "%.1f".format(low), "ms"), fontSize = 12.sp, color = HudMuted)
                Text(hudString(R.string.hud_value, "%.1f".format(high), "ms"), fontSize = 12.sp, color = HudMuted)
            }
            Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                val max = high.coerceAtLeast(1f)
                val path = Path()
                history.forEachIndexed { i, v ->
                    val x = i.toFloat() / history.lastIndex * size.width
                    val y = size.height - v / max * size.height
                    if (i == 0) path.moveTo(x,y) else path.lineTo(x,y)
                }
                drawPath(path,HudCyan,style = Stroke(1.5.dp.toPx()))
            }
        } else { Text(statusText(snapshot[HudMetric.FRAME_TIME].status), color = HudMuted, fontSize = 12.sp) }
        Text(hudString(R.string.hud_frame_note), fontSize = 12.sp, color = HudMuted)
        Text(hudString(R.string.hud_app, snapshot.packageName.ifBlank { "—" }), fontSize = 12.sp)
        Text(hudString(R.string.hud_layer, snapshot.layer.ifBlank { "—" }), fontSize = 12.sp, color = HudMuted)
        Text(hudString(R.string.hud_collection, snapshot.collectionMs.toString()), fontSize = 12.sp, color = HudMuted)
        HudMetric.values().forEach { metric ->
            HudReadingRow(hudString(metric.label), snapshot[metric])
        }
        Text(hudString(R.string.hud_clock_note), fontSize = 12.sp, color = HudMuted)
        snapshot.policies.forEach { (id,clock) -> HudReadingRow(id,clock) }
        Text(hudString(R.string.hud_sensor_note), fontSize = 12.sp, color = HudMuted)
        Text(hudString(R.string.hud_mode_active), fontSize = 12.sp, color = HudMuted)
        Text(hudString(when(snapshot.charging) { true -> R.string.hud_charging; false -> R.string.hud_discharging; else -> R.string.hud_unsupported }), fontSize = 12.sp)
    }
}

@Composable
private fun HudReadingRow(label: String, reading: HudReading) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        HorizontalDivider(color = HudMuted.copy(alpha = 0.12f))
        Text(label, fontSize = 12.sp, color = HudMuted)
        Text(hudString(R.string.hud_value, reading.text(), reading.unit), fontSize = 18.sp)
        Text(statusText(reading.status), fontSize = 12.sp, color = HudMuted)
        if (reading.source.isNotBlank()) {
            Text(hudString(R.string.hud_source, reading.source), fontSize = 12.sp, color = HudMuted)
            Text(hudString(R.string.hud_age, (SystemClock.elapsedRealtime()-reading.atMs).coerceAtLeast(0).toString()), fontSize = 12.sp, color = HudMuted)
        }
    }
}
