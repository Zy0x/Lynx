package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.*

/** Shared settings for the Companion page and sidebar; writes only existing HUD preferences. */
@Composable
internal fun HudFloatingSettings(prefs: HudPreferences, store: HudPreferencesStore) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HudPreferenceToggle(
            label = hudString(R.string.hud_floating_short),
            checked = prefs.floatingEnabled,
            onCheckedChange = store::setFloatingEnabled
        )
        if (!prefs.floatingEnabled) return@Column

        val engineIds = listOf("standalone", "system_freeform")
        val engines = listOf(hudString(R.string.floating_engine_root), hudString(R.string.floating_engine_rom))
        HudAdaptiveGrid(engines, maximum = 2) { index ->
            HudChoice(
                label = engines[index],
                selected = prefs.floatingEngine == engineIds[index],
                onClick = { store.setFloatingEngine(engineIds[index]) },
                modifier = Modifier.weight(1f),
                accentColor = HudPurple
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = hudString(R.string.hud_floating_size_title),
                fontSize = 11.sp,
                color = HudMuted
            )
            Text(
                text = hudString(R.string.hud_window_size_value, prefs.floatingWidthPct, prefs.floatingHeightPct),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = HudCyan
            )
        }

        val presetIds = listOf("compact", "standard", "large", "custom")
        val labels = listOf(
            R.string.hud_floating_compact,
            R.string.hud_floating_standard,
            R.string.hud_floating_large,
            R.string.hud_floating_custom
        ).map { hudString(it) }
        HudAdaptiveGrid(labels, maximum = 2) { index ->
            HudChoice(
                label = labels[index],
                selected = prefs.floatingSizePreset == presetIds[index],
                onClick = {
                    if (presetIds[index] == "custom") {
                        store.setFloatingCustomSize(prefs.floatingWidthPct, prefs.floatingHeightPct)
                    } else {
                        store.setFloatingSizePreset(presetIds[index])
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        if (prefs.floatingSizePreset == "custom") {
            val sliderColors = SliderDefaults.colors(
                thumbColor = HudCyan,
                activeTrackColor = HudCyan,
                inactiveTrackColor = HudElevated
            )
            Text(hudString(R.string.hud_floating_width), fontSize = 11.sp, color = HudMuted)
            Slider(
                value = prefs.floatingWidthPct.toFloat(),
                onValueChange = { store.setFloatingCustomSize(it.toInt(), prefs.floatingHeightPct) },
                valueRange = 35f..95f,
                steps = 11,
                colors = sliderColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 38.dp)
            )
            Text(hudString(R.string.hud_floating_height), fontSize = 11.sp, color = HudMuted)
            Slider(
                value = prefs.floatingHeightPct.toFloat(),
                onValueChange = { store.setFloatingCustomSize(prefs.floatingWidthPct, it.toInt()) },
                valueRange = 35f..90f,
                steps = 10,
                colors = sliderColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 38.dp)
            )
        }

        HudExpandableSection(hudString(R.string.hud_apps_manage), accentColor = HudCyan) {
            HudAppManager(prefs, store)
        }
        HudExpandableSection(hudString(R.string.hud_engine_info), accentColor = HudMuted) {
            Text(
                text = hudString(
                    if (prefs.floatingEngine == "standalone") R.string.floating_engine_root_desc
                    else R.string.floating_engine_rom_desc
                ),
                fontSize = 11.sp,
                color = HudMuted
            )
            Text(
                text = hudString(R.string.floating_engine_note),
                fontSize = 11.sp,
                color = HudMuted
            )
        }
    }
}
