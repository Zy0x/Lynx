package com.noir.lynx.feature.cpu.ui.cluster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noir.lynx.data.CpuClusterInfo
import com.noir.lynx.data.GovernorTunable
import com.noir.lynx.feature.cpu.ui.CompactClusterRow

/**
 * CpuClusterTunerCard — Delegated backward-compatibility wrapper.
 * Single source of truth delegates directly to CompactClusterRow and QuickTunablesBottomSheet.
 */
@Deprecated("Use CompactClusterRow with QuickTunablesBottomSheet directly")
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        clusters.forEach { cluster ->
            CompactClusterRow(
                cluster = cluster,
                governorTunables = governorTunables,
                onFreqChange = onFreqChange,
                onGovChange = onGovChange,
                onLoadTunables = onLoadTunables,
                onTunableChange = onTunableChange,
                onLockToggle = onLockToggle,
                onApplyGovernorPreset = onApplyGovernorPreset
            )
        }
    }
}
