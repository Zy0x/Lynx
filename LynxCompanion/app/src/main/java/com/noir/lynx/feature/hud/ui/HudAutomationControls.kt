package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.HudPreferences

/** Controls the existing guarded runner without changing its timing or foreground rules. */
@Composable
internal fun HudAutomationControls(prefs: HudPreferences, busy: Boolean, isSkipping: Boolean, isPaused: Boolean,
    onEnabled: (Boolean) -> Unit, onSpeed: (Long) -> Unit, onChoice: (Boolean) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    HudSection(hudString(R.string.hud_autoskip_short)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(hudString(R.string.hud_skip_dialog), fontSize = 12.sp, color = Color.White)
                Text(hudString(when {
                    !prefs.autoSkipEnabled -> R.string.hud_skip_off
                    isPaused -> R.string.hud_autoskip_pill_paused
                    isSkipping -> R.string.hud_autoskip_pill_active
                    else -> R.string.hud_skip_ready
                }), fontSize = 12.sp, color = if (isPaused) HudOrange else if (prefs.autoSkipEnabled) HudCyan else HudMuted)
            }
            Switch(prefs.autoSkipEnabled, onEnabled, enabled = !busy,
                colors = SwitchDefaults.colors(checkedTrackColor = HudBlueBadge, checkedThumbColor = Color.White))
        }
        HudDisclosure(hudString(R.string.hud_skip_options), expanded) { expanded = !expanded }
        if (expanded) {
            val speeds = listOf(180L, 250L, 380L)
            val labels = listOf(hudString(R.string.hud_autoskip_speed_fast), hudString(R.string.hud_autoskip_speed_normal), hudString(R.string.hud_autoskip_speed_slow))
            HudAdaptiveGrid(labels) { i -> HudChoice(labels[i], prefs.autoSkipSpeedMs == speeds[i], { onSpeed(speeds[i]) }, Modifier.weight(1f), enabled = !busy) }
            val choices = listOf(hudString(R.string.hud_autoskip_choice_top), hudString(R.string.hud_autoskip_choice_bottom))
            HudAdaptiveGrid(choices, maximum = 2) { i -> HudChoice(choices[i], prefs.autoSkipChoiceBottom == (i == 1), { onChoice(i == 1) }, Modifier.weight(1f), enabled = !busy) }
            Text(hudString(R.string.hud_autoskip_desc), fontSize = 12.sp, color = HudMuted)
            Text(hudString(R.string.hud_autoskip_supported_games), fontSize = 12.sp, color = HudCyan)
        }
    }
}
