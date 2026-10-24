package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.*

/** Shared settings for the Companion page and sidebar; writes only existing HUD preferences. */
@Composable
internal fun HudFloatingSettings(prefs: HudPreferences, store: HudPreferencesStore) {
    HudPreferenceToggle(hudString(R.string.hud_floating_short), prefs.floatingEnabled, store::setFloatingEnabled)
    if (!prefs.floatingEnabled) return
    val engineIds = listOf("standalone", "system_freeform")
    val engines = listOf(hudString(R.string.floating_engine_root), hudString(R.string.floating_engine_rom))
    HudAdaptiveGrid(engines, maximum = 2) { index ->
        HudChoice(engines[index], prefs.floatingEngine == engineIds[index], { store.setFloatingEngine(engineIds[index]) }, Modifier.weight(1f))
    }
    Text(hudString(R.string.hud_window_size_value, prefs.floatingWidthPct, prefs.floatingHeightPct), fontSize = 12.sp, color = HudMuted)
    val presetIds = listOf("compact", "standard", "large", "custom")
    val labels = listOf(R.string.hud_floating_compact, R.string.hud_floating_standard, R.string.hud_floating_large, R.string.hud_floating_custom).map { hudString(it) }
    HudAdaptiveGrid(labels, maximum = 2) { index ->
        HudChoice(labels[index], prefs.floatingSizePreset == presetIds[index], {
            if (presetIds[index] == "custom") store.setFloatingCustomSize(prefs.floatingWidthPct, prefs.floatingHeightPct)
            else store.setFloatingSizePreset(presetIds[index])
        }, Modifier.weight(1f))
    }
    if (prefs.floatingSizePreset == "custom") {
        Text(hudString(R.string.hud_floating_width), fontSize = 12.sp, color = HudMuted)
        Slider(prefs.floatingWidthPct.toFloat(), { store.setFloatingCustomSize(it.toInt(), prefs.floatingHeightPct) },
            valueRange = 35f..95f, steps = 11, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
        Text(hudString(R.string.hud_floating_height), fontSize = 12.sp, color = HudMuted)
        Slider(prefs.floatingHeightPct.toFloat(), { store.setFloatingCustomSize(prefs.floatingWidthPct, it.toInt()) },
            valueRange = 35f..90f, steps = 10, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
    }
    HudExpandableSection(hudString(R.string.hud_engine_info)) {
        Text(hudString(if (prefs.floatingEngine == "standalone") R.string.floating_engine_root_desc else R.string.floating_engine_rom_desc),
            fontSize = 12.sp, color = HudMuted)
        Text(hudString(R.string.floating_engine_note), fontSize = 12.sp, color = HudMuted)
    }
}
