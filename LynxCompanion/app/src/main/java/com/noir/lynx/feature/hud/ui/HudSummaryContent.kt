package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.data.*

/** Keep the launch page short; detailed readings and configuration have their own destinations. */
@Composable
internal fun HudSummaryContent(
    snapshot: HudSnapshot, action: HudActionState,
    onProfile: (String) -> Unit, onDetails: () -> Unit, quickAccess: @Composable () -> Unit
) {
    HudGameReadings(snapshot, onDetails)
    HudSummaryProfileCards(snapshot.profile, action.busy, onProfile)
    Text(hudString(R.string.hud_quick_actions), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = HudMuted)
    quickAccess()
}

@Composable
internal fun HudSummaryProfileCards(
    current: String?,
    busy: Boolean,
    onProfile: (String) -> Unit
) {
    var confirmExtreme by rememberSaveable { mutableStateOf(false) }

    data class ProfileCardItem(
        val key: String,
        val labelRes: Int,
        val icon: ImageVector,
        val accentColor: Color
    )

    val items = listOf(
        ProfileCardItem("powersave", R.string.hud_save, Icons.Default.BatteryChargingFull, Color(0xFF4ADE80)),
        ProfileCardItem("balance", R.string.hud_balanced, Icons.Default.Tune, HudBlue),
        ProfileCardItem("performance", R.string.hud_performance, Icons.Default.Speed, Color(0xFF38BDF8)),
        ProfileCardItem("extreme", R.string.hud_extreme, Icons.Default.LocalFireDepartment, HudOrange)
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = hudString(R.string.hud_profile),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = HudMuted
            )
            if (busy) {
                Text(
                    text = hudString(R.string.hud_applying),
                    fontSize = 11.sp,
                    color = HudBlue
                )
            }
        }

        // Direct 2x2 Grid of small cards / pills (Hemat, Seimbang, Kinerja, Ekstrem)
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowItems.forEach { item ->
                    val isSelected = current == item.key
                    val cardBorder = if (isSelected) {
                        BorderStroke(1.2.dp, item.accentColor.copy(alpha = 0.65f))
                    } else {
                        BorderStroke(1.dp, HudBorder)
                    }
                    val cardBackground = if (isSelected) {
                        item.accentColor.copy(alpha = 0.16f)
                    } else {
                        HudCard
                    }

                    Surface(
                        onClick = {
                            if (!busy) {
                                if (item.key == "extreme" && current != "extreme") {
                                    confirmExtreme = true
                                } else {
                                    onProfile(item.key)
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = cardBackground,
                        border = cardBorder
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = if (isSelected) item.accentColor else HudMuted,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = hudString(item.labelRes),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) Color.White else HudMuted
                            )
                        }
                    }
                }
            }
        }

        if (confirmExtreme) {
            Surface(
                color = HudElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, HudOrange.copy(alpha = 0.45f)),
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
                            onClick = { confirmExtreme = false },
                            modifier = Modifier.weight(1f)
                        )
                        HudAction(
                            label = hudString(R.string.hud_confirm),
                            enabled = !busy,
                            onClick = {
                                confirmExtreme = false
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

@Composable
private fun HudGameReadings(snapshot: HudSnapshot, onDetails: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onDetails,
        color = HudCard,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        val metrics = listOf(HudMetric.FPS, HudMetric.CPU_LOAD, HudMetric.GPU_LOAD)
        val labels = listOf(hudString(R.string.hud_fps_unit), hudString(R.string.hud_cpu), hudString(R.string.hud_gpu))
        val values = metrics.map { metric -> snapshot[metric].let {
            if (it.status == "valid") it.text() + if (metric == HudMetric.FPS) "" else " %" else "—"
        } }
        val widths = values.map { hudLabelWidth(listOf(it), 20, numeric = true) + 8f }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
            val cell: @Composable (Int, Modifier) -> Unit = { index, modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(labels[index], fontSize = 11.sp, color = HudMuted)
                    Text(values[index], fontSize = 20.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace,
                        color = if (index == 0) HudCyan else Color.White)
                }
            }
            if (widths.all { it <= (maxWidth.value - 16f) / 3f }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    metrics.indices.forEach { cell(it, Modifier.weight(1f)) }
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.indices.forEach { cell(it, Modifier.fillMaxWidth()) }
            }
        }
    }
}

internal fun hudProfileLabel(profile: String?): Int = when (profile) {
    "powersave" -> R.string.hud_save
    "balance" -> R.string.hud_balanced
    "performance" -> R.string.hud_performance
    "auto" -> R.string.hud_auto
    "extreme" -> R.string.hud_extreme
    "dormant" -> R.string.hud_dormant
    else -> R.string.hud_profile_unavailable
}
