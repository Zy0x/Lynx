package com.noir.lynx.feature.cpu.ui.cluster.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.data.CpuClusterInfo
import com.noir.lynx.data.GovernorTunable
import com.noir.lynx.designsystem.card.ResetHeaderButton
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.feature.cpu.ui.cluster.formatTunableDisplay
import com.noir.lynx.feature.cpu.ui.cluster.getTunableMeta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTunablesBottomSheet(
    cluster: CpuClusterInfo,
    tunables: List<GovernorTunable>,
    onDismiss: () -> Unit,
    onTunableChange: (policyId: Int, gov: String, key: String, value: String) -> Unit,
    onLoadTunables: (policyId: Int, gov: String) -> Unit,
    onApplyGovernorPreset: ((policyId: Int, preset: String) -> Unit)?,
    onOpenEditTunable: (policyId: Int, gov: String, tunable: GovernorTunable) -> Unit
) {
    val clusterAccent = if (cluster.id > 0) AccentOrange else AccentCyan

    // 1. Sort tunables chronologically: Up Rate Limit first, Down Rate Limit second, then others
    val sortedTunables = remember(tunables) {
        tunables.sortedWith(
            compareBy { t ->
                when (t.key.lowercase()) {
                    "up_rate_limit_us", "rate_limit_us" -> 0
                    "down_rate_limit_us" -> 1
                    "iowait_boost_enable" -> 2
                    "hispeed_freq" -> 3
                    "go_hispeed_load", "up_threshold" -> 4
                    "down_threshold" -> 5
                    "target_loads" -> 6
                    "sampling_rate", "timer_rate" -> 7
                    "sampling_down_factor" -> 8
                    "min_sample_time" -> 9
                    else -> 20
                }
            }
        )
    }

    // 2. Real-time detected preset based on actual sysfs values
    val currentUp = tunables.find { it.key == "up_rate_limit_us" || it.key == "rate_limit_us" }?.currentValue?.toLongOrNull()
    val currentDown = tunables.find { it.key == "down_rate_limit_us" }?.currentValue?.toLongOrNull()
    val isLittle = cluster.id == 0

    val detectedPreset = if (isLittle) {
        when {
            currentUp == 0L && currentDown != null && currentDown in 8000L..12000L -> "responsive"
            currentUp != null && currentUp in 800L..1200L && currentDown != null && currentDown in 18000L..22000L -> "balanced"
            currentUp != null && currentUp in 8000L..12000L && currentDown != null && currentDown in 800L..1500L -> "powersave"
            (currentUp == 0L || (currentUp != null && currentUp in 800L..1200L)) && currentDown != null && currentDown in 28000L..32000L -> "oem"
            // Transitional fallbacks
            currentUp == 0L && currentDown != null && currentDown in 4000L..6000L -> "responsive"
            currentUp != null && currentUp in 800L..1200L && currentDown != null && currentDown in 9000L..11000L -> "balanced"
            currentUp != null && currentUp in 3500L..4500L && currentDown != null && currentDown in 18000L..22000L -> "powersave"
            else -> "custom"
        }
    } else {
        when {
            currentUp == 0L && currentDown != null && currentDown in 1500L..6000L -> "responsive"
            (currentUp == 0L || (currentUp != null && currentUp in 800L..1200L)) && currentDown != null && currentDown in 9000L..11000L -> "balanced"
            currentUp != null && currentUp in 18000L..22000L && currentDown != null && currentDown in 300L..800L -> "powersave"
            (currentUp == 0L || (currentUp != null && currentUp in 800L..1200L)) && currentDown != null && currentDown in 28000L..32000L -> "oem"
            // Transitional fallbacks
            currentUp != null && currentUp in 3500L..4500L && currentDown != null && currentDown in 18000L..22000L -> "powersave"
            else -> "custom"
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showResetTunablesConfirm by remember { mutableStateOf(false) }

    if (showResetTunablesConfirm) {
        AlertDialog(
            onDismissRequest = { showResetTunablesConfirm = false },
            containerColor = BgCard,
            titleContentColor = TextPrimary,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.cpu_cluster_reset_tunables_title, cluster.id),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = stringResource(R.string.cpu_cluster_reset_tunables_desc, cluster.curGov, cluster.id),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onApplyGovernorPreset?.invoke(cluster.id, "oem")
                        onLoadTunables(cluster.id, cluster.curGov)
                        showResetTunablesConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cpu_cluster_reset_oem_btn),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetTunablesConfirm = false }) {
                    Text(stringResource(R.string.action_cancel), color = TextSecondary, fontSize = 12.sp)
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFA0D1017),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                color = Color(0x33FFFFFF),
                shape = CircleShape,
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 4.dp)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = stringResource(R.string.cpu_cluster_gov_tunables_title, cluster.curGov),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.cpu_cluster_gov_tunables_subtitle, cluster.id, cluster.role),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ResetHeaderButton(
                        onClick = { showResetTunablesConfirm = true }
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = clusterAccent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = stringResource(R.string.cpu_cluster_params_count, tunables.size),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = clusterAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderGlass.copy(alpha = 0.5f))

            // ── Schedutil Quick Presets (Rate Limits) ──
            if (cluster.curGov.equals("schedutil", ignoreCase = true)) {
                SchedutilPresetsCard(
                    clusterId = cluster.id,
                    curGov = cluster.curGov,
                    detectedPreset = detectedPreset,
                    clusterAccent = clusterAccent,
                    isLittle = isLittle,
                    onApplyGovernorPreset = onApplyGovernorPreset,
                    onLoadTunables = onLoadTunables
                )
            }

            if (tunables.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.cpu_cluster_no_tunables_detected, cluster.curGov),
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sortedTunables.forEach { tunable ->
                        val meta = getTunableMeta(tunable.key, tunable.displayName, clusterAccent, isLittle = isLittle)
                        val displayVal = formatTunableDisplay(tunable.key, tunable.currentValue, tunable.unit)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF141722),
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(meta.iconColor.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = meta.icon,
                                                contentDescription = null,
                                                tint = meta.iconColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = meta.title,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = tunable.key,
                                                fontSize = 9.sp,
                                                color = TextSecondary.copy(alpha = 0.7f),
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    if (tunable.isBoolean) {
                                        Switch(
                                            checked = tunable.currentValue == "1",
                                            onCheckedChange = { isChecked ->
                                                onTunableChange(cluster.id, cluster.curGov, tunable.key, if (isChecked) "1" else "0")
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = clusterAccent,
                                                checkedTrackColor = clusterAccent.copy(alpha = 0.4f)
                                            )
                                        )
                                    } else {
                                        Surface(
                                            onClick = {
                                                onOpenEditTunable(cluster.id, cluster.curGov, tunable)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = clusterAccent.copy(alpha = 0.16f),
                                            border = BorderStroke(1.dp, clusterAccent.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Text(
                                                    text = displayVal,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = clusterAccent
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit",
                                                    tint = clusterAccent.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Description
                                Text(
                                    text = meta.description,
                                    fontSize = 10.5.sp,
                                    color = TextSecondary,
                                    lineHeight = 14.5.sp
                                )

                                // Guidance Note directly integrated
                                if (meta.hint != null) {
                                    Text(
                                        text = meta.hint,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF8E9BB0),
                                        lineHeight = 13.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
