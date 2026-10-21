package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text

@Composable
internal fun HudProfiles(current: String?, busy: Boolean, onProfile: (String) -> Unit) {
    var more by rememberSaveable { mutableStateOf(false) }
    var extreme by rememberSaveable { mutableStateOf(false) }
    val choices = listOf("powersave" to R.string.hud_save, "balance" to R.string.hud_balanced, "performance" to R.string.hud_performance)
    val labels = choices.map { hudString(it.second) }
    val cellWidth = (hudLabelWidth(labels) + 8f).coerceAtLeast(48f)
    val name = when(current) {
        "powersave" -> R.string.hud_save; "balance" -> R.string.hud_balanced; "performance" -> R.string.hud_performance
        "auto" -> R.string.hud_auto; "extreme" -> R.string.hud_extreme; "dormant" -> R.string.hud_dormant
        else -> R.string.hud_unsupported
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // Header Row: Section Label + Active Status Chip + More Button
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "MODE PERFORMA",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = HudMuted
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val (chipColor, chipText) = when (current) {
                    "performance" -> HudOrange to hudString(R.string.hud_performance)
                    "extreme" -> Color(0xFFFF3B5C) to hudString(R.string.hud_extreme)
                    "powersave" -> Color(0xFF10B981) to hudString(R.string.hud_save)
                    "auto" -> HudBlue to hudString(R.string.hud_auto)
                    "balance" -> HudCyan to hudString(R.string.hud_balanced)
                    else -> HudMuted to hudString(name)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = chipColor.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, chipColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = chipText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = chipColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                val state = hudString(if (more) R.string.hud_expanded else R.string.hud_collapsed)
                IconButton(
                    onClick = { more = !more },
                    enabled = !busy,
                    modifier = Modifier.size(28.dp).semantics { stateDescription = state }
                ) {
                    Icon(Icons.Default.MoreHoriz, hudString(R.string.hud_more), tint = HudMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Segmented Profile Bar
        Surface(
            color = HudElevated,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.8.dp, HudBorder)
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(3.dp).selectableGroup()) {
                if (HudLayoutPolicy.fits(maxWidth.value, List(3) { cellWidth }, 0f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        choices.forEachIndexed { index, (id, _) ->
                            ProfileSegmentPill(
                                label = labels[index],
                                id = id,
                                selected = current == id,
                                enabled = !busy,
                                onClick = { onProfile(id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    choices.forEachIndexed { index, (id, _) ->
                        ProfileSegmentPill(
                            label = labels[index],
                            id = id,
                            selected = current == id,
                            enabled = !busy,
                            onClick = { onProfile(id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (busy) Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = HudCyan)
            Text(hudString(R.string.hud_applying), fontSize = 11.sp, color = HudMuted)
        }

        if (more) Surface(
            color = HudCard,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.8.dp, HudBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(4.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                HudChoice(hudString(R.string.hud_auto), current == "auto", { more = false; onProfile("auto") }, Modifier.fillMaxWidth(), !busy)
                HudChoice(hudString(R.string.hud_extreme), current == "extreme", { more = false; extreme = true }, Modifier.fillMaxWidth(), !busy)
            }
        }

        if (extreme) Surface(
            color = Color(0xFF221115),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.8.dp, Color(0xFFFF3B5C).copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hudString(R.string.hud_extreme_title), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFF3B5C))
                Text(hudString(R.string.hud_extreme_warning), fontSize = 11.sp, color = HudMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HudAction(hudString(R.string.hud_cancel), onClick = { extreme = false }, modifier = Modifier.weight(1f))
                    HudAction(hudString(R.string.hud_confirm), enabled = !busy, onClick = { extreme = false; onProfile("extreme") }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProfileSegmentPill(
    label: String,
    id: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = when (id) {
        "powersave" -> Color(0xFF10B981)
        "performance" -> HudOrange
        else -> HudCyan
    }
    val bg by animateColorAsState(
        if (selected) activeColor.copy(alpha = 0.16f) else Color.Transparent,
        tween(200),
        label = "bg_$id"
    )
    val border by animateColorAsState(
        if (selected) activeColor.copy(alpha = 0.45f) else Color.Transparent,
        tween(200),
        label = "border_$id"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(9.dp),
        color = bg,
        border = BorderStroke(0.8.dp, border),
        modifier = modifier.heightIn(min = 44.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (!enabled) HudMuted.copy(alpha = 0.4f) else if (selected) activeColor else HudMuted
            )
        }
    }
}
