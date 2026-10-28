package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.*

@Composable
internal fun HudFocusPage(shieldStatus: HudShieldStatus, busy: Boolean,
    onToggleHeadsUpShield: (Boolean) -> Unit, onSelectFocusLevel: (Int) -> Unit) {
    val shieldActive = shieldStatus.headsUpBlocked || shieldStatus.zenMode > 0
    val shieldInfo = hudString(R.string.hud_lab_shield_desc)
    HudSubpages(listOf(R.string.hud_detail_focus, R.string.hud_detail_glow)) { section ->
        HudSection(
            title = hudString(R.string.hud_lab_shield_title),
            badge = hudString(if (shieldActive) R.string.hud_active else R.string.hud_cap_ready),
            accentColor = if (shieldActive) HudCyan else HudBlue,
            info = shieldInfo
        ) {
            if (section == 1) HudGlowControls()
            else {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = hudString(R.string.hud_shield_headsup),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = shieldStatus.headsUpBlocked,
                        onCheckedChange = { onToggleHeadsUpShield(it) },
                        enabled = shieldStatus.capability == HudCapability.READY && !busy,
                        colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = HudBlueBadge,
                        uncheckedThumbColor = HudMuted,
                        uncheckedTrackColor = HudElevated
                    )
                    )
                }

                val focusLevels = listOf(
                    0 to hudString(R.string.hud_shield_mode_off), 1 to hudString(R.string.hud_shield_mode_priority),
                    2 to hudString(R.string.hud_shield_mode_silent), 3 to hudString(R.string.hud_shield_mode_alarms)
                )
                HudAdaptiveGrid(focusLevels.map { it.second }, maximum = 2) { index ->
                    val (level, label) = focusLevels[index]
                    HudChoice(label, shieldStatus.zenMode == level, { onSelectFocusLevel(level) }, Modifier.weight(1f),
                        enabled = !busy && shieldStatus.capability == HudCapability.READY)
                }
            }
        }
    }
}
