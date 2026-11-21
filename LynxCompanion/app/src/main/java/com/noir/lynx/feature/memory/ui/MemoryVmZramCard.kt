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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.card.UnsupportedBadge
import com.noir.lynx.designsystem.controls.LynxActionButton
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.applyVmPreset
import com.noir.lynx.ui.applyZramConfiguration
import com.noir.lynx.ui.setSwappiness
import kotlin.math.roundToInt

/**
 * MemoryVmZramCard — Sub-tab 0 (RAM & ZRAM).
 * Split cleanly into two minimalist cards:
 * 1. ZRAM Configuration (Algorithm + proportional size chips <= 50% RAM + clean slider + conditional Apply).
 * 2. Quick VM Profile & Swappiness (3-option preset + single Swappiness slider).
 */
@Composable
fun MemoryVmZramCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val ramTotalMb = uiState.telemetry?.ramTotalMb?.takeIf { it > 0 } ?: 4096
    val maxSafeZramMb = (ramTotalMb / 2).coerceAtLeast(128)
    val liveZramMb = (uiState.telemetry?.zramTotalMb?.takeIf { it > 0 }
        ?: uiState.state.memory.zramSizeMb).coerceAtLeast(128)
    val liveSwapUsedMb = uiState.telemetry?.zramUsedMb?.takeIf { it > 0 }
        ?: (uiState.telemetry?.swapUsedMb ?: 0)

    val kernelAlgos = uiState.availZramCompAlgorithms
    val zramSupported = kernelAlgos.isNotEmpty() ||
        (uiState.telemetry?.zramTotalMb ?: 0) > 0 ||
        uiState.zramCompAlgorithm.isNotBlank()
    val liveAlgo = uiState.zramCompAlgorithm.ifBlank { kernelAlgos.firstOrNull().orEmpty() }
    val swappinessMax = uiState.vmAdvanced.swappinessMax.coerceAtLeast(100)

    // Standard GB capacity presets filtered strictly within the kernel's 50% RAM safety cap
    val dynamicZramSizes = remember(ramTotalMb, maxSafeZramMb) {
        val standardSteps = listOf(0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0, 8.0)
            .map { (it * 1024.0).roundToInt() }
            .filter { it in 128..maxSafeZramMb }
            .toMutableList()
        val maxQuarterAlignedMb = ((maxSafeZramMb / 256) * 256).takeIf { it >= 256 }
            ?: ((maxSafeZramMb / 128) * 128)
        if (maxQuarterAlignedMb >= 128 &&
            maxQuarterAlignedMb !in standardSteps &&
            (standardSteps.isEmpty() || maxQuarterAlignedMb - standardSteps.last() >= 256)
        ) {
            standardSteps.add(maxQuarterAlignedMb)
        }
        standardSteps.distinct().sorted().takeLast(4)
    }

    var stagedAlgo by remember(liveAlgo) { mutableStateOf(liveAlgo) }
    var stagedZramMb by remember(liveZramMb) { mutableIntStateOf(liveZramMb) }
    var showZramConfirmDialog by remember { mutableStateOf(false) }
    var showCustomZramDialog by remember { mutableStateOf(false) }

    val sliderMaxZramMb = maxOf(maxSafeZramMb, stagedZramMb, 512)

    if (showCustomZramDialog) {
        CustomZramGbDialog(
            currentMb = stagedZramMb.coerceIn(128, maxSafeZramMb),
            maxSafeMb = maxSafeZramMb,
            ramTotalMb = ramTotalMb,
            accentColor = AccentCyan,
            onDismiss = { showCustomZramDialog = false },
            onConfirmMb = { customMb ->
                stagedZramMb = customMb
                showCustomZramDialog = false
            }
        )
    }

    if (showZramConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showZramConfirmDialog = false },
            containerColor = Color(0xFF10141F),
            shape = RoundedCornerShape(18.dp),
            icon = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentOrange.copy(alpha = 0.14f),
                    border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.35f)),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Terapkan Konfigurasi ZRAM?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                val stagedGbText = formatZramGb(stagedZramMb)
                val desc = if (liveSwapUsedMb > 0) {
                    "ZRAM saat ini masih menampung ${formatZramGb(liveSwapUsedMb)} halaman aktif. Perubahan ukuran atau algoritma ke ${stagedAlgo.uppercase()} • $stagedGbText hanya dapat diterapkan langsung saat ZRAM kosong (0 GB)."
                } else {
                    "Mengatur ZRAM ke ${stagedAlgo.uppercase()} • $stagedGbText."
                }
                Text(
                    text = desc,
                    fontSize = 12.5.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showZramConfirmDialog = false
                        viewModel.applyZramConfiguration(stagedAlgo, stagedZramMb)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan,
                        contentColor = BgDeepOled
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text("Ya, Terapkan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showZramConfirmDialog = false },
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Card 1: ZRAM Configuration ──
        LynxCard(
            title = "Konfigurasi ZRAM",
            subtitle = "Algoritma kompresi & ukuran swap RAM",
            icon = Icons.Default.Memory,
            accentColor = AccentCyan,
            action = {
                if (zramSupported) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentCyanDim,
                        border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "${liveAlgo.ifBlank { "-" }.uppercase()} • ${formatZramGb(liveZramMb)}",
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        ) {
            if (!zramSupported) {
                UnsupportedBadge(
                    reason = "Node /sys/block/zram0/comp_algorithm tidak ditemukan pada kernel ini."
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (kernelAlgos.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Algoritma Kompresi",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                            kernelAlgos.chunked(3).forEach { rowAlgos ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowAlgos.forEach { algo ->
                                        MemoryOptionChip(
                                            label = algo.uppercase(),
                                            selected = stagedAlgo == algo,
                                            accentColor = AccentCyan,
                                            onClick = { stagedAlgo = algo },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(3 - rowAlgos.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = BorderGlass, thickness = 0.6.dp)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Kapasitas Target (Maks 50% RAM)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        val sizeChipItems: List<Pair<String, Int?>> =
                            dynamicZramSizes.map { formatZramGb(it) to it } + ("Kustom" to null)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            sizeChipItems.chunked(3).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { (label, mbOpt) ->
                                        val isCustomBtn = mbOpt == null
                                        val isSelected = if (isCustomBtn) {
                                            stagedZramMb !in dynamicZramSizes
                                        } else {
                                            stagedZramMb == mbOpt
                                        }
                                        MemoryOptionChip(
                                            label = if (isCustomBtn && isSelected) "${formatZramGb(stagedZramMb)}*" else label,
                                            selected = isSelected,
                                            accentColor = if (isCustomBtn) AccentPurple else AccentCyan,
                                            onClick = {
                                                if (mbOpt != null) stagedZramMb = mbOpt
                                                else showCustomZramDialog = true
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }

                    MemoryValueSlider(
                        label = "Ukuran ZRAM",
                        value = stagedZramMb.toFloat(),
                        valueRange = 128f..sliderMaxZramMb.toFloat(),
                        stepSize = 128f,
                        displayValue = { formatZramGb(it.roundToInt()) },
                        accentColor = AccentCyan,
                        allowCustomInput = false,
                        onCustomClick = { showCustomZramDialog = true },
                        onCommitValue = { stagedZramMb = it.roundToInt() }
                    )

                    val isDirty = stagedAlgo != liveAlgo || stagedZramMb != liveZramMb
                    AnimatedVisibility(
                        visible = isDirty,
                        enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
                        exit = shrinkVertically(animationSpec = tween(160)) + fadeOut(animationSpec = tween(160))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (liveSwapUsedMb > 0) {
                                Text(
                                    text = "ZRAM saat ini menampung ${formatZramGb(liveSwapUsedMb)} data aktif; resize live membutuhkan swap 0 GB.",
                                    fontSize = 10.5.sp,
                                    color = AccentOrange,
                                    lineHeight = 14.sp
                                )
                            }
                            LynxActionButton(
                                text = "Terapkan ZRAM (${stagedAlgo.uppercase()} • ${formatZramGb(stagedZramMb)})",
                                icon = Icons.Default.Bolt,
                                accentColor = AccentCyan,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val targetAlgo = stagedAlgo.ifBlank { liveAlgo.ifBlank { "lz4" } }
                                    if (liveSwapUsedMb > 0) {
                                        showZramConfirmDialog = true
                                    } else {
                                        viewModel.applyZramConfiguration(targetAlgo, stagedZramMb)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Card 2: Quick VM Profile & Swappiness ──
        LynxCard(
            title = "Profil VM & Swappiness",
            subtitle = "Prioritas RAM fisik vs swap kompresi",
            icon = Icons.Default.Speed,
            accentColor = AccentBlue,
            action = {
                if (uiState.vmAdvanced.activePreset == "custom") {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentPurple.copy(alpha = 0.14f),
                        border = BorderStroke(0.6.dp, AccentPurple.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "KUSTOM",
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = AccentPurple,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val vmPresets = listOf(
                    Triple("gaming", "Gaming", AccentOrange),
                    Triple("balanced", "Balanced", AccentBlue),
                    Triple("battery", "Battery", AccentGreen)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vmPresets.forEach { (presetKey, label, color) ->
                        MemoryOptionChip(
                            label = label,
                            selected = uiState.vmAdvanced.activePreset == presetKey,
                            accentColor = color,
                            onClick = { viewModel.applyVmPreset(presetKey) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                MemoryValueSlider(
                    label = "VM Swappiness",
                    value = uiState.vmAdvanced.swappiness.toFloat(),
                    valueRange = 0f..swappinessMax.toFloat(),
                    stepSize = 5f,
                    displayValue = { "${it.roundToInt()} / $swappinessMax" },
                    accentColor = AccentBlue,
                    unit = "",
                    customDialogMin = 0,
                    customDialogMax = swappinessMax,
                    customDialogHint = "Nilai kecil mempertahankan aplikasi lebih lama di RAM fisik; tinggi lebih agresif memakai ZRAM.",
                    onCommitValue = { viewModel.setSwappiness(it.roundToInt()) }
                )
            }
        }
    }
}
