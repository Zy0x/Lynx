package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.data.*
import kotlin.math.abs

@Composable
internal fun HudTools(prefs: HudPreferences, store: HudPreferencesStore, rates: List<Float>, activeHz: HudReading,
    action: HudActionState, onHz: (Float) -> Unit, onCapture: () -> Unit, onStop: () -> Unit) {
    var configure by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HudSection(hudString(R.string.hud_quick_actions)) {
            HudAction(hudString(R.string.hud_capture), !action.busy, onCapture, Modifier.fillMaxWidth())
        }
        HudSection(hudString(R.string.hud_refresh)) {
            HudReadingLine(hudString(R.string.hud_actual_hz), activeHz)
            action.requestedHz?.let {
                Text(hudString(R.string.hud_requested_hz, HudReading(it.toDouble(), "Hz", status = "valid").text()), fontSize = 12.sp, color = HudMuted)
            }
            if (rates.isEmpty()) Text(hudString(R.string.hud_unsupported), color = HudMuted, fontSize = 12.sp)
            else {
                val labels = rates.map { hz -> hudString(R.string.hud_value, HudReading(hz.toDouble(), "Hz", status = "valid").text(), "Hz") }
                val cellWidth = (hudLabelWidth(labels) + 16f).coerceAtLeast(48f)
                BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
                    val columns = if (HudLayoutPolicy.fits(maxWidth.value, listOf(cellWidth, cellWidth), 6f)) 2 else 1
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        rates.indices.chunked(columns).forEach { indices ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                indices.forEach { i ->
                                    HudChoice(labels[i], activeHz.status == "valid" && activeHz.value?.let { abs(it - rates[i]) < 0.05 } == true,
                                        { onHz(rates[i]) }, Modifier.weight(1f), !action.busy)
                                }
                            }
                        }
                    }
                }
            }
        }
        HudSection(hudString(R.string.hud_mini_title)) {
            HudPreferenceToggle(hudString(R.string.hud_pin), prefs.pinned, store::pin)
            HudDisclosure(hudString(R.string.hud_configure), configure) { configure = !configure }
            if (configure) HudMetricSelection(prefs, store, showPin = false)
        }
        HudSection(hudString(R.string.hud_session)) {
            HudAction(hudString(R.string.hud_stop), onClick = onStop, modifier = Modifier.fillMaxWidth())
        }
    }
}
