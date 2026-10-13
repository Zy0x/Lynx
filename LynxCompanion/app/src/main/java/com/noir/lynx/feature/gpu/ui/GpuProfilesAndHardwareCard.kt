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
fun GpuQuickProfilesCard(
    activeProfile: String,
    onSelectProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val profiles = listOf(
        Triple("battery", "Hemat Daya", "Batas 60% OPP • Hemat Baterai"),
        Triple("balanced", "Seimbang", "Skalabilitas Cerdas • Harian"),
        Triple("esports", "Esports", "Bus On • Low Latency Gaming"),
        Triple("extreme", "Ekstrem", "Lock 100% Clock • Bypass Thermal")
    )

    LynxCard(
        title = "Preset Cepat GPU (One-Click Profiles)",
        subtitle = "Aplikasi harmonis clock, boost, bus memori, dan pipeline render",
        icon = Icons.Default.Speed,
        accentColor = AccentCyan,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            profiles.forEach { (id, title, sub) ->
                val isSelected = activeProfile == id
                val profAccent = when (id) {
                    "battery" -> AccentGreen
                    "balanced" -> AccentCyan
                    "esports" -> AccentOrange
                    else -> AccentRed
                }

                Surface(
                    onClick = { onSelectProfile(id) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) profAccent.copy(alpha = 0.2f) else BgElevated,
                    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) profAccent else BorderGlass),
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) profAccent else TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            softWrap = true,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = sub,
                            color = TextSecondary,
                            fontSize = 8.5.sp,
                            softWrap = true,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
//  ADAPTIVE GPU HARDWARE ACCELERATION CARD (SOC-SPECIFIC)
// ============================================================

@Composable
fun AdaptiveGpuHardwareCard(
    gpu: GpuInfo,
    onSetFeature: (GpuHardwareFeature, String) -> Unit = { _, _ -> },
    onSetBusAlwaysOn: (Boolean) -> Unit = {},
    onSetIdleTimer: (Int) -> Unit = {},
    onSetThermalBypass: (Boolean) -> Unit = {},
    onSetAdrenoPwrLevel: (Int) -> Unit = {},
    onSetAdrenoTzTargetLoad: (Int) -> Unit = {},
    onSetAdrenoForceRail: (Boolean) -> Unit = {},
    onSetFramePacing: (Boolean) -> Unit = {},
    onSetFpsgoUltraRescue: (Boolean) -> Unit = {},
    onSetMaliDvfsMargin: (Int) -> Unit = {},
    onSetMaliCoreMask: (Boolean) -> Unit = {},
    onSetMaliPowerPolicy: (String) -> Unit = {},
    onSetGovernor: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isAdreno = gpu.platform == "adreno"
    val isMtk = gpu.platform == "mali_ged"

    val title = when {
        gpu.activeBackendName.isNotBlank() -> gpu.activeBackendName
        isAdreno -> "Akselerasi Qualcomm Adreno (QTI KGSL)"
        isMtk -> "Akselerasi MediaTek (${gpu.mtkGenType.replaceFirstChar { it.uppercase() }} & FPSGO)"
        else -> "Akselerasi Hardware GPU (${gpu.subArchitecture})"
    }
    val subtitle = "Pemeriksaan node kernel langsung, status perizinan, dan kendali clock"
    val cardAccent = AccentOrange

    LynxCard(
        title = title,
        subtitle = subtitle,
        icon = Icons.Default.Memory,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (gpu.hardwareFeatures.isNotEmpty()) {
                // Header: Capability & Confidence Summary
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "KEMAMPUAN HARDWARE TERVERIFIKASI",
                                color = TextTertiary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            val workingCount = gpu.hardwareFeatures.count { it.accessState == FeatureAccessState.VERIFIED_WORKING }
                            val totalCount = gpu.hardwareFeatures.size
                            Text(
                                text = "$workingCount dari $totalCount Parameter Siap Dikonfigurasi",
                                color = AccentGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        val ratingColor = when (gpu.backendConfidence.rating) {
                            ConfidenceRating.HIGH_CONFIDENCE -> AccentGreen
                            ConfidenceRating.READ_ONLY_LOCK -> AccentOrange
                            ConfidenceRating.UNCERTAIN -> AccentOrange
                            ConfidenceRating.UNAVAILABLE -> TextTertiary
                        }
                        val ratingLabel = when (gpu.backendConfidence.rating) {
                            ConfidenceRating.HIGH_CONFIDENCE -> "Verified HAL"
                            ConfidenceRating.READ_ONLY_LOCK -> "Read-Only"
                            ConfidenceRating.UNCERTAIN -> "Uncertain"
                            ConfidenceRating.UNAVAILABLE -> "Fallback"
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ratingColor.copy(alpha = 0.15f),
                            border = BorderStroke(0.8.dp, ratingColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = ratingLabel,
                                color = ratingColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Dynamically Render Scanned Features Grouped by Category
                val grouped = gpu.hardwareFeatures.groupBy { it.category }.entries.toList()
                grouped.forEachIndexed { catIdx, (category, features) ->
                    if (catIdx > 0) {
                        HorizontalDivider(
                            color = BorderSubtle.copy(alpha = 0.45f),
                            thickness = 0.7.dp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 3.dp, height = 11.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(cardAccent)
                            )
                            Text(
                                text = category.uppercase(),
                                color = TextSecondary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                        }
                        Text(
                            text = "${features.size} Kontrol",
                            color = TextTertiary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    features.forEach { feat ->
                        val isWritable = feat.accessState == FeatureAccessState.VERIFIED_WORKING
                        when (feat.uiType) {
                            FeatureUiType.SWITCH -> {
                                val isChecked = feat.currentValue == "1" || feat.currentValue.equals("true", ignoreCase = true) || feat.currentValue.equals("always_on", ignoreCase = true)
                                LynxSwitch(
                                    label = feat.name,
                                    subLabel = if (isWritable) feat.description else "${feat.description} (Terkunci oleh Kernel)",
                                    checked = isChecked,
                                    enabled = isWritable,
                                    onCheckedChange = { checked ->
                                        val newVal = if (checked) "1" else "0"
                                        onSetFeature(feat, newVal)
                                    }
                                )
                            }
                            FeatureUiType.STEPPER, FeatureUiType.CHOICE -> {
                                Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(feat.name, color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                        if (!isWritable) {
                                            Text("Read-Only", color = AccentOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(feat.description, color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
                                    val useFullWidth = feat.options.size <= 4
                                    Row(
                                        modifier = if (useFullWidth) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        feat.options.forEach { opt ->
                                            val isSel = feat.currentValue == opt
                                            val displayOpt = when (feat.id) {
                                                "mali_power_policy" -> when (opt) {
                                                    "always_on" -> "Always-On (Performa)"
                                                    "coarse_demand" -> "Coarse Demand (Hemat)"
                                                    else -> opt
                                                }
                                                "mtk_dvfs_margin" -> when (opt) {
                                                    "0" -> "0% (Std)"
                                                    else -> "+$opt%"
                                                }
                                                "mtk_ged_boost_level" -> when (opt) {
                                                    "0" -> "0 • Mati"
                                                    "1" -> "1 • Normal"
                                                    "2" -> "2 • Ekstrem"
                                                    else -> opt
                                                }
                                                "adrenoboost_level" -> when (opt) {
                                                    "0" -> "0 • Mati"
                                                    "1" -> "1 • Rendah"
                                                    "2" -> "2 • Sedang"
                                                    "3" -> "3 • Maks"
                                                    else -> opt
                                                }
                                                "adreno_tz_target_load" -> when (opt) {
                                                    "50" -> "50% (Max)"
                                                    "80" -> "80% (Std)"
                                                    else -> "$opt%"
                                                }
                                                "mali_dvfs_period", "devfreq_polling_interval", "adreno_idle_timer" -> "$opt ms"
                                                else -> opt
                                            }
                                            Surface(
                                                onClick = {
                                                    if (isWritable) {
                                                        onSetFeature(feat, opt)
                                                    }
                                                },
                                                enabled = isWritable,
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSel) cardAccent.copy(alpha = 0.2f) else BgElevated,
                                                border = BorderStroke(1.dp, if (isSel) cardAccent else BorderGlass),
                                                modifier = (if (useFullWidth) Modifier.weight(1f) else Modifier).defaultMinSize(minWidth = 54.dp, minHeight = 44.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                                    Text(
                                                        text = displayOpt,
                                                        color = if (isSel) cardAccent else (if (isWritable) TextSecondary else TextTertiary),
                                                        fontSize = 10.sp,
                                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            FeatureUiType.SLIDER -> {
                                Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                                    Text(feat.name, color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${feat.description} (Nilai saat ini: ${feat.currentValue})", color = TextSecondary, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                // Fallback rendering
                if (isAdreno) {
                    LynxSwitch(
                        label = "KGSL Memory Bus Always-On",
                        subLabel = "Kunci jalur DDR bus Adreno tetap aktif mencegah micro-stutter frame drop",
                        checked = gpu.isBusAlwaysOn,
                        onCheckedChange = { onSetBusAlwaysOn(it) }
                    )

                    LynxSwitch(
                        label = "Adreno Force Rail Active",
                        subLabel = "Paksa jalur daya power rail GPU aktif bertenaga selama game",
                        checked = gpu.adrenoForceRail,
                        onCheckedChange = { onSetAdrenoForceRail(it) }
                    )

                    LynxSwitch(
                        label = "Bypass GPU Thermal Throttling",
                        subLabel = "Abaikan batas pwrlevel thermal throttling kernel Qualcomm",
                        checked = gpu.isThrottlingBypassed,
                        onCheckedChange = { onSetThermalBypass(it) }
                    )

                    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                        Text("Adreno Idle Timer (Waktu Tahan Clock)", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("Mencegah penurunan clock GPU tiba-tiba saat jeda frame game", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Pair(20, "20ms (Agresif)"),
                                Pair(40, "40ms (Responsif)"),
                                Pair(64, "64ms (Bawaan)"),
                                Pair(80, "80ms (Smooth)"),
                                Pair(100, "100ms (Gaming)")
                            ).forEach { (ms, label) ->
                                val isSel = gpu.idleTimerMs == ms
                                Surface(
                                    onClick = { onSetIdleTimer(ms) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) AccentOrange.copy(alpha = 0.2f) else BgElevated,
                                    border = BorderStroke(1.dp, if (isSel) AccentOrange else BorderGlass),
                                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Text(label, color = if (isSel) AccentOrange else TextSecondary, fontSize = 10.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                        Text("Trustzone TZ Governor Target Load", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("Ambang batas beban sebelum GPU melompat ke frekuensi lebih tinggi", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Pair(50, "50% (Hardcore)"),
                                Pair(60, "60% (Gaming)"),
                                Pair(70, "70% (Responsif)"),
                                Pair(80, "80% (Bawaan)")
                            ).forEach { (load, label) ->
                                val isSel = gpu.adrenoTzTargetLoad == load
                                Surface(
                                    onClick = { onSetAdrenoTzTargetLoad(load) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) AccentOrange.copy(alpha = 0.2f) else BgElevated,
                                    border = BorderStroke(1.dp, if (isSel) AccentOrange else BorderGlass),
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                                        Text(label, color = if (isSel) AccentOrange else TextSecondary, fontSize = 10.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                } else if (isMtk) {
                    LynxSwitch(
                        label = "MediaTek FPSGO Frame Pacing",
                        subLabel = "Sinkronisasi buffer frame real-time untuk frametime gameplay datar",
                        checked = gpu.isFramePacingActive,
                        onCheckedChange = { onSetFramePacing(it) }
                    )

                    LynxSwitch(
                        label = "FPSGO Ultra Rescue (Penyelamat Frame)",
                        subLabel = "Akselerasi frekuensi instan jika frame terancam drop di bawah target FPS",
                        checked = gpu.isFpsgoUltraRescue,
                        onCheckedChange = { onSetFpsgoUltraRescue(it) }
                    )

                    LynxSwitch(
                        label = "Bypass GPU Thermal Throttling",
                        subLabel = "Override pembatasan throttling thermal daemon MediaTek saat beban berat",
                        checked = gpu.isThrottlingBypassed,
                        onCheckedChange = { onSetThermalBypass(it) }
                    )

                    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                        Text("Mali GED DVFS Margin (Sensitivitas Boost)", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("Menaikkan sensitivitas GPU agar instan melompat ke clock tinggi", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Pair(0, "0% (Bawaan)"),
                                Pair(10, "+10% (Responsif)"),
                                Pair(20, "+20% (Gaming)"),
                                Pair(30, "+30% (Agresif)")
                            ).forEach { (margin, label) ->
                                val isSel = gpu.maliDvfsMargin == margin
                                Surface(
                                    onClick = { onSetMaliDvfsMargin(margin) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) AccentOrange.copy(alpha = 0.2f) else BgElevated,
                                    border = BorderStroke(1.dp, if (isSel) AccentOrange else BorderGlass),
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                                        Text(label, color = if (isSel) AccentOrange else TextSecondary, fontSize = 10.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LynxSwitch(
                        label = "Unmask Semua Shader Cores",
                        subLabel = "Paksa seluruh unit komputasi shader core aktif tanpa pemadaman termal OEM",
                        checked = gpu.isMaliAllCoresActive,
                        onCheckedChange = { onSetMaliCoreMask(it) }
                    )

                    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                        Text("Kebijakan Daya GPU (Power Policy)", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("Always-on meniadakan latensi bangun/tidur antara frame rendering", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Pair("always_on", "Always-On (Performa Maksimum)"),
                                Pair("coarse_demand", "Coarse Demand (Hemat Daya)")
                            ).forEach { (pol, label) ->
                                val isSel = gpu.maliPowerPolicy == pol
                                Surface(
                                    onClick = { onSetMaliPowerPolicy(pol) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) AccentOrange.copy(alpha = 0.2f) else BgElevated,
                                    border = BorderStroke(1.dp, if (isSel) AccentOrange else BorderGlass),
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                        Text(label, color = if (isSel) AccentOrange else TextSecondary, fontSize = 10.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }

                    if (gpu.availableGovernors.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                            Text("Devfreq Governor", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                gpu.availableGovernors.forEach { gov ->
                                    val isSel = gpu.currentGovernor.trim() == gov.trim()
                                    Surface(
                                        onClick = { onSetGovernor(gov) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSel) AccentOrange.copy(alpha = 0.2f) else BgElevated,
                                        border = BorderStroke(1.dp, if (isSel) AccentOrange else BorderGlass),
                                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                            Text(gov, color = if (isSel) AccentOrange else TextSecondary, fontSize = 10.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
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
}

// ============================================================
//  DISPLAY CALIBRATION CARD (UNIVERSAL RGB & KCAL)
// ============================================================

