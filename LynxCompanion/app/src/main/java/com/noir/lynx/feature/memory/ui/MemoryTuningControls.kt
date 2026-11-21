package com.noir.lynx.feature.memory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.noir.lynx.designsystem.controls.LynxNumericInputOverlay
import com.noir.lynx.designsystem.theme.*
import java.util.Locale
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
        !enabled -> BgElevated.copy(alpha = 0.45f)
        selected -> accentColor.copy(alpha = 0.18f)
        else -> BgElevated
    }
    val borderColor = when {
        !enabled -> BorderSubtle.copy(alpha = 0.4f)
        selected -> accentColor.copy(alpha = 0.65f)
        else -> BorderSubtle
    }
    val textColor = when {
        !enabled -> TextTertiary
        selected -> accentColor
        else -> TextPrimary
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
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
 * CustomNumericInputDialog — Memory-module wrapper around shared [LynxNumericInputOverlay].
 */
@Composable
internal fun CustomNumericInputDialog(
    title: String,
    unit: String,
    currentValue: Int,
    min: Int,
    max: Int,
    hint: String = "",
    presets: List<Pair<String, Int>> = emptyList(),
    accentColor: Color = AccentCyan,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    LynxNumericInputOverlay(
        title = title,
        currentValue = currentValue,
        min = min,
        max = max,
        unit = unit,
        hint = hint,
        presets = presets,
        accentColor = accentColor,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

/**
 * Formats a ZRAM capacity in megabytes into human-friendly gigabytes (base-2 MiB -> GiB).
 */
internal fun formatZramGb(
    mb: Int,
    includeUnit: Boolean = true,
    includeZeroDecimal: Boolean = false
): String {
    val suffix = if (includeUnit) " GB" else ""
    if (mb <= 0) {
        return (if (includeZeroDecimal) "0.0" else "0") + suffix
    }
    val gb = mb / 1024.0
    val formatted = when {
        mb % 1024 == 0 -> "${mb / 1024}.0"
        mb % 512 == 0 -> "%.1f".format(Locale.US, gb)
        else -> "%.2f".format(Locale.US, gb.coerceAtLeast(0.01))
    }
    return formatted + suffix
}

/**
 * Decimal GB input overlay for custom ZRAM sizing with 1-tap safe ratio presets and 50% RAM guard.
 */
@Composable
internal fun CustomZramGbDialog(
    currentMb: Int,
    maxSafeMb: Int,
    ramTotalMb: Int,
    accentColor: Color = AccentCyan,
    onDismiss: () -> Unit,
    onConfirmMb: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val safeMaxMb = maxSafeMb.coerceAtLeast(128)
    var text by remember(currentMb) {
        mutableStateOf(formatZramGb(currentMb.coerceIn(128, safeMaxMb), includeUnit = false))
    }
    val parsedGb = text.trim().replace(',', '.').toDoubleOrNull()
    val computedMb = parsedGb?.let { (it * 1024.0).roundToInt() }
    val isValid = parsedGb != null && parsedGb > 0.0 && computedMb != null && computedMb in 128..safeMaxMb
    val maxGbText = formatZramGb(safeMaxMb)

    val quickGbPresets = remember(ramTotalMb, safeMaxMb) {
        val quarterAlignedMax = ((safeMaxMb / 256) * 256).takeIf { it >= 256 }
            ?: ((safeMaxMb / 128) * 128)
        listOf(
            "Hemat" to (((ramTotalMb * 20) / 100f) / 128f).roundToInt().coerceAtLeast(1) * 128,
            "Seimbang" to (((ramTotalMb * 33) / 100f) / 128f).roundToInt().coerceAtLeast(1) * 128,
            "Maks 50%" to quarterAlignedMax
        ).filter { it.second in 128..safeMaxMb }.distinctBy { it.second }
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
                text = "Ukuran ZRAM Kustom (GB)",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (quickGbPresets.isNotEmpty()) {
                    Text(
                        text = "Preset Cepat (1-Tap)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickGbPresets.forEach { (presetTag, presetMb) ->
                            val selected = computedMb == presetMb
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
                                        onConfirmMb(presetMb)
                                    }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = formatZramGb(presetMb),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) accentColor else TextPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = presetTag,
                                        fontSize = 9.5.sp,
                                        color = if (selected) accentColor.copy(alpha = 0.85f) else TextTertiary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = BorderGlass, thickness = 0.6.dp)
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        val cleaned = input.filter { it.isDigit() || it == '.' || it == ',' }
                        val sepCount = cleaned.count { it == '.' || it == ',' }
                        if (sepCount <= 1 && cleaned.length <= 6) {
                            text = cleaned
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    label = { Text("Kapasitas ZRAM", fontSize = 12.sp) },
                    suffix = {
                        Text(
                            text = "GB",
                            fontSize = 12.sp,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    isError = text.isNotEmpty() && !isValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = BorderSubtle,
                        errorBorderColor = AccentRed,
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = accentColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (isValid && computedMb != null) {
                        "Setara $computedMb MB (${formatZramGb(computedMb)}). Batas aman 50% RAM: maks $maxGbText."
                    } else {
                        "RAM total ${formatZramGb(ramTotalMb)}. Masukkan 0.13 – ${formatZramGb(safeMaxMb, includeUnit = false)} GB (maks $maxGbText)."
                    },
                    fontSize = 11.sp,
                    color = if (!isValid && text.isNotEmpty()) AccentRed else TextSecondary,
                    lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { computedMb?.let { onConfirmMb(it.coerceIn(128, safeMaxMb)) } },
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
