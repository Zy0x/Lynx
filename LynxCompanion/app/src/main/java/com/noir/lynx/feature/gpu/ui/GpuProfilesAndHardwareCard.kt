package com.noir.lynx.feature.gpu.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.ui.*

private data class GpuCustomParamConfig(
    val feat: GpuHardwareFeature,
    val unit: String,
    val min: Int,
    val max: Int,
    val step: Int = 1
)

@Composable
fun AdaptiveGpuHardwareCard(
    gpu: GpuInfo,
    silicon: GpuSiliconInfo? = null,
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
    onSetSptpPc: (Boolean) -> Unit = {},
    onSetSkipSaveRestore: (Boolean) -> Unit = {},
    onSetPreemptLevel: (Int) -> Unit = {},
    onSetWakeNice: (Int) -> Unit = {},
    onSetMinPwrLevel: (Int) -> Unit = {},
    onSetGpuLlcSlice: (Boolean) -> Unit = {},
    wrapInCard: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isAdreno = gpu.platform == "adreno"
    val isMtk = gpu.platform == "mali_ged"
    var customDialogConfig by remember { mutableStateOf<GpuCustomParamConfig?>(null) }
    var showWakeNiceDialog by remember { mutableStateOf(false) }
    var showMinPwrDialog by remember { mutableStateOf(false) }
    var showMaxPwrDialog by remember { mutableStateOf(false) }

    val title = when {
        gpu.activeBackendName.isNotBlank() -> gpu.activeBackendName
        isAdreno -> "Akselerasi Qualcomm Adreno"
        isMtk -> "Akselerasi MediaTek (${gpu.mtkGenType.replaceFirstChar { it.uppercase() }})"
        else -> "Akselerasi Hardware GPU"
    }
    val subtitle = "Optimasi silikon hardware GPU & respon sentuhan"
    val cardAccent = AccentOrange

    val cardContent = @Composable {
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
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = cardAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = title.uppercase(),
                                    color = TextPrimary,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.4.sp
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            val workingCount = gpu.hardwareFeatures.count { it.accessState == FeatureAccessState.VERIFIED_WORKING }
                            val totalCount = gpu.hardwareFeatures.size
                            Text(
                                text = stringResource(R.string.gpu_configurable_parameters, workingCount, totalCount),
                                color = AccentGreen,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium
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
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                            )
                        }
                    }
                }

                // Dedicated Adreno Silicon Kernel Controls (Consolidated from GpuDeepSiliconCard)
                if (isAdreno && silicon != null) {
                    HorizontalDivider(
                        color = BorderSubtle.copy(alpha = 0.45f),
                        thickness = 0.7.dp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
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
                                text = "SCHEDULING & POWER COLLAPSE",
                                color = TextSecondary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                        }
                        Text(
                            text = "Silicon Direct",
                            color = AccentCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 1. Preemption Granularity (Level 0, 1, 2)
                    PreemptionLevelSelector(
                        preemptLevel = silicon.preemptLevel,
                        isSupported = silicon.isPreemptLevelSupported,
                        onSetPreemptLevel = onSetPreemptLevel
                    )

                    // 2. Power Level Bracket (Ceiling & Floor)
                    PwrLevelFloorCeilingRow(
                        minPwrLevel = silicon.minPwrLevel,
                        maxPwrLevel = silicon.maxPwrLevel,
                        numPwrLevels = silicon.numPwrLevels,
                        onOpenMinPwrDialog = { showMinPwrDialog = true },
                        onOpenMaxPwrDialog = { showMaxPwrDialog = true }
                    )

                    // 3. Sleep States, SPTP PC, Skip Save/Restore, LLC, Wake Nice
                    SiliconSleepAndRegistersSection(
                        silicon = silicon,
                        onSetSptpPc = onSetSptpPc,
                        onSetSkipSaveRestore = onSetSkipSaveRestore,
                        onSetWakeNice = onSetWakeNice,
                        onSetGpuLlcSlice = onSetGpuLlcSlice,
                        onOpenWakeNiceDialog = { showWakeNiceDialog = true }
                    )

                    // 4. Silicon Impact & Safeguard Accordion
                    SiliconImpactAccordion()
                }

                // Dynamically Render Scanned Features Grouped by Category (Filtered from Duplicates)
                val grouped = gpu.hardwareFeatures
                    .filterNot { it.id == "adreno_preemption" || it.id == "adreno_max_pwrlevel" }
                    .groupBy { it.category }
                    .entries
                    .toList()
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
                                val customNumConfig = when (feat.id) {
                                    "adreno_idle_timer" -> GpuCustomParamConfig(feat, "ms", 1, 500, 5)
                                    "adreno_polling_interval", "devfreq_polling_interval" -> GpuCustomParamConfig(feat, "ms", 5, 500, 5)
                                    "adreno_pmqos_latency" -> GpuCustomParamConfig(feat, "µs", 0, 5000, 10)
                                    "adreno_ft_long_ib" -> GpuCustomParamConfig(feat, "ms", 500, 10000, 250)
                                    "mali_dvfs_period" -> GpuCustomParamConfig(feat, "ms", 5, 250, 5)
                                    "adreno_tz_target_load" -> GpuCustomParamConfig(feat, "%", 10, 95, 5)
                                    else -> null
                                }
                                val totalCount = feat.options.size + (if (customNumConfig != null) 1 else 0)
                                val useFullWidth = totalCount <= 4

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
                                                "adreno_pmqos_latency" -> if (opt == "0") "0 µs (Instan)" else "$opt µs"
                                                "adreno_ft_long_ib" -> "${opt.toIntOrNull()?.div(1000) ?: opt}s"
                                                "mali_dvfs_period", "devfreq_polling_interval", "adreno_idle_timer", "adreno_polling_interval" -> "$opt ms"
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

                                        if (customNumConfig != null) {
                                            val isPresetMatched = feat.options.contains(feat.currentValue)
                                            val isCustomActive = !isPresetMatched && feat.currentValue.isNotEmpty()
                                            val customText = if (isCustomActive) "✎ ${feat.currentValue} ${customNumConfig.unit}" else "✎ Kustom"

                                            Surface(
                                                onClick = {
                                                    if (isWritable) {
                                                        customDialogConfig = customNumConfig
                                                    }
                                                },
                                                enabled = isWritable,
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isCustomActive) cardAccent.copy(alpha = 0.25f) else BgElevated,
                                                border = BorderStroke(1.dp, if (isCustomActive) cardAccent else BorderGlass),
                                                modifier = (if (useFullWidth) Modifier.weight(1f) else Modifier).defaultMinSize(minWidth = 54.dp, minHeight = 44.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                                    Text(
                                                        text = customText,
                                                        color = if (isCustomActive) cardAccent else (if (isWritable) TextSecondary else TextTertiary),
                                                        fontSize = 10.sp,
                                                        fontWeight = if (isCustomActive) FontWeight.Bold else FontWeight.Normal,
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

    if (wrapInCard) {
        LynxCard(
            title = title,
            subtitle = subtitle,
            icon = Icons.Default.Memory,
            accentColor = cardAccent,
            modifier = modifier
        ) {
            cardContent()
        }
    } else {
        Box(modifier = modifier.fillMaxWidth()) {
            cardContent()
        }
    }

    customDialogConfig?.let { cfg ->
        GpuCustomNumberDialog(
            title = cfg.feat.name,
            description = cfg.feat.description,
            currentValue = cfg.feat.currentValue.toIntOrNull() ?: cfg.min,
            unit = cfg.unit,
            minVal = cfg.min,
            maxVal = cfg.max,
            step = cfg.step,
            accentColor = cardAccent,
            onConfirm = { newVal ->
                onSetFeature(cfg.feat, newVal.toString())
            },
            onDismiss = { customDialogConfig = null }
        )
    }

    SiliconCustomDialogs(
        silicon = silicon,
        showWakeNiceDialog = showWakeNiceDialog,
        showMinPwrDialog = showMinPwrDialog,
        showMaxPwrDialog = showMaxPwrDialog,
        onSetWakeNice = onSetWakeNice,
        onSetMinPwrLevel = onSetMinPwrLevel,
        onSetAdrenoPwrLevel = onSetAdrenoPwrLevel,
        onDismissWakeNice = { showWakeNiceDialog = false },
        onDismissMinPwr = { showMinPwrDialog = false },
        onDismissMaxPwr = { showMaxPwrDialog = false }
    )
}

