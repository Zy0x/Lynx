package com.noir.lynx.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.noir.lynx.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// ============================================================
//  CATEGORY 1: CPU & GOVERNOR SUBSCREEN
// ============================================================

@Composable
fun TuningCpuCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clusters = uiState.clusters
    val schedInfo = uiState.schedulerInfo
    var pendingResetSection by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Auto refresh CPU data on screen launch
    LaunchedEffect(Unit) {
        viewModel.refreshCpuCores()
        viewModel.refreshClusters()
        viewModel.refreshSchedulerInfo(context)
        viewModel.loadCpuSetsInfo(context)
        viewModel.loadCpuIdleInfo(context)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (uiState.selectedCpuTab) {
            0 -> {
                // TAB 0: PERFORMANCE (Consumer Level 1 & Level 2)
                CpuDashboardHeroCard(uiState = uiState)

                // Smart Recommendation Banner (Non-intrusive & Dismissible)
                val rec = uiState.cpuRecommendation
                if (rec != null && !uiState.isCpuRecommendationDismissed) {
                    CpuSmartRecommendationBanner(
                        recommendation = rec,
                        onApplyRecommendation = {
                            viewModel.applyComprehensiveCpuProfile("balanced", context)
                            viewModel.dismissCpuRecommendation()
                        },
                        onDismiss = { viewModel.dismissCpuRecommendation() }
                    )
                }

                // Unified Performance Card (1-Click Pills & Advanced Expansion)
                CpuUnifiedPerformanceCard(
                    uiState = uiState,
                    clusters = clusters,
                    governorTunables = uiState.governorTunables,
                    onApplyProfile = { profile -> viewModel.applyComprehensiveCpuProfile(profile, context) },
                    onFreqChange = { policyId, min, max -> viewModel.setClusterFrequency(policyId, min, max) },
                    onGovChange = { policyId, gov -> viewModel.setClusterGovernor(policyId, gov) },
                    onLoadTunables = { policyId, gov -> viewModel.loadGovernorTunables(policyId, gov) },
                    onTunableChange = { policyId, gov, k, v -> viewModel.setGovernorTunable(policyId, gov, k, v) },
                    onLockToggle = { policyId, lock, min, max -> viewModel.setClusterLock(policyId, lock, min, max) },
                    schedInfo = schedInfo,
                    onPpmPolicyChange = { idx, en -> viewModel.setPpmPolicy(idx, en, context) },
                    onQcomTouchboostChange = { en -> viewModel.setQcomTouchboost(en, context) },
                    onQcomInputBoostChange = { freq, ms -> viewModel.setQcomInputBoost(freq, ms, context) }
                )
            }
            1 -> {
                // TAB 1: SYSTEM (Level 1 Presets & Level 2 Detailed Controls)
                CpuUnifiedSystemCard(
                    context = context,
                    uiState = uiState,
                    onApplySystemPreset = { preset -> viewModel.applySchedulerPreset(preset, context) },
                    onApplyCpuSetPreset = { preset -> viewModel.applyCpuSetPreset(preset, context) },
                    onToggleCpuSetCore = { group, coreId -> viewModel.toggleCpuSetCore(group, coreId, context) },
                    onSetCpuSetApplyOnBoot = { enabled -> viewModel.setCpuSetApplyOnBoot(enabled, context) },
                    onApplyCpuIdlePreset = { preset -> viewModel.applyCpuIdlePreset(preset, context) },
                    onSetCoreParkingMode = { mode -> viewModel.setCoreParkingMode(mode, 8, context) },
                    onToggleCStateDisabled = { stateIndex, disabled -> viewModel.setCpuIdleStateDisabled(stateIndex, disabled, context) },
                    onArmPllModeChange = { enabled -> viewModel.setArmPllMode(enabled) },
                    onSetIdleApplyOnBoot = { enabled -> viewModel.setCpuIdleApplyOnBoot(enabled, context) },
                    onUclampChange = { min, max ->
                        viewModel.setSchedulerTunable("uclamp_min", min.toLong(), context)
                        viewModel.setSchedulerTunable("uclamp_max", max.toLong(), context)
                    },
                    onMigrationChange = { up, down ->
                        viewModel.setSchedulerTunable("sched_upmigrate", up.toLong(), context)
                        viewModel.setSchedulerTunable("sched_downmigrate", down.toLong(), context)
                    },
                    onResetSection = { pendingResetSection = it },
                    onResetToStandardProfile = { viewModel.resetCpuToActiveProfile(context) }
                )
            }
            2 -> {
                // TAB 2: MONITOR (Telemetry & Hardware Specs)
                CpuHardwareMonitorCard(
                    uiState = uiState,
                    onToggleCore = { core -> viewModel.setCpuCoreOnline(core.coreId, !core.isOnline) }
                )
            }
        }

        // Section Reset Confirmation Dialog
        if (pendingResetSection != null) {
            val (sectionKey, sectionTitle) = pendingResetSection!!
            AlertDialog(
                onDismissRequest = { pendingResetSection = null },
                containerColor = BgCard,
                title = { Text("Reset $sectionTitle", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = { Text("Kembalikan seluruh parameter $sectionTitle ke pengaturan default OEM?", fontSize = 12.5.sp, color = TextSecondary) },
                confirmButton = {
                    Button(
                        onClick = {
                            when (sectionKey) {
                                "cpuset" -> viewModel.resetCpuSetsToOem(context)
                                "cpuidle" -> viewModel.resetCpuIdleToOem(context)
                                "scheduler" -> viewModel.resetSchedulerTunablesToOem(context)
                            }
                            pendingResetSection = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Reset ke Default", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingResetSection = null }) {
                        Text("Batal", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            )
        }
    }
}

// ============================================================
//  CATEGORY 2: GPU & DISPLAY SUBSCREEN
// ============================================================

@Composable
fun TuningGpuCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state = uiState.state
    val gpu = uiState.gpuInfo
    val graphics = uiState.graphicsHwui

    // Auto refresh data on load
    LaunchedEffect(Unit) {
        viewModel.refreshGpuInfo()
        viewModel.refreshGraphicsHwui()
        viewModel.refreshDisplayRefreshRate()
        viewModel.refreshDisplayCalibration()
        viewModel.refreshGraphicsCapabilities()
        viewModel.refreshDisplayPipeline()
        viewModel.refreshColorConflict()
        viewModel.refreshSavedLabSessions(context)
        viewModel.loadPerAppGraphicsRules(context)
        viewModel.refreshInstalledApps()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (uiState.selectedGpuTab) {
            0 -> {
                // ── TAB 0: TUNING ──
                // 1. One-Click GPU Quick Profiles (Harmonic Presets)
                GpuQuickProfilesCard(
                    activeProfile = gpu.activeProfile,
                    onSelectProfile = { viewModel.applyGpuProfile(it, context) }
                )

                // 2. Master GPU Tuner & Telemetri Card (Dual-Pill Clock, Lock, & Waveform)
                GpuMasterTunerCard(
                    gpu = gpu,
                    onSetFreq = { minMhz, maxMhz -> viewModel.setGpuFreq(minMhz, maxMhz) },
                    onSetLock = { locked -> viewModel.setGpuLock(locked) }
                )

                // 2b. Adaptive Hardware Acceleration Card (SoC-Specific Engine)
                AdaptiveGpuHardwareCard(
                    gpu = gpu,
                    onSetFeature = { feature, targetVal -> viewModel.setGpuFeature(feature, targetVal) },
                    onSetBusAlwaysOn = { viewModel.setGpuBusAlwaysOn(it) },
                    onSetIdleTimer = { viewModel.setGpuIdleTimer(it) },
                    onSetThermalBypass = { viewModel.setGpuThermalBypass(it) },
                    onSetAdrenoPwrLevel = { viewModel.setAdrenoPwrLevel(it) },
                    onSetAdrenoTzTargetLoad = { viewModel.setAdrenoTzTargetLoad(it) },
                    onSetAdrenoForceRail = { viewModel.setAdrenoForceRail(it) },
                    onSetFramePacing = { viewModel.setGpuFramePacing(it) },
                    onSetFpsgoUltraRescue = { viewModel.setFpsgoUltraRescue(it) },
                    onSetMaliDvfsMargin = { viewModel.setMaliDvfsMargin(it) },
                    onSetMaliCoreMask = { viewModel.setMaliCoreMask(it) },
                    onSetMaliPowerPolicy = { viewModel.setMaliPowerPolicy(it) },
                    onSetGovernor = { viewModel.setGpuGovernor(it) }
                )

                // 3. Graphics Driver & HWUI Engine Card (Game Driver, SkiaVK, Latch, Backpressure, Shader Cache)
                GraphicsDriverHwuiCard(
                    graphics = graphics,
                    onSetGameDriver = { mode -> viewModel.setUpdatableGameDriver(mode) },
                    onSetRenderer = { backend -> viewModel.setHwuiRenderer(backend) },
                    onSetLatch = { latch -> viewModel.setSurfaceFlingerLatch(latch) },
                    onSetDisableBackpressure = { viewModel.setSurfaceFlingerDisableBackpressure(it) },
                    onSetEarlyPhase = { viewModel.setSurfaceFlingerEarlyPhase(it) },
                    onSetMsaa = { msaa -> viewModel.setForceMsaa(msaa) },
                    onSetOemShield = { shield -> viewModel.setOemThrottlerShield(shield) },
                    onClearShaderCache = { viewModel.clearShaderCache(context) },
                    onPrewarmShaderCache = { viewModel.prewarmShaderCache(context) }
                )

                // 2b. Manajemen Rendering Per-Aplikasi (Game Driver Hub)
                PerAppGraphicsHubCard(
                    rules = uiState.perAppGraphicsRules,
                    installedApps = uiState.installedAppList,
                    supportedRates = uiState.supportedRefreshRates.ifEmpty { listOf(60, 90, 120) },
                    isAngleSupported = graphics.isAngleSupported,
                    onSaveRule = { rule -> viewModel.savePerAppGraphicsRule(rule, context) },
                    onDeleteRule = { pkg -> viewModel.deletePerAppGraphicsRule(pkg, context) }
                )

                // 3. Display Refresh Rate & Touch Card (4-Way Chips: Auto + 60/90/120Hz)
                DisplayRefreshRateTouchCard(
                    currentHz = if (uiState.displayRefreshRate > 0) uiState.displayRefreshRate else 60,
                    isAuto = uiState.isAutoRefreshRate,
                    supportedRates = uiState.supportedRefreshRates.ifEmpty { listOf(60, 90, 120) },
                    touchBoost = state.displayTouch.touchboost,
                    dcDimmingSupported = graphics.isDcDimmingSupported,
                    dcDimmingEnabled = graphics.dcDimmingEnabled,
                    onSetRefreshRate = { hz, isAuto -> viewModel.setDisplayRefreshRate(hz, isAuto) },
                    onSetTouchBoost = { viewModel.setTouchboost(it) },
                    onSetDcDimming = { viewModel.setDcDimming(it) }
                )

                // 4. Color Management Card (SurfaceFlinger Matrix 1015, D65 White Point, & KCAL)
                ColorManagementCard(
                    displayCalibration = uiState.displayCalibration,
                    colorProfile = uiState.colorMatrixProfile,
                    isCalibrationEnabled = uiState.isColorCalibrationEnabled,
                    colorConflict = uiState.colorConflictWarning,
                    onToggleCalibration = { viewModel.setColorCalibrationEnabled(it) },
                    onApplyProfile = { viewModel.applyColorProfile(it) },
                    onResetProfile = { viewModel.resetColorProfile() },
                    onSetUniversalColor = { r, g, b -> viewModel.setUniversalColor(r, g, b) },
                    onSetKcal = { en, r, g, b, sat, v, c, h ->
                        viewModel.setKcalParams(en, r, g, b, sat, v, c, h)
                    },
                    onSetHbm = { viewModel.setHbmEnabled(it) }
                )
            }
            1 -> {
                // ── TAB 1: LAB (PERFORMANCE LAB & IN-GAME OSD) ──
                FloatingGameHudCard(
                    isHudRunning = uiState.isGameHudActive,
                    hudMode = uiState.hudMode,
                    hudStyle = uiState.hudStyle,
                    onToggleHud = { viewModel.toggleGameHud(context, !uiState.isGameHudActive) },
                    onSetMode = { viewModel.setHudMode(context, it) },
                    onSetStyle = { viewModel.setHudStyle(context, it) }
                )

                LabRecordCard(
                    isRecording = uiState.isLabRecording,
                    onStartRecording = { viewModel.startLabRecording(it) },
                    onStopRecording = { viewModel.stopLabRecording(context) }
                )

                uiState.lastLabReport?.let { report ->
                    SessionSummaryCard(
                        report = report,
                        onExportCsv = { viewModel.exportLabReport(context, report) }
                    )
                    DropAnalysisCard(report = report)
                }

                SessionHistoryList(
                    sessions = uiState.savedLabSessions,
                    onSelectSession = { session ->
                        viewModel.selectLabReport(session)
                    }
                )
            }
            2 -> {
                // ── TAB 2: INFO (UNIFIED HARDWARE & COMPOSITOR DIAGNOSTICS) ──
                GpuHardwareInfoDashboard(
                    caps = uiState.graphicsCapabilities,
                    pipeline = uiState.displayPipeline,
                    currentHz = if (uiState.displayRefreshRate > 0) uiState.displayRefreshRate else 60,
                    onRefresh = {
                        viewModel.refreshGraphicsCapabilities()
                        viewModel.refreshDisplayPipeline()
                    }
                )
            }
        }
    }
}

// ============================================================
//  CATEGORY 3: THERMAL & ANTI-THROTTLING SUBSCREEN
// ============================================================

@Composable
fun TuningThermalCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state
    var activeTweakConfig by remember { mutableStateOf<TweakConfig?>(null) }
    var selectedSensorFilter by remember { mutableStateOf("Semua") }

    // Auto-refresh thermal zones on screen load
    LaunchedEffect(Unit) {
        viewModel.refreshThermalZones()
    }

    val validZones = remember(uiState.thermalZones) {
        uiState.thermalZones.filter { it.tempC in 15f..115f }
    }

    val maxTemp = remember(validZones) {
        validZones.maxOfOrNull { it.tempC } ?: (uiState.telemetry?.temp?.toFloat() ?: 0f)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── 1. Hero Status Card (Live Thermal State & Metrics) ──
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = BgCard,
            border = BorderStroke(1.dp, if (state.thermal.fullBypass) AccentRed.copy(alpha = 0.5f) else BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (state.thermal.fullBypass) AccentRedDim else AccentOrangeDim),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = if (state.thermal.fullBypass) AccentRed else AccentOrange,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Status Termal Sistem",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = (-0.2).sp
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                if (state.thermal.fullBypass) "Mode Bypass Aktif (Unrestricted)"
                                else "Proteksi Termal Aktif (Maks ${state.thermal.customTempLimitC}°C)",
                                color = if (state.thermal.fullBypass) AccentRed else TextSecondary,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Temperature Pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            maxTemp >= 60f -> AccentRedDim
                            maxTemp >= 48f -> AccentOrangeDim
                            else -> AccentCyanDim
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                maxTemp >= 60f -> AccentRed.copy(alpha = 0.5f)
                                maxTemp >= 48f -> AccentOrange.copy(alpha = 0.5f)
                                else -> AccentCyan.copy(alpha = 0.5f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Thermostat,
                                contentDescription = null,
                                tint = when {
                                    maxTemp >= 60f -> AccentRed
                                    maxTemp >= 48f -> AccentOrange
                                    else -> AccentCyan
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (maxTemp > 0) String.format("%.1f°C", maxTemp) else "--",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 3 Mini Stat Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Chip 1: Trip Point
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("Batas Trip", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${state.thermal.customTempLimitC}°C",
                                color = AccentOrange,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Chip 2: Clock Floor
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("CPU Floor", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${state.overclock.cpuFloorRatio}%",
                                color = AccentCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Chip 3: Anti-Joyose
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("Anti-OEM", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (state.oemNeutralizer.joyoseNeutralize) "Aktif" else "Off",
                                color = if (state.oemNeutralizer.joyoseNeutralize) AccentGreen else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ── 2. Matriks Sensor Termal Hardware (Live Radar) ──────
        LynxCard(
            title = "Matriks Sensor Termal Hardware",
            icon = Icons.Default.Thermostat,
            accentColor = AccentRed
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${validZones.size} Sensor Terdeteksi",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp
                )
                IconButton(
                    onClick = { viewModel.refreshThermalZones() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Muat Ulang Sensor",
                        tint = AccentRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Category Filter Chips
            val filters = listOf("Semua", "SoC / AP", "CPU", "Baterai", "Lainnya")
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filters) { f ->
                    val isSelected = selectedSensorFilter == f
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) AccentRedDim else BgElevated,
                        border = BorderStroke(0.8.dp, if (isSelected) AccentRed else BorderSubtle),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedSensorFilter = f }
                    ) {
                        Text(
                            text = f,
                            color = if (isSelected) AccentRed else TextSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Filtered sensor list
            val filteredZones = remember(validZones, selectedSensorFilter) {
                when (selectedSensorFilter) {
                    "SoC / AP" -> validZones.filter {
                        val t = it.type.lowercase()
                        t.contains("ap") || t.contains("soc") || t.contains("tsens")
                    }
                    "CPU" -> validZones.filter {
                        val t = it.type.lowercase()
                        t.contains("cpu") || t.contains("cluster")
                    }
                    "Baterai" -> validZones.filter {
                        val t = it.type.lowercase()
                        t.contains("batt") || t.contains("chg") || t.contains("charger") || t.contains("bms")
                    }
                    "Lainnya" -> validZones.filter {
                        val t = it.type.lowercase()
                        !t.contains("ap") && !t.contains("soc") && !t.contains("cpu") && !t.contains("batt") && !t.contains("chg")
                    }
                    else -> validZones
                }.ifEmpty { validZones }
            }

            if (filteredZones.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Tidak ada sensor termal terdeteksi", color = TextSecondary, fontSize = 11.5.sp)
                }
            } else {
                val chunkedZones = filteredZones.take(12).chunked(2)
                chunkedZones.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { zone ->
                            val tempColor = when {
                                zone.tempC >= 60f -> AccentRed
                                zone.tempC >= 48f -> AccentOrange
                                else -> AccentGreen
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BgElevated,
                                border = BorderStroke(1.dp, tempColor.copy(alpha = 0.35f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f).padding(end = 6.dp)) {
                                        val cleanType = zone.type
                                            .replace("mtkts", "")
                                            .replace("tsens_tz_sensor", "sensor_")
                                            .uppercase()
                                        Text(cleanType, color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(zone.type, color = TextSecondary.copy(alpha = 0.6f), fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(
                                        String.format("%.1f°C", zone.tempC),
                                        color = tempColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // ── 3. Mesin Anti-Throttling & Trip Points ──────────────
        LynxCard(
            title = "Mesin Anti-Throttling & Trip Points",
            icon = Icons.Default.Speed,
            accentColor = AccentRed
        ) {
            // Full Thermal Bypass switch
            LynxSwitch(
                label = "Full Thermal Bypass (Unrestricted)",
                subLabel = "Nonaktifkan pembatasan termal kernel & thermal-engine secara menyeluruh. Direkomendasikan menggunakan pendingin aktif (phone cooler).",
                checked = state.thermal.fullBypass,
                onCheckedChange = { viewModel.setThermalBypass(it) },
            )

            // Warning banner when Full Thermal Bypass is ON
            if (state.thermal.fullBypass) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentRedDim,
                    border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = AccentRed, modifier = Modifier.size(16.dp))
                        Text(
                            "Perhatian: Full Thermal Bypass menonaktifkan mekanisme pencegahan panas OEM. Pastikan sirkulasi udara baik atau gunakan cooler eksternal!",
                            color = AccentRed,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // Batas Suhu Thermal Custom slider tile
            var tempLimitValue by remember {
                mutableFloatStateOf(state.thermal.customTempLimitC.toFloat())
            }
            LaunchedEffect(state.thermal.customTempLimitC) {
                if (activeTweakConfig?.id != "temp_limit") {
                    tempLimitValue = state.thermal.customTempLimitC.toFloat()
                }
            }
            LynxTweakTile(
                title = "Batas Suhu Thermal Custom (Trip Point)",
                subtitle = "Ambang batas temperatur trip point sebelum thermal daemon melakukan mitigasi",
                displayValue = "${tempLimitValue.toInt()}°C",
                onClick = {
                    activeTweakConfig = TweakConfig(
                        id = "temp_limit",
                        title = "Batas Suhu Thermal Custom",
                        category = "Termal & Anti-Throttling",
                        description = "Ambang batas temperatur trip point termal SoC sebelum thermal daemon mengambil tindakan perlindungan atau pemotongan performa clock.",
                        currentValue = tempLimitValue,
                        defaultValue = 50f,
                        valueRange = 45f..60f,
                        steps = 15,
                        formatDisplay = { v -> "${v.toInt()}°C" },
                        guideNote = "• Gaming/Kompetitif: 55-60°C (Toleransi tinggi, disarankan pakai cooler)\n• Seimbang: 50°C (Standar harian aman)\n• Sejuk/Hemat: 45°C (Perangkat tetap sejuk, hemat daya)",
                        statusInfo = if (tempLimitValue >= 55f) "Suhu Tinggi (Cooler Disarankan)" else "Suhu Aman",
                        onApply = { v ->
                            tempLimitValue = v
                            viewModel.setCustomTempLimit(v.toInt())
                        }
                    )
                }
            )

            // CPU Anti-Throttling switch
            LynxSwitch(
                label = "CPU Anti-Throttling (Max Clock Lock)",
                subLabel = "Kunci frekuensi maksimum CPU ke batas pabrik tertinggi tanpa pemotongan thermal saat beban tinggi",
                checked = state.overclock.enabled,
                onCheckedChange = { viewModel.setOverclockEnabled(it) },
            )

            // Ambang Bawah Frekuensi CPU (Clock Floor) slider tile
            var floorValue by remember {
                mutableFloatStateOf(state.overclock.cpuFloorRatio.toFloat())
            }
            LaunchedEffect(state.overclock.cpuFloorRatio) {
                if (activeTweakConfig?.id != "cpu_floor") {
                    floorValue = state.overclock.cpuFloorRatio.toFloat()
                }
            }
            LynxTweakTile(
                title = "Ambang Bawah Frekuensi CPU (Clock Floor)",
                subtitle = "Menahan frekuensi CPU agar tidak drop di bawah persentase ini saat game aktif",
                displayValue = "${floorValue.toInt()}%",
                onClick = {
                    activeTweakConfig = TweakConfig(
                        id = "cpu_floor",
                        title = "Ambang Bawah Frekuensi CPU (Clock Floor)",
                        category = "Termal & Anti-Throttling",
                        description = "Menahan frekuensi CPU agar tidak turun di bawah persentase ini saat aplikasi atau game sedang berjalan di foreground.",
                        currentValue = floorValue,
                        defaultValue = 60f,
                        valueRange = 60f..100f,
                        steps = 8,
                        formatDisplay = { v -> "${v.toInt()}%" },
                        guideNote = "• Gaming Berat: 85% (Tahan clock tinggi saat frame drop)\n• Seimbang: 70% (Standar gaming santai)\n• Efisiensi: 60% (Batas aman efisiensi daya)",
                        statusInfo = if (floorValue >= 80f) "Agresif" else "Standar",
                        onApply = { v ->
                            floorValue = v
                            viewModel.setCpuFloorRatio(v.toInt())
                        }
                    )
                }
            )
        }

        // ── 4. Netralisasi Daemon Termal OEM & Framework ────────
        LynxCard(
            title = "Netralisasi Daemon Termal OEM",
            icon = Icons.Default.Shield,
            accentColor = AccentOrange
        ) {
            LynxSwitch(
                label = "Netralkan Joyose / GOS Throttling",
                subLabel = "Hentikan pembatasan performa buatan dari OEM (Xiaomi Joyose, Samsung GOS, thermal-engine) yang memotong FPS dan resolusi game secara agresif",
                checked = state.oemNeutralizer.joyoseNeutralize,
                onCheckedChange = { viewModel.setJoyoseNeutralize(it) },
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                    Text(
                        "Daemon OEM seperti Xiaomi Joyose sering membatasi refresh rate ke 60Hz dan menurunkan resolusi saat suhu mencapai 40°C. Menetralkan daemon mengembalikan kendali grafis penuh ke pengguna.",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // ── 5. Preset Termal Cepat (Quick Profiles) ─────────────
        LynxCard(
            title = "Preset Termal Cepat",
            icon = Icons.Default.Tune,
            accentColor = AccentCyan
        ) {
            Text(
                "Pilih konfigurasi termal instan sesuai kebutuhan penggunaan:",
                color = TextSecondary,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val presets = listOf(
                ThermalPresetItem(
                    name = "Sejuk & Harian",
                    description = "Trip 45°C • Floor 60% • Proteksi OEM Penuh",
                    tempLimit = 45,
                    cpuFloor = 60,
                    bypass = false,
                    antiThrottle = false,
                    joyose = false,
                    accent = AccentGreen
                ),
                ThermalPresetItem(
                    name = "Seimbang (Gaming)",
                    description = "Trip 50°C • Floor 70% • Anti-Joyose Aktif",
                    tempLimit = 50,
                    cpuFloor = 70,
                    bypass = false,
                    antiThrottle = true,
                    joyose = true,
                    accent = AccentCyan
                ),
                ThermalPresetItem(
                    name = "Performa Kompetitif",
                    description = "Trip 55°C • Floor 80% • Kunci Clock Maksimal",
                    tempLimit = 55,
                    cpuFloor = 80,
                    bypass = false,
                    antiThrottle = true,
                    joyose = true,
                    accent = AccentOrange
                ),
                ThermalPresetItem(
                    name = "Ekstrem (Unrestricted)",
                    description = "Trip 60°C • Floor 85% • Full Thermal Bypass (Cooler Wajib)",
                    tempLimit = 60,
                    cpuFloor = 85,
                    bypass = true,
                    antiThrottle = true,
                    joyose = true,
                    accent = AccentRed
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { p ->
                    val isActive = if (p.bypass) {
                        state.thermal.fullBypass && state.thermal.customTempLimitC >= 58
                    } else {
                        !state.thermal.fullBypass && state.thermal.customTempLimitC == p.tempLimit
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isActive) p.accent.copy(alpha = 0.12f) else BgElevated,
                        border = BorderStroke(1.dp, if (isActive) p.accent else BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.setCustomTempLimit(p.tempLimit)
                                viewModel.setCpuFloorRatio(p.cpuFloor)
                                viewModel.setThermalBypass(p.bypass)
                                viewModel.setOverclockEnabled(p.antiThrottle)
                                viewModel.setJoyoseNeutralize(p.joyose)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        p.name,
                                        color = if (isActive) p.accent else TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                    if (isActive) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = p.accent.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                "AKTIF",
                                                color = p.accent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(p.description, color = TextSecondary, fontSize = 10.5.sp)
                            }
                            Icon(
                                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = if (isActive) p.accent else TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── Universal Tweak Bottom Sheet Drawer ─────────────────
        if (activeTweakConfig != null) {
            LynxTweakSheet(
                config = activeTweakConfig!!,
                onDismiss = { activeTweakConfig = null }
            )
        }
    }
}

private data class ThermalPresetItem(
    val name: String,
    val description: String,
    val tempLimit: Int,
    val cpuFloor: Int,
    val bypass: Boolean,
    val antiThrottle: Boolean,
    val joyose: Boolean,
    val accent: Color
)

// ============================================================
//  CATEGORY 4: RAM & STORAGE I/O SUBSCREEN
// ============================================================

@Composable
fun TuningMemoryCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Memory & Swappiness Cache Card ──────────────────────
        LynxCard(
            title = "Memory & Swappiness Cache",
            icon = Icons.Default.Storage,
            accentColor = AccentBlue
        ) {
            // Dynamic ZRAM Compression Algorithm Selection
            Text(
                "Algoritma Kompresi ZRAM (Kernel Swap)",
                color = TextPrimary, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp)
            )
            val algos = uiState.availZramCompAlgorithms.ifEmpty { listOf("lz4", "lzo", "zstd") }
            val activeAlgo = uiState.zramCompAlgorithm.ifBlank { "lz4" }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                algos.forEach { algo ->
                    val isSelected = algo == activeAlgo
                    Surface(
                        onClick = { viewModel.setZramCompAlgorithm(algo) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentBlue.copy(alpha = 0.22f) else BgElevated,
                        border = BorderStroke(1.2.dp, if (isSelected) AccentBlue else BorderGlass)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                algo.uppercase(),
                                color = if (isSelected) AccentBlue else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                when (algo) {
                                    "zstd" -> "Rasio Max"
                                    "lz4" -> "Cepat"
                                    "lz4hc" -> "Optimal"
                                    "lzo-rle" -> "Hemat"
                                    else -> "Kompresi"
                                },
                                color = if (isSelected) AccentBlue.copy(alpha = 0.8f) else TextSecondary,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            val ramTotal = (uiState.telemetry?.ramTotalMb?.takeIf { it > 0 } ?: 4096).toFloat()
            val minZram = 512f
            val maxZram = maxOf(4096f, ramTotal)
            val zramSteps = (((maxZram - minZram) / 512f).toInt() - 1).coerceAtLeast(0)

            var zramValue by remember {
                mutableFloatStateOf(state.memory.zramSizeMb.toFloat().coerceIn(minZram, maxZram))
            }
            var isInteractingZram by remember { mutableStateOf(false) }
            var lastZramInteraction by remember { mutableLongStateOf(0L) }

            LaunchedEffect(state.memory.zramSizeMb) {
                if (!isInteractingZram && (System.currentTimeMillis() - lastZramInteraction > 2000L)) {
                    zramValue = state.memory.zramSizeMb.toFloat().coerceIn(minZram, maxZram)
                }
            }

            LynxSlider(
                label = "Ukuran Alokasi ZRAM (Skala RAM Fisik: ${ramTotal.toInt()} MB)",
                value = zramValue,
                onValueChange = {
                    isInteractingZram = true
                    lastZramInteraction = System.currentTimeMillis()
                    zramValue = it
                },
                onValueChangeFinished = {
                    isInteractingZram = false
                    lastZramInteraction = System.currentTimeMillis()
                    viewModel.setZramSizeMb(zramValue.toInt())
                },
                valueRange = minZram..maxZram,
                steps = zramSteps,
                displayValue = "${zramValue.toInt()} MB",
                accentColor = AccentBlue
            )

            // ── VM Tuning Presets ─────────────────────────────
            Text(
                "Preset Rekomendasi Virtual Memory",
                color = TextPrimary, fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
            )
            val vmPresets = listOf(
                Triple("gaming", "Gaming", "Zero-stutter I/O"),
                Triple("balanced", "Balanced", "OEM Default"),
                Triple("battery", "Battery", "Low Writeback")
            )
            val activeVmPreset = uiState.vmAdvanced.activePreset
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                vmPresets.forEach { (presetKey, title, subtitle) ->
                    val isSelected = activeVmPreset == presetKey
                    Surface(
                        onClick = { viewModel.applyVmPreset(presetKey) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentBlue.copy(alpha = 0.22f) else BgElevated,
                        border = BorderStroke(1.2.dp, if (isSelected) AccentBlue else BorderGlass)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                title,
                                color = if (isSelected) AccentBlue else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                subtitle,
                                color = if (isSelected) AccentBlue.copy(alpha = 0.8f) else TextSecondary,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            var swapValue by remember { mutableFloatStateOf(uiState.vmAdvanced.swappiness.toFloat()) }
            var dirtyValue by remember { mutableFloatStateOf(uiState.vmAdvanced.dirtyRatio.toFloat()) }
            var dirtyBgValue by remember { mutableFloatStateOf(uiState.vmAdvanced.dirtyBackgroundRatio.toFloat()) }
            var vfsValue by remember { mutableFloatStateOf(uiState.vmAdvanced.vfsCachePressure.toFloat()) }
            var expireValue by remember { mutableFloatStateOf((uiState.vmAdvanced.dirtyExpireCentisecs / 100).toFloat()) }
            var writebackValue by remember { mutableFloatStateOf((uiState.vmAdvanced.dirtyWritebackCentisecs / 100).toFloat()) }
            var statValue by remember { mutableFloatStateOf(uiState.vmAdvanced.statInterval.toFloat()) }

            var isInteractingVm by remember { mutableStateOf(false) }
            var lastVmInteraction by remember { mutableLongStateOf(0L) }

            LaunchedEffect(uiState.vmAdvanced) {
                if (!isInteractingVm && (System.currentTimeMillis() - lastVmInteraction > 2000L)) {
                    swapValue = uiState.vmAdvanced.swappiness.toFloat()
                    dirtyValue = uiState.vmAdvanced.dirtyRatio.toFloat()
                    dirtyBgValue = uiState.vmAdvanced.dirtyBackgroundRatio.toFloat()
                    vfsValue = uiState.vmAdvanced.vfsCachePressure.toFloat()
                    expireValue = (uiState.vmAdvanced.dirtyExpireCentisecs / 100).toFloat()
                    writebackValue = (uiState.vmAdvanced.dirtyWritebackCentisecs / 100).toFloat()
                    statValue = uiState.vmAdvanced.statInterval.toFloat()
                }
            }

            LynxSlider(
                label = "Virtual Memory Swappiness",
                value = swapValue,
                onValueChange = {
                    isInteractingVm = true
                    lastVmInteraction = System.currentTimeMillis()
                    swapValue = it
                },
                onValueChangeFinished = {
                    isInteractingVm = false
                    lastVmInteraction = System.currentTimeMillis()
                    viewModel.setSwappiness(swapValue.toInt())
                },
                valueRange = 0f..200f,
                steps = 19,
                displayValue = "${swapValue.toInt()}",
                accentColor = AccentBlue
            )

            LynxSlider(
                label = "VM Dirty Ratio (Batas Writeback)",
                value = dirtyValue,
                onValueChange = {
                    isInteractingVm = true
                    lastVmInteraction = System.currentTimeMillis()
                    dirtyValue = it
                },
                onValueChangeFinished = {
                    isInteractingVm = false
                    lastVmInteraction = System.currentTimeMillis()
                    viewModel.setDirtyRatio(dirtyValue.toInt())
                },
                valueRange = 5f..60f,
                steps = 10,
                displayValue = "${dirtyValue.toInt()}%",
                accentColor = AccentBlue
            )

            LynxSlider(
                label = "VM Dirty Background Ratio",
                value = dirtyBgValue,
                onValueChange = {
                    isInteractingVm = true
                    lastVmInteraction = System.currentTimeMillis()
                    dirtyBgValue = it
                },
                onValueChangeFinished = {
                    isInteractingVm = false
                    lastVmInteraction = System.currentTimeMillis()
                    viewModel.setDirtyBackgroundRatio(dirtyBgValue.toInt())
                },
                valueRange = 1f..30f,
                steps = 28,
                displayValue = "${dirtyBgValue.toInt()}%",
                accentColor = AccentBlue
            )

            LynxSlider(
                label = "VFS Cache Pressure (Reclaim Rate)",
                value = vfsValue,
                onValueChange = {
                    isInteractingVm = true
                    lastVmInteraction = System.currentTimeMillis()
                    vfsValue = it
                },
                onValueChangeFinished = {
                    isInteractingVm = false
                    lastVmInteraction = System.currentTimeMillis()
                    viewModel.setVfsCachePressure(vfsValue.toInt())
                },
                valueRange = 50f..200f,
                steps = 14,
                displayValue = "${vfsValue.toInt()}",
                accentColor = AccentBlue
            )

            LynxSlider(
                label = "VM Dirty Expire Time",
                value = expireValue,
                onValueChange = {
                    isInteractingVm = true
                    lastVmInteraction = System.currentTimeMillis()
                    expireValue = it
                },
                onValueChangeFinished = {
                    isInteractingVm = false
                    lastVmInteraction = System.currentTimeMillis()
                    viewModel.setDirtyExpireCentisecs((expireValue * 100).toInt())
                },
                valueRange = 10f..60f,
                steps = 9,
                displayValue = "${expireValue.toInt()}s",
                accentColor = AccentBlue
            )

            LynxSlider(
                label = "VM Dirty Writeback Interval",
                value = writebackValue,
                onValueChange = {
                    isInteractingVm = true
                    lastVmInteraction = System.currentTimeMillis()
                    writebackValue = it
                },
                onValueChangeFinished = {
                    isInteractingVm = false
                    lastVmInteraction = System.currentTimeMillis()
                    viewModel.setDirtyWritebackCentisecs((writebackValue * 100).toInt())
                },
                valueRange = 1f..15f,
                steps = 13,
                displayValue = "${writebackValue.toInt()}s",
                accentColor = AccentBlue
            )

            LynxSlider(
                label = "VM Stat Interval (Pembaruan Statistik)",
                value = statValue,
                onValueChange = {
                    isInteractingVm = true
                    lastVmInteraction = System.currentTimeMillis()
                    statValue = it
                },
                onValueChangeFinished = {
                    isInteractingVm = false
                    lastVmInteraction = System.currentTimeMillis()
                    viewModel.setVmStatInterval(statValue.toInt())
                },
                valueRange = 1f..10f,
                steps = 8,
                displayValue = "${statValue.toInt()}s",
                accentColor = AccentBlue
            )

            Spacer(Modifier.height(8.dp))
            LynxActionButton(
                text = "Bebaskan Cache RAM (Drop Caches)",
                icon = Icons.Default.CleaningServices,
                onClick = { viewModel.dropCaches() },
                accentColor = AccentCyan,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // ── KSM (Kernel Same-page Merging) Card ──────────────────
        LaunchedEffect(Unit) { viewModel.refreshKsmStats() }
        val ksm = uiState.ksmStats
        LynxCard(
            title = "KSM — Kernel Memory Merging",
            icon = Icons.Default.Memory,
            accentColor = AccentBlue
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Kernel Same-page Merging", color = TextPrimary,
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Gabung halaman memori identik, hemat ${String.format("%.1f", ksm.savedMb)} MB RAM",
                        color = TextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = ksm.enabled,
                    onCheckedChange = { viewModel.setKsmEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = AccentBlue,
                        checkedTrackColor = AccentBlue.copy(alpha = 0.4f))
                )
            }
            if (ksm.enabled) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${ksm.pagesSharing}", color = AccentBlue,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Halaman Sharing", color = TextSecondary, fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${ksm.pagesShared}", color = AccentCyan,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Halaman Shared", color = TextSecondary, fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format("%.1f MB", ksm.savedMb), color = AccentOrange,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("RAM Hemat", color = TextSecondary, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                var scanValue by remember { mutableFloatStateOf(ksm.pagesToScan.toFloat()) }
                var isInteractingKsm by remember { mutableStateOf(false) }
                var lastKsmInteraction by remember { mutableLongStateOf(0L) }

                LaunchedEffect(ksm.pagesToScan) {
                    if (!isInteractingKsm && (System.currentTimeMillis() - lastKsmInteraction > 2000L)) {
                        scanValue = ksm.pagesToScan.toFloat()
                    }
                }

                LynxSlider(
                    label = "Pages to Scan per Cycle",
                    value = scanValue,
                    onValueChange = {
                        isInteractingKsm = true
                        lastKsmInteraction = System.currentTimeMillis()
                        scanValue = it
                    },
                    onValueChangeFinished = {
                        isInteractingKsm = false
                        lastKsmInteraction = System.currentTimeMillis()
                        viewModel.setKsmTunables(scanValue.toInt(), ksm.sleepMs)
                    },
                    valueRange = 50f..500f,
                    steps = 8,
                    displayValue = "${scanValue.toInt()} pages",
                    accentColor = AccentBlue
                )
            }
        }

        // ── LMK Minfree Preset Card ──────────────────────────────
        LynxCard(
            title = "LMK — Low Memory Killer",
            icon = Icons.Default.DeleteSweep,
            accentColor = AccentOrange
        ) {
            Text(
                "Atur seberapa agresif sistem Android mengakhiri aplikasi latar belakang untuk membebaskan RAM.",
                color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            val lmkPresets = listOf(
                Triple("conservative", "Conservative", "Simpan lebih banyak app di RAM"),
                Triple("balanced",     "Balanced",     "Standar harian — default Android"),
                Triple("gaming",       "Gaming",       "Prioritaskan game aktif"),
                Triple("aggressive",   "Aggressive",   "Kill agresif, maksimalkan RAM bebas"),
            )
            lmkPresets.forEach { (id, label, desc) ->
                Surface(
                    onClick = { viewModel.applyLmkPreset(id) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderGlass)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(desc, color = TextSecondary, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(
                "Catatan: LMK Kernel hanya efektif pada Android 10 ke bawah. Android 11+ menggunakan LMKD userspace.",
                color = TextSecondary.copy(alpha = 0.7f), fontSize = 10.sp, lineHeight = 14.sp
            )
        }

        // ── Memory LMK & Entropy Tuner Card ──────────────────────
        MemoryEntropyCard(
            memoryEntropy = uiState.memoryEntropy,
            onSetEntropy = { r, w ->
                viewModel.setEntropyThresholds(r, w)
            }
        )

        // ── I/O Scheduler Card ───────────────────────────────────
        if (uiState.ioDevices.isNotEmpty()) {
            LynxCard(
                title = "I/O Scheduler Manager",
                icon = Icons.Default.Storage,
                accentColor = AccentPurple
            ) {
                uiState.ioDevices.forEach { dev ->
                    Text(
                        "/dev/${dev.device}",
                        color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                    )
                    Text("Scheduler", color = TextSecondary, fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(dev.availableSchedulers.size) { i ->
                            val sched = dev.availableSchedulers[i]
                            val isActive = sched == dev.currentScheduler
                            Surface(
                                onClick = { viewModel.setIoScheduler(dev.device, sched) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isActive) AccentPurple.copy(alpha = 0.2f) else BgElevated,
                                border = BorderStroke(
                                    1.dp, if (isActive) AccentPurple else BorderGlass
                                )
                            ) {
                                Text(sched,
                                    color = if (isActive) AccentPurple else TextSecondary,
                                    fontSize = 11.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    var raValue by remember(dev.device) { mutableFloatStateOf(dev.readAheadKb.toFloat()) }
                    var isInteractingRa by remember(dev.device) { mutableStateOf(false) }
                    var lastRaInteraction by remember(dev.device) { mutableLongStateOf(0L) }

                    LaunchedEffect(dev.device, dev.readAheadKb) {
                        if (!isInteractingRa && (System.currentTimeMillis() - lastRaInteraction > 2000L)) {
                            raValue = dev.readAheadKb.toFloat()
                        }
                    }

                    LynxSlider(
                        label = "Read-Ahead Buffer",
                        value = raValue,
                        onValueChange = {
                            isInteractingRa = true
                            lastRaInteraction = System.currentTimeMillis()
                            raValue = it
                        },
                        onValueChangeFinished = {
                            isInteractingRa = false
                            lastRaInteraction = System.currentTimeMillis()
                            viewModel.setReadAheadKb(dev.device, raValue.toInt())
                        },
                        valueRange = 128f..4096f,
                        steps = 7,
                        displayValue = "${raValue.toInt()} KB",
                        accentColor = AccentPurple
                    )
                    HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

// ============================================================
//  CATEGORY 5: BATTERY & CHARGING SUBSCREEN
// ============================================================

@Composable
fun TuningChargingCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state
    val battDetails = uiState.batteryDetails

    LaunchedEffect(Unit) {
        viewModel.refreshBatteryDetails()
        while (isActive) {
            delay(1000L)
            viewModel.refreshBatteryDetails()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ============================================================
        //  BENTO CARD 1: CHARGING TELEMETRY & HARDWARE INSPECTOR
        // ============================================================
        LynxCard(
            title = "Hardware Inspector & Telemetri",
            icon = Icons.Default.BatteryChargingFull,
            accentColor = AccentCyan
        ) {
            // Live Charging Status Badge
            val isOvernightLatched = battDetails?.isOvernightBypassLatched == true || (battDetails?.level ?: 0) >= 100
            val isSmartTapering = battDetails?.isSmartTaperingActive == true && state.charging.smartTaperingEnabled

            val isBypassLatched = state.charging.bypassEnabled && ((battDetails?.level ?: 0) >= state.charging.maxBatteryPercent || isOvernightLatched)

            val currentModeLabel = when {
                isOvernightLatched -> "100% Full: Hardware Bypass Aktif (Net 0mA - Aman Tidur)"
                isBypassLatched -> "Bypass Charging Aktif (Baterai Latch)"
                isSmartTapering -> "Smart Tapering Aktif (Mendinginkan Baterai 90%+)"
                state.charging.extremeChargingEnabled -> "Extreme Fast Charge (Lockout Bypass)"
                state.charging.limitCurrentMa >= 3000 -> "High-Current Fast Charge (${state.charging.limitCurrentMa} mA)"
                else -> "Pengisian Dibatasi (${state.charging.limitCurrentMa} mA)"
            }
            val currentModeColor = when {
                isOvernightLatched -> AccentCyan
                isBypassLatched -> AccentCyan
                isSmartTapering -> AccentGreen
                state.charging.extremeChargingEnabled -> AccentRed
                state.charging.limitCurrentMa >= 3000 -> AccentCyan
                else -> AccentOrange
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = currentModeColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, currentModeColor.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isOvernightLatched || isBypassLatched) Icons.Default.BatteryChargingFull else if (isSmartTapering) Icons.Default.Shield else Icons.Default.Bolt,
                        contentDescription = null,
                        tint = currentModeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = currentModeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = currentModeColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Overnight 100% Bypass Latch In-App Banner
            if (isOvernightLatched) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(1.2.dp, AccentCyan.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp).padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Overnight Guard Aktif (100% Full Latch)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Baterai telah terisi penuh 100%. Sistem otomatis mengunci sirkuit ke True Hardware Bypass (Net 0mA). Daya operasional HP disuplai langsung oleh adaptor charger via Vsys sehingga baterai tidak akan drop dan sangat aman ditinggal tidur semalaman di kasur.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = TextPrimary.copy(alpha = 0.9f),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            } else if (isSmartTapering) {
                // Smart Tapering In-App Banner (90%+)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.2.dp, AccentGreen.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(20.dp).padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Smart Tapering Aktif (Pendinginan 90%+)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentGreen
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Baterai mencapai 90%+. Arus masuk diturunkan bertahap dan sensor termal dikembalikan normal untuk mendinginkan suhu baterai & bodi HP secara optimal sebelum mencapai 100%. Beban sistem tetap terjamin aman tanpa defisit daya.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = TextPrimary.copy(alpha = 0.9f),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Emergency Thermal Guard Alert Banner (if tripped)
            if (battDetails?.isEmergencyGuardActive == true) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentRed.copy(alpha = 0.15f),
                    border = BorderStroke(1.2.dp, AccentRed.copy(alpha = 0.8f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(20.dp).padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Emergency Thermal Guard Aktif!",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentRed
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Suhu sensor fisik nyata terdeteksi ≥ 46.0°C. Sistem otomatis mencabut temperature spoofing dan menahan arus di batas aman 2.0A demi melindungi kesehatan baterai dan komponen IC.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = TextPrimary.copy(alpha = 0.9f),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Hardware IC & Fast Charge Protocol Badges
            val icLabel = battDetails?.activeICName?.ifBlank { "Direct Pump 2:1 / PMIC" } ?: "Direct Pump 2:1 / PMIC"
            val protoLabel = battDetails?.fastChargeProtocol?.ifBlank { "Standard DCP / Battery" } ?: "Standard DCP / Battery"

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Active IC Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("Active IC", style = MaterialTheme.typography.labelSmall, fontSize = 9.5.sp, color = TextSecondary)
                            Text(
                                text = icLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentPurple,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Protocol Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("Protokol", style = MaterialTheme.typography.labelSmall, fontSize = 9.5.sp, color = TextSecondary)
                            Text(
                                text = protoLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentCyan,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Dual Wattmeter & Metrics Matrix
            if (battDetails != null) {
                val inputWatt = if (battDetails.adapterWatt > 0.05f) battDetails.adapterWatt else battDetails.chargerWatt
                val netBattWatt = (battDetails.voltageMv.toFloat() * battDetails.currentMa.coerceAtLeast(0).toFloat()) / 1_000_000f
                val netWattDisplay = if (netBattWatt > 0.05f) netBattWatt else battDetails.chargerWatt
                val efficiency = battDetails.chargingEfficiencyPercent.coerceIn(0, 100)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Row 1: Dual Wattmeter (Adapter Input vs Battery Net)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Column A: Adapter Input
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BgCard,
                                border = BorderStroke(0.8.dp, BorderGlass),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Daya Masuk (Adapter)", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (inputWatt > 0.05f) String.format(java.util.Locale.US, "%.1f W", inputWatt) else "--",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentPurple
                                    )
                                    val voltVal = if (battDetails.adapterVoltageMv > 1000) battDetails.adapterVoltageMv / 1000f else (if (battDetails.chargerVoltageMv > 1000) battDetails.chargerVoltageMv / 1000f else battDetails.voltageMv / 1000f)
                                    val curVal = if (battDetails.adapterCurrentMa > 0) battDetails.adapterCurrentMa else battDetails.currentMa.coerceAtLeast(0)
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.1f V • %d mA", voltVal, curVal),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            // Column B: Battery Net Power
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BgCard,
                                border = BorderStroke(0.8.dp, BorderGlass),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Net Baterai", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                                    Spacer(Modifier.height(2.dp))
                                    val curColor = if (battDetails.currentMa > 100) AccentGreen else if (state.charging.bypassEnabled) AccentCyan else TextPrimary
                                    Text(
                                        text = if (netWattDisplay > 0.05f) String.format(java.util.Locale.US, "%.1f W", netWattDisplay) else "--",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = curColor
                                    )
                                    val bCurText = if (battDetails.currentMa > 0) "+${battDetails.currentMa} mA" else "${battDetails.currentMa} mA"
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.1f V • %s", battDetails.voltageMv / 1000f, bCurText),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        // Row 2: Conversion Efficiency Progress Bar
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Efisiensi Konversi IC Pump", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp, color = TextSecondary)
                                Text(
                                    text = if (efficiency > 0) "$efficiency%" else if (state.charging.bypassEnabled) "100% (Bypass)" else "--",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGreen
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { if (efficiency > 0) (efficiency / 100f).coerceIn(0f, 1f) else if (state.charging.bypassEnabled) 1f else 0.5f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = AccentGreen,
                                trackColor = BgElevated
                            )
                        }

                        // Row 3: Thermal Duality (Suhu Sensor Fisik Nyata vs Sensor Baterai/Spoof)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BgCard,
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("Thermal Duality Sensor", style = MaterialTheme.typography.labelSmall, fontSize = 10.5.sp, color = TextSecondary)
                                    val isSpoofed = state.charging.thermalLockoutBypassEnabled && (battDetails.tempC in 27.5f..28.5f)
                                    val spoofLabel = if (isSpoofed) "Spoofed 28°C (Bypass Aktif)" else "${String.format(java.util.Locale.US, "%.1f°C", battDetails.tempC)}"
                                    Text(
                                        text = "Sensor Baterai: $spoofLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = if (isSpoofed) AccentCyan else TextSecondary,
                                        maxLines = 1
                                    )
                                }
                                val physColor = if (battDetails.realPhysicalTempC >= 46f) AccentRed else if (battDetails.realPhysicalTempC >= 42f) AccentOrange else AccentGreen
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Fisik Nyata", style = MaterialTheme.typography.labelSmall, fontSize = 9.5.sp, color = TextSecondary)
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.1f°C", battDetails.realPhysicalTempC),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = physColor,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ============================================================
        //  BENTO CARD 1.5: ESTIMASI WAKTU PENGECAASAN & STATUS PENGISIAN
        // ============================================================
        if (battDetails != null) {
            val isCharging = battDetails.currentMa > 50 || battDetails.status.equals("Charging", ignoreCase = true)
            val currentLevel = battDetails.level.coerceIn(0, 100)
            val targetPercent = if (state.charging.bypassEnabled) state.charging.maxBatteryPercent else 100
            val isTargetReached = currentLevel >= targetPercent
            val isOvernightLatched = battDetails.isOvernightBypassLatched || currentLevel >= 100
            val isBypassLocked = isOvernightLatched || (state.charging.bypassEnabled && isTargetReached)

            val netCurMa = battDetails.currentMa.coerceAtLeast(0)
            val designCapMah = 5000f
            val remainingPercent = (targetPercent - currentLevel).coerceAtLeast(0)
            val remainingMah = (designCapMah * remainingPercent / 100f)

            // Dynamic Charging ETA Algorithm (Direct Pump & Tapering curve)
            val etaMinutes = when {
                isBypassLocked || remainingPercent == 0 -> 0
                !isCharging -> -1
                netCurMa < 100 -> -2
                currentLevel < 80 -> {
                    val ccRemMah = (designCapMah * (80 - currentLevel).coerceAtLeast(0) / 100f)
                    val cvRemMah = remainingMah - ccRemMah
                    val ccMins = (ccRemMah / netCurMa.toFloat()) * 60f
                    val cvMins = if (targetPercent > 80) (cvRemMah / (netCurMa * 0.55f).coerceAtLeast(800f)) * 60f else 0f
                    (ccMins + cvMins).toInt().coerceAtLeast(1)
                }
                else -> {
                    val cvAvgCur = (netCurMa * 0.7f).coerceAtLeast(600f)
                    ((remainingMah / cvAvgCur) * 60f).toInt().coerceAtLeast(1)
                }
            }

            val etaMainText = when {
                isBypassLocked -> "Baterai Penuh (0 Menit)"
                etaMinutes == 0 -> "Baterai Penuh"
                etaMinutes in 1..59 -> "± $etaMinutes Menit"
                etaMinutes >= 60 -> "± ${etaMinutes / 60} Jam ${etaMinutes % 60} Menit"
                etaMinutes == -2 -> "Menghitung Laju..."
                else -> {
                    val dischargeMa = Math.abs(battDetails.currentMa).coerceAtLeast(150)
                    val dischargeMins = ((designCapMah * currentLevel / 100f) / dischargeMa.toFloat() * 60f).toInt()
                    if (dischargeMins >= 60) "± ${dischargeMins / 60} Jam ${dischargeMins % 60} Menit" else "± $dischargeMins Menit"
                }
            }

            val etaSubText = when {
                isBypassLocked -> "Hardware Bypass Aktif — Mengalirkan daya adapter langsung tanpa mengisi baterai."
                isCharging && targetPercent < 100 -> "Menuju Target Bypass $targetPercent% (Sisa $remainingPercent% • ${remainingMah.toInt()} mAh)"
                isCharging -> "Menuju 100% Penuh (Sisa $remainingPercent% • ${remainingMah.toInt()} mAh)"
                else -> "Estimasi sisa daya baterai berdasarkan beban saat ini (-${Math.abs(battDetails.currentMa)} mA)"
            }

            val ratePercentPerHour = if (isCharging && netCurMa > 100) ((netCurMa.toFloat() / designCapMah) * 100f).coerceIn(0f, 250f) else 0f
            val cardAccent = if (isBypassLocked) AccentCyan else if (isCharging) AccentGreen else AccentOrange

            LynxCard(
                title = "Estimasi Waktu Pengecasan",
                icon = Icons.Default.Schedule,
                accentColor = cardAccent
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Main Highlight Display Box
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = cardAccent.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isCharging) "WAKTU TERSISA" else "DAYA TAHAN BATERAI",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = etaMainText,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = cardAccent
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = etaSubText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.5.sp,
                                    color = TextPrimary.copy(alpha = 0.85f),
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    // Progress toward target
                    if (isCharging) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Progres Menuju Target ($targetPercent%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "$currentLevel% / $targetPercent%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cardAccent
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            val progressRatio = (currentLevel.toFloat() / targetPercent.toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progressRatio },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = cardAccent,
                                trackColor = BgElevated
                            )
                        }

                        // 3-Metric Speed Strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Strip 1: Laju Kecepatan
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BgElevated.copy(alpha = 0.5f),
                                border = BorderStroke(0.6.dp, BorderSubtle),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Laju Pengisian", style = MaterialTheme.typography.labelSmall, fontSize = 9.5.sp, color = TextSecondary)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (ratePercentPerHour > 1f) "+${String.format(java.util.Locale.US, "%.1f", ratePercentPerHour / 60f)}%/mnt" else "--",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentGreen
                                    )
                                    Text(
                                        text = "+${ratePercentPerHour.toInt()}% / jam",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            // Strip 2: Arus Masuk Riil
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BgElevated.copy(alpha = 0.5f),
                                border = BorderStroke(0.6.dp, BorderSubtle),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Arus Masuk", style = MaterialTheme.typography.labelSmall, fontSize = 9.5.sp, color = TextSecondary)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "+$netCurMa mA",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = cardAccent
                                    )
                                    Text(
                                        text = if (battDetails.adapterWatt > 0.1f) "${String.format(java.util.Locale.US, "%.1fW", battDetails.adapterWatt)} Input" else "Standar",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            // Strip 3: Fase Kernel
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BgElevated.copy(alpha = 0.5f),
                                border = BorderStroke(0.6.dp, BorderSubtle),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Fase Kernel", style = MaterialTheme.typography.labelSmall, fontSize = 9.5.sp, color = TextSecondary)
                                    Spacer(Modifier.height(2.dp))
                                    val phaseText = when {
                                        isBypassLocked -> "Bypass Latch"
                                        currentLevel >= 85 -> "CV Tapering"
                                        battDetails.activeICName.contains("Pump") -> "Direct Pump"
                                        else -> "Arus Konstan"
                                    }
                                    Text(
                                        text = phaseText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (phaseText == "Direct Pump") AccentRed else cardAccent
                                    )
                                    Text(
                                        text = if (phaseText == "Direct Pump") "Super Charge" else "Stabilizer",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Direct Action Button: Paksa Kecepatan Tertinggi
                    Button(
                        onClick = { viewModel.forceMaxSuperCharge() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentRed.copy(alpha = 0.18f),
                            contentColor = AccentRed
                        ),
                        border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = AccentRed
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Paksa Kecepatan Tertinggi (Force Max 33W)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentRed
                        )
                    }
                }
            }
        }

        // ============================================================
        //  BENTO CARD 2: HIGH-CURRENT & ACCELERATION CONTROLS
        // ============================================================
        LynxCard(
            title = "Kontrol Arus Tinggi & Akselerasi",
            icon = Icons.Default.Speed,
            accentColor = AccentCyan
        ) {
            LynxSwitch(
                label = "Extreme Fast Charging (Continuous Screen-On Boost)",
                subLabel = "Membuka batas arus hingga 6000mA (6A) di sel baterai / 33W di adaptor, mengaktifkan Pump Express 4.0 & RT9759 Charge Pump 2:1, membypass batasan layar menyala (BN_TestMode & derating bypass), mengangkat limit termal PCB Transsion ke 85°C serta menonaktifkan pembatasan thermal kernel agar pengisian tetap konsisten dan sangat cepat baik layar hidup maupun mati.",
                checked = state.charging.extremeChargingEnabled,
                onCheckedChange = { viewModel.setExtremeCharging(it) },
            )

            LynxSwitch(
                label = "Thermal Lockout Bypass (DV2_TBAT)",
                subLabel = "Mem-bypass limitasi thermal lockout (Battery_Temperature = 28°C spoofing & abcct disable) agar charger tidak drop ke 500mA saat baterai hangat ketika gaming.",
                checked = state.charging.thermalLockoutBypassEnabled,
                onCheckedChange = { viewModel.setThermalLockoutBypass(it) },
            )

            var highTargetValue by remember {
                mutableFloatStateOf(state.charging.highCurrentTargetPercent.toFloat())
            }
            var currentLimitValue by remember {
                mutableFloatStateOf(state.charging.limitCurrentMa.toFloat())
            }
            var isInteractingChgCur by remember { mutableStateOf(false) }
            var lastChgCurInteraction by remember { mutableLongStateOf(0L) }

            LaunchedEffect(state.charging.highCurrentTargetPercent, state.charging.limitCurrentMa) {
                if (!isInteractingChgCur && (System.currentTimeMillis() - lastChgCurInteraction > 2000L)) {
                    highTargetValue = state.charging.highCurrentTargetPercent.toFloat()
                    currentLimitValue = state.charging.limitCurrentMa.toFloat()
                }
            }

            LynxSlider(
                label = "Target Kapasitas Arus Tinggi (sc_tuisoc)",
                value = highTargetValue,
                onValueChange = {
                    isInteractingChgCur = true
                    lastChgCurInteraction = System.currentTimeMillis()
                    highTargetValue = it
                },
                onValueChangeFinished = {
                    isInteractingChgCur = false
                    lastChgCurInteraction = System.currentTimeMillis()
                    viewModel.setHighCurrentTarget(highTargetValue.toInt())
                },
                valueRange = 80f..100f,
                steps = 19,
                displayValue = "${highTargetValue.toInt()}% Kapasitas",
                accentColor = AccentCyan
            )
            Text(
                text = "Menahan pengisian pompa arus tinggi (Direct Charge Pump 2:1) hingga baterai mencapai ${highTargetValue.toInt()}% sebelum beralih ke arus rendah trickle. Default pabrikan biasanya membatasi di 70-80%.",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            Spacer(Modifier.height(6.dp))

            LynxSlider(
                label = "Batas Arus Pengisian Game",
                value = currentLimitValue,
                onValueChange = {
                    isInteractingChgCur = true
                    lastChgCurInteraction = System.currentTimeMillis()
                    currentLimitValue = it
                },
                onValueChangeFinished = {
                    isInteractingChgCur = false
                    lastChgCurInteraction = System.currentTimeMillis()
                    viewModel.setChargeCurrentLimit(currentLimitValue.toInt())
                },
                valueRange = 1000f..6000f,
                steps = 9,
                displayValue = if (currentLimitValue >= 3500f) "${currentLimitValue.toInt()} mA (Fast Charge)" else "${currentLimitValue.toInt()} mA",
                accentColor = AccentPurple
            )
        }

        // ============================================================
        //  BENTO CARD 3: SAFETY & BATTERY LONGEVITY
        // ============================================================
        LynxCard(
            title = "Proteksi & Kesehatan Baterai",
            icon = Icons.Default.Security,
            accentColor = AccentGreen
        ) {
            LynxSwitch(
                label = "Smart Tapering & Overnight Guard",
                subLabel = "Menurunkan arus bertahap di atas 90% (90-95% 1500mA, 95-99% 750mA) demi mendinginkan baterai, serta mengunci True Hardware Bypass di 100% (Net 0mA) agar baterai tidak drop dan mustahil overcharge saat ditinggal tidur di kasur.",
                checked = state.charging.smartTaperingEnabled,
                onCheckedChange = { viewModel.setSmartTapering(it) },
            )

            LynxSwitch(
                label = "Emergency Thermal Guard (46.0°C)",
                subLabel = "Failsafe cerdas anti-overheat. Jika sensor termal fisik nyata (mtktsAP/thermal_zone) mencapai ≥ 46°C, temperatur spoofing dicabut otomatis dan arus diturunkan seketika ke level aman.",
                checked = state.charging.emergencyTempGuardEnabled,
                onCheckedChange = { viewModel.setEmergencyTempGuard(it) },
            )

            LynxSwitch(
                label = "Bypass Charging (Direct Motherboard)",
                subLabel = "Mengalirkan daya charger LANGSUNG ke motherboard (Vsys) & MENGHENTIKAN pengisian ke baterai (Net Arus ~0mA). Khusus gaming agar baterai tidak panas. JANGAN aktifkan jika Anda berniat mengisi baterai!",
                checked = state.charging.bypassEnabled,
                onCheckedChange = { viewModel.setBypassCharging(it) },
            )

            var tempCutoffValue by remember {
                mutableFloatStateOf(state.charging.tempCutoffC.toFloat())
            }
            var maxBatteryValue by remember {
                mutableFloatStateOf(state.charging.maxBatteryPercent.toFloat())
            }
            var isInteractingSafety by remember { mutableStateOf(false) }
            var lastSafetyInteraction by remember { mutableLongStateOf(0L) }

            LaunchedEffect(state.charging.tempCutoffC, state.charging.maxBatteryPercent) {
                if (!isInteractingSafety && (System.currentTimeMillis() - lastSafetyInteraction > 2000L)) {
                    tempCutoffValue = state.charging.tempCutoffC.toFloat()
                    maxBatteryValue = state.charging.maxBatteryPercent.toFloat()
                }
            }

            LynxSlider(
                label = "Batas Suhu Thermal AutoCut (Kasur / Pelindung Suhu)",
                value = tempCutoffValue,
                onValueChange = {
                    isInteractingSafety = true
                    lastSafetyInteraction = System.currentTimeMillis()
                    tempCutoffValue = it
                },
                onValueChangeFinished = {
                    isInteractingSafety = false
                    lastSafetyInteraction = System.currentTimeMillis()
                    viewModel.setTempCutoff(tempCutoffValue.toInt())
                },
                valueRange = 40f..50f,
                steps = 9,
                displayValue = "${tempCutoffValue.toInt()}°C",
                accentColor = AccentOrange
            )
            Text(
                text = "Ambang batas suhu pemotong arus jika panas terperangkap (misal saat HP ditaruh di kasur/bantal). Arus otomatis dipangkas ke 1200mA saat menyentuh suhu ini hingga bodi kembali dingin.",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            LynxSlider(
                label = "Batas Pengisian Baterai (Stop-At-%)",
                value = maxBatteryValue,
                onValueChange = {
                    isInteractingSafety = true
                    lastSafetyInteraction = System.currentTimeMillis()
                    maxBatteryValue = it
                },
                onValueChangeFinished = {
                    isInteractingSafety = false
                    lastSafetyInteraction = System.currentTimeMillis()
                    viewModel.setMaxBatteryPercent(maxBatteryValue.toInt())
                },
                valueRange = 70f..100f,
                steps = 5,
                displayValue = if (maxBatteryValue >= 99.5f) "100% (Penuh & Auto-Bypass)" else "${maxBatteryValue.toInt()}%",
                accentColor = AccentGreen
            )
        }
    }
}

// ============================================================
//  CATEGORY 6: NETWORK & AUDIO SUBSCREEN
// ============================================================

@Composable
fun TuningNetworkCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── TCP Congestion Control Card ──────────────────────────
        LaunchedEffect(Unit) { viewModel.refreshTcpAlgorithms() }
        LynxCard(
            title = "TCP Congestion Control",
            icon = Icons.Default.NetworkCheck,
            accentColor = AccentCyan
        ) {
            val currentAlg = uiState.currentTcpCongestion.ifBlank { state.network.tcpCongestion }
            val algs = uiState.availableTcpAlgorithms

            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Algoritma kontrol kongesti TCP/IP aktif mempengaruhi latensi dan throughput koneksi game online.",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                if (currentAlg.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentCyan.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.35f))
                    ) {
                        Text(
                            "● ${currentAlg.uppercase()}",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (algs.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentOrange.copy(alpha = 0.2f),
                            modifier = Modifier.padding(end = 10.dp)
                        ) {
                            Text(
                                "UNSUPPORTED",
                                color = AccentOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            "Kernel perangkat ini tidak mengekspos pemilihan algoritma TCP dinamis via sysfs.",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
            } else {
                val algInfo = mapOf(
                    "bic"      to Pair("BIC", "High Speed & Low Latency (Kernel Active)"),
                    "cubic"    to Pair("CUBIC", "Linux Default — Balanced"),
                    "reno"     to Pair("RENO", "Classic Standard — Stable"),
                    "bbr"      to Pair("BBR", "Google BBR — Low Latency Gaming"),
                    "westwood" to Pair("Westwood", "WiFi & Wireless Loss Tolerant"),
                    "htcp"     to Pair("H-TCP", "High Bandwidth Delay Product"),
                    "vegas"    to Pair("Vegas", "Delay-Based Congestion Avoidance"),
                    "hybla"    to Pair("Hybla", "Satellite & High Latency Links")
                )

                algs.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { alg ->
                            val isActive = alg.equals(currentAlg, ignoreCase = true)
                            val info = algInfo[alg.lowercase()]
                            Surface(
                                onClick = { viewModel.setTcpCongestion(alg) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isActive) AccentCyan.copy(alpha = 0.16f) else BgElevated,
                                border = BorderStroke(
                                    1.5.dp, if (isActive) AccentCyan else BorderGlass
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            info?.first ?: alg.uppercase(),
                                            color = if (isActive) AccentCyan else TextPrimary,
                                            fontWeight = FontWeight.Bold, fontSize = 12.sp
                                        )
                                        if (isActive) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentCyan.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    "AKTIF",
                                                    color = AccentCyan,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        info?.second ?: "Algoritma Kernel Linux",
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // ── Subsystem Audio & Wi-Fi Enhancer Card ────────────────
        LynxCard(
            title = "Subsystem Audio & Wi-Fi Enhancer",
            icon = Icons.Default.Wifi,
            accentColor = AccentBlue
        ) {
            LynxSwitch(
                label = "Wi-Fi Ping Stabilizer & FQ-CoDel",
                subLabel = "Mengurangi bufferbloat dan jitter koneksi game online",
                checked = state.network.wifiPingStabilizer,
                onCheckedChange = { viewModel.setWifiPingStabilizer(it) },
            )
            LynxSwitch(
                label = "Low-Latency Audio MMAP",
                subLabel = "Bypass mixer audio Android untuk latensi respon suara terendah",
                checked = state.audio.lowLatencyMmap,
                onCheckedChange = { viewModel.setAudioMmap(it) },
            )
        }

        // ── Sound Control (Gain Booster) Card ────────────────────
        SoundControlCard(
            soundControl = uiState.soundControl,
            onSetGain = { hpL, hpR, spk, mic, hpMode ->
                viewModel.setSoundGain(hpL, hpR, spk, mic, hpMode)
            }
        )
    }
}

// ============================================================
//  CATEGORY 7: SUBSYSTEM & DEEP TUNABLES SUBSCREEN
// ============================================================

@Composable
fun TuningSystemCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    onAddAppClick: () -> Unit,
    onEditRuleClick: (AppProfileRule) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Otomasi Profil Per-Aplikasi Card ────────────────────
        LynxCard(
            title = "Otomasi Profil Per-Aplikasi",
            icon = Icons.Default.SportsEsports,
            accentColor = AccentGreen
        ) {
            // Master Service Toggle
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Layanan Otomasi Latar Belakang",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        "Pantau aplikasi aktif dan alihkan profil CPU/GPU seketika (0ms boost saat launch)",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                Switch(
                    checked = uiState.isAppAutomationActive,
                    onCheckedChange = { viewModel.toggleAppAutomation(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgDeepOled,
                        checkedTrackColor = AccentGreen,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = BgElevated,
                        uncheckedBorderColor = BorderGlass
                    )
                )
            }

            HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 10.dp))

            // Header: Rule Count & Add Button
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${uiState.appProfileRules.size} Aturan Profil Dikonfigurasi",
                        color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp
                    )
                    Text(
                        "Profil khusus diterapkan otomatis saat aplikasi berada di foreground",
                        color = TextSecondary, fontSize = 10.5.sp
                    )
                }
                Surface(
                    onClick = onAddAppClick,
                    shape = RoundedCornerShape(10.dp),
                    color = AccentGreen.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f))
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tambah", color = AccentGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // App Profile Rules List
            if (uiState.appProfileRules.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Tune, null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Belum ada aturan khusus",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Klik tombol 'Tambah' untuk mengunci profil tertentu (Performa, Extreme, Balance, Hemat) ke game atau app favorit.",
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                uiState.appProfileRules.forEach { rule ->
                    val profColor = when (rule.targetProfile.lowercase()) {
                        "extreme" -> AccentRed
                        "performance" -> AccentOrange
                        "powersave" -> AccentGreen
                        else -> AccentCyan
                    }
                    val profLabel = when (rule.targetProfile.lowercase()) {
                        "extreme" -> "Extreme"
                        "performance" -> "Performa"
                        "powersave" -> "Hemat"
                        else -> "Balance"
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, if (rule.enabled) profColor.copy(alpha = 0.35f) else BorderGlass),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            onEditRuleClick(rule)
                        }
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                AppIconImage(
                                    packageName = rule.packageName,
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            rule.appName,
                                            color = if (rule.enabled) TextPrimary else TextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                        if (rule.isGame) {
                                            Spacer(Modifier.width(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentOrange.copy(alpha = 0.18f)
                                            ) {
                                                Text(
                                                    "GAME",
                                                    color = AccentOrange,
                                                    fontSize = 7.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = profColor.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, profColor.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                profLabel,
                                                color = profColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                        if (rule.targetRefreshRate != null) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentPurple.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "${rule.targetRefreshRate} Hz",
                                                    color = AccentPurple,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        if (rule.autoFloatingHud) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentCyan.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "OSD",
                                                    color = AccentCyan,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { enabled ->
                                        val updated = rule.copy(enabled = enabled)
                                        viewModel.addOrUpdateAppProfileRule(context, updated)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = BgDeepOled,
                                        checkedTrackColor = profColor,
                                        uncheckedThumbColor = TextSecondary,
                                        uncheckedTrackColor = BgElevated
                                    ),
                                    modifier = Modifier.scale(0.75f)
                                )
                                IconButton(
                                    onClick = { onEditRuleClick(rule) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Info Relokasi Anti-Throttling OEM ───────────────────
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "Pengaturan Anti-Throttling OEM (Joyose/GOS) kini dipusatkan pada modul Thermal & Anti-Throttling.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        // ── Deep Kernel & System Tunables Card ──────────────────
        var manualNodePath by remember { mutableStateOf("") }
        var maxVisibleItems by remember { mutableStateOf(20) }
        var deepSearchQuery by remember { mutableStateOf("") }

        LynxCard(
            title = "Deep Kernel & System Tunables",
            icon = Icons.Default.Search,
            accentColor = AccentCyan
        ) {
            Text(
                "Mesin pemindai adaptif universal. Membaca seluruh file konfigurasi kernel, OEM, CPU, GPU, & ROM. Petunjuk internal (#) dikonversi otomatis menjadi widget interaktif (toggle, slider, dropdown pilihan).",
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Action Bar: Scan button + Auto-Discovery status + Count info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.runDeepScan() },
                    enabled = !uiState.isDeepScanning,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan.copy(alpha = 0.16f),
                        contentColor = AccentCyan
                    ),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    if (uiState.isDeepScanning) {
                        CircularProgressIndicator(
                            color = AccentCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Memindai...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (uiState.deepTunables.isEmpty()) "Pindai Kernel" else "Pindai Ulang", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.deepTunables.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentCyan.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f))
                        ) {
                            Text(
                                "${uiState.deepTunables.size} Node",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass)
                    ) {
                        Text(
                            if (uiState.isDeepScanning) "Memindai Sysfs..." else "Zero Hardcoding",
                            color = if (uiState.isDeepScanning) AccentCyan else TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Manual Node Direct Tester
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = manualNodePath,
                    onValueChange = { manualNodePath = it },
                    placeholder = { Text("/proc/sys/... atau /sys/...", fontSize = 11.sp, color = TextTertiary) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderGlass,
                        focusedContainerColor = BgElevated,
                        unfocusedContainerColor = BgElevated
                    )
                )
                Button(
                    onClick = {
                        if (manualNodePath.isNotBlank()) {
                            viewModel.inspectManualNode(manualNodePath.trim())
                            manualNodePath = ""
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan.copy(alpha = 0.18f),
                        contentColor = AccentCyan
                    ),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Uji Node", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Search Bar & Category Filter Chips
            if (uiState.deepTunables.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = deepSearchQuery,
                    onValueChange = {
                        deepSearchQuery = it
                        maxVisibleItems = 20
                    },
                    placeholder = { Text("Cari parameter kernel, deskripsi, atau path...", fontSize = 11.sp, color = TextTertiary) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    },
                    trailingIcon = {
                        if (deepSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { deepSearchQuery = "" }) {
                                Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderGlass,
                        focusedContainerColor = BgElevated,
                        unfocusedContainerColor = BgElevated
                    )
                )

                // Quick Category Filters
                val detectedCategories = remember(uiState.deepTunables) {
                    listOf("Semua") + uiState.deepTunables.map { it.category }.distinct().sorted()
                }
                var activeCategoryFilter by remember { mutableStateOf("Semua") }

                Spacer(Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(detectedCategories) { catName ->
                        val isCatSel = activeCategoryFilter == catName
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCatSel) AccentCyan.copy(alpha = 0.22f) else BgElevated,
                            border = BorderStroke(1.dp, if (isCatSel) AccentCyan else BorderGlass),
                            modifier = Modifier.clickable {
                                activeCategoryFilter = catName
                                maxVisibleItems = 20
                            }
                        ) {
                            Text(
                                catName,
                                color = if (isCatSel) AccentCyan else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isCatSel) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                val displayedTunables = remember(uiState.deepTunables, deepSearchQuery, activeCategoryFilter) {
                    uiState.deepTunables.filter { tunable ->
                        val matchesCat = activeCategoryFilter == "Semua" || tunable.category.equals(activeCategoryFilter, ignoreCase = true)
                        val matchesSearch = deepSearchQuery.isBlank() ||
                                tunable.name.contains(deepSearchQuery, ignoreCase = true) ||
                                tunable.rawName.contains(deepSearchQuery, ignoreCase = true) ||
                                tunable.desc.contains(deepSearchQuery, ignoreCase = true) ||
                                tunable.path.contains(deepSearchQuery, ignoreCase = true) ||
                                tunable.help.contains(deepSearchQuery, ignoreCase = true)
                        matchesCat && matchesSearch
                    }
                }

                if (displayedTunables.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.SearchOff, null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Tidak ada parameter yang cocok dengan '$deepSearchQuery'",
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val itemsToShow = displayedTunables.take(maxVisibleItems)
                    itemsToShow.forEach { tunable ->
                        DeepTunableItemCard(
                            tunable = tunable,
                            onApply = { path, value -> viewModel.applyDeepTunable(path, value) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    if (displayedTunables.size > maxVisibleItems) {
                        Button(
                            onClick = { maxVisibleItems += 30 },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BgElevated,
                                contentColor = AccentCyan
                            ),
                            border = BorderStroke(1.dp, BorderGlass)
                        ) {
                            Text("Muat Lebih Banyak (${displayedTunables.size - maxVisibleItems} tersisa)", fontSize = 11.5.sp)
                        }
                    }
                }
            } else if (!uiState.isDeepScanning) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Explore, null, tint = AccentCyan, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Belum ada hasil pemindaian sysfs",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tekan tombol 'Pindai Kernel' di atas untuk memulai penemuan parameter secara dinamis pada perangkat Anda.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
//  DeepTunableItemCard — Adaptive Sysfs Node Control with # Docs
// ============================================================

@Composable
fun DeepTunableItemCard(
    tunable: DeepTunable,
    onApply: (String, String) -> Unit
) {
    var editValue by remember(tunable.path) { mutableStateOf(tunable.value) }
    var sliderValue by remember(tunable.path) {
        val fVal = tunable.value.toFloatOrNull() ?: tunable.min
        mutableFloatStateOf(fVal.coerceIn(tunable.min, tunable.max))
    }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var lastSliderInteraction by remember { mutableLongStateOf(0L) }
    var isEditingText by remember { mutableStateOf(false) }
    var lastTextInteraction by remember { mutableLongStateOf(0L) }
    var lastApplyTime by remember { mutableLongStateOf(0L) }
    var showPathDetails by remember { mutableStateOf(false) }

    LaunchedEffect(tunable.path, tunable.value) {
        val now = System.currentTimeMillis()
        if (!isDraggingSlider && (now - lastSliderInteraction > 2000L)) {
            val fVal = tunable.value.toFloatOrNull() ?: tunable.min
            sliderValue = fVal.coerceIn(tunable.min, tunable.max)
        }
        if (!isEditingText && (now - lastTextInteraction > 3000L)) {
            editValue = tunable.value
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = BgElevated,
        border = BorderStroke(1.dp, BorderGlass)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 1. Header: Name + Category + RW status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        tunable.name,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (tunable.rawName.isNotBlank() && tunable.rawName != tunable.name) {
                        Text(
                            tunable.rawName,
                            color = TextTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when {
                            tunable.category.contains("CPU", true) -> AccentCyan.copy(alpha = 0.15f)
                            tunable.category.contains("GPU", true) -> AccentPurple.copy(alpha = 0.15f)
                            tunable.category.contains("Memory", true) || tunable.category.contains("VM", true) -> AccentBlue.copy(alpha = 0.15f)
                            tunable.category.contains("Storage", true) || tunable.category.contains("I/O", true) -> AccentOrange.copy(alpha = 0.15f)
                            tunable.category.contains("Power", true) || tunable.category.contains("Thermal", true) -> AccentGreen.copy(alpha = 0.15f)
                            else -> AccentCyan.copy(alpha = 0.15f)
                        },
                        border = BorderStroke(1.dp, BorderGlass)
                    ) {
                        Text(
                            tunable.category,
                            color = when {
                                tunable.category.contains("CPU", true) -> AccentCyan
                                tunable.category.contains("GPU", true) -> AccentPurple
                                tunable.category.contains("Memory", true) || tunable.category.contains("VM", true) -> AccentBlue
                                tunable.category.contains("Storage", true) || tunable.category.contains("I/O", true) -> AccentOrange
                                tunable.category.contains("Power", true) || tunable.category.contains("Thermal", true) -> AccentGreen
                                else -> AccentCyan
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (tunable.writable) AccentGreen.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (tunable.writable) AccentGreen.copy(alpha = 0.4f) else Color.Red.copy(alpha = 0.3f))
                    ) {
                        Text(
                            if (tunable.writable) "R/W" else "READ-ONLY",
                            color = if (tunable.writable) AccentGreen else Color(0xFFFF5252),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 2. Friendly Description
            if (tunable.desc.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    tunable.desc,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            }

            // 3. Recommendation Tip
            if (tunable.recommendation.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentGreen.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Tips: ${tunable.recommendation}",
                            color = AccentGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 4. Collapsible Monospace Path
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPathDetails = !showPathDetails },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (showPathDetails) tunable.path else tunable.path.take(45) + (if (tunable.path.length > 45) "..." else ""),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TextTertiary,
                    maxLines = if (showPathDetails) 3 else 1
                )
                Text(
                    if (showPathDetails) "Tutup" else "Path",
                    fontSize = 8.5.sp,
                    color = AccentCyan.copy(alpha = 0.8f)
                )
            }

            // 5. Inline '#' Kernel Comment Banner (if available)
            if (tunable.help.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x1AFFB300),
                    border = BorderStroke(1.dp, Color(0x4DFFB300)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(14.dp).padding(top = 1.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                "PETUNJUK KERNEL (#):",
                                color = Color(0xFFFFB300),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                tunable.help,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TextPrimary,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 6. Interactive Tunable Controls
            if (!tunable.writable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Nilai Terbaca:", color = TextSecondary, fontSize = 11.5.sp)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0x1AFFFFFF),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Text(
                            tunable.value.ifBlank { "(kosong)" },
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                            fontSize = 11.sp,
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
                            Column {
                                Text(
                                    if (isChecked) "Status: Aktif (1)" else "Status: Nonaktif (0)",
                                    color = if (isChecked) AccentCyan else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (isChecked) "Sentuh sakelar untuk mematikan" else "Sentuh sakelar untuk mengaktifkan",
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
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = Color(0xFF161A24)
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
                                Text("Pilihan Tersedia:", color = TextSecondary, fontSize = 10.5.sp)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AccentCyan.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        "Aktif: ${tunable.value}",
                                        color = AccentCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(tunable.options.size) { i ->
                                    val opt = tunable.options[i]
                                    val isSelected = opt.value == tunable.value ||
                                            (tunable.value.isBlank() && i == 0)
                                    Surface(
                                        onClick = { onApply(tunable.path, opt.value) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) AccentCyan.copy(alpha = 0.25f) else Color(0x1AFFFFFF),
                                        border = BorderStroke(1.dp, if (isSelected) AccentCyan else BorderGlass)
                                    ) {
                                        Text(
                                            opt.label.ifBlank { opt.value },
                                            color = if (isSelected) AccentCyan else TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TunableType.SLIDER -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Nilai Parameter:", color = TextSecondary, fontSize = 11.sp)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AccentCyan.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f))
                                ) {
                                    val displayVal = if (tunable.unit.isNotEmpty()) {
                                        "${sliderValue.toInt()} ${tunable.unit}"
                                    } else {
                                        "${sliderValue.toInt()}"
                                    }
                                    Text(
                                        displayVal,
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
                                    "Min: ${tunable.min.toInt()}${tunable.unit}",
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
                                    "Max: ${tunable.max.toInt()}${tunable.unit}",
                                    color = TextTertiary,
                                    fontSize = 9.5.sp
                                )
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
                                    "Rentang: ${tunable.min.toInt()} - ${tunable.max.toInt()} ${tunable.unit}",
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
                                        "$curInt ${tunable.unit}".trim(),
                                        color = AccentCyan,
                                        fontSize = 13.sp,
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
                                    editValue = it
                                    isEditingText = true
                                    lastTextInteraction = System.currentTimeMillis()
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TextPrimary
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentCyan,
                                    unfocusedBorderColor = BorderGlass,
                                    focusedContainerColor = Color(0xFF0A0C12),
                                    unfocusedContainerColor = Color(0xFF0A0C12)
                                )
                            )

                            Button(
                                onClick = {
                                    val now = System.currentTimeMillis()
                                    if (now - lastApplyTime >= 400L) {
                                        lastApplyTime = now
                                        isEditingText = false
                                        onApply(tunable.path, editValue.trim())
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentCyan.copy(alpha = 0.2f),
                                    contentColor = AccentCyan
                                ),
                                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("Terapkan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
