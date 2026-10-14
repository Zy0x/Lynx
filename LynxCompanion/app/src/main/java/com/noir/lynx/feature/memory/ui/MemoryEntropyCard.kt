package com.noir.lynx.feature.memory.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*

@Composable
fun MemoryEntropyCard(
    memoryEntropy: MemoryEntropyInfo,
    onSetEntropy: (Int, Int) -> Unit,
) {
    var readThresh by remember { mutableIntStateOf(memoryEntropy.readThreshold) }
    var writeThresh by remember { mutableIntStateOf(memoryEntropy.writeThreshold) }
    var lastEntropyTouch by remember { mutableLongStateOf(0L) }

    LaunchedEffect(memoryEntropy.readThreshold, memoryEntropy.writeThreshold) {
        if (System.currentTimeMillis() - lastEntropyTouch > 3500L) {
            readThresh = memoryEntropy.readThreshold
            writeThresh = memoryEntropy.writeThreshold
        }
    }

    LynxCard(
        title = "LMK Minfree & Entropy Tuner",
        icon = Icons.Default.Memory,
        accentColor = AccentBlue
    ) {
        // LMK Architecture status
        Text("Low Memory Killer (LMK)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (!memoryEntropy.isLmkLegacySupported) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = memoryEntropy.lmkReason,
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        } else {
            Text("Legacy Minfree Levels: ${memoryEntropy.minfreeMb.joinToString(", ") { "${it}MB" }}", color = TextSecondary, fontSize = 11.5.sp)
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = BorderSubtle)
        Spacer(Modifier.height(14.dp))

        // Entropy Pool Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Linux Kernel Random Entropy", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                "${memoryEntropy.entropyAvail} / 4096 bits",
                color = AccentCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (memoryEntropy.entropyAvail.toFloat() / 4096f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = AccentCyan,
            trackColor = BgElevated
        )

        Spacer(Modifier.height(10.dp))
        Text("Read Wakeup Threshold: $readThresh bits", color = TextSecondary, fontSize = 11.5.sp)
        Slider(
            value = readThresh.toFloat(),
            onValueChange = {
                readThresh = it.toInt()
                lastEntropyTouch = System.currentTimeMillis()
            },
            valueRange = 32f..256f,
            colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
        )

        Text("Write Wakeup Threshold: $writeThresh bits", color = TextSecondary, fontSize = 11.5.sp)
        Slider(
            value = writeThresh.toFloat(),
            onValueChange = {
                writeThresh = it.toInt()
                lastEntropyTouch = System.currentTimeMillis()
            },
            valueRange = 256f..2048f,
            colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
        )

        LynxActionButton(
            text = "Terapkan Threshold Entropi",
            icon = Icons.Default.Save,
            onClick = {
                lastEntropyTouch = System.currentTimeMillis()
                onSetEntropy(readThresh, writeThresh)
            },
            accentColor = AccentBlue
        )
    }
}

// ============================================================
//  FKM PARITY: CUSTOM SCRIPT MANAGER CARD
// ============================================================
