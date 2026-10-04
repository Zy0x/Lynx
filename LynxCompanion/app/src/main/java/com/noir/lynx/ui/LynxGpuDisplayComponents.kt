package com.noir.lynx.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private val AccentGreenDim = Color(0x1F10B981)

// ============================================================
//  1. GPU & DISPLAY TOP NAVIGATION (3 TABS: TUNING, LAB, INFO)
// ============================================================

@Composable
fun GpuDisplayTabRow(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        Triple(0, "Tuning", Icons.Default.Tune),
        Triple(1, "Lab", Icons.Default.Analytics),
        Triple(2, "Info", Icons.Default.Memory)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        border = BorderStroke(0.8.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEach { (index, title, icon) ->
                val isSelected = selectedTab == index
                val tabAccent = when (index) {
                    0 -> AccentOrange
                    1 -> AccentCyan
                    else -> AccentBlue
                }

                Surface(
                    onClick = { onSelectTab(index) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) tabAccent.copy(alpha = 0.15f) else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, tabAccent.copy(alpha = 0.5f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp) // Touch target >= 48dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = if (isSelected) tabAccent else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = title,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
//  2. COLOR MANAGEMENT CARD (SURFACEFLINGER MATRIX + KCAL)
// ============================================================

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
    var tempK by remember(colorProfile) { mutableIntStateOf(colorProfile.temperatureK) }
    var sat by remember(colorProfile) { mutableFloatStateOf(colorProfile.saturation) }
    var cont by remember(colorProfile) { mutableFloatStateOf(colorProfile.contrast) }
    var rGain by remember(colorProfile) { mutableFloatStateOf(colorProfile.red) }
    var gGain by remember(colorProfile) { mutableFloatStateOf(colorProfile.green) }
    var bGain by remember(colorProfile) { mutableFloatStateOf(colorProfile.blue) }
    var showAdvancedGains by remember { mutableStateOf(false) }

    // KCAL State
    var kcalEnabled by remember { mutableStateOf(displayCalibration.kcalEnabled) }
    var kcalSat by remember { mutableIntStateOf(displayCalibration.saturation) }

    LynxCard(
        title = "Manajemen Warna Layar (Color Engine)",
        subtitle = "SurfaceFlinger Color Matrix 1015, D65 White Point, & KCAL",
        icon = Icons.Default.Palette,
        accentColor = cardAccent,
        action = {
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
        },
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
                        contentDescription = "Peringatan Konflik",
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
                    onValueChange = { tempK = it.toInt() },
                    onValueChangeFinished = {
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
                    onValueChange = { sat = it },
                    onValueChangeFinished = {
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
                    onValueChange = { cont = it },
                    onValueChangeFinished = {
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
                            onValueChange = { rGain = it },
                            onValueChangeFinished = {
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
                            onValueChange = { gGain = it },
                            onValueChangeFinished = {
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
                            onValueChange = { bGain = it },
                            onValueChangeFinished = {
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
                                contentDescription = "Reset",
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("HBM Sunlight Booster", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Tingkatkan batas kecerahan panel display di bawah terik matahari langsung",
                        color = TextSecondary,
                        fontSize = 10.5.sp
                    )
                }
                Switch(
                    checked = displayCalibration.hbmEnabled,
                    onCheckedChange = { onSetHbm(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = cardAccent)
                )
            }
        }

        // ── Kernel KCAL Hardware Engine (If kernel supports KCAL) ──
        if (displayCalibration.isKcalSupported) {
            HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Kernel KCAL Hardware Engine", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Koreksi warna pada tingkat kernel driver panel", color = TextSecondary, fontSize = 10.5.sp)
                }
                Switch(
                    checked = kcalEnabled,
                    onCheckedChange = {
                        kcalEnabled = it
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
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = cardAccent)
                )
            }

            if (kcalEnabled) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("KCAL Saturation: $kcalSat", color = AccentPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = kcalSat.toFloat(),
                    onValueChange = { kcalSat = it.toInt() },
                    onValueChangeFinished = {
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

@Composable
fun FloatingGameHudCard(
    isHudRunning: Boolean,
    hudMode: Int,
    hudStyle: Int,
    onToggleHud: () -> Unit,
    onSetMode: (Int) -> Unit,
    onSetStyle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentCyan
    val context = LocalContext.current
    val hasOverlayPermission = remember(isHudRunning) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else true
    }

    LynxCard(
        title = "Floating Game HUD (In-Game OSD)",
        subtitle = "Overlay performa mengambang real-time di atas game fullscreen",
        icon = Icons.Default.Layers,
        accentColor = cardAccent,
        action = {
            Switch(
                checked = isHudRunning,
                onCheckedChange = {
                    if (!hasOverlayPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    } else {
                        onToggleHud()
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BgDeepOled,
                    checkedTrackColor = cardAccent,
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = BgSurfaceLowest
                )
            )
        },
        modifier = modifier
    ) {
        if (!hasOverlayPermission) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AccentOrange.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                    Text(
                        "Izin Tampilkan di Atas Aplikasi Lain diperlukan agar overlay HUD dapat muncul saat Anda bermain game.",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // HUD Active Status Banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isHudRunning) cardAccent.copy(alpha = 0.1f) else BgElevated,
            border = BorderStroke(1.dp, if (isHudRunning) cardAccent.copy(alpha = 0.4f) else BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    if (!hasOverlayPermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    } else {
                        onToggleHud()
                    }
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isHudRunning) cardAccent.copy(alpha = 0.2f) else TextTertiary.copy(alpha = 0.12f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isHudRunning) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = if (isHudRunning) cardAccent else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isHudRunning) "HUD Mengambang Aktif" else "HUD Nonaktif",
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isHudRunning) "Menampilkan FPS, Frame Time, GPU Load, & Suhu di layar" else "Ketuk sakelar untuk memunculkan OSD di game",
                        color = TextSecondary,
                        fontSize = 10.5.sp
                    )
                }
            }
        }

        AnimatedVisibility(visible = isHudRunning) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Layout Style Selector Chips
                Text("Gaya Tampilan Overlay (OSD Style)", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple(1, "RTSS Slim Pillar", "Vertikal"),
                        Triple(2, "Top Nano-Ribbon", "Horizontal"),
                        Triple(3, "Dual-Block", "Esport"),
                        Triple(4, "Quad-Tiles", "Grid"),
                        Triple(5, "Steam Deck", "Banner")
                    ).forEach { (styleId, title, badge) ->
                        val isSel = hudStyle == styleId
                        Surface(
                            onClick = { onSetStyle(styleId) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) cardAccent.copy(alpha = 0.22f) else BgElevated,
                            border = BorderStroke(1.dp, if (isSel) cardAccent else BorderGlass),
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(title, color = if (isSel) cardAccent else TextPrimary, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                                Text(badge, color = TextSecondary, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  4. PERFORMANCE LAB — RECORDER CARD
// ============================================================

@Composable
fun LabRecordCard(
    isRecording: Boolean,
    onStartRecording: (String?) -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentRed
    val context = LocalContext.current

    // Pulsing animation when recording
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    LynxCard(
        title = "Performance Lab — Frame Recorder",
        subtitle = "Perekam frame-pacing real-time untuk game & aplikasi aktif",
        icon = Icons.Default.Analytics,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // Status Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) cardAccent.copy(alpha = pulseAlpha) else AccentGreen)
                )
                Text(
                    text = if (isRecording) "SEDANG MEREKAM SESI AKTIF" else "Status: Standby (Siap Merekam)",
                    color = if (isRecording) cardAccent else TextSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle)
            ) {
                Text(
                    text = "SurfaceFlinger BLAST",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Text(
            text = "Lynx Performance Lab mengukur frame presentation timestamps langsung dari SurfaceFlinger untuk mendeteksi micro-stutter, jank, variansi frame-time, serta korelasi drop frekuensi GPU dan thermal throttling secara presisi.",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 14.dp)
        )

        // Action Button
        Button(
            onClick = {
                if (isRecording) {
                    onStopRecording()
                    Toast.makeText(context, "Menganalisis sesi perekaman...", Toast.LENGTH_SHORT).show()
                } else {
                    onStartRecording(null)
                    Toast.makeText(context, "Perekaman dimulai. Buka game/aplikasi Anda.", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) AccentRed else AccentCyan
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp) // Touch target >= 48dp
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = BgDeepOled,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isRecording) "Hentikan Sesi & Analisis Frame" else "Mulai Perekaman Sesi Baru",
                color = BgDeepOled,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================
//  4. PERFORMANCE LAB — SESSION SUMMARY CARD
// ============================================================

@Composable
fun SessionSummaryCard(
    report: FrameSessionReport,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentCyan
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LynxCard(
        title = "Metrik Sesi: ${report.packageName.substringAfterLast('.')}",
        subtitle = "Pukul ${timeFormat.format(Date(report.timestamp))} • Durasi ${report.sessionDurationSec}s • ${report.totalFrames} Frame",
        icon = Icons.Default.Speed,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // ── 4-Metric Grid ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Avg FPS
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("RATA-RATA FPS", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = String.format("%.1f", report.avgFps),
                        color = cardAccent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // 1% Low FPS
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("1% LOW FPS", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = String.format("%.1f", report.low1PctFps),
                        color = if (report.low1PctFps < 45f) AccentRed else AccentOrange,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // 0.1% Low FPS
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("0.1% LOW FPS", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = String.format("%.1f", report.low01PctFps),
                        color = if (report.low01PctFps < 30f) AccentRed else TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Stability %
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("KESTABILAN", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = String.format("%.0f%%", report.stabilityPct),
                        color = if (report.stabilityPct >= 90f) AccentGreen else AccentOrange,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // ── Secondary Detail Stats ──
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Variansi Frame Time (Deviasi)", color = TextSecondary, fontSize = 11.5.sp)
                    Text("${String.format("%.2f", report.frameTimeVarianceMs)} ms", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Frame Jank (Drop > 16.7ms)", color = TextSecondary, fontSize = 11.5.sp)
                    Text("${report.jankCount} frame", color = if (report.jankCount > 10) AccentRed else TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Persentil Frame Time (p50 / p95 / p99)", color = TextSecondary, fontSize = 11.5.sp)
                    Text(
                        "${String.format("%.1f", report.p50Ms)} / ${String.format("%.1f", report.p95Ms)} / ${String.format("%.1f", report.p99Ms)} ms",
                        color = AccentCyan,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Suhu GPU Puncak / Rata-rata", color = TextSecondary, fontSize = 11.5.sp)
                    Text(
                        "${String.format("%.1f", report.peakTempC)}°C / ${String.format("%.1f", report.avgTempC)}°C",
                        color = if (report.peakTempC >= 50f) AccentRed else AccentGreen,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Frekuensi GPU Puncak", color = TextSecondary, fontSize = 11.5.sp)
                    Text("${report.peakGpuMhz} MHz", color = AccentOrange, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Export Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                onClick = {
                    onExportCsv()
                    Toast.makeText(context, "Sesi diexport ke Download/Lynx/", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.5f)),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Export CSV",
                        tint = cardAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Export Sesi ke CSV",
                        color = cardAccent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ============================================================
//  5. PERFORMANCE LAB — DROP ROOT CAUSE ANALYSIS CARD
// ============================================================

@Composable
fun DropAnalysisCard(
    report: FrameSessionReport,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentOrange

    val dropEvents = report.dropEvents
    val recommendation = report.recommendation
        ?: DropCorrelator.generateRecommendation(dropEvents, report.avgFps, report.peakGpuMhz, 120)

    LynxCard(
        title = "Analisis Akar Penyebab Frame Drop",
        subtitle = "Korelasi timestamp frame drop terhadap clock, thermal, & CPU queue",
        icon = Icons.Default.Troubleshoot,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        if (dropEvents.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text("Tidak Terdeteksi Stutter Signifikan", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Frame pacing berjalan mulus mendekati target refresh rate.", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        } else {
            // Recommendation Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardAccent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = cardAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Rekomendasi Optimasi Lynx", color = cardAccent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = recommendation,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Top Drop Events Table
            Text("Sampel Kejadian Frame Drop Terparah", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                dropEvents.take(5).forEach { event ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Detik ke-${event.timeOffsetSec}s",
                                    color = AccentCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${String.format("%.1f", event.frameTimeMs)} ms",
                                    color = AccentRed,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = event.suspectedCause,
                                color = TextPrimary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Kondisi: ${event.gpuClockMhz} MHz • ${event.gpuLoadPct}% Load • ${String.format("%.1f", event.tempC)}°C",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  6. PERFORMANCE LAB — SESSION HISTORY LIST
// ============================================================

@Composable
fun SessionHistoryList(
    sessions: List<FrameSessionReport>,
    onSelectSession: (FrameSessionReport) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentPurple
    val timeFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    LynxCard(
        title = "Riwayat Sesi Rekaman (${sessions.size}/20)",
        subtitle = "Daftar histori benchmark frame pacing yang tersimpan secara lokal",
        icon = Icons.Default.History,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Belum ada rekaman sesi. Mulai rekam pada panel di atas.",
                    color = TextSecondary,
                    fontSize = 11.5.sp
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sessions.take(6).forEach { session ->
                    Surface(
                        onClick = { onSelectSession(session) },
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = session.packageName.substringAfterLast('.'),
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${timeFormat.format(Date(session.timestamp))} • ${session.sessionDurationSec}s",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AccentCyanDim,
                                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "${String.format("%.1f", session.avgFps)} FPS",
                                        color = AccentCyan,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AccentOrangeDim,
                                    border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "1% ${String.format("%.1f", session.low1PctFps)}",
                                        color = AccentOrange,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  7. HARDWARE CAPABILITY SCANNER CARD (INFO TAB)
// ============================================================

@Composable
fun CapabilityScannerCard(
    caps: GraphicsCapabilities,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentCyan

    LynxCard(
        title = "Hardware Capability Scanner",
        subtitle = "Deteksi driver fisik, GLES, Vulkan, dan kapabilitas panel display",
        icon = Icons.Default.Memory,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // ── GPU Subsistem Section ──
        Text("Subsistem GPU & Driver Fisik", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Vendor GPU", color = TextSecondary, fontSize = 11.5.sp)
                    Text(caps.gpuVendor, color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Model GPU", color = TextSecondary, fontSize = 11.5.sp)
                    Text(caps.gpuModel, color = cardAccent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Backend Kernel", color = TextSecondary, fontSize = 11.5.sp)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = cardAccent.copy(alpha = 0.15f),
                        border = BorderStroke(0.8.dp, cardAccent.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = caps.backend.name,
                            color = cardAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Versi Driver", color = TextSecondary, fontSize = 11.5.sp)
                    Text(caps.driverVersion, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Vulkan API", color = TextSecondary, fontSize = 11.5.sp)
                    Text(caps.vulkanVersion ?: "Tidak Terdeteksi", color = AccentPurple, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Panel & Display Capabilities Section ──
        Text("Panel Display & Format Warna", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mode Resolusi & Refresh Rate", color = TextSecondary, fontSize = 11.5.sp)
                    Text(
                        text = if (caps.displayModes.isNotEmpty()) {
                            caps.displayModes.map { "${it.fps.toInt()}Hz" }.distinct().joinToString(", ")
                        } else "60Hz, 90Hz, 120Hz",
                        color = AccentCyan,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Dukungan HDR", color = TextSecondary, fontSize = 11.5.sp)
                    Text(
                        text = if (caps.hdrTypes.isNotEmpty()) caps.hdrTypes.joinToString(", ") else "SDR (Standard)",
                        color = if (caps.hdrTypes.isNotEmpty()) AccentGreen else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Wide Color Gamut (Display P3)", color = TextSecondary, fontSize = 11.5.sp)
                    Text(
                        text = if (caps.wideColor) "Didukung (Display P3)" else "Standar sRGB",
                        color = if (caps.wideColor) AccentGreen else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("DC Dimming Hardware", color = TextSecondary, fontSize = 11.5.sp)
                    Text(
                        text = if (caps.dcDimmingNode != null) "Didukung (/sys/...)" else "Tidak Tersedia",
                        color = if (caps.dcDimmingNode != null) AccentGreen else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("HBM Sunlight Booster", color = TextSecondary, fontSize = 11.5.sp)
                    Text(
                        text = if (caps.hbmNode != null) "Didukung (/sys/...)" else "Tidak Tersedia",
                        color = if (caps.hbmNode != null) AccentGreen else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kernel KCAL Engine", color = TextSecondary, fontSize = 11.5.sp)
                    Text(
                        text = if (caps.hasKcal) "Didukung (/dev/kcal)" else "Tidak Tersedia",
                        color = if (caps.hasKcal) AccentGreen else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Refresh Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                onClick = onRefresh,
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, BorderGlass),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Pindai Ulang", tint = cardAccent, modifier = Modifier.size(15.dp))
                    Text("Pindai Ulang Hardware", color = cardAccent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================
//  8. DISPLAY PIPELINE & COMPOSITION DIAGNOSTICS CARD
// ============================================================

@Composable
fun GraphicsDebugCard(
    caps: GraphicsCapabilities,
    pipeline: DisplayPipelineInfo,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentBlue

    LynxCard(
        title = "Display Pipeline & Komposisi",
        subtitle = "Metrik SurfaceFlinger timestats & rasio komposisi Hardware Composer",
        icon = Icons.Default.Layers,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // ── Composition Ratios ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // HWC Direct Ratio
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("HWC KOMPOSISI", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    val hwcPct = ((1f - pipeline.clientCompositionRatio) * 100f).coerceIn(0f, 100f)
                    Text(
                        text = String.format("%.1f%%", hwcPct),
                        color = AccentGreen,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // GPU Client Fallback
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("GPU FALLBACK", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    val clientPct = (pipeline.clientCompositionRatio * 100f).coerceIn(0f, 100f)
                    Text(
                        text = String.format("%.1f%%", clientPct),
                        color = if (clientPct > 20f) AccentOrange else TextSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Missed Frames
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("FRAME MISSED", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${pipeline.missedFrames}",
                        color = if (pipeline.missedFrames > 50) AccentRed else TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Details
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pipeline Renderer HWUI", color = TextSecondary, fontSize = 11.5.sp)
                    Text(pipeline.skiaPipeline, color = cardAccent, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kompositor Vulkan", color = TextSecondary, fontSize = 11.5.sp)
                    Text(if (pipeline.isVulkanCompositor) "Aktif (Vulkan Surface)" else "Nonaktif (OpenGL ES)", color = TextPrimary, fontSize = 11.5.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("HWC Missed Frames", color = TextSecondary, fontSize = 11.5.sp)
                    Text("${pipeline.hwcMissedFrames}", color = TextPrimary, fontSize = 11.5.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("String GLES Lengkap", color = TextSecondary, fontSize = 11.5.sp)
                    Text(
                        text = caps.glesVersion,
                        color = TextTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Refresh Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                onClick = onRefresh,
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, BorderGlass),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Pindai Pipeline", tint = cardAccent, modifier = Modifier.size(15.dp))
                    Text("Pindai Pipeline Ulang", color = cardAccent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================
//  9. UNIVERSAL SYSFS NODE EXPLORER CARD
// ============================================================

@Composable
fun NodeExplorerCard(
    caps: GraphicsCapabilities,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentOrange
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val nodes = caps.nodes

    LynxCard(
        title = "Universal Sysfs Node Explorer",
        subtitle = "Verifikasi hak akses baca/tulis node kernel nyata pada hardware perangkat",
        icon = Icons.Default.Terminal,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        if (nodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Node kernel GPU diverifikasi otomatis saat pemindaian.",
                    color = TextSecondary,
                    fontSize = 11.5.sp
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                nodes.forEach { node ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboard.setText(AnnotatedString(node.path))
                                Toast.makeText(context, "Path disalin: ${node.path}", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = node.label,
                                    color = TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    text = node.path,
                                    color = TextTertiary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            // Status Badges
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (node.writable) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AccentGreenDim,
                                        border = BorderStroke(0.8.dp, AccentGreen.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            "WRITABLE",
                                            color = AccentGreen,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                } else if (node.readable) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AccentCyanDim,
                                        border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            "READ-ONLY",
                                            color = AccentCyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AccentRedDim,
                                        border = BorderStroke(0.8.dp, AccentRed.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            "UNAVAILABLE",
                                            color = AccentRed,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
