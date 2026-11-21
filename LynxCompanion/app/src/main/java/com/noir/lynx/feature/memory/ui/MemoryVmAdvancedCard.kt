package com.noir.lynx.feature.memory.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.controls.LynxNumericPresetRow
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.setDirtyBackgroundRatio
import com.noir.lynx.ui.setDirtyExpireCentisecs
import com.noir.lynx.ui.setDirtyRatio
import com.noir.lynx.ui.setDirtyWritebackCentisecs
import com.noir.lynx.ui.setKsmEnabled
import com.noir.lynx.ui.setKsmTunables
import com.noir.lynx.ui.setMglruMinTtlMs
import com.noir.lynx.ui.setPageCluster
import com.noir.lynx.ui.setVfsCachePressure
import java.util.Locale

/**
 * MemoryVmAdvancedCard — Sub-tab 1 (VM & Entropi).
 * Uses Progressive Disclosure to show concise VM telemetry up front, keeping the 5 writeback
 * sliders and KSM scan tunables neatly collapsed until explicitly expanded by the user.
 */
@Composable
fun MemoryVmAdvancedCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val vm = uiState.vmAdvanced
    val ksm = uiState.ksmStats
    var isWritebackExpanded by remember { mutableStateOf(false) }
    var showCustomPageClusterDialog by remember { mutableStateOf(false) }

    if (showCustomPageClusterDialog && vm.pageCluster >= 0) {
        CustomNumericInputDialog(
            title = "VM Page-Cluster Kustom",
            unit = "order (2^N halaman)",
            currentValue = vm.pageCluster.coerceIn(0, 8),
            min = 0,
            max = 8,
            hint = "0 = 1 halaman (ideal ZRAM acak), 3 = 8 halaman (baca berurutan). Maksimal 8 (256 halaman).",
            accentColor = AccentBlue,
            onDismiss = { showCustomPageClusterDialog = false },
            onConfirm = { value ->
                showCustomPageClusterDialog = false
                viewModel.setPageCluster(value)
            }
        )
    }

    LynxCard(
        title = "Tuning VM & Ekstensi Kernel",
        subtitle = "Writeback cache, VFS pressure" +
            (if (vm.mglruSupported) ", MGLRU" else "") +
            (if (vm.pageCluster >= 0) ", Page-Cluster" else "") +
            (if (ksm.supported) " & KSM" else ""),
        icon = Icons.Default.Tune,
        accentColor = AccentPurple,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── 1. Compact Live VM Summary + Collapsible Writeback & VFS Sliders ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VmSummaryTile(
                    label = "Dirty Ratio",
                    value = "${vm.dirtyRatio}% / ${vm.dirtyBackgroundRatio}%",
                    accent = AccentPurple,
                    modifier = Modifier.weight(1f)
                )
                VmSummaryTile(
                    label = "VFS Cache",
                    value = "${vm.vfsCachePressure}%",
                    accent = AccentBlue,
                    modifier = Modifier.weight(1f)
                )
                VmSummaryTile(
                    label = "Writeback",
                    value = "${vm.dirtyExpireCentisecs / 100}s / ${vm.dirtyWritebackCentisecs / 100}s",
                    accent = AccentCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            Surface(
                onClick = { isWritebackExpanded = !isWritebackExpanded },
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(
                    0.6.dp,
                    if (isWritebackExpanded) AccentPurple.copy(alpha = 0.50f) else BorderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isWritebackExpanded) "Sembunyikan Parameter Writeback & VFS" else "Sesuaikan Writeback & VFS Lanjutan",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isWritebackExpanded) TextPrimary else TextSecondary
                    )
                    Icon(
                        imageVector = if (isWritebackExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = if (isWritebackExpanded) AccentPurple else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isWritebackExpanded,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LynxNumericPresetRow(
                        label = "Dirty Ratio",
                        subtitle = "Batas RAM kotor sebelum I/O menunggu writeback",
                        value = vm.dirtyRatio,
                        displayValue = "${vm.dirtyRatio}%",
                        min = 1,
                        max = 95,
                        unit = "%",
                        dialogHint = "Batas persen RAM kotor sebelum proses I/O dipaksa menunggu writeback ke flash.",
                        presets = listOf(
                            "10% • Rendah" to 10,
                            "20% • Standar" to 20,
                            "30% • Seimbang" to 30,
                            "40% • Cache" to 40
                        ),
                        accentColor = AccentPurple,
                        onCommitValue = { viewModel.setDirtyRatio(it) }
                    )

                    LynxNumericPresetRow(
                        label = "Dirty Background Ratio",
                        subtitle = "Ambang awal flush thread menulis ke flash",
                        value = vm.dirtyBackgroundRatio,
                        displayValue = "${vm.dirtyBackgroundRatio}%",
                        min = 1,
                        max = 80,
                        unit = "%",
                        dialogHint = "Ambang saat flush thread mulai menulis data kotor di latar belakang.",
                        presets = listOf(
                            "5% • Cepat" to 5,
                            "10% • Standar" to 10,
                            "15% • Cache" to 15,
                            "20% • Besar" to 20
                        ),
                        accentColor = AccentPurple,
                        onCommitValue = { viewModel.setDirtyBackgroundRatio(it) }
                    )

                    LynxNumericPresetRow(
                        label = "VFS Cache Pressure",
                        subtitle = "Agresivitas reclaim cache metadata dentry & inode",
                        value = vm.vfsCachePressure,
                        displayValue = "${vm.vfsCachePressure}%",
                        min = 1,
                        max = 500,
                        unit = "%",
                        dialogHint = "Kontrol agresivitas reclaim cache dentry/inode (<100 menahan metadata file di RAM).",
                        presets = listOf(
                            "25% • Tahan" to 25,
                            "50% • Hemat I/O" to 50,
                            "100% • Default" to 100,
                            "150% • Bebas" to 150
                        ),
                        accentColor = AccentBlue,
                        onCommitValue = { viewModel.setVfsCachePressure(it) }
                    )

                    val expireSec = (vm.dirtyExpireCentisecs / 100).coerceAtLeast(1)
                    LynxNumericPresetRow(
                        label = "Dirty Expire",
                        subtitle = "Usia maksimal data kotor sebelum wajib ditulis",
                        value = expireSec,
                        displayValue = "$expireSec dtk",
                        min = 1,
                        max = 600,
                        unit = "detik",
                        dialogHint = "Usia maksimal halaman kotor di RAM sebelum wajib ditulis ke storage.",
                        presets = listOf(
                            "10 dtk • Aman" to 10,
                            "30 dtk • Standar" to 30,
                            "60 dtk • Moderat" to 60,
                            "120 dtk • Panjang" to 120
                        ),
                        accentColor = AccentCyan,
                        onCommitValue = { viewModel.setDirtyExpireCentisecs(it * 100) }
                    )

                    val wbSec = (vm.dirtyWritebackCentisecs / 100).coerceAtLeast(1)
                    LynxNumericPresetRow(
                        label = "Dirty Writeback Interval",
                        subtitle = "Interval bangun thread writeback latar belakang",
                        value = wbSec,
                        displayValue = "$wbSec dtk",
                        min = 1,
                        max = 300,
                        unit = "detik",
                        dialogHint = "Interval bangun thread writeback kernel (nilai lebih tinggi menghemat wakeup I/O).",
                        presets = listOf(
                            "3 dtk • Responsif" to 3,
                            "5 dtk • Standar" to 5,
                            "15 dtk • Hemat" to 15,
                            "30 dtk • Santai" to 30
                        ),
                        accentColor = AccentCyan,
                        onCommitValue = { viewModel.setDirtyWritebackCentisecs(it * 100) }
                    )
                }
            }

            // ── 2. Conditional Kernel Extensions (MGLRU & Page-Cluster) ──
            if (vm.mglruSupported || vm.pageCluster >= 0) {
                HorizontalDivider(color = BorderGlass, thickness = 0.6.dp)

                if (vm.mglruSupported) {
                    LynxNumericPresetRow(
                        label = "MGLRU Anti-Thrash (min_ttl_ms)",
                        subtitle = "Lindungi working-set aktif dari swap prematur",
                        value = vm.mglruMinTtlMs,
                        displayValue = "${vm.mglruMinTtlMs} ms",
                        min = 0,
                        max = 5000,
                        unit = "ms",
                        dialogHint = "Mencegah Multi-Gen LRU menukar working-set aktif lebih cepat dari TTL ini (0–5000 ms).",
                        presets = listOf(
                            "0 ms • Off" to 0,
                            "500 ms • Cepat" to 500,
                            "1000 ms • Optimal" to 1000,
                            "2000 ms • Proteksi" to 2000
                        ),
                        accentColor = AccentCyan,
                        onCommitValue = { viewModel.setMglruMinTtlMs(it) }
                    )
                }

                if (vm.pageCluster >= 0) {
                    val pagesCount = 1 shl vm.pageCluster.coerceIn(0, 10)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VM Page-Cluster (Readahead Swap)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${vm.pageCluster} ($pagesCount hal)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AccentBlue
                        )
                    }

                    val clusterPresets = listOf(0, 1, 2, 3)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        clusterPresets.forEach { order ->
                            MemoryOptionChip(
                                label = "$order (${1 shl order}p)",
                                selected = vm.pageCluster == order,
                                accentColor = AccentBlue,
                                onClick = { viewModel.setPageCluster(order) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        MemoryOptionChip(
                            label = if (vm.pageCluster !in clusterPresets) "${vm.pageCluster}*" else "Kustom",
                            selected = vm.pageCluster !in clusterPresets,
                            accentColor = AccentPurple,
                            onClick = { showCustomPageClusterDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── 3. Conditional KSM (Kernel Samepage Merging) with Progressive Disclosure ──
            if (ksm.supported) {
                HorizontalDivider(color = BorderGlass, thickness = 0.6.dp)

                LynxSwitch(
                    label = "KSM / UKSM (Deduplikasi Halaman)",
                    subLabel = if (ksm.enabled) {
                        String.format(Locale.US, "Aktif • Hemat %.1f MB (%d shared)", ksm.savedMb, ksm.pagesShared)
                    } else {
                        "Gabungkan halaman memori identik untuk menghemat RAM"
                    },
                    checked = ksm.enabled,
                    onCheckedChange = { enabled -> viewModel.setKsmEnabled(enabled) }
                )

                AnimatedVisibility(
                    visible = ksm.enabled,
                    enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                    exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LynxNumericPresetRow(
                            label = "KSM Pages to Scan",
                            subtitle = "Jumlah halaman per siklus pemindaian ksmd",
                            value = ksm.pagesToScan,
                            displayValue = "${ksm.pagesToScan} hal",
                            min = 10,
                            max = 10000,
                            unit = "halaman",
                            dialogHint = "Jumlah halaman yang dipindai ksmd per siklus bangun.",
                            presets = listOf(
                                "100 hal • Ringan" to 100,
                                "256 hal • Standar" to 256,
                                "500 hal • Aktif" to 500,
                                "1000 hal • Kuat" to 1000
                            ),
                            accentColor = AccentGreen,
                            onCommitValue = { viewModel.setKsmTunables(it, ksm.sleepMs) }
                        )

                        LynxNumericPresetRow(
                            label = "KSM Sleep Interval",
                            subtitle = "Jeda tidur ksmd antar pemindaian",
                            value = ksm.sleepMs,
                            displayValue = "${ksm.sleepMs} ms",
                            min = 5,
                            max = 10000,
                            unit = "ms",
                            dialogHint = "Jeda tidur ksmd antar pemindaian (nilai lebih besar menekan pemakaian CPU).",
                            presets = listOf(
                                "20 ms • Cepat" to 20,
                                "100 ms • Standar" to 100,
                                "250 ms • Hemat" to 250,
                                "500 ms • Santai" to 500
                            ),
                            accentColor = AccentGreen,
                            onCommitValue = { viewModel.setKsmTunables(ksm.pagesToScan, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VmSummaryTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgElevated,
        border = BorderStroke(0.6.dp, BorderSubtle),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(
                text = label,
                fontSize = 9.5.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
