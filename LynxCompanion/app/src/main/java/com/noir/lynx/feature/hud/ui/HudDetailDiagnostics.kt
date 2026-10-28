package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.*

@Composable
internal fun HudDetailDiagnostics(snapshot: HudSnapshot) {
    val help = listOf(hudString(R.string.hud_frame_note), hudString(R.string.hud_clock_note), hudString(R.string.hud_sensor_note))
        .joinToString("\n\n")
    HudSection(hudString(R.string.hud_diagnostics), info = help) {
        HudExpandableSection(hudString(R.string.hud_detail_connection)) {
            Text(hudString(R.string.hud_app, snapshot.packageName.ifBlank { "—" }), fontSize = 12.sp, color = HudMuted)
            Text(hudString(R.string.hud_layer, snapshot.layer.ifBlank { "—" }), fontSize = 12.sp, color = HudMuted)
            Text(hudString(R.string.hud_collection, snapshot.collectionMs.toString()), fontSize = 12.sp, color = HudMuted)
        }
        HudSubpages(hudMetricGroups.map { it.first }) { group ->
            HudDiagnosticReading(snapshot, group)
        }
    }
}

@Composable
private fun HudDiagnosticReading(snapshot: HudSnapshot, group: Int) {
    val (title, metrics) = hudMetricGroups[group]
    val readings = metrics.map { hudString(it.label) to snapshot[it] } +
    if (title == R.string.hud_group_processor) snapshot.policies else emptyList()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val (label, reading) = readings.getOrElse(selected) { readings.first() }
    val labels = readings.map { it.first }
    HudAdaptiveGrid(labels, maximum = 2) { index ->
        HudChoice(labels[index], selected == index, { selected = index }, Modifier.weight(1f))
    }
    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = HudBlue)
    Text(statusText(reading.status), fontSize = 12.sp, color = if (reading.status == "valid") HudCyan else HudOrange)
    Text(hudString(R.string.hud_source, reading.source.ifBlank { "—" }), fontSize = 12.sp, color = HudMuted)
    val age = if (reading.atMs > 0 && snapshot.atMs >= reading.atMs) (snapshot.atMs - reading.atMs).toString() else "—"
    Text(hudString(R.string.hud_age, age), fontSize = 12.sp, color = HudMuted)
}
