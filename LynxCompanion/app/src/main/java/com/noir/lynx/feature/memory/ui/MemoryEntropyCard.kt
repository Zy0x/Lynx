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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Security
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
import com.noir.lynx.designsystem.card.UnsupportedBadge
import com.noir.lynx.designsystem.controls.LynxActionButton
import com.noir.lynx.designsystem.theme.*
import kotlin.math.roundToInt

/**
 * MemoryEntropyCard — Sub-tab 1 (VM & Entropi).
 * Dynamically handles legacy LMK sysfs minfree (synchronized 4 presets + 6-tier custom MB)
 * OR modern userspace lmkd + /proc/pressure/memory (PSI), plus poolsize-aware Entropy monitoring.
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

    var stagedReadThresh by remember(memoryEntropy.readThreshold, readMax) {
        mutableIntStateOf(memoryEntropy.readThreshold.coerceIn(8, readMax))
    }
    var stagedWriteThresh by remember(memoryEntropy.writeThreshold, writeMax) {
        mutableIntStateOf(memoryEntropy.writeThreshold.coerceIn(16, writeMax))
    }
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

    LynxCard(
        title = "LMK, PSI & Entropi Kernel",
        subtitle = buildString {
            append(if (memoryEntropy.isLmkLegacySupported) "LMK Legacy Sysfs" else "Userspace lmkd / PSI")
            append(" • RNG Pool $poolSize bit")
        },
        icon = Icons.Default.Security,
        accentColor = AccentCyan,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── 1. PSI Memory Pressure Telemetry (/proc/pressure/memory) ──
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
                                text = "PSI Memory Stall (10 dtk)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "/proc/pressure/memory",
                                fontSize = 10.sp,
                                color = TextTertiary
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (memoryEntropy.psiMemorySomeAvg10.isNotBlank()) {
                                PsiPill("some ${memoryEntropy.psiMemorySomeAvg10}%", AccentBlue)
                            }
                            if (memoryEntropy.psiMemoryFullAvg10.isNotBlank()) {
                                PsiPill("full ${memoryEntropy.psiMemoryFullAvg10}%", AccentOrange)
                            }
                        }
                    }
                }
            }

            // ── 2. Low Memory Killer (Legacy Sysfs vs Userspace lmkd) ──
            if (!memoryEntropy.isLmkLegacySupported) {
                UnsupportedBadge(reason = memoryEntropy.lmkReason)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Low Memory Killer (minfree)",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (!memoryEntropy.isLmkWritable) {
                            Text(
                                text = "READ-ONLY",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TextTertiary
                            )
                        }
                    }

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
                                    text = "Ambang Saat Ini (FG • VIS • SEC • HID • CNT • EMP)",
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
                            Triple("conservative", "Multitask", AccentGreen),
                            Triple("balanced", "Balanced", AccentBlue),
                            Triple("gaming", "Gaming", AccentCyan),
                            Triple("aggressive", "Aggressive", AccentOrange)
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
                                label = if (activePreset == "custom") "Edit Kustom 6-Tier (Aktif)" else "Kustom 6-Tier (MB)",
                                selected = activePreset == "custom",
                                accentColor = AccentPurple,
                                onClick = { showCustomLmkDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text(
                            text = "Node sysfs minfree hanya-baca di kernel ini.",
                            fontSize = 11.sp,
                            color = TextTertiary
                        )
                    }
                }
            }

            // ── 3. Entropy Pool & Dynamic Kernel RNG Adaptation ──
            if (memoryEntropy.isEntropySupported) {
                HorizontalDivider(color = BorderGlass, thickness = 0.6.dp)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(0.6.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Kernel Entropy Pool",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "$entropyAvail / $poolSize bits",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan
                            )
                        }

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
                                text = "CSPRNG modern ($poolSize-bit) — manajemen entropi otomatis.",
                                fontSize = 10.5.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                if (memoryEntropy.isEntropyThresholdWritable) {
                    MemoryValueSlider(
                        label = "Read Wakeup Threshold",
                        value = stagedReadThresh.toFloat(),
                        valueRange = 8f..readMax.toFloat(),
                        stepSize = maxOf(8f, (readMax / 16).toFloat()),
                        displayValue = { "${it.roundToInt()} bit" },
                        accentColor = AccentCyan,
                        unit = "bit",
                        customDialogMin = 8,
                        customDialogMax = poolSize,
                        customDialogHint = "Batas bawah entropi untuk pembacaan /dev/random (pool: $poolSize bit).",
                        onCommitValue = { stagedReadThresh = it.roundToInt() }
                    )

                    MemoryValueSlider(
                        label = "Write Wakeup Threshold",
                        value = stagedWriteThresh.toFloat(),
                        valueRange = 16f..writeMax.toFloat(),
                        stepSize = maxOf(16f, (writeMax / 16).toFloat()),
                        displayValue = { "${it.roundToInt()} bit" },
                        accentColor = AccentBlue,
                        unit = "bit",
                        customDialogMin = 16,
                        customDialogMax = poolSize,
                        customDialogHint = "Ambang pengisian entropi (/proc/sys/kernel/random/write_wakeup_threshold).",
                        onCommitValue = { stagedWriteThresh = it.roundToInt() }
                    )

                    val entropyDirty = stagedReadThresh != memoryEntropy.readThreshold ||
                        stagedWriteThresh != memoryEntropy.writeThreshold
                    AnimatedVisibility(
                        visible = entropyDirty,
                        enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
                        exit = shrinkVertically(animationSpec = tween(160)) + fadeOut(animationSpec = tween(160))
                    ) {
                        LynxActionButton(
                            text = "Terapkan Ambang ($stagedReadThresh / $stagedWriteThresh bit)",
                            icon = Icons.Default.Bolt,
                            accentColor = AccentCyan,
                            onClick = { onApplyEntropy(stagedReadThresh, stagedWriteThresh) }
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
