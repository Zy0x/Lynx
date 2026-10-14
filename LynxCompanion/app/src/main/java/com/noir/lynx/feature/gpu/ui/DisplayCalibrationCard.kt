package com.noir.lynx.feature.gpu.ui

import java.util.Locale
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*
import com.noir.lynx.feature.navigation.model.TuningCategory

// ============================================================
//  Live Hardware Telemetry Card — Luxury Bento-Box Layout
// ============================================================

import com.noir.lynx.ui.*

@Composable
fun DisplayCalibrationCard(
    displayCalibration: DisplayCalibrationInfo,
    onSetUniversalColor: (Float, Float, Float) -> Unit,
    onSetKcal: (Boolean, Int, Int, Int, Int, Int, Int, Int) -> Unit,
    onSetHbm: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentGreen
    var uR by remember { mutableFloatStateOf(displayCalibration.universalRed) }
    var uG by remember { mutableFloatStateOf(displayCalibration.universalGreen) }
    var uB by remember { mutableFloatStateOf(displayCalibration.universalBlue) }
    var lastUnivTouch by remember { mutableLongStateOf(0L) }

    LaunchedEffect(displayCalibration) {
        if (System.currentTimeMillis() - lastUnivTouch > 3500L) {
            uR = displayCalibration.universalRed
            uG = displayCalibration.universalGreen
            uB = displayCalibration.universalBlue
        }
    }

    var kcalEnabled by remember { mutableStateOf(displayCalibration.kcalEnabled) }
    var red by remember { mutableIntStateOf(displayCalibration.red) }
    var green by remember { mutableIntStateOf(displayCalibration.green) }
    var blue by remember { mutableIntStateOf(displayCalibration.blue) }
    var saturation by remember { mutableIntStateOf(displayCalibration.saturation) }
    var lastKcalTouch by remember { mutableLongStateOf(0L) }

    LaunchedEffect(displayCalibration) {
        if (System.currentTimeMillis() - lastKcalTouch > 3500L) {
            kcalEnabled = displayCalibration.kcalEnabled
            red = displayCalibration.red
            green = displayCalibration.green
            blue = displayCalibration.blue
            saturation = displayCalibration.saturation
        }
    }

    LynxCard(
        title = "Kalibrasi Warna Layar (Universal RGB)",
        subtitle = "Penyesuaian kanal warna Red, Green, Blue tingkat sistem Android",
        icon = Icons.Default.Palette,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // Universal RGB Section (Works across ALL Android 10-14 ROMs)
        Text("Kanal Warna Layar (Sistem Android)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
        Text("Koreksi temperatur warna tampilan untuk kenyamanan visual", color = TextSecondary, fontSize = 10.5.sp, modifier = Modifier.padding(bottom = 6.dp))

        // Red Slider
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Merah (Red)", color = AccentRed, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            Text(String.format("%.2f", uR), color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = uR,
            onValueChange = { uR = it; lastUnivTouch = System.currentTimeMillis() },
            onValueChangeFinished = { onSetUniversalColor(uR, uG, uB) },
            valueRange = 0.5f..1.5f,
            colors = SliderDefaults.colors(thumbColor = AccentRed, activeTrackColor = AccentRed)
        )

        // Green Slider
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Hijau (Green)", color = AccentGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            Text(String.format("%.2f", uG), color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = uG,
            onValueChange = { uG = it; lastUnivTouch = System.currentTimeMillis() },
            onValueChangeFinished = { onSetUniversalColor(uR, uG, uB) },
            valueRange = 0.5f..1.5f,
            colors = SliderDefaults.colors(thumbColor = AccentGreen, activeTrackColor = AccentGreen)
        )

        // Blue Slider
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Biru (Blue)", color = AccentBlue, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            Text(String.format("%.2f", uB), color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = uB,
            onValueChange = { uB = it; lastUnivTouch = System.currentTimeMillis() },
            onValueChangeFinished = { onSetUniversalColor(uR, uG, uB) },
            valueRange = 0.5f..1.5f,
            colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                onClick = {
                    uR = 1.0f; uG = 1.0f; uB = 1.0f
                    lastUnivTouch = System.currentTimeMillis()
                    onSetUniversalColor(1.0f, 1.0f, 1.0f)
                },
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, BorderGlass)
            ) {
                Text(
                    "Reset Standar (1.0)",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // HBM Sunlight Booster (If supported)
        if (displayCalibration.isHbmSupported) {
            HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("HBM Sunlight Booster", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("Meningkatkan batas kecerahan panel display di luar slider standar", color = TextSecondary, fontSize = 10.5.sp)
                }
                Switch(
                    checked = displayCalibration.hbmEnabled,
                    onCheckedChange = { onSetHbm(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = cardAccent)
                )
            }
        }

        // Legacy KCAL Section (If kernel supports KCAL)
        if (displayCalibration.isKcalSupported) {
            HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Kernel KCAL Hardware Engine", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = kcalEnabled,
                    onCheckedChange = {
                        lastKcalTouch = System.currentTimeMillis()
                        kcalEnabled = it
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = cardAccent)
                )
            }

            if (kcalEnabled) {
                Text("Saturation: $saturation", color = AccentPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = saturation.toFloat(),
                    onValueChange = { saturation = it.toInt(); lastKcalTouch = System.currentTimeMillis() },
                    valueRange = 128f..383f,
                    colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple)
                )

                LynxActionButton(
                    text = "Terapkan KCAL",
                    icon = Icons.Default.Save,
                    onClick = {
                        lastKcalTouch = System.currentTimeMillis()
                        onSetKcal(true, red, green, blue, saturation, displayCalibration.value, displayCalibration.contrast, displayCalibration.hue)
                    },
                    accentColor = cardAccent
                )
            }
        }
    }
}

