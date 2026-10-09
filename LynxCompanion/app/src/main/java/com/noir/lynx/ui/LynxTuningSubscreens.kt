package com.noir.lynx.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
                    onQcomInputBoostChange = { freq, ms -> viewModel.setQcomInputBoost(freq, ms, context) },
                    onMtkInterconnectBoostChange = { en -> viewModel.setMtkInterconnectBusBoost(en, context) },
                    onMtkCpuPowerModeChange = { mode -> viewModel.setMtkCpuPowerMode(mode, context) },
                    onMtkDlptBypassChange = { en -> viewModel.setMtkDlptImaxBypass(en, context) },
                    onQcomDevfreqBusBoostChange = { en -> viewModel.setQcomDevfreqBusBoost(en, context) },
                    onUniversalBusProfileChange = { prof -> viewModel.setUniversalBusBandwidthProfile(prof, context) },
                    onUniversalTouchBoostChange = { en -> viewModel.setUniversalTouchBoost(en, context) },
                    onUniversalAntiThrottlingGuardChange = { en -> viewModel.setUniversalAntiThrottlingGuard(en, context) }
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
                    onSetCoreParkingMode = { mode -> viewModel.setCoreParkingMode(mode, uiState.cpuCores.size.coerceAtLeast(8), context) },
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
                    onArchitectureModeChange = { mode -> viewModel.setSchedulerArchitectureMode(mode, context) },
                    onTopAppPreferIdleChange = { en -> viewModel.setSchedtunePreferIdle("top-app", en, context) },
                    onWorkqueuePowerEfficientChange = { en -> viewModel.setWorkqueuePowerEfficient(en, context) },
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

