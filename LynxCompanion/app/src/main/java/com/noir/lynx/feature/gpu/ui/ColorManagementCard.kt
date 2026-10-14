package com.noir.lynx.feature.gpu.ui

import com.noir.lynx.core.locale.localizedUiText

import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*

import java.util.Locale
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.display.ColorMatrixEngine
import com.noir.lynx.display.ColorMatrixProfile
import com.noir.lynx.display.DisplayPipelineInfo
import com.noir.lynx.hardware.DisplayModeInfo
import com.noir.lynx.hardware.GpuBackend
import com.noir.lynx.hardware.GraphicsCapabilities
import com.noir.lynx.hardware.NodeStatus
import com.noir.lynx.lab.DropCorrelator
import com.noir.lynx.lab.DropEvent
import com.noir.lynx.lab.FrameSessionReport
import java.text.SimpleDateFormat
import java.util.*
import com.noir.lynx.ui.*

@Composable
fun ColorManagementCard(
    displayCalibration: DisplayCalibrationInfo,
    colorProfile: ColorMatrixProfile,
    isCalibrationEnabled: Boolean,
    colorConflict: String?,
    onToggleCalibration: (Boolean) -> Unit,
    onApplyProfile: (ColorMatrixProfile) -> Unit,
    onResetProfile: () -> Unit,
    onSetUniversalColor: (Float, Float, Float) -> Unit,
    onSetKcal: (Boolean, Int, Int, Int, Int, Int, Int, Int) -> Unit,
    onSetHbm: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentGreen
    var tempK by remember { mutableIntStateOf(colorProfile.temperatureK) }
    var sat by remember { mutableFloatStateOf(colorProfile.saturation) }
    var cont by remember { mutableFloatStateOf(colorProfile.contrast) }
    var rGain by remember { mutableFloatStateOf(colorProfile.red) }
    var gGain by remember { mutableFloatStateOf(colorProfile.green) }
    var bGain by remember { mutableFloatStateOf(colorProfile.blue) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var lastSliderInteraction by remember { mutableLongStateOf(0L) }
    var showAdvancedGains by remember { mutableStateOf(false) }

    LaunchedEffect(colorProfile) {
        val now = System.currentTimeMillis()
        if (!isDraggingSlider && (now - lastSliderInteraction > 3500L)) {
            tempK = colorProfile.temperatureK
            sat = colorProfile.saturation
            cont = colorProfile.contrast
            rGain = colorProfile.red
            gGain = colorProfile.green
            bGain = colorProfile.blue
        }
    }

    // KCAL State
    var kcalEnabled by remember { mutableStateOf(displayCalibration.kcalEnabled) }
    var kcalSat by remember { mutableIntStateOf(displayCalibration.saturation) }
    var lastKcalInteraction by remember { mutableLongStateOf(0L) }

    LaunchedEffect(displayCalibration) {
        val now = System.currentTimeMillis()
        if (!isDraggingSlider && (now - lastKcalInteraction > 3500L)) {
            kcalEnabled = displayCalibration.kcalEnabled
            kcalSat = displayCalibration.saturation
        }
    }

    LynxCard(
        title = "Manajemen Warna Layar (Color Engine)",
        subtitle = "SurfaceFlinger Color Matrix 1015, D65 White Point, & KCAL",
        icon = Icons.Default.Palette,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // ── Conflict Warning Banner (Night Light / Extra Dim) ──
        if (!colorConflict.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AccentOrangeDim,
                border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = localizedUiText("Peringatan Konflik"),
                        tint = AccentOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = colorConflict,
                        color = TextPrimary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── Master Calibration State & Lock Banner ──
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isCalibrationEnabled) cardAccent.copy(alpha = 0.08f) else BgElevated,
            border = BorderStroke(1.dp, if (isCalibrationEnabled) cardAccent.copy(alpha = 0.4f) else BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onToggleCalibration(!isCalibrationEnabled) }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isCalibrationEnabled) cardAccent.copy(alpha = 0.2f) else TextTertiary.copy(alpha = 0.12f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isCalibrationEnabled) Icons.Default.Tune else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isCalibrationEnabled) cardAccent else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isCalibrationEnabled) "Engine Kalibrasi Aktif" else "Kalibrasi Layar Nonaktif",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = if (isCalibrationEnabled) cardAccent.copy(alpha = 0.15f) else BgSurfaceLowest,
                            border = BorderStroke(0.6.dp, if (isCalibrationEnabled) cardAccent.copy(alpha = 0.5f) else BorderSubtle)
                        ) {
                            Text(
                                text = if (isCalibrationEnabled) colorProfile.name.uppercase() else "STANDAR OEM",
                                color = if (isCalibrationEnabled) cardAccent else TextTertiary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (isCalibrationEnabled)
                            "Matriks warna aktif diterapkan ke SurfaceFlinger & sistem."
                        else
                            "Profil OEM D65 aktif. Seluruh slider terkunci rapat dari sentuhan tidak sengaja.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                Switch(
                    checked = isCalibrationEnabled,
                    onCheckedChange = { onToggleCalibration(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgDeepOled,
                        checkedTrackColor = cardAccent,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = BgSurfaceLowest
                    )
                )
            }
        }

        // ── Sliders & Presets: ONLY Accessible when isCalibrationEnabled == true ──
        AnimatedVisibility(
            visible = isCalibrationEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                // ── Quick Presets ──
                Text(
                    "Preset Profil Warna Universal",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))

                val presets = listOf(
                    Triple("Akurat", ColorMatrixProfile.ACCURATE, Icons.Default.Verified),
                    Triple("Gaming", ColorMatrixProfile.GAMING, Icons.Default.SportsEsports),
                    Triple("Cinema", ColorMatrixProfile.CINEMA, Icons.Default.Movie),
                    Triple("Membaca", ColorMatrixProfile.READING, Icons.AutoMirrored.Filled.MenuBook)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (name, profile, icon) ->
                        val isSelected = colorProfile.name.equals(name, ignoreCase = true)
                        Surface(
                            onClick = {
                                if (isCalibrationEnabled) {
                                    tempK = profile.temperatureK
                                    sat = profile.saturation
                                    cont = profile.contrast
                                    rGain = profile.red
                                    gGain = profile.green
                                    bGain = profile.blue
                                    onApplyProfile(profile)
                                }
                            },
                            enabled = isCalibrationEnabled,
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) cardAccent.copy(alpha = 0.2f) else BgElevated,
                            border = BorderStroke(1.dp, if (isSelected) cardAccent else BorderSubtle),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = name,
                                    tint = if (isSelected) cardAccent else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = name,
                                    color = if (isSelected) cardAccent else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 14.dp))

                // ── Color Temperature Slider (Kelvin) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Temperatur Warna (Kelvin)", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = when {
                                tempK < 6000 -> "Hangat / Warm (Filter Cahaya Biru)"
                                tempK in 6000..7000 -> "Standar Netral D65"
                                else -> "Sejuk / Cool (Tampilan Dingin)"
                            },
                            color = TextSecondary,
                            fontSize = 10.5.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle)
                    ) {
                        Text(
                            text = "$tempK K",
                            color = cardAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Slider(
                    value = tempK.toFloat(),
                    onValueChange = {
                        isDraggingSlider = true
                        lastSliderInteraction = System.currentTimeMillis()
                        tempK = it.toInt()
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        lastSliderInteraction = System.currentTimeMillis()
                        onApplyProfile(
                            colorProfile.copy(
                                name = "Kustom",
                                temperatureK = tempK,
                                saturation = sat,
                                contrast = cont,
                                red = rGain,
                                green = gGain,
                                blue = bGain
                            )
                        )
                    },
                    enabled = isCalibrationEnabled,
                    valueRange = 4000f..9000f,
                    steps = 49,
                    colors = SliderDefaults.colors(thumbColor = cardAccent, activeTrackColor = cardAccent)
                )

                Spacer(Modifier.height(8.dp))

                // ── Saturation Slider ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Saturasi Tampilan", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(String.format(java.util.Locale.US, "%.2fx", sat), color = AccentPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = sat,
                    onValueChange = {
                        isDraggingSlider = true
                        lastSliderInteraction = System.currentTimeMillis()
                        sat = it
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        lastSliderInteraction = System.currentTimeMillis()
                        onApplyProfile(
                            colorProfile.copy(
                                name = "Kustom",
                                temperatureK = tempK,
                                saturation = sat,
                                contrast = cont,
                                red = rGain,
                                green = gGain,
                                blue = bGain
                            )
                        )
                    },
                    enabled = isCalibrationEnabled,
                    valueRange = 0.5f..1.8f,
                    colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple)
                )

                Spacer(Modifier.height(8.dp))

                // ── Contrast Slider ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Kontras Warna", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(String.format(java.util.Locale.US, "%.2fx", cont), color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = cont,
                    onValueChange = {
                        isDraggingSlider = true
                        lastSliderInteraction = System.currentTimeMillis()
                        cont = it
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        lastSliderInteraction = System.currentTimeMillis()
                        onApplyProfile(
                            colorProfile.copy(
                                name = "Kustom",
                                temperatureK = tempK,
                                saturation = sat,
                                contrast = cont,
                                red = rGain,
                                green = gGain,
                                blue = bGain
                            )
                        )
                    },
                    enabled = isCalibrationEnabled,
                    valueRange = 0.7f..1.3f,
                    colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
                )

                // ── Advanced Individual RGB Gains Toggle ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAdvancedGains = !showAdvancedGains }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Penyesuaian Gain RGB Individual",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = if (showAdvancedGains) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = showAdvancedGains) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Red Gain
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Gain Merah", color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(String.format(java.util.Locale.US, "%.2f", rGain), color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = rGain,
                            onValueChange = {
                                isDraggingSlider = true
                                lastSliderInteraction = System.currentTimeMillis()
                                rGain = it
                            },
                            onValueChangeFinished = {
                                isDraggingSlider = false
                                lastSliderInteraction = System.currentTimeMillis()
                                onApplyProfile(
                                    colorProfile.copy(
                                        name = "Kustom",
                                        temperatureK = tempK,
                                        saturation = sat,
                                        contrast = cont,
                                        red = rGain,
                                        green = gGain,
                                        blue = bGain
                                    )
                                )
                            },
                            enabled = isCalibrationEnabled,
                            valueRange = 0.5f..1.5f,
                            colors = SliderDefaults.colors(thumbColor = AccentRed, activeTrackColor = AccentRed)
                        )

                        // Green Gain
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Gain Hijau", color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(String.format(java.util.Locale.US, "%.2f", gGain), color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = gGain,
                            onValueChange = {
                                isDraggingSlider = true
                                lastSliderInteraction = System.currentTimeMillis()
                                gGain = it
                            },
                            onValueChangeFinished = {
                                isDraggingSlider = false
                                lastSliderInteraction = System.currentTimeMillis()
                                onApplyProfile(
                                    colorProfile.copy(
                                        name = "Kustom",
                                        temperatureK = tempK,
                                        saturation = sat,
                                        contrast = cont,
                                        red = rGain,
                                        green = gGain,
                                        blue = bGain
                                    )
                                )
                            },
                            enabled = isCalibrationEnabled,
                            valueRange = 0.5f..1.5f,
                            colors = SliderDefaults.colors(thumbColor = AccentGreen, activeTrackColor = AccentGreen)
                        )

                        // Blue Gain
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Gain Biru", color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(String.format(java.util.Locale.US, "%.2f", bGain), color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = bGain,
                            onValueChange = {
                                isDraggingSlider = true
                                lastSliderInteraction = System.currentTimeMillis()
                                bGain = it
                            },
                            onValueChangeFinished = {
                                isDraggingSlider = false
                                lastSliderInteraction = System.currentTimeMillis()
                                onApplyProfile(
                                    colorProfile.copy(
                                        name = "Kustom",
                                        temperatureK = tempK,
                                        saturation = sat,
                                        contrast = cont,
                                        red = rGain,
                                        green = gGain,
                                        blue = bGain
                                    )
                                )
                            },
                            enabled = isCalibrationEnabled,
                            valueRange = 0.5f..1.5f,
                            colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
                        )
                    }
                }

                // ── Reset Profile Button ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        onClick = {
                            tempK = 6500
                            sat = 1.0f
                            cont = 1.0f
                            rGain = 1.0f
                            gGain = 1.0f
                            bGain = 1.0f
                            onResetProfile()
                        },
                        enabled = isCalibrationEnabled,
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = localizedUiText("Reset"),
                                tint = TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                "Reset Standar D65",
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ── HBM Sunlight Booster (If supported) ──
        if (displayCalibration.isHbmSupported) {
            HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 10.dp))
            LynxSwitch(
                label = "HBM Sunlight Booster",
                subLabel = "Tingkatkan batas kecerahan panel display di bawah terik matahari langsung",
                checked = displayCalibration.hbmEnabled,
                onCheckedChange = { onSetHbm(it) }
            )
        }

        // ── Kernel KCAL Hardware Engine (If kernel supports KCAL) ──
        if (displayCalibration.isKcalSupported) {
            HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 10.dp))
            LynxSwitch(
                label = "Kernel KCAL Hardware Engine",
                subLabel = "Koreksi warna pada tingkat kernel driver panel",
                checked = kcalEnabled,
                onCheckedChange = {
                    kcalEnabled = it
                    lastKcalInteraction = System.currentTimeMillis()
                    onSetKcal(
                        it,
                        displayCalibration.red,
                        displayCalibration.green,
                        displayCalibration.blue,
                        kcalSat,
                        displayCalibration.value,
                        displayCalibration.contrast,
                        displayCalibration.hue
                    )
                }
            )

            if (kcalEnabled) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("KCAL Saturation: $kcalSat", color = AccentPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        onSetKcal(
                            kcalEnabled,
                            displayCalibration.red,
                            displayCalibration.green,
                            displayCalibration.blue,
                            kcalSat,
                            displayCalibration.value,
                            displayCalibration.contrast,
                            displayCalibration.hue
                        )
                    },
                    valueRange = 128f..384f,
                    colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple)
                )
            }
        }
    }
}

// ============================================================
//  3. FLOATING GAME HUD OVERLAY CARD (IN-GAME OSD)
// ============================================================

