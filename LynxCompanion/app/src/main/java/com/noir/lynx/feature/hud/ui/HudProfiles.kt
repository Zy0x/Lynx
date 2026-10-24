package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text

@Composable
internal fun HudProfiles(current: String?, busy: Boolean, onProfile: (String) -> Unit) {
    var more by rememberSaveable { mutableStateOf(false) }
    var extreme by rememberSaveable { mutableStateOf(false) }

    val choices = listOf(
        "powersave" to R.string.hud_save,
        "balance" to R.string.hud_balanced,
        "performance" to R.string.hud_performance
    )
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
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                choices.forEach { (id, labelRes) ->
                    HudChoice(
                        label = hudString(labelRes),
                        selected = current == id,
                        onClick = { onProfile(id) },
                        modifier = Modifier.weight(1f),
                        enabled = !busy
                    )
                }
                Surface(
                    onClick = { more = !more },
                    enabled = !busy,
                    color = if (more || current in setOf("auto", "extreme")) HudBlueBadge else HudElevated,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = hudString(R.string.hud_more),
                                modifier = Modifier.size(18.dp),
                                tint = if (more || current in setOf("auto", "extreme")) Color.White else HudMuted
                            )
                        }
                    }
                }
            }

            if (more || current in setOf("auto", "extreme")) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HudChoice(
                        label = hudString(R.string.hud_auto),
                        selected = current == "auto",
                        onClick = {
                            more = false
                            onProfile("auto")
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !busy
                    )
                    HudChoice(
                        label = hudString(R.string.hud_extreme),
                        selected = current == "extreme",
                        onClick = {
                            more = false
                            extreme = true
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                        accentColor = HudOrange
                    )
                }
            }

            if (current == null) {
                Text(
                    text = hudString(R.string.hud_profile_unavailable),
                    fontSize = 11.5.sp,
                    color = HudMuted
                )
            }
            if (busy) {
                Text(
                    text = hudString(R.string.hud_applying),
                    fontSize = 11.5.sp,
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
