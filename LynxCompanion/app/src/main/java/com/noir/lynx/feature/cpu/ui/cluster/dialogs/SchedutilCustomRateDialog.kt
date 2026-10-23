package com.noir.lynx.feature.cpu.ui.cluster.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.designsystem.theme.*

/**
 * SchedutilCustomRateDialog — High-precision numeric dialog for exact microsecond (µs) rate limits.
 * Touch-First (>=48dp), mobile-first responsive layout, and strict safety bounds.
 */
@Composable
fun SchedutilCustomRateDialog(
    policyId: Int,
    clusterRole: String,
    initialUpUs: Long,
    initialDownUs: Long,
    onDismiss: () -> Unit,
    onApply: (policyId: Int, upUs: Long, downUs: Long) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var upText by remember { mutableStateOf(initialUpUs.toString()) }
    var downText by remember { mutableStateOf(initialDownUs.toString()) }

    val upVal = upText.trim().toLongOrNull()
    val downVal = downText.trim().toLongOrNull()

    val isUpValid = upVal != null && upVal in 0L..50000L
    val isDownValid = downVal != null && downVal in 100L..100000L
    val canApply = isUpValid && isDownValid

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentCyan.copy(alpha = 0.15f),
                    border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = stringResource(R.string.cpu_schedutil_custom_rate_title),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Policy $policyId • $clusterRole",
                        fontSize = 11.sp,
                        color = TextSecondary.copy(alpha = 0.75f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.cpu_schedutil_custom_rate_desc),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = TextSecondary
                )

                // ── 1. Up Rate Limit (µs) ──
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.cpu_schedutil_up_label),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentCyan
                    )

                    OutlinedTextField(
                        value = upText,
                        onValueChange = { upText = it.filter { ch -> ch.isDigit() } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = !isUpValid && upText.isNotEmpty(),
                        supportingText = {
                            Text(
                                text = if (!isUpValid && upText.isNotEmpty()) "Batas aman: 0 s/d 50.000 µs" else "0 µs = akselerasi instan tanpa jeda",
                                fontSize = 10.sp,
                                color = if (!isUpValid && upText.isNotEmpty()) AccentRed else TextSecondary.copy(alpha = 0.6f)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = Color(0x25FFFFFF),
                            focusedContainerColor = Color(0xFF0C0F17),
                            unfocusedContainerColor = Color(0xFF0C0F17),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Quick helper chips for Up Rate Limit
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            0L to "0 µs (Instan)",
                            500L to "500 µs",
                            1000L to "1 ms",
                            5000L to "5 ms"
                        ).forEach { (presetVal, chipLabel) ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (upVal == presetVal) AccentCyan.copy(alpha = 0.18f) else Color(0xFF141824),
                                border = BorderStroke(0.6.dp, if (upVal == presetVal) AccentCyan else Color(0x18FFFFFF)),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 32.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        upText = presetVal.toString()
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp)) {
                                    Text(
                                        text = chipLabel,
                                        fontSize = 9.sp,
                                        fontWeight = if (upVal == presetVal) FontWeight.Bold else FontWeight.Medium,
                                        color = if (upVal == presetVal) AccentCyan else TextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0x12FFFFFF), thickness = 0.8.dp)

                // ── 2. Down Rate Limit (µs) ──
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.cpu_schedutil_down_label),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentOrange
                    )

                    OutlinedTextField(
                        value = downText,
                        onValueChange = { downText = it.filter { ch -> ch.isDigit() } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = !isDownValid && downText.isNotEmpty(),
                        supportingText = {
                            Text(
                                text = if (!isDownValid && downText.isNotEmpty()) "Batas aman: 100 s/d 100.000 µs" else "Waktu penahanan clock tinggi (menjaga FPS stabil)",
                                fontSize = 10.sp,
                                color = if (!isDownValid && downText.isNotEmpty()) AccentRed else TextSecondary.copy(alpha = 0.6f)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = Color(0x25FFFFFF),
                            focusedContainerColor = Color(0xFF0C0F17),
                            unfocusedContainerColor = Color(0xFF0C0F17),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Quick helper chips for Down Rate Limit
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            2000L to "2 ms",
                            5000L to "5 ms",
                            10000L to "10 ms",
                            20000L to "20 ms"
                        ).forEach { (presetVal, chipLabel) ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (downVal == presetVal) AccentOrange.copy(alpha = 0.18f) else Color(0xFF141824),
                                border = BorderStroke(0.6.dp, if (downVal == presetVal) AccentOrange else Color(0x18FFFFFF)),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 32.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        downText = presetVal.toString()
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp)) {
                                    Text(
                                        text = chipLabel,
                                        fontSize = 9.sp,
                                        fontWeight = if (downVal == presetVal) FontWeight.Bold else FontWeight.Medium,
                                        color = if (downVal == presetVal) AccentOrange else TextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canApply) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onApply(policyId, upVal!!, downVal!!)
                        onDismiss()
                    }
                },
                enabled = canApply,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan,
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0x18FFFFFF),
                    disabledContentColor = TextSecondary.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Terapkan Latensi", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
            ) {
                Text("Batal", color = TextSecondary, fontSize = 12.sp)
            }
        }
    )
}
