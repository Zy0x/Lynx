package com.noir.lynx.feature.memory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.MemoryEntropyInfo
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.controls.LynxNumericPresetRow
import com.noir.lynx.designsystem.theme.*

/**
 * MemoryEntropyCard — Sub-tab 1 (VM & Entropi).
 * Cleanly separates two distinct domains:
 * 1. Tekanan Memori & LMK Engine (PSI Memory Stall + Userspace LMKD status OR Legacy minfree presets)
 *    without frightening users with false "Unsupported" banners on modern LMKD kernels.
 * 2. Kernel Entropy Pool (RNG meter + immediate 1-tap overlay controls for Read/Write wakeup thresholds).
 */
@Composable
fun MemoryEntropyCard(
    memoryEntropy: MemoryEntropyInfo,
    onApplyEntropy: (readThresh: Int, writeThresh: Int) -> Unit,
    onApplyLmkPreset: (preset: String) -> Unit,
    onApplyCustomLmkMb: (tiersMb: List<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val poolSize = memoryEntropy.poolSizeBits.coerceAtLeast(1)
    val entropyAvail = memoryEntropy.entropyAvail.coerceIn(0, poolSize)
    val entropyRatio = (entropyAvail.toFloat() / poolSize.toFloat()).coerceIn(0f, 1f)

    val readMax = maxOf(64, (poolSize / 2).coerceAtLeast(memoryEntropy.readThreshold)).coerceAtMost(poolSize)
    val writeMax = maxOf(128, poolSize)

    var showCustomLmkDialog by remember { mutableStateOf(false) }

    if (showCustomLmkDialog) {
        CustomLmkMinfreeDialog(
            currentTiersMb = memoryEntropy.minfreeMb,
            onDismiss = { showCustomLmkDialog = false },
            onConfirm = { customTiers ->
                showCustomLmkDialog = false
                onApplyCustomLmkMb(customTiers)
            }
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Card 1: Memory Pressure & Process Kill Engine (PSI / LMK) ──
        LynxCard(
            title = "Low Memory Killer (LMK / LMKD)",
            subtitle = "Pengatur batas penutupan aplikasi latar saat RAM hampir penuh",
            icon = Icons.Default.Security,
            accentColor = AccentCyan,
            action = {
                val badgeText = when {
                    !memoryEntropy.isLmkLegacySupported -> "LMKD USERSPACE"
                    memoryEntropy.isLmkWritable -> "MINFREE AKTIF"
                    else -> "READ-ONLY"
                }
                val badgeColor = when {
                    !memoryEntropy.isLmkLegacySupported -> AccentBlue
                    memoryEntropy.isLmkWritable -> AccentCyan
                    else -> TextTertiary
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.14f),
                    border = BorderStroke(0.6.dp, badgeColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (memoryEntropy.psiMemorySomeAvg10.isNotBlank() || memoryEntropy.psiMemoryFullAvg10.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(0.6.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "PSI Memory Stall (10 Detik)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Persentase waktu aplikasi tertahan menunggu alokasi RAM",
                                    fontSize = 10.sp,
                                    color = TextTertiary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (memoryEntropy.psiMemorySomeAvg10.isNotBlank()) {
                                    PsiPill("Some ${memoryEntropy.psiMemorySomeAvg10}%", AccentBlue)
                                }
                                if (memoryEntropy.psiMemoryFullAvg10.isNotBlank()) {
                                    PsiPill("Full ${memoryEntropy.psiMemoryFullAvg10}%", AccentOrange)
                                }
                            }
                        }
                    }
                }

                if (!memoryEntropy.isLmkLegacySupported) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgSurfaceLowest,
                        border = BorderStroke(0.6.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Pembersihan aplikasi latar dikelola otomatis oleh daemon LMKD userspace berbasis tekanan memori (PSI).",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                } else {
                    if (memoryEntropy.minfreeMb.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BgElevated,
                            border = BorderStroke(0.6.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "LMK Minfree (6 Kategori Prioritas Aplikasi)",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = memoryEntropy.minfreeMb.joinToString(" • ") { "${it}M" },
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan
                                )
                            }
                        }
                    }

                    if (memoryEntropy.isLmkWritable) {
                        val activePreset = remember(memoryEntropy.minfreeMb) {
                            when (memoryEntropy.minfreeMb) {
                                listOf(72, 90, 108, 126, 216, 315) -> "conservative"
                                listOf(72, 90, 108, 144, 256, 360) -> "balanced"
                                listOf(72, 90, 108, 168, 280, 392) -> "gaming"
                                listOf(72, 90, 126, 192, 315, 456) -> "aggressive"
                                else -> "custom"
                            }
                        }

                        val lmkOptions = listOf(
                            Triple("conservative", "Multitasking", AccentGreen),
                            Triple("balanced", "Seimbang", AccentBlue),
                            Triple("gaming", "Mode Gaming", AccentCyan),
                            Triple("aggressive", "Lega Maksimal", AccentOrange)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            lmkOptions.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pair.forEach { (key, label, color) ->
                                        MemoryOptionChip(
                                            label = label,
                                            selected = activePreset == key,
                                            accentColor = color,
                                            onClick = { onApplyLmkPreset(key) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            MemoryOptionChip(
                                label = if (activePreset == "custom") "Atur Manual 6 Tier Minfree (Aktif)" else "Atur Manual 6 Tier Minfree (MB)",
                                selected = activePreset == "custom",
                                accentColor = AccentPurple,
                                onClick = { showCustomLmkDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text(
                            text = "Node LMK minfree dikunci hanya-baca (read-only) oleh kernel.",
                            fontSize = 11.sp,
                            color = TextTertiary
                        )
                    }
                }
            }
        }

        // ── Card 2: Kernel Entropy Pool (RNG) ──
        if (memoryEntropy.isEntropySupported) {
            LynxCard(
                title = "Kernel Entropy Pool (RNG)",
                subtitle = "Cadangan bit acak kriptografi untuk mempercepat enkripsi & pembukaan sesi aplikasi",
                icon = Icons.Default.Tune,
                accentColor = AccentBlue,
                action = {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentCyanDim,
                        border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "$entropyAvail / $poolSize bit",
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(
                        progress = { entropyRatio },
                        color = AccentCyan,
                        trackColor = BgSurfaceLowest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )

                    if (!memoryEntropy.isEntropyThresholdWritable) {
                        Text(
                            text = "Kernel modern (Linux 5.17+ Blake2s RNG) mengelola pool entropi ($poolSize-bit) secara otomatis tanpa perlu batas wakeup manual.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    } else {
                        val readPresets = remember(readMax) {
                            listOf(32, 64, 128, 256, 512, 1024)
                                .filter { it in 8..readMax }
                                .takeLast(4)
                                .map { "$it bit" to it }
                        }
                        LynxNumericPresetRow(
                            label = "Read Wakeup Threshold (Batas Minimum Baca)",
                            subtitle = "Batas bit minimum sebelum proses sistem membangunkan generator angka acak baru",
                            value = memoryEntropy.readThreshold,
                            displayValue = "${memoryEntropy.readThreshold} bit",
                            min = 8,
                            max = readMax,
                            unit = "bit",
                            dialogHint = "Ambang minimum pembacaan data acak (kapasitas: $poolSize bit).",
                            presets = readPresets,
                            accentColor = AccentCyan,
                            onCommitValue = { newRead ->
                                onApplyEntropy(newRead, memoryEntropy.writeThreshold)
                            }
                        )

                        HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), thickness = 0.7.dp)

                        val writePresets = remember(writeMax) {
                            listOf(64, 128, 256, 512, 1024, 2048)
                                .filter { it in 16..writeMax }
                                .takeLast(4)
                                .map { "$it bit" to it }
                        }
                        LynxNumericPresetRow(
                            label = "Write Wakeup Threshold (Batas Isi Ulang)",
                            subtitle = "Target kapasitas bit saat kernel mengisi ulang cadangan entropi di latar belakang",
                            value = memoryEntropy.writeThreshold,
                            displayValue = "${memoryEntropy.writeThreshold} bit",
                            min = 16,
                            max = writeMax,
                            unit = "bit",
                            dialogHint = "Target pengisian cadangan entropi di latar belakang.",
                            presets = writePresets,
                            accentColor = AccentBlue,
                            onCommitValue = { newWrite ->
                                onApplyEntropy(memoryEntropy.readThreshold, newWrite)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PsiPill(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(0.6.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CustomLmkMinfreeDialog(
    currentTiersMb: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (List<Int>) -> Unit
) {
    val tierLabels = listOf(
        "Foreground",
        "Visible",
        "Secondary",
        "Hidden",
        "Content",
        "Empty"
    )
    val initial = List(6) { idx ->
        currentTiersMb.getOrNull(idx)?.coerceIn(4, 4096) ?: ((idx + 1) * 48)
    }
    var inputs by remember(currentTiersMb) {
        mutableStateOf(initial.map { it.toString() })
    }
    val parsedValues = inputs.map { it.trim().toIntOrNull() }
    val allValid = parsedValues.all { it != null && it in 4..4096 }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10141F),
        shape = RoundedCornerShape(18.dp),
        icon = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AccentPurple.copy(alpha = 0.14f),
                border = BorderStroke(0.8.dp, AccentPurple.copy(alpha = 0.35f)),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = AccentPurple,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = "Kustom LMK Minfree (6-Tier MB)",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Sesuaikan batas kill memori per kategori proses (4 – 4096 MB).",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
                tierLabels.chunked(2).forEachIndexed { rowIdx, pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEachIndexed { colIdx, label ->
                            val idx = rowIdx * 2 + colIdx
                            val valText = inputs[idx]
                            val validItem = parsedValues[idx]?.let { it in 4..4096 } == true
                            OutlinedTextField(
                                value = valText,
                                onValueChange = { raw ->
                                    val clean = raw.filter(Char::isDigit)
                                    inputs = inputs.toMutableList().also { it[idx] = clean }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                label = { Text("$label (MB)", fontSize = 10.5.sp) },
                                isError = !validItem,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentPurple,
                                    unfocusedBorderColor = BorderSubtle,
                                    focusedLabelColor = AccentPurple,
                                    unfocusedLabelColor = TextSecondary,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val values = parsedValues.filterNotNull()
                    if (values.size == 6) onConfirm(values)
                },
                enabled = allValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPurple,
                    contentColor = BgDeepOled
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text("Terapkan 6-Tier", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text("Batal", color = TextSecondary)
            }
        }
    )
}