@OptIn(ExperimentalMaterial3Api::class)
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

    // Custom Current Limit Dialog
    if (uiState.isCustomCurrentDialogOpen) {
        CustomCurrentLimitDialog(
            currentLimitMa = state.charging.limitCurrentMa,
            onConfirm = { viewModel.setCustomCurrentLimit(it) },
            onDismiss = { viewModel.closeCustomCurrentDialog() }
        )
    }

    // Deep Telemetry Modal Bottom Sheet
    if (uiState.isBatteryDetailSheetOpen && battDetails != null) {
        BatteryDetailBottomSheet(
            battDetails = battDetails,
            onDismiss = { viewModel.closeBatteryDetailSheet() }
        )
    }

    val selectedTab = uiState.batterySubTab

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Dual-Tab Navigation Bar
        BatterySubscreenTabRow(
            selectedTab = selectedTab,
            onSelectTab = { viewModel.setBatterySubTab(it) }
        )

        if (selectedTab == 0) {
            // ============================================================
            //  BENTO CARD 1: MASTER SUPER FAST CHARGING & POWER MONITOR
            // ============================================================
        LynxCard(
            title = "Super Fast Charging & Monitor Daya",
            icon = Icons.Default.Bolt,
            accentColor = AccentCyan
        ) {
            // Master Switch: Super Fast Charging
            LynxSwitch(
                label = "Super Fast Charging",
                subLabel = "1-Click Universal Fast Charge Unlock. Membuka batas arus ke hardware maksimum (Pump Express 4.0, Qualcomm SMB, Xiaomi HyperCharge, SuperVOOC, Samsung SFC), otomatis bypass limitasi layar menyala & thermal throttling kernel.",
                checked = state.charging.extremeChargingEnabled,
                onCheckedChange = { viewModel.setMasterFastCharging(it) }
            )

            // Active Mode Status Pill
            val isExtreme = state.charging.extremeChargingEnabled
            val isOvernightLatched = battDetails?.isOvernightBypassLatched == true || (battDetails?.level ?: 0) >= 100
            val isBypassActive = state.charging.bypassEnabled && ((battDetails?.level ?: 0) >= state.charging.maxBatteryPercent || isOvernightLatched)
            val isSmartTapering = battDetails?.isSmartTaperingActive == true && state.charging.smartTaperingEnabled

            val modePillLabel = when {
                isOvernightLatched -> "100% Full Latch: Hardware Bypass Aktif (Net 0mA)"
                isBypassActive -> "Hardware Bypass Aktif (Baterai Latch ~0mA)"
                isSmartTapering -> "Smart Tapering Aktif (Mendinginkan Baterai 90%+)"
                isExtreme -> if (state.charging.isUnconstrainedMaxHw) "⚡ SUPER FAST CHARGE AKTIF (Max HW Unconstrained)" else "⚡ SUPER FAST CHARGE AKTIF (${state.charging.limitCurrentMa} mA)"
                else -> "Pengisian Standar Regulated (${state.charging.limitCurrentMa} mA)"
            }
            val modePillColor = when {
                isOvernightLatched || isBypassActive -> AccentCyan
                isSmartTapering -> AccentGreen
                isExtreme -> AccentCyan
                else -> AccentOrange
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = modePillColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, modePillColor.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isExtreme) Icons.Default.Bolt else Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = modePillColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = modePillLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = modePillColor
                    )
                }
            }

            // Laptop / PC Port Safeguard Alert
            if (battDetails?.isLaptopPort == true) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(1.2.dp, AccentCyan.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Laptop,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Port Host PC/Laptop Terdeteksi (${battDetails.portType})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan
                            )
                            Text(
                                text = "Arus otomatis dibatasi ke 1.5A demi melindungi sekering/polyfuse port USB laptop Anda.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.5.sp,
                                color = TextPrimary.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Cable Resistance Badge (if available)
            if (battDetails != null && battDetails.cableResistanceMohm > 0) {
                val rColor = if (battDetails.cableResistanceMohm < 150) AccentGreen else if (battDetails.cableResistanceMohm < 250) AccentOrange else AccentRed
                val rStatus = if (battDetails.cableResistanceMohm < 150) "Kualitas Bagus" else if (battDetails.cableResistanceMohm < 250) "Standar" else "Resistansi Tinggi (Drop Voltase)"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Resistansi Kabel Fisik:",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "${battDetails.cableResistanceMohm} mΩ ($rStatus)",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = rColor
                    )
                }
            }

            // Active IC & Fast Charge Protocol Badges
            val icLabel = battDetails?.activeICName?.ifBlank { "Direct Pump 2:1 / PMIC" } ?: "Direct Pump 2:1 / PMIC"
            val protoLabel = battDetails?.fastChargeProtocol?.ifBlank { "Standard DCP / Battery" } ?: "Standard DCP / Battery"

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

            // Dual Wattmeter
            if (battDetails != null) {
                val inputWatt = if (battDetails.adapterWatt > 0.05f) battDetails.adapterWatt else battDetails.chargerWatt
                val netBattWatt = (battDetails.voltageMv.toFloat() * battDetails.currentMa.coerceAtLeast(0).toFloat()) / 1_000_000f
                val netWattDisplay = if (netBattWatt > 0.05f) netBattWatt else battDetails.chargerWatt
                val efficiency = battDetails.chargingEfficiencyPercent.coerceIn(0, 100)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Column A: Adapter Input
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BgCard,
                                border = BorderStroke(0.8.dp, BorderGlass),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Daya Masuk Adaptor", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
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

                        // Efficiency Bar
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
                    }
                }
            }

            // Real-time 60s Bezier Sparkline Waveform
            ChargingSparklineWaveform(
                samples = battDetails?.currentHistorySamples ?: emptyList(),
                currentMa = battDetails?.currentMa ?: 0,
                accentColor = AccentCyan,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Quick-Pills for Current Limit
            Text(
                text = "PILIHAN CEPAT BATAS ARUS",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            Spacer(Modifier.height(4.dp))

            val quickPresets = listOf(
                Triple("2.0A", 2000, false),
                Triple("4.5A", 4500, false),
                Triple("6.0A", 6000, false),
                Triple("10.0A", 10000, false),
                Triple("15.0A", 15000, false),
                Triple("Max HW", 15000, true),
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickPresets) { (label, ma, isMaxHw) ->
                    val isSelected = if (isMaxHw) state.charging.isUnconstrainedMaxHw else (!state.charging.isUnconstrainedMaxHw && state.charging.limitCurrentMa == ma)
                    val pillColor = if (isSelected) AccentCyan else BgElevated
                    val textColor = if (isSelected) AccentCyan else TextPrimary

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = pillColor.copy(alpha = if (isSelected) 0.18f else 0.5f),
                        border = BorderStroke(1.dp, if (isSelected) AccentCyan else BorderSubtle),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 60.dp, minHeight = 44.dp)
                            .clickable { viewModel.setQuickCurrentPreset(ma, isMaxHw) }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textColor
                            )
                        }
                    }
                }

                // Custom Pill
                item {
                    val isCustom = !state.charging.isUnconstrainedMaxHw && state.charging.limitCurrentMa !in listOf(2000, 4500, 6000, 10000, 15000)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCustom) AccentPurple.copy(alpha = 0.18f) else BgElevated.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (isCustom) AccentPurple else BorderSubtle),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 70.dp, minHeight = 44.dp)
                            .clickable { viewModel.openCustomCurrentDialog() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = if (isCustom) AccentPurple else TextSecondary, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isCustom) "${state.charging.limitCurrentMa} mA" else "Kustom...",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCustom) AccentPurple else TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // ============================================================
        //  BENTO CARD 2: PROTEKSI CERDAS & BYPASS MOTHERBOARD
        // ============================================================
        LynxCard(
            title = "Proteksi Cerdas & Hardware Bypass",
            icon = Icons.Default.Shield,
            accentColor = AccentGreen
        ) {
            // Motherboard Bypass Switch
            LynxSwitch(
                label = "Bypass Motherboard (Direct Power)",
                subLabel = "Mengalirkan daya adaptor langsung ke motherboard (Vsys) & MENGHENTIKAN pengisian baterai (Net Arus ~0mA). Khusus gaming berat agar baterai tetap dingin. JANGAN aktifkan jika berniat mengisi baterai!",
                checked = state.charging.bypassEnabled,
                onCheckedChange = { viewModel.setBypassCharging(it) }
            )

            var maxBatteryValue by remember {
                mutableFloatStateOf(state.charging.maxBatteryPercent.toFloat())
            }
            var isInteractingSafety by remember { mutableStateOf(false) }
            var lastSafetyInteraction by remember { mutableLongStateOf(0L) }

            LaunchedEffect(state.charging.maxBatteryPercent) {
                if (!isInteractingSafety && (System.currentTimeMillis() - lastSafetyInteraction > 2000L)) {
                    maxBatteryValue = state.charging.maxBatteryPercent.toFloat()
                }
            }

            LynxSlider(
                label = "Ambang Batas Auto-Bypass (Stop-At-%)",
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
                displayValue = if (maxBatteryValue >= 99.5f) "100% (Penuh & Overnight Guard)" else "${maxBatteryValue.toInt()}%",
                accentColor = AccentGreen
            )

            // Hysteresis Explanation Banner
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BgElevated.copy(alpha = 0.5f),
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(15.dp).padding(top = 1.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Hysteresis Buffer 95%: Baterai diisi cepat hingga ${maxBatteryValue.toInt()}%, lalu otomatis mengunci ke bypass. Jika baterai turun ke ${(maxBatteryValue * 0.95f).toInt()}%, pengisian akan aktif kembali. Pada 100%, sistem mengunci bypass semalaman agar aman ditinggal tidur di kasur.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Smart Tapering (90%+)
            LynxSwitch(
                label = "Smart Tapering (Pendinginan Baterai 90%+)",
                subLabel = "Menurunkan arus secara bertahap di atas 90% (90-95% 1500mA, 95-99% 750mA) demi mendinginkan sel baterai sebelum mencapai 100%.",
                checked = state.charging.smartTaperingEnabled,
                onCheckedChange = { viewModel.setSmartTapering(it) }
            )

            // Silent Emergency Guard Status Banner
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BgCard,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Silent Emergency Guard: Siaga di ambang ≥ 49.0°C sensor fisik nyata. Otomatis menurunkan arus darurat jika ponsel kepanasan parah.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = TextPrimary.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // ============================================================
        //  BENTO CARD 3: STATUS FISIK, KESEHATAN & TELEMETRI MENDALAM
        // ============================================================
        LynxCard(
            title = "Kesehatan Fisik & Sensor Baterai",
            icon = Icons.Default.Favorite,
            accentColor = AccentOrange
        ) {
            // Dual-Temperature Display
            if (battDetails != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Sensor Baterai / Spoofed
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sensor Baterai / Spoofed", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                            Spacer(Modifier.height(2.dp))
                            val isSpoofed = state.charging.thermalLockoutBypassEnabled && (battDetails.spoofedTempC in 27f..29f || battDetails.tempC in 27.5f..28.5f)
                            Text(
                                text = if (isSpoofed) "28.0°C (Bypass Aktif)" else String.format(java.util.Locale.US, "%.1f°C", battDetails.tempC),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSpoofed) AccentCyan else TextPrimary
                            )
                        }

                        // Right: Sensor Fisik Nyata (BMS / AP)
                        val physColor = if (battDetails.realPhysicalTempC >= 46f) AccentRed else if (battDetails.realPhysicalTempC >= 42f) AccentOrange else AccentGreen
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Sensor Fisik Nyata (BMS)", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f°C", battDetails.realPhysicalTempC),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = physColor
                            )
                        }
                    }
                }

                // Battery Metrics Grid
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated.copy(alpha = 0.5f),
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Level Kapasitas", style = MaterialTheme.typography.bodySmall, fontSize = 11.5.sp, color = TextSecondary)
                            Text("${battDetails.level}%", style = MaterialTheme.typography.bodySmall, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Status Kesehatan", style = MaterialTheme.typography.bodySmall, fontSize = 11.5.sp, color = TextSecondary)
                            Text(battDetails.health, style = MaterialTheme.typography.bodySmall, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Siklus Pengisian", style = MaterialTheme.typography.bodySmall, fontSize = 11.5.sp, color = TextSecondary)
                            Text(if (battDetails.cycleCount >= 0) "${battDetails.cycleCount} siklus" else "N/A (Kernel Generic)", style = MaterialTheme.typography.bodySmall, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tegangan Baterai", style = MaterialTheme.typography.bodySmall, fontSize = 11.5.sp, color = TextSecondary)
                            Text(String.format(java.util.Locale.US, "%.2f V", battDetails.voltageMv / 1000f), style = MaterialTheme.typography.bodySmall, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Kapasitas Terisi", style = MaterialTheme.typography.bodySmall, fontSize = 11.5.sp, color = TextSecondary)
                            Text("${battDetails.chargeCounterMah} mAh", style = MaterialTheme.typography.bodySmall, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }

                // Button: Lihat Detail Lengkap (Raw ADC & Thermal Matrix)
                OutlinedButton(
                    onClick = { viewModel.openBatteryDetailSheet() },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        } else {
            // ============================================================
            //  TAB 2: INFORMASI & STATISTIK KOMPREHENSIF
            // ============================================================
            BatteryInformationContent(
                uiState = uiState,
                viewModel = viewModel
            )
        }
    }
}

// ============================================================
//  HELPER: BATTERY SUBSCREEN TAB ROW
// ============================================================

@Composable
fun BatterySubscreenTabRow(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        Triple(0, "Kontrol & Tuning", Icons.Default.Bolt),
        Triple(1, "Informasi & Statistik", Icons.Default.Analytics)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
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
                val tabAccent = if (index == 0) AccentCyan else AccentBlue

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
                            contentDescription = null,
                            tint = if (isSelected) tabAccent else TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = title,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
//  HELPER: BATTERY INFORMATION & STATISTICS CONTENT (TAB 1)
// ============================================================

@Composable
fun BatteryInformationContent(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val stats = uiState.batteryInfoStats
    val battDetails = uiState.batteryDetails

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ============================================================
        //  BENTO CARD 1: STATUS DAYA & SESI PENGISIAN AKTIF / TERAKHIR
        // ============================================================
        LynxCard(
            title = "Status Daya & Sesi Pengisian",
            icon = Icons.Default.BatteryChargingFull,
            accentColor = AccentCyan
        ) {
            // Status & Power Source Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (stats.isCharging) AccentCyan.copy(alpha = 0.15f) else BgElevated,
                    border = BorderStroke(0.8.dp, if (stats.isCharging) AccentCyan.copy(alpha = 0.5f) else BorderGlass),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (stats.isCharging) AccentCyan else AccentOrange)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stats.batteryStatusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (stats.isCharging) AccentCyan else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stats.powerSourceText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Timer & Delta Level Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Durasi Pengecasan", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (stats.isSessionCharging) stats.chargingDurationText else "Tidak Aktif",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (stats.isSessionCharging) AccentCyan else TextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Progres Delta Level", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (stats.isSessionCharging) "${stats.chargeStartLevel}% ➔ ${stats.currentChargeLevel}% (+${stats.chargeDeltaLevel}%)" else "${battDetails?.level ?: 0}% Saat Ini",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Energy In & ETA Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Total Energi Masuk", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "+${stats.totalEnergyInMah} mAh • +${String.format(java.util.Locale.US, "%.1f", stats.totalEnergyInMwh)} Wh",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Estimasi Sampai Penuh", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stats.timeToFullEstimatedText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = AccentOrange
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Charging Speed (Peak & Average)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated.copy(alpha = 0.5f),
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Daya Puncak", style = MaterialTheme.typography.labelSmall, fontSize = 9.5.sp, color = TextSecondary)
                        Text("${String.format(java.util.Locale.US, "%.1f", stats.peakChargingWatt)} W", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Daya Rata-rata", style = MaterialTheme.typography.labelSmall, fontSize = 9.5.sp, color = TextSecondary)
                        Text("${String.format(java.util.Locale.US, "%.1f", stats.avgChargingWatt)} W", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AccentCyan)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Arus Puncak", style = MaterialTheme.typography.labelSmall, fontSize = 9.5.sp, color = TextSecondary)
                        Text("${stats.peakChargingMa} mA", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AccentGreen)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Last Session Summary Banner
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BgCard,
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Sesi Terakhir: ${stats.lastChargingSessionSummary}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // ============================================================
        //  BENTO CARD 2: PENGGUNAAN & KETAHANAN DAYA (DISCHARGE / SOT)
        // ============================================================
        LynxCard(
            title = "Penggunaan & Ketahanan Daya",
            icon = Icons.Default.Timer,
            accentColor = AccentGreen
        ) {
            // Row 1: Time since unplugged & SOT
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Sejak Charger Dicabut", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stats.timeSinceUnpluggedText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Screen-On Time (SOT)", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stats.screenOnTimeText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Row 2: Screen-Off & Deep Sleep Ratio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Screen-Off (Siaga)", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stats.screenOffTimeText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Rasio Deep Sleep", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${stats.deepSleepPercentage}% Tidur Lepas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Drain Rate & Remaining Life Table
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated.copy(alpha = 0.5f),
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Laju Saat Layar Aktif", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                        Text(stats.activeDrainRatePerHour, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Laju Saat Layar Mati (Idle)", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                        Text(stats.idleDrainRatePerHour, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Estimasi Sisa Layar Aktif", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                        Text(stats.estimatedScreenRemainingText, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Estimasi Sisa Waktu Siaga", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                        Text(stats.estimatedStandbyRemainingText, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                    }
                }
            }
        }

        // ============================================================
        //  BENTO CARD 3: SPESIFIKASI HARDWARE & DEGRADASI SEL BATERAI
        // ============================================================
        LynxCard(
            title = "Spesifikasi Hardware & Degradasi Sel",
            icon = Icons.Default.Memory,
            accentColor = AccentBlue
        ) {
            // Design vs FCC Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Kapasitas Desain Pabrik", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text("${stats.designCapacityMah} mAh", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Kapasitas Riil Penuh (FCC)", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text("${stats.fullChargeCapacityMah} mAh", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AccentCyan)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // State of Health (SoH) Progress Bar
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated.copy(alpha = 0.5f),
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Kesehatan Kimia (State of Health)", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                        Text("${stats.stateOfHealthPercent}% (Keausan ${stats.wearLevelPercent}%)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (stats.stateOfHealthPercent >= 85) AccentGreen else AccentOrange)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (stats.stateOfHealthPercent.toFloat() / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (stats.stateOfHealthPercent >= 85) AccentGreen else AccentOrange,
                        trackColor = BorderSubtle
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Hardware Details Grid
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgCard,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Siklus Pengisian Hardware", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                        Text("${stats.cycleCount} siklus", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Teknologi Kimia Baterai", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                        Text(stats.technology, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Resistansi Internal Sel (ESR)", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                        Text("${stats.batteryResistanceMohm} mΩ (Kondisi Prima)", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Resistansi Kabel Fisik", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                        Text("${battDetails?.cableResistanceMohm ?: 100} mΩ", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Rentang Tegangan Sel", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                        Text("Nominal 3.85V (3.4V - 4.45V)", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Tegangan Sel Real-time", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                        Text(String.format(java.util.Locale.US, "%.2f V (%d mV)", stats.voltageNowMv / 1000f, stats.voltageNowMv), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Chip Identifier Chip
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Chip Pengontrol: ${stats.fuelGaugeChip}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
            }
        }

        // ============================================================
        //  BENTO CARD 4: DIAGNOSTIK JALUR LISTRIK & SUHU MULTI-TITIK
        // ============================================================
        LynxCard(
            title = "Diagnostik Jalur Listrik & Suhu",
            icon = Icons.Default.Bolt,
            accentColor = AccentOrange
        ) {
            // Protocol & VBUS Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Protokol Negosiasi", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(stats.negotiatedProtocolText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = AccentCyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Tegangan Jalur VBUS", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text("${String.format(java.util.Locale.US, "%.1f", stats.vbusVoltageV)} V Adaptor", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = AccentOrange)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 4 Thermal Zone Matrix
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated.copy(alpha = 0.5f),
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Matriks Suhu Titik Kritis", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Sensor 1: BMS Inti
                        Surface(shape = RoundedCornerShape(8.dp), color = BgCard, modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("BMS Inti", fontSize = 9.5.sp, color = TextSecondary)
                                Text("${String.format(java.util.Locale.US, "%.1f", stats.bmsTempC)}°C", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                            }
                        }
                        // Sensor 2: IC Charger
                        Surface(shape = RoundedCornerShape(8.dp), color = BgCard, modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("IC Charger", fontSize = 9.5.sp, color = TextSecondary)
                                Text("${String.format(java.util.Locale.US, "%.1f", stats.chargerIcTempC)}°C", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                            }
                        }
                        // Sensor 3: Port USB
                        Surface(shape = RoundedCornerShape(8.dp), color = BgCard, modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Port USB", fontSize = 9.5.sp, color = TextSecondary)
                                Text("${String.format(java.util.Locale.US, "%.1f", stats.usbPortTempC)}°C", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                            }
                        }
                        // Sensor 4: SoC / Board
                        Surface(shape = RoundedCornerShape(8.dp), color = BgCard, modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Board AP", fontSize = 9.5.sp, color = TextSecondary)
                                Text("${String.format(java.util.Locale.US, "%.1f", uiState.resolveCpuTempC().toFloat())}°C", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Button: Lihat Detail Lengkap
            OutlinedButton(
                onClick = { viewModel.openBatteryDetailSheet() },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Buka Telemetri Raw ADC & Sensor Matrix", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

// ============================================================
//  HELPER: CHARGING REAL-TIME 60S BEZIER SPARKLINE WAVEFORM
// ============================================================

@Composable
fun ChargingSparklineWaveform(
    samples: List<Float>,
    currentMa: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgCard,
        border = BorderStroke(0.8.dp, BorderGlass),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(accentColor, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Arus Real-time (60s Buffer)",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
                Text(
                    text = if (currentMa > 0) "+$currentMa mA" else "$currentMa mA",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (currentMa > 0) AccentGreen else TextPrimary
                )
            }
            Spacer(Modifier.height(6.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                if (samples.size < 2) {
                    drawLine(
                        color = accentColor.copy(alpha = 0.25f),
                        start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                        strokeWidth = 2f
                    )
                    return@Canvas
                }

                val minVal = (samples.minOrNull() ?: 0f) - 100f
                val maxVal = (samples.maxOrNull() ?: 1000f) + 100f
                val range = (maxVal - minVal).coerceAtLeast(200f)

                val points = samples.mapIndexed { index, value ->
                    val x = (index.toFloat() / (samples.size - 1).toFloat()) * size.width
                    val y = size.height - (((value - minVal) / range) * size.height)
                    androidx.compose.ui.geometry.Offset(x, y.coerceIn(4f, size.height - 4f))
                }

                val strokePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val cur = points[i]
                        val cx = (prev.x + cur.x) / 2f
                        cubicTo(cx, prev.y, cx, cur.y, cur.x, cur.y)
                    }
                }

                val fillPath = Path().apply {
                    addPath(strokePath)
                    lineTo(points.last().x, size.height)
                    lineTo(points.first().x, size.height)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(accentColor.copy(alpha = 0.25f), Color.Transparent),
                        startY = 0f,
                        endY = size.height
                    )
                )

                drawPath(
                    path = strokePath,
                    color = accentColor,
                    style = Stroke(width = 2.5.dp.toPx())
                )

                val lastPoint = points.last()
                drawCircle(
                    color = accentColor,
                    radius = 3.5.dp.toPx(),
                    center = lastPoint
                )
                drawCircle(
                    color = Color.White,
                    radius = 1.8.dp.toPx(),
                    center = lastPoint
                )
            }
        }
    }
}

// ============================================================
//  HELPER: CUSTOM CURRENT LIMIT DIALOG
// ============================================================

@Composable
fun CustomCurrentLimitDialog(
    currentLimitMa: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var textVal by remember { mutableStateOf(currentLimitMa.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Input Arus Kustom (mA)", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column {
                Text(
                    text = "Tentukan batas arus pengisian daya manual tanpa batasan slider. Berlaku universal untuk seluruh protokol.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = textVal,
                    onValueChange = { if (it.all { ch -> ch.isDigit() } && it.length <= 6) textVal = it },
                    label = { Text("Batas Arus (mA)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedLabelColor = AccentCyan,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Contoh: 8000 (8A), 12000 (12A), 18000 (18A)",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ma = textVal.toIntOrNull() ?: currentLimitMa
                    onConfirm(ma)
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black)
            ) {
                Text("Terapkan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = TextSecondary)
            }
        },
        containerColor = BgCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary
    )
}

// ============================================================
//  HELPER: BATTERY DETAIL MODAL BOTTOM SHEET
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryDetailBottomSheet(
    battDetails: BatteryDetails,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFA0D1017),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                color = Color(0x33FFFFFF),
                shape = CircleShape,
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 38.dp, height = 4.dp)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Analytics, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Hardware Telemetri & Raw ADC", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextSecondary)
                }
            }

            // Section 1: Raw ADC Registers
            Text("REGISTER CHARGER & ADC PHYSICAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AccentCyan)
            if (battDetails.rawAdcDetails.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        battDetails.rawAdcDetails.forEach { (k, v) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(k, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                                Text(v, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            } else {
                Text("Tidak ada register ADC terdeteksi.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }

            // Section 2: Thermal Zone Matrix
            Text("MATRIKS SENSOR THERMAL ZONE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AccentOrange)
            if (battDetails.thermalZoneMatrix.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        battDetails.thermalZoneMatrix.forEach { (name, temp) ->
                            val tColor = if (temp >= 46f) AccentRed else if (temp >= 42f) AccentOrange else AccentGreen
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(name, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                                Text(String.format(java.util.Locale.US, "%.1f°C", temp), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tColor)
                            }
                        }
                    }
                }
            } else {
                Text("Tidak ada data thermal zone.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
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
