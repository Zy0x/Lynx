package com.noir.lynx.feature.memory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.triggerMountXBoost

/**
 * Universal Hardware-Aware Storage Shield Card.
 * Intelligently presents Primary System Storage (eMMC 5.1 Flash Shield / UFS Queue)
 * alongside External MicroSD Card with strict multi-tier physical separation.
 */
@Composable
fun MountXStorageCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val storage = uiState.mountXStorageInfo
    val context = LocalContext.current

    LynxCard(
        title = "Arsitektur & Manajemen Storage Fisik",
        icon = Icons.Default.Storage,
        accentColor = if (storage.isPrimaryEmmc) AccentOrange else AccentPurple,
        modifier = modifier
    ) {
        // ── 1. PRIMARY SYSTEM STORAGE BANNER (eMMC 5.1 Shield / UFS Direct) ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(1.dp, if (storage.isPrimaryEmmc) AccentOrange.copy(alpha = 0.4f) else BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (storage.isPrimaryEmmc) Icons.Default.Shield else Icons.Default.Storage,
                            contentDescription = null,
                            tint = if (storage.isPrimaryEmmc) AccentOrange else AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (storage.isPrimaryEmmc) "eMMC 5.1 Flash Shield" else storage.primaryStorageType.ifEmpty { "Penyimpanan Sistem Primer" },
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (storage.isPrimaryEmmc) AccentOrange else TextPrimary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = (if (storage.isPrimaryEmmc) AccentOrange else AccentCyan).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (storage.isPrimaryEmmc) "HALF-DUPLEX" else "FULL-DUPLEX",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (storage.isPrimaryEmmc) AccentOrange else AccentCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                if (storage.isPrimaryEmmc) {
                    Text(
                        text = "Anti-Starvation Bound: 128KB Read-Ahead • BFQ slice_idle=0 • Low Latency SQLite • Zero drop_caches saat wakeup",
                        fontSize = 10.5.sp,
                        color = TextSecondary,
                        lineHeight = 14.sp
                    )
                    if (storage.emmcName.isNotEmpty() || storage.emmcLifeTime.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Chip: ${storage.emmcName.ifEmpty { "JEDEC eMMC" }} ${if (storage.emmcLifeTime.isNotEmpty()) "• Health: ${storage.emmcLifeTime}" else ""}",
                            fontSize = 10.sp,
                            color = TextTertiary
                        )
                    }
                } else {
                    Text(
                        text = "High-Throughput Multi-Queue • Direct Concurrency Active • Low-Overhead Scheduling",
                        fontSize = 10.5.sp,
                        color = TextSecondary,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 2. EXTERNAL MICROSD CARD CONTROLS ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.SdCard,
                contentDescription = null,
                tint = AccentPurple,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Kartu MicroSD Eksternal (Removable)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(Modifier.height(4.dp))
        if (storage.isDetected) {
            Text(
                stringResource(R.string.storage_policy_device, storage.parentDevice, storage.mountPoint, storage.fsType),
                fontSize = 11.sp,
                color = TextSecondary
            )
            Text(
                stringResource(if (storage.isOptimized) R.string.storage_policy_conforms else R.string.storage_policy_pending),
                fontSize = 11.sp,
                color = if (storage.isOptimized) AccentGreen else TextSecondary
            )
            Text(
                stringResource(R.string.storage_policy_read_ahead, storage.currentReadAheadKb),
                fontSize = 11.sp,
                color = AccentPurple
            )
            storage.targetReadAheadKb?.let { target ->
                Text(stringResource(R.string.storage_policy_target, target), fontSize = 10.5.sp, color = TextTertiary)
            }
            Text(
                stringResource(R.string.storage_policy_scheduler, storage.currentScheduler.ifEmpty { stringResource(R.string.storage_policy_unavailable) }),
                fontSize = 10.5.sp,
                color = TextTertiary
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.triggerMountXBoost(context, true) },
                enabled = storage.targetReadAheadKb != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("Terapkan Optimal Sequential SD (256-512KB)") }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = { viewModel.triggerMountXBoost(context, false) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.storage_policy_restore)) }
        } else {
            Text(
                text = "Tidak terdeteksi kartu MicroSD eksternal yang terpasang.",
                fontSize = 11.sp,
                color = TextTertiary
            )
        }
    }
}
