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
import com.noir.lynx.ui.setMglruEnabled
import com.noir.lynx.ui.setMglruMinTtlMs
import com.noir.lynx.ui.setPageCluster
import com.noir.lynx.ui.setVfsCachePressure
import java.util.Locale

/**
 * MemoryVmAdvancedCard — Sub-tab 1 (VM & Entropi).
 * Zero duplicate summary tiles: surfaces core VM controls (VFS Cache, MGLRU status/TTL, Page-Cluster)
 * cleanly up front, groups disk writeback parameters inside one compact expandable accordion,
 * and shows KSM/UKSM strictly when supported by the running kernel.
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

    LynxCard(
        title = "Manajemen VM & Ekstensi Kernel",
        subtitle = buildString {
            append("VFS cache, writeback")
            if (vm.mglruSupported) append(", MGLRU")
            if (vm.pageCluster >= 0) append(", Page-Cluster")
            if (ksm.supported) append(" & KSM")
        },
        icon = Icons.Default.Tune,
        accentColor = AccentPurple,
        action = {
            if (vm.mglruSupported) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (vm.mglruEnabled) AccentCyanDim else BgElevated,
                    border = BorderStroke(
                        0.6.dp,
                        if (vm.mglruEnabled) AccentCyan.copy(alpha = 0.35f) else BorderSubtle
                    )
                ) {
                    Text(
                        text = if (vm.mglruEnabled) "MGLRU AKTIF" else "MGLRU OFF",
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (vm.mglruEnabled) AccentCyan else TextSecondary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        },
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ── 1. Core Memory Reclaim & Paging Tunables ──
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

            if (vm.mglruSupported) {
                if (vm.mglruWritable) {
                    LynxSwitch(
                        label = "MGLRU (Multi-Gen LRU)",
                        subLabel = if (vm.mglruEnabled) {
                            "Generasi paging adaptif aktif (/sys/kernel/mm/lru_gen)"
                        } else {
                            "Nonaktif — menggunakan LRU standar kernel"
                        },
                        checked = vm.mglruEnabled,
                        onCheckedChange = { enabled -> viewModel.setMglruEnabled(enabled) }
                    )
                }

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
                        "250 ms • Cepat" to 250,
                        "500 ms • Moderat" to 500,
                        "1000 ms • Optimal" to 1000,
                        "2000 ms • Proteksi" to 2000
                    ),
                    accentColor = AccentCyan,
                    onCommitValue = { viewModel.setMglruMinTtlMs(it) }
                )
            }

            if (vm.pageCluster >= 0) {
                val pagesCount = 1 shl vm.pageCluster.coerceIn(0, 10)
                LynxNumericPresetRow(
                    label = "VM Page-Cluster",
                    subtitle = "Jumlah halaman dibaca sekaligus saat swap-in",
                    value = vm.pageCluster,
                    displayValue = "${vm.pageCluster} ($pagesCount hal)",
                    min = 0,
                    max = 8,
                    unit = "order",
                    dialogTitle = "Atur VM Page-Cluster",
                    dialogHint = "0 = 1 halaman (ideal ZRAM acak), 3 = 8 halaman (baca berurutan). Maksimal order 8 (256 halaman).",
                    presets = listOf(
                        "0 • 1 hal (ZRAM)" to 0,
                        "1 • 2 hal" to 1,
                        "2 • 4 hal" to 2,
                        "3 • 8 hal" to 3
                    ),
                    accentColor = AccentBlue,
                    onCommitValue = { viewModel.setPageCluster(it) }
                )
            }

            HorizontalDivider(color = BorderGlass, thickness = 0.6.dp)

            // ── 2. Disk Writeback Collapsible Group (inline live summary inside header) ──
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
                    .heightIn(min = 48.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (isWritebackExpanded) "Sembunyikan Writeback Disk" else "Parameter Writeback Disk",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isWritebackExpanded) TextPrimary else TextSecondary
                        )
                        Text(
                            text = "Dirty ${vm.dirtyRatio}% / ${vm.dirtyBackgroundRatio}% • ${formatCentisecs(vm.dirtyExpireCentisecs)} / ${formatCentisecs(vm.dirtyWritebackCentisecs)}",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                    modifier = Modifier.fillMaxWidth(),
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

            // ── 3. Conditional KSM (Kernel Samepage Merging) ──
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

private fun formatCentisecs(cs: Int): String {
    val safeCs = cs.coerceAtLeast(0)
    return if (safeCs >= 100 && safeCs % 100 == 0) {
        "${safeCs / 100}s"
    } else if (safeCs > 0 && safeCs < 100) {
        String.format(Locale.US, "%.1fs", safeCs / 100f)
    } else {
        "${safeCs / 100}s"
    }
}
