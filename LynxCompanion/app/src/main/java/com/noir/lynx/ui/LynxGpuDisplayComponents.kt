package com.noir.lynx.ui

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
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        color = BgCard,
        border = BorderStroke(0.8.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
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
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) tabAccent.copy(alpha = 0.15f) else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, tabAccent.copy(alpha = 0.5f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
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
                                    softWrap = true
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
//  7. HARDWARE CAPABILITY & INFO DASHBOARD (OVERHAULED INFO TAB)
// ============================================================

@Composable
fun GpuHardwareInfoDashboard(
    caps: GraphicsCapabilities,
    pipeline: DisplayPipelineInfo,
    currentHz: Int = 0,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GpuSiliconIdentityCard(
            caps = caps,
            onRefresh = onRefresh
        )

        DisplayTelemetryCard(
            caps = caps,
            currentHz = currentHz
        )

        CompositorPipelineCard(
            pipeline = pipeline
        )

        KernelNodesInspectorCard(
            caps = caps
        )
    }
}

// ── 1. SILICON IDENTITY & ANTI-SPOOFING CARD ──
@Composable
fun GpuSiliconIdentityCard(
    caps: GraphicsCapabilities,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentCyan
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LynxCard(
        title = "Identitas Silikon & Driver",
        subtitle = "Verifikasi ground truth kernel dan subsistem grafis",
        icon = Icons.Default.Memory,
        accentColor = cardAccent,
        action = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.size(34.dp)
            ) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Segarkan",
                        tint = cardAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        modifier = modifier
    ) {
        // Anti-Spoofing Security & Ground Truth Status
        if (caps.isSpoofed) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AccentRed.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Spoofer Terdeteksi",
                            tint = AccentRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Manipulasi Identitas SoC / GPU Terdeteksi",
                            color = AccentRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            softWrap = true
                        )
                    }
                    Text(
                        text = "Modul eksternal memalsukan getprop userspace. Lynx secara otomatis mengisolasi driver kernel fisik demi menjaga kestabilan dan keamanan hardware.",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        softWrap = true,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgSurfaceLowest,
                        border = BorderStroke(0.6.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Kernel Asli (Fisik)", color = TextSecondary, fontSize = 10.5.sp)
                                Text(caps.groundTruthSoc, color = AccentGreen, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("GPU Driver Asli", color = TextSecondary, fontSize = 10.5.sp)
                                Text(caps.groundTruthGpu, color = AccentGreen, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                            HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 2.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Identitas Tiruan (Userspace)", color = TextSecondary, fontSize = 10.5.sp)
                                Text(caps.spoofedSoc ?: "Qualcomm / Generic", color = AccentOrange, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("GPU Tiruan (SurfaceFlinger)", color = TextSecondary, fontSize = 10.5.sp)
                                Text(caps.spoofedGpuModel ?: caps.gpuModel, color = AccentOrange, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, AccentGreen.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Integritas Hardware",
                            tint = AccentGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Hardware Asli Terverifikasi",
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            softWrap = true
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentGreen.copy(alpha = 0.15f),
                        border = BorderStroke(0.6.dp, AccentGreen.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "Driver Fisik • ${caps.backend.displayName}",
                            color = AccentGreen,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                            softWrap = true
                        )
                    }
                }
            }
        }

        // Modern 2x2 Clean Spec Tiles with Matching Height
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoSpecTile(
                label = "MODEL GPU",
                value = caps.gpuModel,
                sub = "Vendor ${caps.gpuVendor}",
                accentColor = cardAccent,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            InfoSpecTile(
                label = "ARSITEKTUR SOC",
                value = caps.groundTruthSoc,
                sub = "Backend ${caps.backend.displayName}",
                accentColor = AccentBlue,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoSpecTile(
                label = "API OPENGL ES",
                value = caps.glesVersion,
                sub = "Driver ${caps.driverVersion}",
                accentColor = AccentGreen,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            val hasVk = !caps.vulkanVersion.isNullOrBlank()
            InfoSpecTile(
                label = "API VULKAN",
                value = if (hasVk) "Vulkan ${caps.vulkanVersion}" else "Tidak Didukung",
                sub = if (hasVk) (caps.vulkanDriverId ?: "ARM Mali Driver") else "Perangkat Hanya OpenGL ES",
                accentColor = if (hasVk) AccentPurple else TextTertiary,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        // Driver Binary File Path (Monospace row with copy action)
        if (caps.gpuDriverPath.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        clipboard.setText(AnnotatedString(caps.gpuDriverPath))
                        Toast.makeText(context, "Path driver disalin ke clipboard", Toast.LENGTH_SHORT).show()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "BINARY DRIVER FISIK",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = caps.gpuDriverPath,
                            color = TextPrimary,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 13.5.sp,
                            softWrap = true
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Salin",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ── 2. DISPLAY TELEMETRY & PANEL ENGINE CARD ──
@Composable
fun DisplayTelemetryCard(
    caps: GraphicsCapabilities,
    currentHz: Int = 0,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentBlue

    LynxCard(
        title = "Panel Layar & Engine Visual",
        subtitle = "Karakteristik fisik panel, resolusi, dan gamut warna",
        icon = Icons.Default.Smartphone,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // Native Resolution & Supported Refresh Rates
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "RESOLUSI FISIK PANEL",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    val resText = caps.displayModes.firstOrNull()?.let { "${it.width} × ${it.height}" } ?: "1080 × 2460"
                    Text(
                        text = "$resText Piksel",
                        color = AccentBlue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        softWrap = true
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        val supportedFrequencies = if (caps.displayModes.isNotEmpty()) {
                            caps.displayModes.map { "${it.fps.toInt()}Hz" }.distinct()
                        } else listOf("60Hz", "90Hz", "120Hz")

                        val maxHz = caps.displayModes.maxOfOrNull { it.fps.toInt() } ?: 120

                        supportedFrequencies.forEach { hz ->
                            val hzInt = hz.removeSuffix("Hz").toIntOrNull() ?: 60
                            val isCurrent = (currentHz > 0 && currentHz == hzInt) || (currentHz == 0 && hz == "${maxHz}Hz")
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = if (isCurrent) AccentCyan.copy(alpha = 0.22f) else BgSurfaceLowest,
                                border = BorderStroke(1.dp, if (isCurrent) AccentCyan else BorderGlass),
                                modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = if (isCurrent) "$hz (Aktif)" else hz,
                                        color = if (isCurrent) AccentCyan else TextSecondary,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Mode Refresh Rate Panel (Kontrol di Tab Tuning)",
                        color = TextTertiary,
                        fontSize = 9.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 2x2 Display Spec Tiles with IntrinsicSize.Min for matching height
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoSpecTile(
                label = "KEPADATAN PIKSEL",
                value = "${caps.displayDpi} DPI",
                sub = String.format(Locale.US, "Skala Kepadatan %.2fx (xxhdpi)", caps.displayDensity),
                accentColor = TextPrimary,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            InfoSpecTile(
                label = "GAMUT & RUANG WARNA",
                value = caps.displayColorMode,
                sub = if (caps.wideColor) "DCI-P3 Wide Gamut (10-bit)" else "Profil Warna Terkalibrasi",
                accentColor = if (caps.wideColor) AccentGreen else AccentCyan,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val hdrLabel = if (caps.hdrTypes.isNotEmpty()) caps.hdrTypes.joinToString(", ") else "SDR (Standar)"
            InfoSpecTile(
                label = "RENTANG DINAMIS (HDR)",
                value = hdrLabel,
                sub = caps.maxLuminanceNits?.let { "Kecerahan Puncak ${it.toInt()} Nits" } ?: "Rentang Kontras Standar",
                accentColor = if (caps.hdrTypes.isNotEmpty()) AccentGreen else TextSecondary,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            InfoSpecTile(
                label = "HARDWARE COMPOSER",
                value = if (caps.surfaceFlingerHwc.isNotBlank()) caps.surfaceFlingerHwc else "HWC 2.x Direct",
                sub = "Komposisi Layar Hardware",
                accentColor = AccentBlue,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        // Hardware Panel Feature Row (clean, informative)
        val activeFeatures = buildList {
            if (caps.dcDimmingNode != null) add("DC Dimming")
            if (caps.hbmNode != null) add("HBM Booster")
            if (caps.hasKcal) add("Kernel KCAL")
        }
        if (activeFeatures.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.6.dp, AccentGreen.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Fitur Kernel Aktif: ${activeFeatures.joinToString(" • ")}",
                        color = AccentGreen,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        softWrap = true
                    )
                }
            }
        }
    }
}

// ── 3. SURFACEFLINGER & COMPOSITOR PIPELINE CARD ──
@Composable
fun CompositorPipelineCard(
    pipeline: DisplayPipelineInfo,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentGreen
    val hwcPct = ((1f - pipeline.clientCompositionRatio) * 100f).coerceIn(0f, 100f)
    val clientPct = (pipeline.clientCompositionRatio * 100f).coerceIn(0f, 100f)

    LynxCard(
        title = "Pipeline Komposisi Grafis",
        subtitle = "Distribusi SurfaceFlinger dan HWUI Canvas",
        icon = Icons.Default.Layers,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // Visual Composition Segmented Bar
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DISTRIBUSI KOMPOSISI BUFFER",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%% Direct HWC", hwcPct),
                        color = AccentGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Segmented Progress Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(BgSurfaceLowest)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(hwcPct.coerceAtLeast(1f))
                            .background(AccentGreen)
                    )
                    if (clientPct > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(clientPct.coerceAtLeast(1f))
                                .background(AccentOrange)
                        )
                    }
                }

                Spacer(Modifier.height(7.dp))

                Text(
                    text = if (clientPct == 0f) {
                        "Seluruh frame dikomposisikan langsung oleh Hardware Composer tanpa membebani GPU."
                    } else {
                        "Sebagian buffer dikomposisikan via GPU Client Fallback (${String.format(Locale.US, "%.1f%%", clientPct)})."
                    },
                    color = TextTertiary,
                    fontSize = 10.sp,
                    lineHeight = 14.5.sp,
                    softWrap = true
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // HWUI & Frame Stability 2-Tile Grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoSpecTile(
                label = "RENDERER KANVAS HWUI",
                value = pipeline.skiaPipeline,
                sub = if (pipeline.isVulkanCompositor) "Vulkan Backend Aktif" else "OpenGL ES Backend Aktif",
                accentColor = AccentCyan,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            InfoSpecTile(
                label = "KESEHATAN BUFFER FRAME",
                value = if (pipeline.missedFrames == 0L) "Optimal (0 Terlewat)" else "${pipeline.missedFrames} Terlewat",
                sub = "Sinkronisasi Hardware VSYNC",
                accentColor = if (pipeline.missedFrames == 0L) AccentGreen else AccentRed,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

// ── 4. KERNEL NODES ACCORDION CARD ──
@Composable
fun KernelNodesInspectorCard(
    caps: GraphicsCapabilities,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentOrange
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    val nodes = caps.nodes

    LynxCard(
        title = "Node Kernel GPU & Sysfs",
        subtitle = "${nodes.size} node kernel terverifikasi • Akses I/O langsung",
        icon = Icons.Default.Terminal,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        Surface(
            onClick = { isExpanded = !isExpanded },
            shape = RoundedCornerShape(10.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isExpanded) "Sembunyikan Daftar Node Fisik" else "Daftar Node Fisik Driver (${nodes.size} Node)",
                        color = TextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        softWrap = true
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle",
                    tint = cardAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                nodes.forEach { node ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.6.dp, BorderSubtle),
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
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = node.label,
                                    color = TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    softWrap = true
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = node.path,
                                    color = TextTertiary,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 13.sp,
                                    softWrap = true
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = if (node.writable) AccentGreenDim else AccentCyanDim,
                                border = BorderStroke(0.6.dp, if (node.writable) AccentGreen.copy(alpha = 0.5f) else AccentCyan.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = if (node.writable) "R/W" else "RO",
                                    color = if (node.writable) AccentGreen else AccentCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── REUSABLE HELPER TILES ──
@Composable
private fun InfoSpecTile(
    label: String,
    value: String,
    sub: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderSubtle),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label,
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    lineHeight = 12.sp,
                    softWrap = true
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = value,
                    color = accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 17.sp,
                    softWrap = true
                )
            }
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = sub,
                    color = TextTertiary,
                    fontSize = 9.5.sp,
                    lineHeight = 13.5.sp,
                    softWrap = true
                )
            }
        }
    }
}


// Backwards-compatible aliases
@Composable
fun CapabilityScannerCard(caps: GraphicsCapabilities, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    GpuSiliconIdentityCard(caps = caps, onRefresh = onRefresh, modifier = modifier)
}

@Composable
fun GraphicsDebugCard(caps: GraphicsCapabilities, pipeline: DisplayPipelineInfo, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    CompositorPipelineCard(pipeline = pipeline, modifier = modifier)
}

@Composable
fun NodeExplorerCard(caps: GraphicsCapabilities, modifier: Modifier = Modifier) {
    KernelNodesInspectorCard(caps = caps, modifier = modifier)
}

// ============================================================
//  PER-APP GRAPHICS & GAME DRIVER HUB CARD
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppGraphicsHubCard(
    rules: List<PerAppGraphicsRule>,
    installedApps: List<AppInfo>,
    supportedRates: List<Int> = listOf(60, 90, 120),
    isAngleSupported: Boolean = true,
    onSaveRule: (PerAppGraphicsRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentGreen
    var showAppPicker by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<PerAppGraphicsRule?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    LynxCard(
        title = "Manajemen Rendering Per-Aplikasi",
        subtitle = "Konfigurasi Game Driver (ANGLE & Refresh Rate) khusus per judul game",
        icon = Icons.Default.SportsEsports,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // --- 1. Rules List or Empty State ---
        if (rules.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgSurfaceLowest,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = cardAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Belum ada konfigurasi game/aplikasi. Tambahkan game favorit Anda untuk mengaktifkan Game Driver AOSP atau translasi ANGLE Vulkan.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rules.forEach { rule ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(cardAccent.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AppIconImage(
                                            packageName = rule.packageName,
                                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = rule.appName.ifBlank { rule.packageName },
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            softWrap = true
                                        )
                                        Text(
                                            text = rule.packageName,
                                            fontSize = 10.sp,
                                            color = TextSecondary,
                                            softWrap = true
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = { editingRule = rule },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Aturan",
                                            tint = cardAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteRule(rule.packageName) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Hapus Aturan",
                                            tint = AccentRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Badges Row
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Driver Type Badge
                                val (driverLabel, driverColor) = when (rule.driverType) {
                                    "game" -> Pair("Game Driver", AccentGreen)
                                    "prerelease" -> Pair("Prerelease Driver", AccentOrange)
                                    else -> Pair("Bawaan Sistem", TextSecondary)
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = driverColor.copy(alpha = 0.15f),
                                    border = BorderStroke(0.6.dp, driverColor.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = driverLabel,
                                        color = driverColor,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }

                                // ANGLE Badge
                                if (rule.useAngle) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AccentCyan.copy(alpha = 0.15f),
                                        border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "ANGLE Vulkan",
                                            color = AccentCyan,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                // Refresh Rate Badge
                                if (rule.targetRefreshRate > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AccentBlue.copy(alpha = 0.15f),
                                        border = BorderStroke(0.6.dp, AccentBlue.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "${rule.targetRefreshRate} Hz",
                                            color = AccentBlue,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 2. Add App Button ---
        Surface(
            onClick = {
                searchQuery = ""
                showAppPicker = true
            },
            shape = RoundedCornerShape(12.dp),
            color = cardAccent.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Row(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = cardAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Tambah Aplikasi / Game",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = cardAccent
                )
            }
        }
    }

    // --- Modal 1: App Picker Dialog ---
    if (showAppPicker) {
        val filteredApps = remember(searchQuery, installedApps) {
            if (searchQuery.isBlank()) {
                installedApps
            } else {
                installedApps.filter {
                    it.label.contains(searchQuery, ignoreCase = true) ||
                            it.packageName.contains(searchQuery, ignoreCase = true)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = {
                Text(
                    "Pilih Aplikasi / Game",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Cari nama game atau paket...", fontSize = 12.sp, color = TextSecondary) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = cardAccent,
                            unfocusedBorderColor = BorderGlass,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = cardAccent
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (filteredApps.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Aplikasi tidak ditemukan", fontSize = 12.sp, color = TextSecondary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                Surface(
                                    onClick = {
                                        showAppPicker = false
                                        val existing = rules.find { it.packageName == app.packageName }
                                        editingRule = existing ?: PerAppGraphicsRule(
                                            packageName = app.packageName,
                                            appName = app.label,
                                            driverType = "game",
                                            useAngle = false,
                                            targetRefreshRate = 0
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = BgElevated,
                                    border = BorderStroke(0.6.dp, BorderSubtle),
                                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(if (app.isGame) AccentGreen.copy(alpha = 0.15f) else BgSurfaceLowest),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AppIconImage(
                                                packageName = app.packageName,
                                                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(6.dp))
                                            )
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = app.label.ifBlank { app.packageName },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary,
                                                softWrap = true
                                            )
                                            Text(
                                                text = app.packageName,
                                                fontSize = 9.5.sp,
                                                color = TextSecondary,
                                                softWrap = true
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) {
                    Text("Tutup", color = TextSecondary)
                }
            },
            containerColor = BgCard
        )
    }

    // --- Modal 2: Edit / Configure Rule Dialog ---
    editingRule?.let { currentEdit ->
        var selectedDriver by remember { mutableStateOf(currentEdit.driverType) }
        var useAngle by remember { mutableStateOf(currentEdit.useAngle) }
        var selectedRefreshRate by remember { mutableStateOf(currentEdit.targetRefreshRate) }

        AlertDialog(
            onDismissRequest = { editingRule = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(cardAccent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AppIconImage(
                            packageName = currentEdit.packageName,
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Column {
                        Text(
                            "Konfigurasi Rendering Grafis",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = currentEdit.appName.ifBlank { currentEdit.packageName },
                            fontSize = 11.5.sp,
                            color = cardAccent,
                            fontWeight = FontWeight.SemiBold,
                            softWrap = true
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Driver Type
                    Column {
                        Text("Tipe Driver GPU (AOSP)", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Pilih varian driver GPU yang dialokasikan Android untuk game ini", fontSize = 10.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Pair("default", "Bawaan"),
                                Pair("game", "Game Driver"),
                                Pair("prerelease", "Prerelease")
                            ).forEach { (type, label) ->
                                val isSel = selectedDriver == type
                                Surface(
                                    onClick = { selectedDriver = type },
                                    modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) cardAccent.copy(alpha = 0.2f) else BgElevated,
                                    border = BorderStroke(1.dp, if (isSel) cardAccent else BorderGlass)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp)) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSel) cardAccent else TextSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = BorderGlass)

                    // 2. ANGLE Layer Switch
                    if (isAngleSupported) {
                        LynxSwitch(
                            label = "Translasi ANGLE (OpenGL → Vulkan)",
                            subLabel = "Rute panggilan draw GL melalui backend Vulkan berkecepatan tinggi",
                            checked = useAngle,
                            onCheckedChange = { useAngle = it }
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BgElevated.copy(alpha = 0.4f),
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.fillMaxWidth().alpha(0.55f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("Translasi ANGLE (OpenGL → Vulkan)", color = TextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Tidak didukung pada OS/Hardware ini", color = AccentOrange, fontSize = 9.5.sp)
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = BgSurfaceLowest,
                                    border = BorderStroke(0.6.dp, BorderGlass)
                                ) {
                                    Text(
                                        text = "Tidak Didukung",
                                        color = TextSecondary,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = BorderGlass)

                    // 3. Target Refresh Rate
                    Column {
                        Text("Kunci Refresh Rate Layar", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Target frame rate display saat game ini berada di layar depan", fontSize = 10.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                        val refreshOptions = listOf(Pair(0, "Bawaan")) + supportedRates.map { Pair(it, "$it Hz") }
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            refreshOptions.forEach { (hz, label) ->
                                val isSel = selectedRefreshRate == hz
                                Surface(
                                    onClick = { selectedRefreshRate = hz },
                                    modifier = Modifier.defaultMinSize(minWidth = 60.dp, minHeight = 44.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) AccentBlue.copy(alpha = 0.2f) else BgElevated,
                                    border = BorderStroke(1.dp, if (isSel) AccentBlue else BorderGlass)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSel) AccentBlue else TextSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newRule = currentEdit.copy(
                            driverType = selectedDriver,
                            useAngle = useAngle,
                            targetRefreshRate = selectedRefreshRate
                        )
                        onSaveRule(newRule)
                        editingRule = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = cardAccent)
                ) {
                    Text("Simpan Konfigurasi", color = BgDeepOled, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingRule = null }) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = BgCard
        )
    }
}
