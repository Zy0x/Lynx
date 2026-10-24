package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text

@Composable
internal fun HudProfiles(current: String?, busy: Boolean, onProfile: (String) -> Unit) {
    var extreme by rememberSaveable { mutableStateOf(false) }

    val activeLabelRes = when (current) {
        "powersave" -> R.string.hud_save
        "balance" -> R.string.hud_balanced
        "performance" -> R.string.hud_performance
        "auto" -> R.string.hud_auto
        "extreme" -> R.string.hud_extreme
        "dormant" -> R.string.hud_dormant
        else -> null
    }

    HudSection(
        title = hudString(R.string.hud_profile),
        badge = activeLabelRes?.let { hudString(it) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HudChoice(
                    label = hudString(R.string.hud_auto),
                    selected = current == "auto",
                    onClick = { onProfile("auto") },
                    modifier = Modifier.weight(1f),
                    enabled = !busy
                )
                HudChoice(
                    label = hudString(R.string.hud_balanced),
                    selected = current == "balance",
                    onClick = { onProfile("balance") },
                    modifier = Modifier.weight(1f),
                    enabled = !busy
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HudChoice(
                    label = hudString(R.string.hud_performance),
                    selected = current == "performance",
                    onClick = { onProfile("performance") },
                    modifier = Modifier.weight(1f),
                    enabled = !busy
                )
                HudChoice(
                    label = hudString(R.string.hud_extreme),
                    selected = current == "extreme",
                    onClick = { extreme = true },
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    accentColor = HudOrange
                )
            }

            if (busy) {
                Text(
                    text = hudString(R.string.hud_applying),
                    fontSize = 11.sp,
                    color = HudBlue
                )
            }

            if (extreme) {
                Surface(
                    color = HudElevated,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = hudString(R.string.hud_extreme_title),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HudOrange
                        )
                        Text(
                            text = hudString(R.string.hud_extreme_warning),
                            fontSize = 11.5.sp,
                            color = HudMuted
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            HudAction(
                                label = hudString(R.string.hud_cancel),
                                enabled = !busy,
                                onClick = { extreme = false },
                                modifier = Modifier.weight(1f)
                            )
                            HudAction(
                                label = hudString(R.string.hud_confirm),
                                enabled = !busy,
                                onClick = {
                                    extreme = false
                                    onProfile("extreme")
                                },
                                modifier = Modifier.weight(1f),
                                isDanger = true
                            )
                        }
                    }
                }
            }
        }
    }
}
