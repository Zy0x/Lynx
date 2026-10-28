package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.data.HudReading
import kotlin.math.abs

@Composable
internal fun HudToolDisplay(
    rates: List<Float>, activeHz: HudReading, action: HudActionState, onHz: (Float) -> Unit,
    brightnessLocked: Boolean, onToggleBrightnessLock: (Boolean) -> Unit
) {
    HudSubpages(listOf(R.string.hud_refresh, R.string.hud_brightness_lock_title)) { section ->
        if (section == 0) {
            HudExpandableSection(
                title = hudString(R.string.hud_refresh),
                detail = hudString(R.string.hud_value, activeHz.text(), activeHz.unit),
                initiallyOpen = true,
                info = hudString(R.string.hud_tool_refresh_help)
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
                    HudAdaptiveGrid(labels, maximum = 2) { i ->
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

        } else {
            // Game Brightness Lock
            HudExpandableSection(
                title = hudString(R.string.hud_brightness_lock_title),
                detail = hudString(if (brightnessLocked) R.string.hud_active else R.string.hud_skip_off),
                initiallyOpen = true
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

        }
    }
}
