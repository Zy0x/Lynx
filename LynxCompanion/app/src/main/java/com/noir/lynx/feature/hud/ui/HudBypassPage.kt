package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.*

@Composable
internal fun HudBypassPage(snapshot: HudSnapshot, bypassStatus: HudBypassStatus, busy: Boolean,
    onToggleBypass: (Boolean) -> Unit, onRefreshBypass: () -> Unit) {
    LaunchedEffect(Unit) { onRefreshBypass() }
    val capText = hudString(
        when (bypassStatus.capability) {
            HudCapability.READY -> R.string.hud_cap_ready
            HudCapability.NEEDS_ROOT -> R.string.hud_cap_needs_root
            HudCapability.UNSUPPORTED -> R.string.hud_cap_unsupported
            HudCapability.CHECKING -> R.string.hud_cap_checking
        }
    )
    val capColor = when (bypassStatus.capability) {
        HudCapability.READY -> HudCyan
        HudCapability.NEEDS_ROOT -> HudOrange
        HudCapability.UNSUPPORTED -> HudMuted
        HudCapability.CHECKING -> HudBlue
    }

    val bypassInfo = hudString(R.string.hud_lab_bypass_desc)
    HudSubpages(listOf(R.string.hud_detail_controls, R.string.hud_detail_readings)) { section ->
        HudSection(
            title = hudString(R.string.hud_lab_bypass_title),
            badge = capText,
            accentColor = capColor,
            info = bypassInfo
        ) {
            if (section == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = hudString(R.string.hud_lab_bypass_title),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        if (bypassStatus.chipSummary.isNotBlank()) {
                            Text(
                                text = bypassStatus.chipSummary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = HudBlue
                            )
                        }
                    }
                    Switch(
                        checked = bypassStatus.enabled || bypassStatus.active,
                        onCheckedChange = { onToggleBypass(it) },
                        enabled = bypassStatus.capability == HudCapability.READY && !busy,
                        colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = HudBlueBadge,
                        uncheckedThumbColor = HudMuted,
                        uncheckedTrackColor = HudElevated
                    )
                    )
                }

                if (bypassStatus.capability == HudCapability.READY) {
                    val runtime = com.noir.lynx.feature.hud.addins.HudGamingAddins.current
                    var threshold by remember(bypassStatus.thresholdPct) { mutableIntStateOf(bypassStatus.thresholdPct) }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = hudString(R.string.hud_bypass_threshold_label, threshold),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Slider(
                        value = threshold.toFloat(),
                        onValueChange = {
                            threshold = it.toInt()
                            runtime?.bypass?.setThreshold(threshold)
                        },
                        valueRange = 20f..90f,
                        modifier = Modifier.heightIn(min = 44.dp)
                    )
                    Text(
                        text = hudString(R.string.hud_bypass_threshold_desc, threshold),
                        fontSize = 11.sp,
                        color = HudMuted
                    )

                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xD9101014))
                        .border(BorderStroke(0.8.dp, if (bypassStatus.thermalCutActive) HudRed else HudCyan), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Thermostat, contentDescription = null, tint = if (bypassStatus.thermalCutActive) HudRed else HudCyan, modifier = Modifier.size(16.dp))
                        Text(
                            text = hudString(R.string.hud_bypass_thermal_cut),
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }

            } else {
                val current = snapshot[HudMetric.BATTERY_CURRENT]
                val temp = snapshot[HudMetric.BATTERY_TEMP]
                HudStatGrid(listOf(
                    hudString(R.string.hud_metric_current) to if (current.status == "valid") hudString(R.string.hud_value, current.text(), current.unit) else "—",
                    hudString(R.string.hud_metric_battery_temp) to if (temp.status == "valid") hudString(R.string.hud_value, temp.text(), temp.unit) else "—"
                ))
                Text(hudString(R.string.hud_lab_bypass_desc), fontSize = 12.sp, color = HudMuted)
            }
        }
    }
}
