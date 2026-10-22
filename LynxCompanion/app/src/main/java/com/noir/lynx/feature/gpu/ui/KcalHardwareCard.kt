package com.noir.lynx.feature.gpu.ui

import com.noir.lynx.core.locale.localizedUiText
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.data.DisplayCalibrationInfo

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Kartu Kalibrasi Hardware Panel Fisik (KCAL Driver).
 * Mengontrol driver kernel KCAL untuk kalibrasi warna level silikon layar.
 */
@Composable
fun KcalHardwareCard(
    displayCalibration: DisplayCalibrationInfo,
    onSetKcal: (Boolean, Int, Int, Int, Int, Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentPurple

    var kcalEnabled by remember { mutableStateOf(displayCalibration.kcalEnabled) }
    var kcalSat by remember { mutableIntStateOf(displayCalibration.saturation) }
    var kcalCont by remember { mutableIntStateOf(displayCalibration.contrast) }
    var kcalVal by remember { mutableIntStateOf(displayCalibration.value) }
    var kcalR by remember { mutableIntStateOf(displayCalibration.red) }
    var kcalG by remember { mutableIntStateOf(displayCalibration.green) }
    var kcalB by remember { mutableIntStateOf(displayCalibration.blue) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var lastKcalInteraction by remember { mutableLongStateOf(0L) }

    LaunchedEffect(displayCalibration) {
        val now = System.currentTimeMillis()
        if (!isDraggingSlider && (now - lastKcalInteraction > 3500L)) {
            kcalEnabled = displayCalibration.kcalEnabled
            kcalSat = displayCalibration.saturation
            kcalCont = displayCalibration.contrast
            kcalVal = displayCalibration.value
            kcalR = displayCalibration.red
            kcalG = displayCalibration.green
            kcalB = displayCalibration.blue
        }
    }

    LynxCard(
        title = "Kalibrasi Hardware Panel (KCAL)",
        subtitle = "Koreksi warna pada tingkat kernel driver panel fisik",
        icon = Icons.Default.Tune,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        LynxSwitch(
            label = "Aktifkan Engine KCAL Kernel",
            subLabel = "Kontrol langsung parameter warna panel melalui driver kernel hardware",
            checked = kcalEnabled,
            onCheckedChange = {
                kcalEnabled = it
                lastKcalInteraction = System.currentTimeMillis()
                onSetKcal(it, kcalR, kcalG, kcalB, kcalSat, kcalVal, kcalCont, 0)
            }
        )

        AnimatedVisibility(visible = kcalEnabled) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                // Preset Cepat KCAL
                Text(
                    text = "Pilihan Preset KCAL:",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        Triple("Vivid OLED", Triple(280, 265, 256), Triple(256, 256, 256)),
                        Triple("Cinematic D65", Triple(256, 256, 256), Triple(256, 248, 240)),
                        Triple("Outdoor Boost", Triple(270, 280, 280), Triple(256, 256, 256)),
                        Triple("Default Flat", Triple(256, 256, 256), Triple(256, 256, 256))
                    )
                    presets.forEach { (name, satContVal, rgb) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BgElevated,
                            border = BorderStroke(1.dp, BorderGlass),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 44.dp)
                                .clickable {
                                    kcalSat = satContVal.first
                                    kcalCont = satContVal.second
                                    kcalVal = satContVal.third
                                    kcalR = rgb.first
                                    kcalG = rgb.second
                                    kcalB = rgb.third
                                    lastKcalInteraction = System.currentTimeMillis()
                                    onSetKcal(true, kcalR, kcalG, kcalB, kcalSat, kcalVal, kcalCont, 0)
                                }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name,
                                    color = cardAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Saturation Slider
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Saturasi Hardware: $kcalSat", color = AccentPurple, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    Text("Standar: 256", color = TextTertiary, fontSize = 10.sp)
                }
                Slider(
                    value = kcalSat.toFloat(),
                    onValueChange = {
                        isDraggingSlider = true
                        lastKcalInteraction = System.currentTimeMillis()
                        kcalSat = it.toInt()
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        lastKcalInteraction = System.currentTimeMillis()
                        onSetKcal(true, kcalR, kcalG, kcalB, kcalSat, kcalVal, kcalCont, 0)
                    },
                    valueRange = 128f..384f,
                    colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple)
                )

                Spacer(Modifier.height(8.dp))

                // Contrast Slider
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kontras Hardware: $kcalCont", color = AccentCyan, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    Text("Standar: 256", color = TextTertiary, fontSize = 10.sp)
                }
                Slider(
                    value = kcalCont.toFloat(),
                    onValueChange = {
                        isDraggingSlider = true
                        lastKcalInteraction = System.currentTimeMillis()
                        kcalCont = it.toInt()
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        lastKcalInteraction = System.currentTimeMillis()
                        onSetKcal(true, kcalR, kcalG, kcalB, kcalSat, kcalVal, kcalCont, 0)
                    },
                    valueRange = 128f..384f,
                    colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan)
                )

                Spacer(Modifier.height(8.dp))

                // Value / Brightness Slider
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kecerahan Panel (Value): $kcalVal", color = AccentBlue, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    Text("Standar: 256", color = TextTertiary, fontSize = 10.sp)
                }
                Slider(
                    value = kcalVal.toFloat(),
                    onValueChange = {
                        isDraggingSlider = true
                        lastKcalInteraction = System.currentTimeMillis()
                        kcalVal = it.toInt()
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        lastKcalInteraction = System.currentTimeMillis()
                        onSetKcal(true, kcalR, kcalG, kcalB, kcalSat, kcalVal, kcalCont, 0)
                    },
                    valueRange = 128f..384f,
                    colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
                )
            }
        }
    }
}
