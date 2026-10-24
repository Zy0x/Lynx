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
    action: HudActionState, onHz: (Float) -> Unit, onCapture: () -> Unit, onStop: () -> Unit, onBoostRam: () -> Unit) {
    var configure by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HudSection(hudString(R.string.hud_quick_actions)) {
            val labels = listOf(hudString(R.string.hud_capture_short), hudString(R.string.hud_boost_ram))
            HudAdaptiveGrid(labels, maximum = 2) { index ->
                HudAction(labels[index], !action.busy, if (index == 0) onCapture else onBoostRam, Modifier.weight(1f))
            }
        }
        HudExpandableSection(hudString(R.string.hud_refresh), hudString(R.string.hud_value, activeHz.text(), activeHz.unit)) {
            HudReadingLine(hudString(R.string.hud_actual_hz), activeHz)
            action.requestedHz?.let {
                Text(hudString(R.string.hud_requested_hz, HudReading(it.toDouble(), "Hz", status = "valid").text()), fontSize = 12.sp, color = HudMuted)
            }
            if (rates.isEmpty()) Text(hudString(R.string.hud_unsupported), color = HudMuted, fontSize = 12.sp)
            else {
                val labels = rates.map { hz -> hudString(R.string.hud_value, HudReading(hz.toDouble(), "Hz", status = "valid").text(), "Hz") }
                HudAdaptiveGrid(labels, maximum = 4) { i ->
                    HudChoice(labels[i], activeHz.status == "valid" && activeHz.value?.let { abs(it - rates[i]) < 0.05 } == true,
                        { onHz(rates[i]) }, Modifier.weight(1f), !action.busy)
                }
            }
        }
        HudSection(hudString(R.string.hud_mini_title)) {
            HudPreferenceToggle(hudString(R.string.hud_pin), prefs.pinned, store::pin)
            HudDisclosure(hudString(R.string.hud_configure), configure) { configure = !configure }
            if (configure) HudMetricSelection(prefs, store, showPin = false)
        }
        HudExpandableSection(hudString(R.string.hud_floating_short), hudString(
            if (prefs.floatingEngine == "standalone") R.string.floating_engine_root else R.string.floating_engine_rom)) {
            HudFloatingSettings(prefs, store)
        }
        HudAction(hudString(R.string.hud_stop), onClick = onStop, modifier = Modifier.fillMaxWidth())
    }
}
