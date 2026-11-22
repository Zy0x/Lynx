package com.noir.lynx.feature.system.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.designsystem.controls.LynxNumericInputOverlay
import com.noir.lynx.designsystem.controls.LynxNumericPresetRow
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.DeepTunable
import com.noir.lynx.data.TunableType
import com.noir.lynx.R
import com.noir.lynx.core.locale.localizedUiText
import com.noir.lynx.designsystem.theme.*

/**
 * Kartu Item Interaktif Deep Sysfs Tunable.
 * Menerapkan pola Progressive Disclosure: tampilan awal bersih dan bebas beban kognitif,
 * detail teknis (path Linux, raw name, petunjuk '#') disembunyikan di lipatan info teknis.
 * Opsi pilihan multi-item (CHOICE) disajikan adaptif (Grid jika <= 3, Dropdown Menu jika > 3)
 * guna mengeliminasi sepenuhnya konflik gestur horizontal scroll vs tab swipe pager.
 */
@Composable
fun DeepTunableItemCard(
    tunable: DeepTunable,
    onApply: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    var editValue by remember(tunable.path) { mutableStateOf(tunable.value) }
    var sliderValue by remember(tunable.path) {
        val fVal = tunable.value.toFloatOrNull() ?: tunable.min
        mutableFloatStateOf(fVal.coerceIn(tunable.min, tunable.max))
    }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var lastSliderInteraction by remember { mutableLongStateOf(0L) }
    var isEditingText by remember { mutableStateOf(false) }
    var lastTextInteraction by remember { mutableLongStateOf(0L) }
    var showTechDetails by remember { mutableStateOf(false) }
    var isChoiceDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(tunable.path, tunable.value) {
        val now = System.currentTimeMillis()
        if (!isDraggingSlider && (now - lastSliderInteraction > 3500L)) {
            val fVal = tunable.value.toFloatOrNull() ?: tunable.min
            sliderValue = fVal.coerceIn(tunable.min, tunable.max)
        }
        if (!isEditingText && (now - lastTextInteraction > 3500L)) {
            editValue = tunable.value
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        color = BgElevated,
        border = BorderStroke(1.dp, BorderGlass)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // ── 1. HEADER: Nama Ramah + Kategori + Status Izin ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = localizedUiText(tunable.name),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Category Badge
                    val catColor = when {
                        tunable.category.contains("CPU", true) || tunable.category.contains("Penjadwal", true) -> AccentCyan
                        tunable.category.contains("GPU", true) || tunable.category.contains("Grafis", true) -> AccentPurple
                        tunable.category.contains("Memory", true) || tunable.category.contains("VM", true) || tunable.category.contains("RAM", true) -> AccentBlue
                        tunable.category.contains("Storage", true) || tunable.category.contains("I/O", true) || tunable.category.contains("Penyimpanan", true) -> AccentOrange
                        tunable.category.contains("Power", true) || tunable.category.contains("Thermal", true) || tunable.category.contains("Baterai", true) -> AccentGreen
                        else -> AccentCyan
                    }

                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = catColor.copy(alpha = 0.12f),
                        border = BorderStroke(0.6.dp, catColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = localizedUiText(tunable.category),
                            color = catColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                        )
                    }

                    // Writable Lock Status Indicator
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = if (tunable.writable) AccentGreen.copy(alpha = 0.12f) else Color.Red.copy(alpha = 0.12f),
                        border = BorderStroke(0.6.dp, if (tunable.writable) AccentGreen.copy(alpha = 0.35f) else Color.Red.copy(alpha = 0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.5.dp)
                        ) {
                            Icon(
                                imageVector = if (tunable.writable) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (tunable.writable) AccentGreen else Color(0xFFFF5252),
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = if (tunable.writable) "R/W" else "KUNCI",
                                color = if (tunable.writable) AccentGreen else Color(0xFFFF5252),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── 2. DESKRIPSI MANFAAT SINGKAT ──
            if (tunable.desc.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = localizedUiText(tunable.desc),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = BorderGlass.copy(alpha = 0.5f), thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            // ── 3. WIDGET KONTROL INTERAKTIF DINAMIS ──
            if (!tunable.writable) {
                // Read-Only Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nilai Terbaca Saat Ini:",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0x15FFFFFF),
                        border = BorderStroke(0.6.dp, BorderSubtle)
                    ) {
                        Text(
                            text = tunable.value.ifBlank { "(kosong)" },
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            } else {
                when (tunable.type) {
                    TunableType.BOOL -> {
                        val isChecked = tunable.value == "1" ||
                                tunable.value.equals("enable", ignoreCase = true) ||
                                tunable.value.equals("enabled", ignoreCase = true) ||
                                tunable.value.equals("on", ignoreCase = true) ||
                                tunable.value.equals("y", ignoreCase = true)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isChecked) "Status: Aktif (1)" else "Status: Nonaktif (0)",
                                    color = if (isChecked) AccentCyan else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isChecked) "Sentuh sakelar untuk mematikan" else "Sentuh sakelar untuk mengaktifkan",
                                    color = TextTertiary,
                                    fontSize = 9.5.sp
                                )
                            }
                            Switch(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    val newVal = if (checked) "1" else "0"
                                    onApply(tunable.path, newVal)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = BgDeepOled,
                                    checkedTrackColor = AccentCyan,
                                    uncheckedThumbColor = TextTertiary,
                                    uncheckedTrackColor = BgElevated
                                )
                            )
                        }
                    }

                    TunableType.CHOICE -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pilihan Parameter:",
                                    color = TextSecondary,
                                    fontSize = 10.5.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    color = AccentCyan.copy(alpha = 0.15f),
                                    border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = "Aktif: ${tunable.value.ifBlank { "Default" }}",
                                        color = AccentCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            if (tunable.options.size <= 3) {
                                // <= 3 Opsi: Gunakan Grid Baris Proporsional (weight(1f)), BEBAS konflik scroll
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for (opt in tunable.options) {
                                        val isSelected = opt.value == tunable.value ||
                                                (tunable.value.isBlank() && opt == tunable.options.firstOrNull())
                                        Surface(
                                            onClick = { onApply(tunable.path, opt.value) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) AccentCyan.copy(alpha = 0.22f) else Color(0x12FFFFFF),
                                            border = BorderStroke(1.dp, if (isSelected) AccentCyan else BorderGlass),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                            ) {
                                                Text(
                                                    text = localizedUiText(opt.label.ifBlank { opt.value }),
                                                    color = if (isSelected) AccentCyan else TextPrimary,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // > 3 Opsi: Gunakan Dropdown Selector Box yang Elegan & Ringkas
                                val activeLabel = tunable.options.firstOrNull { it.value == tunable.value }?.label
                                    ?: tunable.value.ifBlank { "Pilih Opsi..." }

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Surface(
                                        onClick = { isChoiceDropdownExpanded = true },
                                        shape = RoundedCornerShape(8.dp),
                                        color = BgDeepOled,
                                        border = BorderStroke(1.dp, if (isChoiceDropdownExpanded) AccentCyan else BorderSubtle),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 9.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = localizedUiText(activeLabel),
                                                color = AccentCyan,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = if (isChoiceDropdownExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = isChoiceDropdownExpanded,
                                        onDismissRequest = { isChoiceDropdownExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    ) {
                                        for (opt in tunable.options) {
                                            val isOptSelected = opt.value == tunable.value
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(
                                                            text = localizedUiText(opt.label.ifBlank { opt.value }),
                                                            color = if (isOptSelected) AccentCyan else TextPrimary,
                                                            fontWeight = if (isOptSelected) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 11.5.sp
                                                        )
                                                        if (opt.value != opt.label && opt.value.isNotBlank()) {
                                                            Text(
                                                                text = "Nilai: ${opt.value}",
                                                                color = TextTertiary,
                                                                fontSize = 9.5.sp,
                                                                fontFamily = FontFamily.Monospace
                                                            )
                                                        }
                                                    }
                                                },
                                                trailingIcon = if (isOptSelected) {
                                                    { Icon(Icons.Default.Check, null, tint = AccentCyan, modifier = Modifier.size(16.dp)) }
                                                } else null,
                                                onClick = {
                                                    isChoiceDropdownExpanded = false
                                                    onApply(tunable.path, opt.value)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TunableType.SLIDER -> {
                        val safeStep = tunable.step.takeIf { it > 0f } ?: 1f
                        val totalSteps = ((tunable.max - tunable.min) / safeStep).roundToInt().coerceAtLeast(1)
                        val usePresetOverlay = totalSteps > 50 || (tunable.max - tunable.min) > 100f
                        val minInt = tunable.min.roundToInt()
                        val maxInt = maxOf(minInt + 1, tunable.max.roundToInt())
                        val curInt = sliderValue.roundToInt().coerceIn(minInt, maxInt)
                        val autoPresets = remember(minInt, maxInt, tunable.unit) {
                            val span = maxInt - minInt
                            val unitSuffix = if (tunable.unit.isNotEmpty()) " ${tunable.unit}" else ""
                            listOf(minInt, minInt + span / 4, minInt + span / 2, maxInt)
                                .distinct()
                                .map { v -> "$v$unitSuffix" to v }
                        }
                        val displayVal = if (tunable.unit.isNotEmpty()) {
                            "$curInt ${tunable.unit}"
                        } else {
                            "$curInt"
                        }

                        if (usePresetOverlay) {
                            LynxNumericPresetRow(
                                label = "Nilai Parameter",
                                subtitle = "Rentang $minInt – $maxInt${if (tunable.unit.isNotEmpty()) " ${tunable.unit}" else ""}",
                                value = curInt,
                                displayValue = displayVal,
                                min = minInt,
                                max = maxInt,
                                unit = tunable.unit,
                                dialogTitle = localizedUiText(tunable.name),
                                dialogHint = localizedUiText(tunable.desc).ifBlank {
                                    "Pilih preset cepat atau ketik angka antara $minInt – $maxInt."
                                },
                                presets = autoPresets,
                                accentColor = AccentCyan,
                                onCommitValue = { newVal ->
                                    isDraggingSlider = false
                                    lastSliderInteraction = System.currentTimeMillis()
                                    sliderValue = newVal.toFloat()
                                    onApply(tunable.path, newVal.toString())
                                }
                            )
                        } else {
                            var showCompactOverlay by remember { mutableStateOf(false) }
                            if (showCompactOverlay) {
                                LynxNumericInputOverlay(
                                    title = localizedUiText(tunable.name),
                                    currentValue = curInt,
                                    min = minInt,
                                    max = maxInt,
                                    unit = tunable.unit,
                                    hint = localizedUiText(tunable.desc).ifBlank {
                                        "Pilih preset cepat atau ketik angka antara $minInt – $maxInt."
                                    },
                                    presets = autoPresets,
                                    accentColor = AccentCyan,
                                    onDismiss = { showCompactOverlay = false },
                                    onConfirm = { newVal ->
                                        showCompactOverlay = false
                                        isDraggingSlider = false
                                        lastSliderInteraction = System.currentTimeMillis()
                                        sliderValue = newVal.toFloat()
                                        onApply(tunable.path, newVal.toString())
                                    }
                                )
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Nilai Parameter:", color = TextSecondary, fontSize = 11.sp)
                                    Surface(
                                        onClick = { showCompactOverlay = true },
                                        shape = RoundedCornerShape(6.dp),
                                        color = AccentCyan.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = displayVal,
                                            color = AccentCyan,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Slider(
                                    value = sliderValue,
                                    onValueChange = {
                                        isDraggingSlider = true
                                        lastSliderInteraction = System.currentTimeMillis()
                                        sliderValue = it
                                    },
                                    onValueChangeFinished = {
                                        isDraggingSlider = false
                                        lastSliderInteraction = System.currentTimeMillis()
                                    },
                                    valueRange = tunable.min..tunable.max,
                                    steps = if (tunable.step > 0 && (tunable.max - tunable.min) / tunable.step > 1) {
                                        ((tunable.max - tunable.min) / tunable.step).toInt() - 1
                                    } else 0,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AccentCyan,
                                        activeTrackColor = AccentCyan,
                                        inactiveTrackColor = Color(0xFF161A24)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Min: ${tunable.min.toInt()}${tunable.unit}",
                                        color = TextTertiary,
                                        fontSize = 9.5.sp
                                    )

                                    Button(
                                        onClick = {
                                            isDraggingSlider = false
                                            lastSliderInteraction = System.currentTimeMillis()
                                            onApply(tunable.path, sliderValue.toInt().toString())
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentCyan.copy(alpha = 0.2f),
                                            contentColor = AccentCyan
                                        ),
                                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Terapkan (${sliderValue.toInt()})", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Text(
                                        text = "Max: ${tunable.max.toInt()}${tunable.unit}",
                                        color = TextTertiary,
                                        fontSize = 9.5.sp
                                    )
                                }
                            }
                        }
                    }

                    TunableType.STEPPER -> {
                        val curInt = tunable.value.toIntOrNull() ?: tunable.min.toInt()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Tingkat Level:", color = TextSecondary, fontSize = 11.sp)
                                Text(
                                    text = "Rentang: ${tunable.min.toInt()} - ${tunable.max.toInt()} ${tunable.unit}".trim(),
                                    color = TextTertiary,
                                    fontSize = 9.5.sp
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val nextVal = (curInt - tunable.step.toInt()).coerceAtLeast(tunable.min.toInt())
                                        onApply(tunable.path, nextVal.toString())
                                    },
                                    enabled = curInt > tunable.min.toInt(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("-", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AccentCyan.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "$curInt ${tunable.unit}".trim(),
                                        color = AccentCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val nextVal = (curInt + tunable.step.toInt()).coerceAtMost(tunable.max.toInt())
                                        onApply(tunable.path, nextVal.toString())
                                    },
                                    enabled = curInt < tunable.max.toInt(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    TunableType.INT, TunableType.TEXT -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = editValue,
                                onValueChange = {
                                    isEditingText = true
                                    lastTextInteraction = System.currentTimeMillis()
                                    editValue = it
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(tunable.value.ifBlank { "Ketik nilai..." }, fontSize = 11.sp, color = TextTertiary) },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    color = TextPrimary
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentCyan,
                                    unfocusedBorderColor = BorderSubtle,
                                    focusedContainerColor = BgDeepOled,
                                    unfocusedContainerColor = BgDeepOled
                                )
                            )

                            Button(
                                onClick = {
                                    isEditingText = false
                                    onApply(tunable.path, editValue.trim())
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentCyan.copy(alpha = 0.2f),
                                    contentColor = AccentCyan
                                ),
                                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Terapkan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── 4. PROGRESSIVE DISCLOSURE: DETAIL TEKNIS & PATH LIPAT ──
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTechDetails = !showTechDetails }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AccentCyan.copy(alpha = 0.7f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (showTechDetails) "Sembunyikan Info Teknis" else "Info Teknis & Path",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = AccentCyan.copy(alpha = 0.85f)
                    )
                }

                Icon(
                    imageVector = if (showTechDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }

            AnimatedVisibility(
                visible = showTechDetails,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Recommendation Tip (if available)
                    if (tunable.recommendation.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentGreen.copy(alpha = 0.1f),
                            border = BorderStroke(0.6.dp, AccentGreen.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TipsAndUpdates,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Saran: ${localizedUiText(tunable.recommendation)}",
                                    color = AccentGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Copyable Path Box
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BgDeepOled,
                        border = BorderStroke(0.6.dp, BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboardManager.setText(AnnotatedString(tunable.path))
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                Toast.makeText(context, "Path disalin ke clipboard", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = tunable.path,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = TextTertiary,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Salin Path",
                                tint = AccentCyan,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    // Raw Help Text / Comment (#) if available
                    if (tunable.help.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0x18FFB300),
                            border = BorderStroke(0.6.dp, Color(0x40FFB300)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "PETUNJUK VENDOR KERNEL (#):",
                                    color = Color(0xFFFFB300),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = localizedUiText(tunable.help),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.5.sp,
                                    color = TextPrimary,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
