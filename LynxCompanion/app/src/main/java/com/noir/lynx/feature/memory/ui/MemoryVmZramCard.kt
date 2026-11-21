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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.*
import java.util.Locale
import kotlin.math.roundToInt

/**
 * MemoryVmZramCard — Guarded Virtual Memory, ZRAM Staging, and Kernel KSM controls.
 * All sliders & ZRAM reconfiguration require explicit edit-unlock + commit to prevent accidental
 * horizontal drag mutations or unexpected swapoff stalls while scrolling.
 */
@Composable
internal fun MemoryVmZramCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state
    var isUnlocked by remember { mutableStateOf(false) }
    var showAdvancedVm by remember { mutableStateOf(false) }
    var showZramConfirmDialog by remember { mutableStateOf(false) }

    val ramTotal = (uiState.telemetry?.ramTotalMb?.takeIf { it > 0 } ?: 4096).toFloat()
    val minZram = 512f
    val maxZram = maxOf(4096f, ramTotal)
    val zramSteps = (((maxZram - minZram) / 512f).toInt() - 1).coerceAtLeast(0)

    val currentZramMb = state.memory.zramSizeMb.toFloat().coerceIn(minZram, maxZram).roundToInt()
    val activeAlgo = uiState.zramCompAlgorithm.ifBlank { "lz4" }
    val algos = uiState.availZramCompAlgorithms.ifEmpty { listOf("lz4", "lzo", "zstd") }

    var stagedZramMb by remember { mutableIntStateOf(currentZramMb) }
    var stagedAlgo by remember { mutableStateOf(activeAlgo) }
    var isStagingZram by remember { mutableStateOf(false) }

    LaunchedEffect(currentZramMb, activeAlgo) {
        if (!isStagingZram) {
            stagedZramMb = currentZramMb
            stagedAlgo = activeAlgo
        }
    }

    val zramChanged = stagedZramMb != currentZramMb || stagedAlgo != activeAlgo
    val liveSwapUsedMb = uiState.telemetry?.zramUsedMb?.takeIf { it > 0 } ?: (uiState.telemetry?.swapUsedMb ?: 0)

    LynxCard(
        title = "Virtual Memory, ZRAM & Cache",
        subtitle = "Staging aman sebelum reset ZRAM & kontrol VM kernel",
        icon = Icons.Default.Memory,
        accentColor = AccentBlue,
        modifier = modifier
    ) {
        GuardedTuningBar(
            isUnlocked = isUnlocked,
            onToggleUnlock = { isUnlocked = it },
            accentColor = AccentBlue,
            lockedText = "Mode Aman • ZRAM & Slider VM Terkunci",
            unlockedText = "Mode Edit Aktif • Siap Sesuaikan VM & ZRAM"
        )

        // ── 1. ZRAM Compression Algorithm & Staged Size ────────
        Text(
            text = "Algoritma Kompresi ZRAM",
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            algos.forEach { algo ->
                val isSelected = algo == stagedAlgo
                Surface(
                    onClick = {
                        if (isUnlocked) {
                            isStagingZram = true
                            stagedAlgo = algo
                        }
                    },
                    enabled = isUnlocked,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) AccentBlue.copy(alpha = 0.22f) else BgElevated,
                    border = BorderStroke(
                        if (isSelected) 1.2.dp else 0.7.dp,
                        if (isSelected) AccentBlue else BorderSubtle
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = algo.uppercase(),
                            color = if (isSelected) AccentBlue else if (isUnlocked) TextPrimary else TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        Text(
                            text = when (algo) {
                                "zstd" -> "Rasio Max"
                                "lz4" -> "Cepat"
                                "lz4hc" -> "Optimal"
                                "lzo-rle", "lzo" -> "Hemat CPU"
                                else -> "Kompresi"
                            },
                            color = if (isSelected) AccentBlue.copy(alpha = 0.85f) else TextSecondary,
                            fontSize = 9.5.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        GuardedStepperSlider(
            label = "Ukuran ZRAM Target (RAM Fisik: ${ramTotal.toInt()} MB)",
            value = stagedZramMb.toFloat(),
            valueRange = minZram..maxZram,
            steps = zramSteps,
            stepDelta = 512f,
            displayValue = { v -> "${v.roundToInt()} MB" },
            isUnlocked = isUnlocked,
            accentColor = AccentBlue,
            onCommitValue = { nextVal ->
                isStagingZram = true
                stagedZramMb = nextVal.roundToInt()
            }
        )

        AnimatedVisibility(visible = zramChanged) {
            Column(modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)) {
                Surface(
                    onClick = {
                        if (liveSwapUsedMb > 64) {
                            showZramConfirmDialog = true
                        } else {
                            isStagingZram = false
                            viewModel.applyZramConfiguration(stagedAlgo, stagedZramMb)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = AccentBlue.copy(alpha = 0.16f),
                    border = BorderStroke(0.9.dp, AccentBlue.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Terapkan ZRAM: $stagedZramMb MB (${stagedAlgo.uppercase()})",
                            color = AccentBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))

        // ── 2. Virtual Memory Presets & Swappiness ────────────
        Text(
            text = "Preset Virtual Memory",
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        val vmPresets = listOf(
            Triple("gaming", "Gaming", "Zero-stutter I/O"),
            Triple("balanced", "Balanced", "Standar Harian"),
            Triple("battery", "Battery", "Hemat Writeback")
        )
        val activeVmPreset = uiState.vmAdvanced.activePreset
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            vmPresets.forEach { (presetKey, title, subtitle) ->
                val isSelected = activeVmPreset == presetKey
                Surface(
                    onClick = { if (isUnlocked) viewModel.applyVmPreset(presetKey) },
                    enabled = isUnlocked,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) AccentBlue.copy(alpha = 0.22f) else BgElevated,
                    border = BorderStroke(
                        if (isSelected) 1.2.dp else 0.7.dp,
                        if (isSelected) AccentBlue else BorderSubtle
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) AccentBlue else if (isUnlocked) TextPrimary else TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        Text(
                            text = subtitle,
                            color = if (isSelected) AccentBlue.copy(alpha = 0.85f) else TextSecondary,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        GuardedStepperSlider(
            label = "VM Swappiness (Prioritas Swap)",
            value = uiState.vmAdvanced.swappiness.toFloat(),
            valueRange = 0f..200f,
            steps = 39,
            stepDelta = 5f,
            displayValue = { v -> "${v.roundToInt()}" },
            isUnlocked = isUnlocked,
            accentColor = AccentBlue,
            onCommitValue = { viewModel.setSwappiness(it.roundToInt()) }
        )

        Spacer(Modifier.height(6.dp))

        // ── 3. Progressive Disclosure: Advanced Writeback & KSM ──
        Surface(
            onClick = { showAdvancedVm = !showAdvancedVm },
            shape = RoundedCornerShape(10.dp),
            color = BgElevated,
            border = BorderStroke(0.7.dp, if (showAdvancedVm) AccentCyan.copy(alpha = 0.45f) else BorderSubtle),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                    Column {
                        Text(
                            text = "Parameter Writeback, VFS & KSM",
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (showAdvancedVm) "Sembunyikan parameter lanjutan" else "Dirty Ratio ${uiState.vmAdvanced.dirtyRatio}% • VFS ${uiState.vmAdvanced.vfsCachePressure}",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
                Icon(
                    imageVector = if (showAdvancedVm) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = showAdvancedVm,
            enter = expandVertically(tween(220)) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(180))
        ) {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GuardedStepperSlider(
                    label = "VM Dirty Ratio (Batas Writeback)",
                    value = uiState.vmAdvanced.dirtyRatio.toFloat(),
                    valueRange = 5f..60f,
                    steps = 10,
                    stepDelta = 5f,
                    displayValue = { v -> "${v.roundToInt()}%" },
                    isUnlocked = isUnlocked,
                    accentColor = AccentBlue,
                    onCommitValue = { viewModel.setDirtyRatio(it.roundToInt()) }
                )

                GuardedStepperSlider(
                    label = "VM Dirty Background Ratio",
                    value = uiState.vmAdvanced.dirtyBackgroundRatio.toFloat(),
                    valueRange = 1f..30f,
                    steps = 28,
                    stepDelta = 1f,
                    displayValue = { v -> "${v.roundToInt()}%" },
                    isUnlocked = isUnlocked,
                    accentColor = AccentBlue,
                    onCommitValue = { viewModel.setDirtyBackgroundRatio(it.roundToInt()) }
                )

                GuardedStepperSlider(
                    label = "VFS Cache Pressure",
                    value = uiState.vmAdvanced.vfsCachePressure.toFloat(),
                    valueRange = 50f..200f,
                    steps = 14,
                    stepDelta = 10f,
                    displayValue = { v -> "${v.roundToInt()}" },
                    isUnlocked = isUnlocked,
                    accentColor = AccentBlue,
                    onCommitValue = { viewModel.setVfsCachePressure(it.roundToInt()) }
                )

                GuardedStepperSlider(
                    label = "VM Dirty Expire Time",
                    value = (uiState.vmAdvanced.dirtyExpireCentisecs / 100).toFloat(),
                    valueRange = 10f..60f,
                    steps = 9,
                    stepDelta = 5f,
                    displayValue = { v -> "${v.roundToInt()}s" },
                    isUnlocked = isUnlocked,
                    accentColor = AccentBlue,
                    onCommitValue = { viewModel.setDirtyExpireCentisecs(it.roundToInt() * 100) }
                )

                GuardedStepperSlider(
                    label = "VM Dirty Writeback Interval",
                    value = (uiState.vmAdvanced.dirtyWritebackCentisecs / 100).toFloat(),
                    valueRange = 1f..15f,
                    steps = 13,
                    stepDelta = 1f,
                    displayValue = { v -> "${v.roundToInt()}s" },
                    isUnlocked = isUnlocked,
                    accentColor = AccentBlue,
                    onCommitValue = { viewModel.setDirtyWritebackCentisecs(it.roundToInt() * 100) }
                )

                // Conditional KSM Block (only shown when supported by kernel)
                val ksm = uiState.ksmStats
                if (ksm.supported) {
                    HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "KSM — Kernel Same-page Merging",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = stringResource(R.string.ksm_saved_memory, String.format(Locale.US, "%.1f", ksm.savedMb)),
                                        color = TextSecondary,
                                        fontSize = 10.5.sp
                                    )
                                }
                                Switch(
                                    checked = ksm.enabled,
                                    enabled = isUnlocked,
                                    onCheckedChange = { if (isUnlocked) viewModel.setKsmEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = AccentBlue,
                                        checkedTrackColor = AccentBlue.copy(alpha = 0.4f)
                                    )
                                )
                            }

                            if (ksm.enabled) {
                                Spacer(Modifier.height(8.dp))
                                GuardedStepperSlider(
                                    label = "Pages to Scan per Cycle",
                                    value = ksm.pagesToScan.toFloat(),
                                    valueRange = 50f..500f,
                                    steps = 8,
                                    stepDelta = 50f,
                                    displayValue = { v -> "${v.roundToInt()} pages" },
                                    isUnlocked = isUnlocked,
                                    accentColor = AccentBlue,
                                    onCommitValue = { viewModel.setKsmTunables(it.roundToInt(), ksm.sleepMs) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showZramConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showZramConfirmDialog = false },
            containerColor = BgCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = "Terapkan Ulang ZRAM (${stagedZramMb} MB • ${stagedAlgo.uppercase()})?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Saat ini ZRAM sedang menampung ~$liveSwapUsedMb MB data swap. Mengubah ukuran/algoritma akan mengosongkan ZRAM sementara (swapoff) dan dapat menimbulkan jeda singkat.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showZramConfirmDialog = false
                        isStagingZram = false
                        viewModel.applyZramConfiguration(stagedAlgo, stagedZramMb)
                    }
                ) {
                    Text("Tetap Terapkan", color = AccentBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showZramConfirmDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }
}
