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
 * Decimal GB input overlay for ZRAM target sizing with 1-tap GB presets (including Off / 0 GB)
 * and safe RAM-aware validation.
 */
@Composable
internal fun CustomZramGbDialog(
    currentMb: Int,
    maxSafeMb: Int,
    ramTotalMb: Int,
    recommendedMaxMb: Int = (ramTotalMb / 2).coerceAtLeast(128),
    accentColor: Color = AccentCyan,
    onDismiss: () -> Unit,
    onConfirmMb: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val safeMaxMb = maxOf(maxSafeMb, currentMb, 128)
    var text by remember(currentMb) {
        mutableStateOf(
            if (currentMb <= 0) "0" else formatZramGb(currentMb.coerceIn(128, safeMaxMb), includeUnit = false)
        )
    }
    val parsedGb = text.trim().replace(',', '.').toDoubleOrNull()
    val computedMb = parsedGb?.let { (it * 1024.0).roundToInt() }
    val isValid = parsedGb != null && computedMb != null && (computedMb == 0 || computedMb in 128..safeMaxMb)
    val maxGbText = formatZramGb(safeMaxMb)
    val recGbText = formatZramGb(recommendedMaxMb)

    val quickGbPresets = remember(ramTotalMb, safeMaxMb, recommendedMaxMb) {
        val stdSteps = listOf(0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0, 8.0)
            .map { (it * 1024.0).roundToInt() }
            .filter { it in 128..safeMaxMb }
        val maxQuarterMb = ((safeMaxMb / 256) * 256).takeIf { it >= 256 } ?: ((safeMaxMb / 128) * 128)
        val pool = (stdSteps + listOfNotNull(maxQuarterMb.takeIf { it >= 128 }))
            .distinct()
            .sorted()
        val chosen = if (pool.size <= 5) {
            pool
        } else {
            val mid = pool.filter { it <= recommendedMaxMb }
            val high = pool.filter { it > recommendedMaxMb }
            (mid.takeLast(3) + high.takeLast(2)).distinct().sorted().take(5)
        }
        val items = mutableListOf("Nonaktif" to 0)
        chosen.forEach { mbOpt ->
            val tag = when {
                mbOpt == recommendedMaxMb || (mbOpt <= recommendedMaxMb && mbOpt == chosen.lastOrNull { it <= recommendedMaxMb }) -> "Standar"
                mbOpt > recommendedMaxMb -> "Ekstra"
                else -> "Hemat"
            }
            items.add(tag to mbOpt)
        }
        items
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
                text = "Kapasitas Target ZRAM (GB)",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Preset Cepat (1-Tap)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickGbPresets.chunked(3).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowPresets.forEach { (presetTag, presetMb) ->
                                val selected = computedMb == presetMb
                                val chipAccent = if (presetMb == 0) AccentOrange else accentColor
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) chipAccent.copy(alpha = 0.18f) else BgElevated,
                                    border = BorderStroke(
                                        if (selected) 1.dp else 0.6.dp,
                                        if (selected) chipAccent.copy(alpha = 0.65f) else BorderSubtle
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 46.dp)
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
                                            text = if (presetMb == 0) "0 GB" else formatZramGb(presetMb),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) chipAccent else TextPrimary,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = presetTag,
                                            fontSize = 9.5.sp,
                                            color = if (selected) chipAccent.copy(alpha = 0.85f) else TextTertiary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            repeat(3 - rowPresets.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                HorizontalDivider(color = BorderGlass, thickness = 0.6.dp)

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
                    label = { Text("Atau Ketik Ukuran Kustom", fontSize = 12.sp) },
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
                    text = when {
                        !isValid && text.isNotEmpty() ->
                            "Masukkan 0 (Off) atau 0.13 – ${formatZramGb(safeMaxMb, includeUnit = false)} GB (maks $maxGbText)."
                        computedMb == 0 ->
                            "Swap ZRAM dimatikan (0 GB); halaman swap aktif akan dipindahkan ke RAM."
                        computedMb != null && computedMb > recommendedMaxMb ->
                            "Setara $computedMb MB (${formatZramGb(computedMb)}). Di atas 50% RAM ($recGbText) — disarankan memakai kompresi cepat."
                        computedMb != null ->
                            "Setara $computedMb MB (${formatZramGb(computedMb)}). Rekomendasi ≤ $recGbText, maks aman $maxGbText."
                        else ->
                            "RAM total ${formatZramGb(ramTotalMb)}. Rekomendasi ≤ $recGbText, maks $maxGbText."
                    },
                    fontSize = 11.sp,
                    color = when {
                        !isValid && text.isNotEmpty() -> AccentRed
                        computedMb != null && (computedMb == 0 || computedMb > recommendedMaxMb) -> AccentOrange
                        else -> TextSecondary
                    },
                    lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { computedMb?.let { onConfirmMb(if (it == 0) 0 else it.coerceIn(128, safeMaxMb)) } },
                enabled = isValid,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = BgDeepOled
                ),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text("Pilih Ukuran", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
