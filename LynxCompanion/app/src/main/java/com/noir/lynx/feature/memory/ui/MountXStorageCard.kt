package com.noir.lynx.feature.memory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.data.IoDeviceInfo
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.setIoScheduler
import com.noir.lynx.ui.setReadAheadKb
import com.noir.lynx.ui.triggerMountXBoost

private val DISCRETE_READ_AHEAD_KB = listOf(128, 256, 512, 1024, 2048)

private fun isPhysicalBlockDevice(devName: String): Boolean {
    val d = devName.lowercase()
    return !d.startsWith("loop") &&
        !d.startsWith("ram") &&
        !d.startsWith("zram") &&
        !d.startsWith("dm-") &&
        !d.startsWith("nbd") &&
        !d.contains("boot") &&
        !d.contains("rpmb")
}

/**
 * MountXStorageCard — Unified Storage Shield (eMMC/UFS), MicroSD control, and guarded Block I/O Manager.
 * Uses discrete Read-Ahead chips & an edit lock instead of continuous sliders to prevent accidental drags.
 */
@Composable
fun MountXStorageCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val storage = uiState.mountXStorageInfo
    val context = LocalContext.current
    var isIoUnlocked by remember { mutableStateOf(false) }

    val primaryColor = if (storage.isPrimaryEmmc) AccentOrange else AccentPurple
    val physicalIoDevices: List<IoDeviceInfo> = remember(uiState.ioDevices) {
        val filtered = uiState.ioDevices.filter { isPhysicalBlockDevice(it.device) }
        if (filtered.isNotEmpty()) filtered else uiState.ioDevices.filter { it.availableSchedulers.size > 1 }
    }

    LynxCard(
        title = "Penyimpanan Fisik & I/O Scheduler",
        subtitle = "Proteksi storage internal, MicroSD & antrean I/O",
        icon = Icons.Default.Storage,
        accentColor = primaryColor,
        modifier = modifier
    ) {
        // ── 1. Primary System Storage Banner ────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, if (storage.isPrimaryEmmc) AccentOrange.copy(alpha = 0.40f) else BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (storage.isPrimaryEmmc) Icons.Default.Shield else Icons.Default.Storage,
                            contentDescription = null,
                            tint = if (storage.isPrimaryEmmc) AccentOrange else AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = if (storage.isPrimaryEmmc) "eMMC 5.1 Flash Shield" else storage.primaryStorageType.ifEmpty { "Penyimpanan Sistem Primer" },
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (storage.isPrimaryEmmc) AccentOrange else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = (if (storage.isPrimaryEmmc) AccentOrange else AccentCyan).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (storage.isPrimaryEmmc) "HALF-DUPLEX" else "FULL-DUPLEX",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (storage.isPrimaryEmmc) AccentOrange else AccentCyan,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (storage.isPrimaryEmmc) {
                        "128KB Read-Ahead • BFQ slice_idle=0 • Anti-starvation tulis/baca"
                    } else {
                        "High-Throughput Multi-Queue • Direct Concurrency Active"
                    },
                    fontSize = 10.5.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
                if (storage.isPrimaryEmmc && (storage.emmcName.isNotEmpty() || storage.emmcLifeTime.isNotEmpty())) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Chip: ${storage.emmcName.ifEmpty { "JEDEC eMMC" }} ${if (storage.emmcLifeTime.isNotEmpty()) "• Health: ${storage.emmcLifeTime}" else ""}",
                        fontSize = 10.sp,
                        color = TextTertiary
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── 2. External MicroSD Card Controls ──────────────────
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(0.6.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SdCard, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "Kartu MicroSD Eksternal",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (storage.isDetected) {
                    Text(
                        text = stringResource(R.string.storage_policy_device, storage.parentDevice, storage.mountPoint, storage.fsType),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${stringResource(R.string.storage_policy_read_ahead, storage.currentReadAheadKb)} • ${storage.currentScheduler.ifEmpty { "-" }}",
                        fontSize = 11.sp,
                        color = AccentPurple,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.triggerMountXBoost(context, true) },
                            enabled = storage.targetReadAheadKb != null,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text("Optimal SD", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.triggerMountXBoost(context, false) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text("Default", fontSize = 11.5.sp)
                        }
                    }
                } else {
                    Text(
                        text = "Tidak ada kartu MicroSD eksternal yang terdeteksi.",
                        fontSize = 11.sp,
                        color = TextTertiary
                    )
                }
            }
        }

        // ── 3. Guarded Block Device I/O Scheduler & Read-Ahead ──
        if (physicalIoDevices.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = BorderSubtle)
            Spacer(Modifier.height(12.dp))

            GuardedTuningBar(
                isUnlocked = isIoUnlocked,
                onToggleUnlock = { isIoUnlocked = it },
                accentColor = AccentPurple,
                lockedText = "Mode Aman • Scheduler & Read-Ahead Terkunci",
                unlockedText = "Mode Edit Aktif • Aman Pilih Chip I/O"
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                physicalIoDevices.forEach { dev ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(0.6.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "/dev/${dev.device}",
                                    color = AccentPurple,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${dev.currentScheduler} • ${dev.readAheadKb} KB",
                                    color = TextSecondary,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (dev.availableSchedulers.size > 1) {
                                Spacer(Modifier.height(8.dp))
                                Text("Scheduler Antrean", fontSize = 10.sp, color = TextSecondary)
                                Spacer(Modifier.height(4.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(dev.availableSchedulers.size) { idx ->
                                        val sched = dev.availableSchedulers[idx]
                                        val isActive = sched == dev.currentScheduler
                                        Surface(
                                            onClick = { if (isIoUnlocked) viewModel.setIoScheduler(dev.device, sched) },
                                            enabled = isIoUnlocked,
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isActive) AccentPurple.copy(alpha = 0.22f) else BgSurfaceLowest,
                                            border = BorderStroke(0.8.dp, if (isActive) AccentPurple else BorderSubtle),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                            ) {
                                                Text(
                                                    text = sched,
                                                    color = if (isActive) AccentPurple else if (isIoUnlocked) TextPrimary else TextSecondary,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Text("Read-Ahead Buffer", fontSize = 10.sp, color = TextSecondary)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DISCRETE_READ_AHEAD_KB.forEach { kb ->
                                    val isSelected = dev.readAheadKb == kb
                                    Surface(
                                        onClick = { if (isIoUnlocked) viewModel.setReadAheadKb(dev.device, kb) },
                                        enabled = isIoUnlocked,
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) AccentPurple.copy(alpha = 0.22f) else BgSurfaceLowest,
                                        border = BorderStroke(0.8.dp, if (isSelected) AccentPurple else BorderSubtle),
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp)
                                        ) {
                                            Text(
                                                text = if (kb >= 1024) "${kb / 1024}M" else "${kb}K",
                                                color = if (isSelected) AccentPurple else if (isIoUnlocked) TextPrimary else TextSecondary,
                                                fontSize = 10.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1
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
    }
}
