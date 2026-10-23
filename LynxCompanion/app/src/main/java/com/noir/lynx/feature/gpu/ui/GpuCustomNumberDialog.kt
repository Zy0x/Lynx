package com.noir.lynx.feature.gpu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.noir.lynx.designsystem.theme.*

/**
 * GpuCustomNumberDialog - Interactive Cyberpunk OLED modal for custom numerical GPU tuning.
 * Allows users to manually type or slide any custom value beyond fixed presets.
 */
@Composable
fun GpuCustomNumberDialog(
    title: String,
    description: String,
    currentValue: Int,
    unit: String,
    minVal: Int,
    maxVal: Int,
    step: Int = 1,
    accentColor: Color = AccentCyan,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var textInput by remember { mutableStateOf(currentValue.coerceIn(minVal, maxVal).toString()) }
    var sliderValue by remember { mutableStateOf(currentValue.coerceIn(minVal, maxVal).toFloat()) }

    val parsedInt = textInput.toIntOrNull()
    val isValid = parsedInt != null && parsedInt in minVal..maxVal

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF121520),
            border = BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    listOf(accentColor.copy(alpha = 0.5f), BorderGlass)
                )
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 1. Header with Icon & Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accentColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Rentang Aman: $minVal – $maxVal $unit",
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 2. Description
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 3. Slider Control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Geser Halus",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${sliderValue.toInt()} $unit",
                        color = accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = sliderValue,
                    onValueChange = { newVal ->
                        val stepped = (newVal / step).toInt() * step
                        val clamped = stepped.coerceIn(minVal, maxVal)
                        sliderValue = clamped.toFloat()
                        textInput = clamped.toString()
                    },
                    valueRange = minVal.toFloat()..maxVal.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = BgElevated
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Direct Manual Number Input TextField
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { input ->
                        val clean = input.filter { it.isDigit() }
                        textInput = clean
                        clean.toIntOrNull()?.let {
                            if (it in minVal..maxVal) {
                                sliderValue = it.toFloat()
                            }
                        }
                    },
                    label = { Text("Input Angka Kustom ($unit)") },
                    trailingIcon = {
                        Text(
                            text = unit,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !isValid,
                    supportingText = {
                        if (!isValid) {
                            Text(
                                text = "Masukkan angka antara $minVal dan $maxVal $unit",
                                color = AccentRed,
                                fontSize = 10.5.sp
                            )
                        } else {
                            Text(
                                text = "Ketik angka manual untuk presisi tingkat tinggi",
                                color = TextTertiary,
                                fontSize = 10.sp
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = BorderGlass,
                        focusedLabelColor = accentColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = accentColor,
                        errorBorderColor = AccentRed
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 5. Action Buttons (Batal & Terapkan)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text("Batal", color = TextSecondary, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            parsedInt?.let {
                                if (isValid) {
                                    onConfirm(it)
                                    onDismiss()
                                }
                            }
                        },
                        enabled = isValid,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            disabledContainerColor = BgElevated
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text(
                            "Terapkan",
                            color = if (isValid) Color.Black else TextTertiary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
