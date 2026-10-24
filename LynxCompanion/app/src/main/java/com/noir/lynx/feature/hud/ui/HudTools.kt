package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.PowerSettingsNew
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
internal fun HudTools(
    prefs: HudPreferences,
    store: HudPreferencesStore,
    rates: List<Float>,
    activeHz: HudReading,
    action: HudActionState,
    onHz: (Float) -> Unit,
    onCapture: () -> Unit,
    onStop: () -> Unit,
    onBoostRam: () -> Unit
) {
    var configure by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 1. Quick Actions Card
        HudSection(title = hudString(R.string.hud_quick_actions), accentColor = HudCyan) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HudAction(
                    label = hudString(R.string.hud_capture_short),
                    enabled = !action.busy,
                    onClick = onCapture,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CameraAlt,
                    accentColor = HudCyan
                )
                HudAction(
                    label = hudString(R.string.hud_boost_ram),
                    enabled = !action.busy,
                    onClick = onBoostRam,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CleaningServices,
                    accentColor = HudBlue
                )
            }
        }

        // 2. Display Refresh Rate Selector (Direct 1-Tap Access)
        HudSection(
            title = hudString(R.string.hud_refresh),
            badge = hudString(R.string.hud_value, activeHz.text(), activeHz.unit),
            accentColor = HudBlue
        ) {
            action.requestedHz?.let {
                Text(
                    text = hudString(
                        R.string.hud_requested_hz,
                        HudReading(it.toDouble(), "Hz", status = "valid").text()
                    ),
                    fontSize = 11.sp,
                    color = HudCyan
                )
            }
            if (rates.isEmpty()) {
                Text(
                    text = hudString(R.string.hud_unsupported),
                    color = HudMuted,
                    fontSize = 11.5.sp
                )
            } else {
                val labels = rates.map { hz ->
                    hudString(R.string.hud_value, HudReading(hz.toDouble(), "Hz", status = "valid").text(), "Hz")
                }
                HudAdaptiveGrid(labels, maximum = 4) { i ->
                    val isSelected = activeHz.status == "valid" && activeHz.value?.let { abs(it - rates[i]) < 0.05 } == true
                    HudChoice(
                        label = labels[i],
                        selected = isSelected,
                        onClick = { onHz(rates[i]) },
                        modifier = Modifier.weight(1f),
                        enabled = !action.busy,
                        accentColor = HudBlue
                    )
                }
            }
        }

        // 3. Mini HUD Capsule Control
        HudSection(
            title = hudString(R.string.hud_mini_title),
            badge = hudString(R.string.hud_selected_count, prefs.selected.size),
            accentColor = HudCyan
        ) {
            HudPreferenceToggle(
                label = hudString(R.string.hud_pin),
                checked = prefs.pinned,
                onCheckedChange = store::pin
            )
            HudDisclosure(
                label = hudString(R.string.hud_configure),
                expanded = configure,
                onClick = { configure = !configure }
            )
            if (configure) {
                HudMetricSelection(prefs, store, showPin = false)
            }
        }

        // 4. Floating Window Engine Settings
        HudExpandableSection(
            title = hudString(R.string.hud_floating_short),
            detail = hudString(
                if (prefs.floatingEngine == "standalone") R.string.floating_engine_root else R.string.floating_engine_rom
            ),
            accentColor = HudPurple
        ) {
            HudFloatingSettings(prefs, store)
        }

        // 5. Stop HUD Danger Button
        HudAction(
            label = hudString(R.string.hud_stop),
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.PowerSettingsNew,
            isDanger = true
        )
    }
}
