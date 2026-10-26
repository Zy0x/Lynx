package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.data.*
import kotlinx.coroutines.delay
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
    onBoostRam: () -> Unit,
    brightnessLocked: Boolean = false,
    onToggleBrightnessLock: (Boolean) -> Unit = {},
    onToggleAutoSkip: (Boolean) -> Unit = { store.setAutoSkipEnabled(it) },
    onSetAutoSkipSpeed: (Long) -> Unit = { store.setAutoSkipSpeed(it) },
    onSetAutoSkipChoiceBottom: (Boolean) -> Unit = { store.setAutoSkipChoiceBottom(it) },
    isSkipping: Boolean = false, isPaused: Boolean = false
) {
    var timerRemainingSec by rememberSaveable { mutableStateOf(0) }
    var timerActivePreset by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(timerRemainingSec > 0) {
        while (timerRemainingSec > 0) {
            delay(1_000L)
            timerRemainingSec = (timerRemainingSec - 1).coerceAtLeast(0)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HudExpandableSection(
            title = hudString(R.string.hud_refresh),
            detail = hudString(R.string.hud_value, activeHz.text(), activeHz.unit),
            initiallyOpen = true
        ) {
            action.requestedHz?.let {
                Text(
                    text = hudString(
                        R.string.hud_requested_hz,
                        HudReading(it.toDouble(), "Hz", status = "valid").text()
                    ),
                    fontSize = 11.5.sp,
                    color = HudCyan
                )
            }
            if (rates.isEmpty()) {
                Text(
                    text = hudString(R.string.hud_unsupported),
                    color = HudMuted,
                    fontSize = 12.sp
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
                        enabled = !action.busy
                    )
                }
            }
        }

        HudExpandableSection(
            title = hudString(R.string.hud_mini_title),
            detail = hudString(R.string.hud_selected_count, prefs.selected.size),
            initiallyOpen = false
        ) {
            HudMetricSelection(prefs, store, showPin = false)
        }

        HudExpandableSection(
            title = hudString(R.string.hud_floating_short),
            detail = hudString(
                if (prefs.floatingEngine == "standalone") R.string.floating_engine_root else R.string.floating_engine_rom
            ),
            initiallyOpen = false
        ) {
            HudFloatingSettings(prefs, store)
        }

        // Game Brightness Lock
        HudExpandableSection(
            title = hudString(R.string.hud_brightness_lock_title),
            detail = hudString(if (brightnessLocked) R.string.hud_active else R.string.hud_skip_off)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = hudString(R.string.hud_brightness_lock_desc),
                    fontSize = 11.5.sp,
                    color = HudMuted,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = brightnessLocked,
                    onCheckedChange = { onToggleBrightnessLock(it) },
                    enabled = !action.busy,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = HudBlueBadge,
                        uncheckedThumbColor = HudMuted,
                        uncheckedTrackColor = HudElevated
                    )
                )
            }
        }

        HudAutomationControls(prefs, action.busy, isSkipping, isPaused,
            onToggleAutoSkip, onSetAutoSkipSpeed, onSetAutoSkipChoiceBottom)

        // Magic Box · Cooldown Timer
        val mins = timerRemainingSec / 60
        val secs = timerRemainingSec % 60
        val timerBadge = if (timerRemainingSec > 0) {
            String.format("%02d:%02d", mins, secs)
        } else {
            hudString(R.string.hud_timer_ready)
        }
        HudExpandableSection(
            title = hudString(R.string.hud_timer_title),
            detail = timerBadge
        ) {
            Text(
                text = hudString(R.string.hud_timer_desc),
                fontSize = 11.5.sp,
                color = HudMuted
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format("%02d:%02d", mins, secs),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (timerRemainingSec > 0) HudCyan else Color.White
                )
                HudChoice(
                    label = hudString(R.string.hud_timer_reset),
                    selected = false,
                    onClick = {
                        timerActivePreset = 0
                        timerRemainingSec = 0
                    },
                    modifier = Modifier.width(82.dp),
                    enabled = timerRemainingSec > 0
                )
            }

            val presets = listOf(
                30 to "30s",
                60 to "60s",
                120 to "2m",
                180 to "3m"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { (sec, label) ->
                    HudChoice(
                        label = label,
                        selected = timerRemainingSec > 0 && timerActivePreset == sec,
                        onClick = {
                            timerActivePreset = sec
                            timerRemainingSec = sec
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        HudAction(
            label = hudString(R.string.hud_stop),
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
            isDanger = true
        )
    }
}
