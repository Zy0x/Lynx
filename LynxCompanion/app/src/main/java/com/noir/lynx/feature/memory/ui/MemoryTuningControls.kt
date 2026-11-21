package com.noir.lynx.feature.memory.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.designsystem.controls.SafeSlider
import com.noir.lynx.designsystem.theme.*
import kotlin.math.roundToInt

/**
 * MemoryOptionChip — Touch-first >= 48dp option chip used across Memory & Storage sections.
 */
@Composable
internal fun MemoryOptionChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    accentColor: Color = AccentCyan,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val bgColor = when {
        selected -> accentColor.copy(alpha = 0.16f)
        enabled -> BgElevated
        else -> BgElevated.copy(alpha = 0.55f)
    }
    val borderColor = if (selected) accentColor.copy(alpha = 0.70f) else BorderSubtle
    val textColor = when {
        selected -> accentColor
        enabled -> TextPrimary
        else -> TextTertiary
    }

    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(if (selected) 1.dp else 0.6.dp, borderColor)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * CustomNumericInputDialog — Standardized Obsidian modal allowing custom integers within safe hardware bounds.
 */
@Composable
internal fun CustomNumericInputDialog(
    title: String,
    unit: String,
    currentValue: Int,
    min: Int,
    max: Int,
    hint: String = "",
    accentColor: Color = AccentCyan,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val safeMin = minOf(min, max)
    val safeMax = maxOf(min, max)
    var inputText by remember(currentValue) { mutableStateOf(currentValue.coerceIn(safeMin, safeMax).toString()) }
    val parsed = inputText.trim().toIntOrNull()
    val isValid = parsed != null && parsed in safeMin..safeMax

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
                fontSize = 16.sp,
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

/**
 * MemoryValueSlider — Clean minimalist slider using [SafeSlider] (zero swipe conflict with pager)
 * with a subtle tap-to-edit custom value badge instead of bulky +/- boxes or lock walls.
 */
@Composable
internal fun MemoryValueSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    stepSize: Float = 1f,
    displayValue: (Float) -> String,
    accentColor: Color = AccentCyan,
    helperText: String? = null,
    unit: String = "",
    allowCustomInput: Boolean = true,
    customDialogMin: Int = valueRange.start.roundToInt(),
    customDialogMax: Int = valueRange.endInclusive.roundToInt(),
    customDialogHint: String = "",
    onCommitValue: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeStart = valueRange.start
    val safeEnd = maxOf(valueRange.start + 1f, valueRange.endInclusive)
    val clampedExternal = value.coerceIn(safeStart, safeEnd)
    var staged by remember { mutableFloatStateOf(clampedExternal) }
    var isDragging by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }

    LaunchedEffect(clampedExternal) {
        if (!isDragging) {
            staged = clampedExternal
        }
    }

    if (showCustomDialog) {
        CustomNumericInputDialog(
            title = "Atur $label",
            unit = unit,
            currentValue = staged.roundToInt(),
            min = customDialogMin,
            max = customDialogMax,
            hint = customDialogHint,
            accentColor = accentColor,
            onDismiss = { showCustomDialog = false },
            onConfirm = { customVal ->
                val clamped = customVal.toFloat().coerceIn(safeStart, safeEnd)
                staged = clamped
                showCustomDialog = false
                onCommitValue(clamped)
            }
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.14f),
                border = BorderStroke(0.6.dp, accentColor.copy(alpha = 0.35f)),
                modifier = if (allowCustomInput) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showCustomDialog = true }
                } else Modifier
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = displayValue(staged),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    if (allowCustomInput) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Ubah $label",
                            tint = accentColor.copy(alpha = 0.85f),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }

        SafeSlider(
            value = staged.coerceIn(safeStart, safeEnd),
            onValueChange = { raw ->
                isDragging = true
                val snapped = if (stepSize > 1f) {
                    ((raw - safeStart) / stepSize).roundToInt() * stepSize + safeStart
                } else {
                    raw.roundToInt().toFloat()
                }
                staged = snapped.coerceIn(safeStart, safeEnd)
            },
            onValueChangeFinished = {
                isDragging = false
                onCommitValue(staged)
            },
            valueRange = safeStart..safeEnd,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = BgSurfaceLowest
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (!helperText.isNullOrBlank()) {
            Text(
                text = helperText,
                fontSize = 10.5.sp,
                color = TextTertiary,
                lineHeight = 14.sp
            )
        }
    }
}
