package com.noir.lynx.feature.cpu.ui.cluster

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.data.CpuClusterInfo
import com.noir.lynx.data.GovernorTunable
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.TextSecondary
import com.noir.lynx.feature.cpu.ui.cluster.dialogs.FrequencyPickerBottomSheet
import com.noir.lynx.feature.cpu.ui.cluster.dialogs.GovernorPickerBottomSheet
import com.noir.lynx.feature.cpu.ui.cluster.dialogs.QuickTunablesBottomSheet
import com.noir.lynx.feature.cpu.ui.cluster.dialogs.TunableEditDialog

@Composable
fun CpuClusterTunerCard(
    clusters: List<CpuClusterInfo>,
    governorTunables: Map<Int, List<GovernorTunable>> = emptyMap(),
    onFreqChange: (policyId: Int, minFreq: Long?, maxFreq: Long?) -> Unit,
    onGovChange: (policyId: Int, gov: String) -> Unit,
    onLoadTunables: (policyId: Int, gov: String) -> Unit = { _, _ -> },
    onTunableChange: (policyId: Int, gov: String, key: String, value: String) -> Unit = { _, _, _, _ -> },
    onApplyGovernorPreset: ((policyId: Int, preset: String) -> Unit)? = null,
    onLockToggle: (policyId: Int, isLock: Boolean, minFreq: Long, maxFreq: Long) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    // ── Bottom Sheet & Dialog States ──
    var freqPickerTarget by remember { mutableStateOf<Pair<CpuClusterInfo, Boolean>?>(null) }
    var govPickerTarget by remember { mutableStateOf<CpuClusterInfo?>(null) }
    var tunablesTarget by remember { mutableStateOf<CpuClusterInfo?>(null) }
    var editingTunable by remember { mutableStateOf<Triple<Int, String, GovernorTunable>?>(null) }

    // ── Edit Single Tunable Value Dialog ──
    editingTunable?.let { (policyId, gov, tunable) ->
        TunableEditDialog(
            policyId = policyId,
            gov = gov,
            tunable = tunable,
            onDismiss = { editingTunable = null },
            onApply = onTunableChange
        )
    }

    // ── Main Card: Dynamic CPU Clusters ──
    LynxCard(
        title = stringResource(R.string.cpu_cluster_tuner_title),
        icon = Icons.Default.Tune,
        accentColor = AccentCyan,
        action = null,
        modifier = modifier
    ) {
        if (clusters.isEmpty()) {
            Text(
                text = stringResource(R.string.cpu_cluster_loading_topology),
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                clusters.forEach { cluster ->
                    CpuClusterItemCard(
                        cluster = cluster,
                        onFreqClick = { targetCluster, isMin ->
                            freqPickerTarget = Pair(targetCluster, isMin)
                        },
                        onLockToggle = onLockToggle,
                        onGovClick = { targetCluster ->
                            govPickerTarget = targetCluster
                        },
                        onTunablesClick = { targetCluster ->
                            tunablesTarget = targetCluster
                            onLoadTunables(targetCluster.id, targetCluster.curGov)
                        }
                    )
                }
            }
        }
    }

    // ── Frequency Picker Modal Bottom Sheet ──
    freqPickerTarget?.let { (initCluster, isMinPicker) ->
        val targetCluster = clusters.find { it.id == initCluster.id } ?: initCluster
        FrequencyPickerBottomSheet(
            cluster = targetCluster,
            isMinPicker = isMinPicker,
            onDismiss = { freqPickerTarget = null },
            onFreqChange = onFreqChange
        )
    }

    // ── Governor Picker Modal Bottom Sheet ──
    govPickerTarget?.let { targetCluster ->
        GovernorPickerBottomSheet(
            cluster = targetCluster,
            onDismiss = { govPickerTarget = null },
            onGovChange = onGovChange
        )
    }

    // ── Governor Tunables Modal Bottom Sheet ──
    tunablesTarget?.let { targetCluster ->
        val tunables = governorTunables[targetCluster.id] ?: emptyList()
        QuickTunablesBottomSheet(
            cluster = targetCluster,
            tunables = tunables,
            onDismiss = { tunablesTarget = null },
            onTunableChange = onTunableChange,
            onLoadTunables = onLoadTunables,
            onApplyGovernorPreset = onApplyGovernorPreset,
            onOpenEditTunable = { pId, gov, t ->
                editingTunable = Triple(pId, gov, t)
            }
        )
    }
}
