package com.noir.lynx.designsystem.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.designsystem.theme.*

/**
 * Touch-first compact parameter row displaying the active numeric value and opening
 * [LynxNumericInputOverlay] on tap so dense or wide-range values avoid imprecise finger sliding.
 */
@Composable
fun LynxNumericPresetRow(
    label: String,
    value: Int,
    displayValue: String,
    min: Int,
    max: Int,
    onCommitValue: (Int) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    unit: String = "",
    dialogTitle: String = "Atur $label",
    dialogHint: String = "",
    presets: List<Pair<String, Int>> = emptyList(),
    accentColor: Color = AccentCyan
) {
    val haptic = LocalHapticFeedback.current
    var showOverlay by remember { mutableStateOf(false) }

    if (showOverlay) {
        LynxNumericInputOverlay(
            title = dialogTitle,
            currentValue = value,
            min = min,
            max = max,
            unit = unit,
            hint = dialogHint,
            presets = presets,
            accentColor = accentColor,
            onDismiss = { showOverlay = false },
            onConfirm = { newVal ->
                showOverlay = false
                onCommitValue(newVal)
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BgElevated,
        border = BorderStroke(0.6.dp, BorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                showOverlay = true
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
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 10.5.sp,
                        color = TextSecondary,
                        lineHeight = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.14f),
                border = BorderStroke(0.6.dp, accentColor.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = displayValue,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Ubah $label",
                        tint = accentColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Numeric configuration overlay combining 1-tap quick preset chips with validated integer input.
 */
@Composable
fun LynxNumericInputOverlay(
    title: String,
    currentValue: Int,
    min: Int,
    max: Int,
    unit: String = "",
    hint: String = "",
    presets: List<Pair<String, Int>> = emptyList(),
    accentColor: Color = AccentCyan,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val safeMin = minOf(min, max)
    val safeMax = maxOf(min, max)
    var inputText by remember(currentValue) {
        mutableStateOf(currentValue.coerceIn(safeMin, safeMax).toString())
    }
    val parsed = inputText.trim().toIntOrNull()
    val isValid = parsed != null && parsed in safeMin..safeMax
    val validPresets = remember(presets, safeMin, safeMax) {
        presets.filter { it.second in safeMin..safeMax }.distinctBy { it.second }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10141F),
        shape = RoundedCornerShape(18.dp),
        icon = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.14f),
                border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.35f)),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = title,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hint.isNotBlank()) {
                    Text(
                        text = hint,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                }

                if (validPresets.isNotEmpty()) {
                    Text(
                        text = "Preset Cepat (1-Tap)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        validPresets.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { (presetLabel, presetVal) ->
                                    val selected = parsed == presetVal
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) accentColor.copy(alpha = 0.18f) else BgElevated,
                                        border = BorderStroke(
                                            if (selected) 1.dp else 0.6.dp,
                                            if (selected) accentColor.copy(alpha = 0.65f) else BorderSubtle
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onConfirm(presetVal)
                                            }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = presetLabel,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) accentColor else TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    HorizontalDivider(color = BorderGlass, thickness = 0.6.dp)
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { raw ->
                        inputText = raw.filter { it.isDigit() || (safeMin < 0 && it == '-') }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = {
                        Text(
                            text = if (unit.isNotBlank()) "Nilai kustom ($unit)" else "Nilai kustom",
                            fontSize = 12.sp
                        )
                    },
                    suffix = if (unit.isNotBlank()) {
                        {
                            Text(
                                text = unit,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                    } else null,
                    supportingText = {
                        val unitSuffix = if (unit.isNotBlank()) " $unit" else ""
                        Text(
                            text = if (isValid) {
                                "Rentang aman: $safeMin – $safeMax$unitSuffix"
                            } else {
                                "Masukkan angka valid antara $safeMin – $safeMax$unitSuffix"
                            },
                            fontSize = 11.sp,
                            color = if (isValid) TextTertiary else AccentRed
                        )
                    },
                    isError = !isValid,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = BorderSubtle,
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = accentColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { parsed?.let(onConfirm) },
                enabled = isValid,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = BgDeepOled
                ),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text("Terapkan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text("Batal", color = TextSecondary, fontSize = 13.sp)
            }
        }
    )
}
