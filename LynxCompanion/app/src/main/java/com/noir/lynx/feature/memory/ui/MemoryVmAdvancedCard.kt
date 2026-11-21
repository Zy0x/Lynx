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
import kotlin.math.roundToInt

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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MemoryValueSlider(
                        label = "Dirty Ratio",
                        value = vm.dirtyRatio.toFloat(),
                        valueRange = 1f..90f,
                        displayValue = { "${it.roundToInt()}%" },
                        accentColor = AccentPurple,
                        unit = "%",
                        customDialogMin = 1,
                        customDialogMax = 95,
                        customDialogHint = "Batas persen RAM kotor sebelum proses I/O dipaksa menunggu writeback ke flash.",
                        onCommitValue = { viewModel.setDirtyRatio(it.roundToInt()) }
                    )

                    MemoryValueSlider(
                        label = "Dirty Background Ratio",
                        value = vm.dirtyBackgroundRatio.toFloat(),
                        valueRange = 1f..60f,
                        displayValue = { "${it.roundToInt()}%" },
                        accentColor = AccentPurple,
                        unit = "%",
                        customDialogMin = 1,
                        customDialogMax = 80,
                        customDialogHint = "Ambang saat flush thread mulai menulis data kotor di latar belakang.",
                        onCommitValue = { viewModel.setDirtyBackgroundRatio(it.roundToInt()) }
                    )

                    val vfsSliderMax = maxOf(250, vm.vfsCachePressure)
                    MemoryValueSlider(
                        label = "VFS Cache Pressure",
                        value = vm.vfsCachePressure.toFloat(),
                        valueRange = 10f..vfsSliderMax.toFloat(),
                        stepSize = 5f,
                        displayValue = { "${it.roundToInt()}%" },
                        accentColor = AccentBlue,
                        unit = "%",
                        customDialogMin = 1,
                        customDialogMax = 500,
                        customDialogHint = "Kontrol agresivitas reclaim cache dentry/inode (<100 menahan metadata file di RAM).",
                        onCommitValue = { viewModel.setVfsCachePressure(it.roundToInt()) }
                    )

                    val expireSecMax = maxOf(120, vm.dirtyExpireCentisecs / 100)
                    MemoryValueSlider(
                        label = "Dirty Expire",
                        value = (vm.dirtyExpireCentisecs / 100f).coerceAtLeast(1f),
                        valueRange = 1f..expireSecMax.toFloat(),
                        stepSize = 2f,
                        displayValue = { "${it.roundToInt()} dtk" },
                        accentColor = AccentCyan,
                        unit = "detik",
                        customDialogMin = 1,
                        customDialogMax = 600,
                        customDialogHint = "Usia maksimal halaman kotor di RAM sebelum wajib ditulis ke storage.",
                        onCommitValue = { viewModel.setDirtyExpireCentisecs(it.roundToInt() * 100) }
                    )

                    val wbSecMax = maxOf(60, vm.dirtyWritebackCentisecs / 100)
                    MemoryValueSlider(
                        label = "Dirty Writeback Interval",
                        value = (vm.dirtyWritebackCentisecs / 100f).coerceAtLeast(1f),
                        valueRange = 1f..wbSecMax.toFloat(),
                        displayValue = { "${it.roundToInt()} dtk" },
                        accentColor = AccentCyan,
                        unit = "detik",
                        customDialogMin = 1,
                        customDialogMax = 300,
                        customDialogHint = "Interval bangun thread writeback kernel (nilai lebih tinggi menghemat wakeup I/O).",
                        onCommitValue = { viewModel.setDirtyWritebackCentisecs(it.roundToInt() * 100) }
                    )
                }
            }

            // ── 2. Conditional Kernel Extensions (MGLRU & Page-Cluster) ──
            if (vm.mglruSupported || vm.pageCluster >= 0) {
                HorizontalDivider(color = BorderGlass, thickness = 0.6.dp)

                if (vm.mglruSupported) {
                    MemoryValueSlider(
                        label = "MGLRU Anti-Thrash (min_ttl_ms)",
                        value = vm.mglruMinTtlMs.toFloat(),
                        valueRange = 0f..5000f,
                        stepSize = 100f,
                        displayValue = { "${it.roundToInt()} ms" },
                        accentColor = AccentCyan,
                        unit = "ms",
                        customDialogMin = 0,
                        customDialogMax = 5000,
                        customDialogHint = "Mencegah Multi-Gen LRU menukar working-set aktif lebih cepat dari TTL ini (0–5000 ms).",
                        onCommitValue = { viewModel.setMglruMinTtlMs(it.roundToInt()) }
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
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val maxPages = maxOf(2000, ksm.pagesToScan)
                        MemoryValueSlider(
                            label = "KSM Pages to Scan",
                            value = ksm.pagesToScan.toFloat(),
                            valueRange = 10f..maxPages.toFloat(),
                            stepSize = 50f,
                            displayValue = { "${it.roundToInt()} hal" },
                            accentColor = AccentGreen,
                            unit = "halaman",
                            customDialogMin = 10,
                            customDialogMax = 10000,
                            customDialogHint = "Jumlah halaman yang dipindai ksmd per siklus bangun.",
                            onCommitValue = { viewModel.setKsmTunables(it.roundToInt(), ksm.sleepMs) }
                        )

                        val maxSleep = maxOf(2000, ksm.sleepMs)
                        MemoryValueSlider(
                            label = "KSM Sleep Interval",
                            value = ksm.sleepMs.toFloat(),
                            valueRange = 5f..maxSleep.toFloat(),
                            stepSize = 25f,
                            displayValue = { "${it.roundToInt()} ms" },
                            accentColor = AccentGreen,
                            unit = "ms",
                            customDialogMin = 5,
                            customDialogMax = 10000,
                            customDialogHint = "Jeda tidur ksmd antar pemindaian (nilai lebih besar menekan pemakaian CPU).",
                            onCommitValue = { viewModel.setKsmTunables(ksm.pagesToScan, it.roundToInt()) }
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
