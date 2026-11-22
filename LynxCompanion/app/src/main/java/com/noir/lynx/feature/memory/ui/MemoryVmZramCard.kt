package com.noir.lynx.feature.memory.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.card.UnsupportedBadge
import com.noir.lynx.designsystem.controls.LynxActionButton
import com.noir.lynx.designsystem.controls.LynxNumericPresetRow
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.applyVmPreset
import com.noir.lynx.ui.applyZramConfiguration
import com.noir.lynx.ui.setSwappiness

/**
 * MemoryVmZramCard — Sub-tab 0 (RAM & ZRAM).
 * Clean, zero-redundancy layout:
 * 1. ZRAM Configuration: Compression algorithm selector + interactive target capacity row opening
 *    a dedicated preset+custom GB overlay dialog, with a single guarded Apply action.
 * 2. Quick VM Profile & Swappiness: 1-tap macro presets + clean Swappiness preset/input row.
 */
@Composable
fun MemoryVmZramCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val ramTotalMb = uiState.telemetry?.ramTotalMb?.takeIf { it > 0 } ?: 4096
    val recommendedMaxZramMb = (ramTotalMb / 2).coerceAtLeast(128)
    val telemetryZramTotalMb = uiState.telemetry?.zramTotalMb
    val liveZramMb = (telemetryZramTotalMb ?: uiState.state.memory.zramSizeMb).coerceAtLeast(0)
    val maxAllowedZramMb = maxOf((ramTotalMb * 3) / 4, liveZramMb, 128)
    val liveSwapUsedMb = if (liveZramMb > 0) {
        (uiState.telemetry?.zramUsedMb?.takeIf { it >= 0 } ?: (uiState.telemetry?.swapUsedMb ?: 0)).coerceAtLeast(0)
    } else 0

    val kernelAlgos = uiState.availZramCompAlgorithms
    val zramSupported = kernelAlgos.isNotEmpty() ||
        (telemetryZramTotalMb ?: 0) > 0 ||
        uiState.zramCompAlgorithm.isNotBlank()
    val liveAlgo = uiState.zramCompAlgorithm.ifBlank { kernelAlgos.firstOrNull().orEmpty() }
    val swappinessMax = uiState.vmAdvanced.swappinessMax.coerceAtLeast(100)

    var stagedAlgo by remember(liveAlgo) { mutableStateOf(liveAlgo) }
    var stagedZramMb by remember(liveZramMb) { mutableIntStateOf(liveZramMb) }
    var showHazardConfirmDialog by remember { mutableStateOf(false) }
    var showZramCapacityOverlay by remember { mutableStateOf(false) }

    if (showZramCapacityOverlay) {
        CustomZramGbDialog(
            currentMb = stagedZramMb,
            maxSafeMb = maxAllowedZramMb,
            recommendedMaxMb = recommendedMaxZramMb,
            ramTotalMb = ramTotalMb,
            accentColor = AccentCyan,
            onDismiss = { showZramCapacityOverlay = false },
            onConfirmMb = { customMb ->
                stagedZramMb = customMb
                showZramCapacityOverlay = false
            }
        )
    }

    if (showHazardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showHazardConfirmDialog = false },
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
                    text = if (stagedZramMb == 0) "Nonaktifkan ZRAM?" else "Konfirmasi Kapasitas ZRAM",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                val desc = if (stagedZramMb == 0) {
                    "ZRAM saat ini menyimpan ${formatZramGb(liveSwapUsedMb)} data swap. Menonaktifkan ZRAM akan memindahkan halaman tersebut kembali ke RAM fisik."
                } else {
                    "Kapasitas ${formatZramGb(stagedZramMb)} melebihi rekomendasi 50% RAM (${formatZramGb(recommendedMaxZramMb)}). Pastikan algoritma kompresi cepat (${stagedAlgo.uppercase()}) aktif."
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
                        showHazardConfirmDialog = false
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
                    onClick = { showHazardConfirmDialog = false },
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
            title = "Pengaturan RAM Virtual (ZRAM)",
            subtitle = "Pilih metode kompresi & kapasitas tambahan RAM",
            icon = Icons.Default.Memory,
            accentColor = AccentCyan,
            action = {
                if (zramSupported) {
                    val activeText = if (liveZramMb > 0) {
                        "${liveAlgo.ifBlank { "-" }.uppercase()} • ${formatZramGb(liveZramMb)}"
                    } else {
                        "NONAKTIF"
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (liveZramMb > 0) AccentCyanDim else BgElevated,
                        border = BorderStroke(
                            0.6.dp,
                            if (liveZramMb > 0) AccentCyan.copy(alpha = 0.35f) else BorderSubtle
                        )
                    ) {
                        Text(
                            text = activeText,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (liveZramMb > 0) AccentCyan else TextSecondary,
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Algoritma Kompresi",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondary
                                )
                                Text(
                                    text = describeCompressionAlgo(stagedAlgo),
                                    fontSize = 10.sp,
                                    color = AccentCyan.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            kernelAlgos.chunked(3).forEach { rowAlgos ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowAlgos.forEach { algo ->
                                        MemoryOptionChip(
                                            label = algo.uppercase(),
                                            selected = stagedAlgo == algo,
                                            enabled = !uiState.isApplyingZram,
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

                    // Interactive target capacity row opening dedicated ZRAM GB preset + custom overlay
                    val isDirtySize = stagedZramMb != liveZramMb
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(
                            if (isDirtySize) 0.9.dp else 0.6.dp,
                            if (isDirtySize) AccentCyan.copy(alpha = 0.55f) else BorderSubtle
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !uiState.isApplyingZram) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showZramCapacityOverlay = true
                            }
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
                                    .padding(end = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Ukuran ZRAM",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (isDirtySize) {
                                        "Target baru: ${if (stagedZramMb == 0) "Nonaktif (0 GB)" else formatZramGb(stagedZramMb)} (aktif: ${formatZramGb(liveZramMb)})"
                                    } else {
                                        "Ketuk untuk memilih kapasitas GB"
                                    },
                                    fontSize = 10.5.sp,
                                    color = if (isDirtySize) AccentCyan else TextSecondary,
                                    maxLines = 2,
                                    lineHeight = 14.sp,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AccentCyan.copy(alpha = 0.14f),
                                border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = if (stagedZramMb == 0) "Off (0 GB)" else formatZramGb(stagedZramMb),
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentCyan,
                                        maxLines = 1
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Ubah Kapasitas Target ZRAM",
                                        tint = AccentCyan.copy(alpha = 0.85f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    val isDirty = (stagedZramMb > 0 && stagedAlgo != liveAlgo) || stagedZramMb != liveZramMb
                    AnimatedVisibility(
                        visible = isDirty || uiState.isApplyingZram,
                        enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
                        exit = shrinkVertically(animationSpec = tween(160)) + fadeOut(animationSpec = tween(160))
                    ) {
                        val targetAlgo = stagedAlgo.ifBlank { liveAlgo.ifBlank { "lz4" } }
                        val actionLabel = when {
                            uiState.isApplyingZram -> "Mengonfigurasi Ulang ZRAM..."
                            stagedZramMb == 0 -> "Nonaktifkan ZRAM (0 GB)"
                            else -> "Terapkan ZRAM (${targetAlgo.uppercase()} • ${formatZramGb(stagedZramMb)})"
                        }
                        LynxActionButton(
                            text = actionLabel,
                            icon = Icons.Default.Bolt,
                            isLoading = uiState.isApplyingZram,
                            accentColor = if (stagedZramMb == 0) AccentOrange else AccentCyan,
                            onClick = {
                                if (uiState.isApplyingZram) return@LynxActionButton
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val needsHazardConfirm =
                                    (stagedZramMb == 0 && liveSwapUsedMb > 0) ||
                                        (stagedZramMb > recommendedMaxZramMb && stagedZramMb != liveZramMb)
                                if (needsHazardConfirm) {
                                    showHazardConfirmDialog = true
                                } else {
                                    viewModel.applyZramConfiguration(targetAlgo, stagedZramMb)
                                }
                            }
                        )
                    }
                }
            }
        }

        // ── Card 2: Quick VM Profile & Swappiness ──
        LynxCard(
            title = "Prioritas RAM vs ZRAM",
            subtitle = "Atur seberapa sering data dipindahkan ke ZRAM",
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
                    Triple("gaming", "Mode Gaming", AccentOrange),
                    Triple("balanced", "Seimbang", AccentBlue),
                    Triple("battery", "Hemat Daya", AccentGreen)
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

                val swappinessPresets = remember(swappinessMax) {
                    listOf(
                        "10 • Utamakan RAM" to 10,
                        "30 • Responsif" to 30,
                        "60 • Standar" to 60,
                        "100 • Aktif ZRAM" to 100,
                        "130 • Optimal" to 130,
                        "160 • Maksimal" to 160
                    ).filter { it.second <= swappinessMax }
                }
                LynxNumericPresetRow(
                    label = "Agresivitas ZRAM (Swappiness)",
                    subtitle = "Rendah = utamakan RAM fisik • Tinggi = aktif gunakan ZRAM",
                    value = uiState.vmAdvanced.swappiness,
                    displayValue = "${uiState.vmAdvanced.swappiness} / $swappinessMax",
                    min = 0,
                    max = swappinessMax,
                    dialogTitle = "Atur Agresivitas ZRAM",
                    dialogHint = "Nilai rendah menahan aplikasi lebih lama di RAM fisik; nilai tinggi lebih aktif memindahkan data ke ZRAM (0 – $swappinessMax).",
                    presets = swappinessPresets,
                    accentColor = AccentBlue,
                    onCommitValue = { viewModel.setSwappiness(it) }
                )
            }
        }
    }
}

private fun describeCompressionAlgo(algo: String): String {
    return when (algo.trim().lowercase()) {
        "lz4" -> "Latensi tercepat"
        "zstd" -> "Rasio hemat RAM"
        "lzo", "lzo-rle" -> "Seimbang ringan"
        "deflate" -> "Kompresi klasik"
        "842" -> "Hardware cepat"
        else -> ""
    }
}
