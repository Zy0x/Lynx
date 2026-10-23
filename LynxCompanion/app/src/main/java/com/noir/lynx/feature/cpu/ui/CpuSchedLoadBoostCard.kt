package com.noir.lynx.feature.cpu.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.data.SchedulerInfo
import com.noir.lynx.designsystem.theme.*

/**
 * CpuSchedLoadBoostCard — Task Capacity Bias & Anti-Capacity Inversion Controller.
 * Manages /sys/devices/system/cpu/cpu[0-7]/sched_load_boost to bias heavy threads towards Big clusters.
 * Mobile-first & touch-first (>=48dp), OLED Cyberpunk dark aesthetic.
 */
@Composable
fun CpuSchedLoadBoostCard(
    schedInfo: SchedulerInfo,
    onSetSchedLoadBoost: (littleBoost: Int, bigBoost: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showCustomDialog by remember { mutableStateOf(false) }

    val curLittle = schedInfo.littleSchedLoadBoost
    val curBig = schedInfo.bigSchedLoadBoost

    val detectedPreset = when {
        curLittle == -10 && curBig == 20 -> "anti_inversion"
        curLittle == -6 && curBig == 15 -> "performance"
        curLittle == -6 && curBig == -6 -> "battery"
        curLittle == 0 && curBig == 0 -> "balanced"
        else -> "custom"
    }

    val presetLabel = when (detectedPreset) {
        "anti_inversion" -> "Anti-Capacity Inversion"
        "performance" -> "Prioritas Core Performa"
        "battery" -> "Hemat Baterai"
        "balanced" -> "Seimbang Default"
        else -> "Kustom ($curLittle / $curBig)"
    }

    val presetColor = when (detectedPreset) {
        "anti_inversion" -> AccentCyan
        "performance" -> AccentOrange
        "battery" -> Color(0xFF8B5CF6)
        "balanced" -> AccentGreen
        else -> Color(0xFF94A3B8)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0C0F17),
        border = BorderStroke(0.6.dp, Color(0x15FFFFFF)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Title & Active Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = presetColor.copy(alpha = 0.14f),
                        border = BorderStroke(0.6.dp, presetColor.copy(alpha = 0.35f)),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.VerticalAlignTop,
                                contentDescription = null,
                                tint = presetColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.cpu_sched_load_boost_title),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Little: $curLittle • Big: $curBig",
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = presetColor
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = presetColor.copy(alpha = 0.16f),
                    border = BorderStroke(0.6.dp, presetColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = presetLabel,
                        color = presetColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.cpu_sched_load_boost_desc),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = TextSecondary.copy(alpha = 0.8f)
            )

            // 4 Tactile Bias Pills
            val biasOptions = listOf(
                Tuple4("anti_inversion", "Anti-Inversi", "-10 / +20", AccentCyan, -10, 20),
                Tuple4("performance", "Performa Big", "-6 / +15", AccentOrange, -6, 15),
                Tuple4("balanced", "Seimbang", "0 / 0", AccentGreen, 0, 0),
                Tuple4("battery", "Hemat Daya", "-6 / -6", Color(0xFF8B5CF6), -6, -6)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141824), RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                biasOptions.forEach { (key, label, boostText, optColor, litVal, bigVal) ->
                    val isSelected = detectedPreset == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) optColor.copy(alpha = 0.18f) else Color.Transparent)
                            .border(
                                width = if (isSelected) 0.8.dp else 0.dp,
                                color = if (isSelected) optColor.copy(alpha = 0.55f) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSetSchedLoadBoost(litVal, bigVal)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) optColor else TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = boostText,
                                color = if (isSelected) optColor.copy(alpha = 0.9f) else TextSecondary.copy(alpha = 0.6f),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Custom Dialog Trigger
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showCustomDialog = true
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0x12FFFFFF),
                    border = BorderStroke(0.5.dp, Color(0x20FFFFFF)),
                    modifier = Modifier.defaultMinSize(minHeight = 30.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Kustom Angka Bias",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        SchedLoadBoostCustomDialog(
            currentLittle = curLittle,
            currentBig = curBig,
            onDismiss = { showCustomDialog = false },
            onApply = { lit, big ->
                onSetSchedLoadBoost(lit, big)
                showCustomDialog = false
            }
        )
    }
}

private data class Tuple4(
    val key: String,
    val label: String,
    val note: String,
    val color: Color,
    val little: Int,
    val big: Int
)

@Composable
fun SchedLoadBoostCustomDialog(
    currentLittle: Int,
    currentBig: Int,
    onDismiss: () -> Unit,
    onApply: (little: Int, big: Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var litText by remember { mutableStateOf(currentLittle.toString()) }
    var bigText by remember { mutableStateOf(currentBig.toString()) }

    val litVal = litText.trim().toIntOrNull()
    val bigVal = bigText.trim().toIntOrNull()

    val isLitValid = litVal != null && litVal in -100..100
    val isBigValid = bigVal != null && bigVal in -100..100
    val canApply = isLitValid && isBigValid

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Kustom Bias Kapasitas Tugas",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Nilai sched_load_boost (-100 s/d +100). Nilai positif pada core Big memprioritaskan penjadwalan thread render berat pada kluster performa.",
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = litText,
                    onValueChange = { litText = it },
                    label = { Text("Bias Little Cores (-100 s/d +100)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !isLitValid && litText.isNotEmpty()
                )

                OutlinedTextField(
                    value = bigText,
                    onValueChange = { bigText = it },
                    label = { Text("Bias Big Cores (-100 s/d +100)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !isBigValid && bigText.isNotEmpty()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canApply) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onApply(litVal!!, bigVal!!)
                    }
                },
                enabled = canApply,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Terapkan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = TextSecondary)
            }
        }
    )
}
