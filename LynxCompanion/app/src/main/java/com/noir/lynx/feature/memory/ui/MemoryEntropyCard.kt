package com.noir.lynx.feature.memory.ui

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.MemoryEntropyInfo
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*
import kotlin.math.roundToInt

/**
 * MemoryEntropyCard — Consolidated LMK architecture status (no dead legacy card on PSI kernels)
 * and lock-shielded Linux kernel random entropy threshold controls.
 */
@Composable
fun MemoryEntropyCard(
    memoryEntropy: MemoryEntropyInfo,
    onSetEntropy: (Int, Int) -> Unit,
    onApplyLmkPreset: (String) -> Unit = {},
    activeLmkPreset: String = ""
) {
    var isUnlocked by remember { mutableStateOf(false) }
    var selectedLmkPreset by remember { mutableStateOf(activeLmkPreset) }
    var readThresh by remember { mutableIntStateOf(memoryEntropy.readThreshold) }
    var writeThresh by remember { mutableIntStateOf(memoryEntropy.writeThreshold) }
    var isStaged by remember { mutableStateOf(false) }

    LaunchedEffect(memoryEntropy.readThreshold, memoryEntropy.writeThreshold) {
        if (!isStaged) {
            readThresh = memoryEntropy.readThreshold
            writeThresh = memoryEntropy.writeThreshold
        }
    }

    val entropyChanged = readThresh != memoryEntropy.readThreshold || writeThresh != memoryEntropy.writeThreshold

    LynxCard(
        title = "Manajemen LMK & Entropi Kernel",
        subtitle = "Keamanan lmkd & kesediaan pool keacakan I/O",
        icon = Icons.Default.Security,
        accentColor = AccentBlue
    ) {
        // 1. Honest LMK Status or Guarded Legacy Minfree Presets
        Text("Low Memory Killer (LMK)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))

        if (!memoryEntropy.isLmkLegacySupported) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.7.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = memoryEntropy.lmkReason,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        } else {
            if (memoryEntropy.minfreeMb.isNotEmpty()) {
                Text(
                    text = "Minfree aktif: ${memoryEntropy.minfreeMb.joinToString(", ") { "${it}MB" }}",
                    color = AccentCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            val lmkPresets = listOf(
                Triple("conservative", "Conservative", "Simpan banyak app di RAM"),
                Triple("balanced", "Balanced", "Standar harian"),
                Triple("gaming", "Gaming", "Prioritaskan game aktif"),
                Triple("aggressive", "Aggressive", "Bebaskan RAM agresif")
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lmkPresets.chunked(2).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPresets.forEach { (id, label, desc) ->
                            val isSelected = selectedLmkPreset == id
                            Surface(
                                onClick = {
                                    if (isUnlocked) {
                                        selectedLmkPreset = id
                                        onApplyLmkPreset(id)
                                    }
                                },
                                enabled = isUnlocked,
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) AccentOrange.copy(alpha = 0.20f) else BgElevated,
                                border = BorderStroke(
                                    if (isSelected) 1.dp else 0.7.dp,
                                    if (isSelected) AccentOrange else BorderSubtle
                                ),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) AccentOrange else if (isUnlocked) TextPrimary else TextSecondary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    )
                                    Text(desc, color = TextSecondary, fontSize = 9.5.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = BorderSubtle)
        Spacer(Modifier.height(12.dp))

        // 2. Kernel Entropy Pool Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Linux Random Entropy Pool", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(
                text = "${memoryEntropy.entropyAvail} / 4096 bits",
                color = AccentCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (memoryEntropy.entropyAvail.toFloat() / 4096f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = AccentCyan,
            trackColor = BgElevated
        )

        Spacer(Modifier.height(12.dp))

        GuardedTuningBar(
            isUnlocked = isUnlocked,
            onToggleUnlock = { isUnlocked = it },
            accentColor = AccentBlue,
            lockedText = "Mode Aman • Threshold Entropi Terkunci",
            unlockedText = "Mode Edit Aktif • Threshold Siap Diubah"
        )

        GuardedStepperSlider(
            label = "Read Wakeup Threshold",
            value = readThresh.toFloat(),
            valueRange = 32f..256f,
            steps = 13,
            stepDelta = 16f,
            displayValue = { v -> "${v.roundToInt()} bits" },
            isUnlocked = isUnlocked,
            accentColor = AccentBlue,
            onCommitValue = {
                isStaged = true
                readThresh = it.roundToInt()
            }
        )

        GuardedStepperSlider(
            label = "Write Wakeup Threshold",
            value = writeThresh.toFloat(),
            valueRange = 256f..2048f,
            steps = 13,
            stepDelta = 128f,
            displayValue = { v -> "${v.roundToInt()} bits" },
            isUnlocked = isUnlocked,
            accentColor = AccentBlue,
            onCommitValue = {
                isStaged = true
                writeThresh = it.roundToInt()
            }
        )

        if (isUnlocked && entropyChanged) {
            Spacer(Modifier.height(6.dp))
            Surface(
                onClick = {
                    isStaged = false
                    onSetEntropy(readThresh, writeThresh)
                },
                shape = RoundedCornerShape(10.dp),
                color = AccentBlue.copy(alpha = 0.16f),
                border = BorderStroke(0.8.dp, AccentBlue.copy(alpha = 0.50f)),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Terapkan Threshold ($readThresh / $writeThresh bits)",
                        color = AccentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
