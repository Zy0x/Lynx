package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.*

@Composable
internal fun HudDetailDiagnostics(snapshot: HudSnapshot) {
    HudExpandableSection(hudString(R.string.hud_diagnostics)) {
        Text(hudString(R.string.hud_app, snapshot.packageName.ifBlank { "—" }), fontSize = 12.sp, color = HudMuted)
        Text(hudString(R.string.hud_layer, snapshot.layer.ifBlank { "—" }), fontSize = 12.sp, color = HudMuted)
        Text(hudString(R.string.hud_collection, snapshot.collectionMs.toString()), fontSize = 12.sp, color = HudMuted)
        Text(hudString(R.string.hud_frame_note), fontSize = 12.sp, color = HudMuted)
        Text(hudString(R.string.hud_clock_note), fontSize = 12.sp, color = HudMuted)
        Text(hudString(R.string.hud_sensor_note), fontSize = 12.sp, color = HudMuted)
        val readings = HudMetric.values().map { hudString(it.label) to snapshot[it] } + snapshot.policies
        readings.forEach { (label, reading) ->
            HorizontalDivider(color = HudBorder, thickness = .5.dp)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = HudBlue)
                Text(statusText(reading.status), fontSize = 12.sp, color = if (reading.status == "valid") HudCyan else HudOrange)
                Text(hudString(R.string.hud_source, reading.source.ifBlank { "—" }), fontSize = 12.sp, color = HudMuted)
                val age = if (reading.atMs > 0 && snapshot.atMs >= reading.atMs) (snapshot.atMs - reading.atMs).toString() else "—"
                Text(hudString(R.string.hud_age, age), fontSize = 12.sp, color = HudMuted)
            }
        }
    }
}
