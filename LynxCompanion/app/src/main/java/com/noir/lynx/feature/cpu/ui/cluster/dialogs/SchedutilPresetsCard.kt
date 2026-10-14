package com.noir.lynx.feature.cpu.ui.cluster.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.designsystem.theme.*

@Composable
fun SchedutilPresetsCard(
    clusterId: Int,
    curGov: String,
    detectedPreset: String,
    clusterAccent: Color,
    isLittle: Boolean,
    onApplyGovernorPreset: ((policyId: Int, preset: String) -> Unit)?,
    onLoadTunables: (policyId: Int, gov: String) -> Unit
) {
    val (presetBadgeLabel, presetBadgeIcon) = when (detectedPreset) {
        "responsive" -> stringResource(R.string.cpu_cluster_preset_responsive_active) to Icons.Default.Bolt
        "balanced" -> stringResource(R.string.cpu_cluster_preset_balanced_active) to Icons.Default.Balance
        "powersave" -> stringResource(R.string.cpu_cluster_preset_powersave_active) to Icons.Default.Eco
        "oem" -> stringResource(R.string.cpu_cluster_preset_oem_active) to Icons.Default.Restore
        else -> stringResource(R.string.cpu_cluster_preset_custom_active) to Icons.Default.Tune
    }

    val presetBadgeColor = when (detectedPreset) {
        "responsive" -> AccentCyan
        "balanced" -> AccentGreen
        "powersave" -> Color(0xFF8B5CF6)
        "oem" -> AccentOrange
        else -> Color(0xFF94A3B8)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BgElevated,
        border = BorderStroke(1.dp, BorderGlass),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = clusterAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.cpu_cluster_clock_presets_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = presetBadgeColor.copy(alpha = 0.16f),
                    border = BorderStroke(0.8.dp, presetBadgeColor.copy(alpha = 0.35f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = presetBadgeIcon,
                            contentDescription = null,
                            tint = presetBadgeColor,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = presetBadgeLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = presetBadgeColor
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            val coreLabel = if (isLittle) {
                stringResource(R.string.cpu_cluster_little_cores)
            } else {
                stringResource(R.string.cpu_cluster_big_cores)
            }
            Text(
                text = stringResource(R.string.cpu_cluster_clock_profile_label, coreLabel),
                fontSize = 10.5.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presetItems = if (isLittle) {
                    listOf(
                        Triple("responsive", stringResource(R.string.cpu_cluster_preset_responsive), "0 µs / 10 ms"),
                        Triple("balanced", stringResource(R.string.cpu_cluster_preset_balanced), "1 ms / 20 ms"),
                        Triple("powersave", stringResource(R.string.cpu_cluster_preset_powersave), "10 ms / 1 ms"),
                        Triple("oem", stringResource(R.string.cpu_cluster_preset_oem), "0 µs / 30 ms")
                    )
                } else {
                    listOf(
                        Triple("responsive", stringResource(R.string.cpu_cluster_preset_responsive), "0 µs / 5 ms"),
                        Triple("balanced", stringResource(R.string.cpu_cluster_preset_balanced), "0 µs / 10 ms"),
                        Triple("powersave", stringResource(R.string.cpu_cluster_preset_powersave), "20 ms / 0.5 ms"),
                        Triple("oem", stringResource(R.string.cpu_cluster_preset_oem), "0 µs / 30 ms")
                    )
                }
                presetItems.forEach { (preset, label, note) ->
                    val isCurrentPreset = detectedPreset == preset
                    val cardColor = when (preset) {
                        "responsive" -> AccentCyan
                        "balanced" -> AccentGreen
                        "powersave" -> Color(0xFF8B5CF6)
                        "oem" -> AccentOrange
                        else -> clusterAccent
                    }
                    val icon = when (preset) {
                        "responsive" -> Icons.Default.Bolt
                        "balanced" -> Icons.Default.Balance
                        "powersave" -> Icons.Default.Eco
                        else -> Icons.Default.Restore
                    }
                    Surface(
                        onClick = {
                            onApplyGovernorPreset?.invoke(clusterId, preset)
                            onLoadTunables(clusterId, curGov)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrentPreset) cardColor.copy(alpha = 0.18f) else Color(0xFF141722),
                        border = BorderStroke(1.dp, if (isCurrentPreset) cardColor else BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isCurrentPreset) cardColor else TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = label,
                                color = if (isCurrentPreset) cardColor else TextPrimary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = note,
                                color = if (isCurrentPreset) cardColor.copy(alpha = 0.9f) else TextSecondary,
                                fontSize = 8.sp,
                                fontWeight = if (isCurrentPreset) FontWeight.Medium else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
