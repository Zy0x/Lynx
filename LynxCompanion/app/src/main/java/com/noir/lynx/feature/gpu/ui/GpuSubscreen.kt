package com.noir.lynx.feature.gpu.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.noir.lynx.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.theme.rememberFluidFlingBehavior
import com.noir.lynx.ui.*

/**
 * Lazy items list scope for GPU & Display Category.
 */
fun LazyListScope.tuningGpuCategoryItems(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    targetTab: Int = uiState.selectedGpuTab,
    onOpenStressTest: () -> Unit = {}
) {
    val state = uiState.state
    val gpu = uiState.gpuInfo
    val graphics = uiState.graphicsHwui
    val gpuOverride = state.profileOverrides[com.noir.lynx.profiles.ProfileDomain.GPU]
        ?: state.profileOverrides[com.noir.lynx.profiles.ProfileDomain.GRAPHICS]

    when (targetTab) {
        0 -> {
            // ── TAB 0: TUNING ──
            // 0. GPU & Display Hero Card (Zen Pulse Telemetry & System Status)
            item(key = "gpu_hero_card") {
                GpuHeroCard(
                    uiState = uiState,
                    gpu = gpu
                )
            }

            // ── SECTION 1: PROFIL & CLOCK OPP ──
            item(key = "section_header_profiles_clock") {
                GpuSectionHeader(
                    title = "Profil & Clock OPP",
                    subtitle = "Harmoni preset performa & kontrol frekuensi clock fisik",
                    accentColor = com.noir.lynx.designsystem.theme.AccentCyan,
                    badge = "PRESET & TUNER"
                )
            }

            // 1. One-Click GPU Quick Profiles (Harmonic Presets)
            item(key = "gpu_quick_profiles") {
                GpuQuickProfilesCard(
                    activeProfile = gpu.activeProfile,
                    onSelectProfile = { viewModel.applyGpuProfile(it, context) }
                )
            }

            gpuOverride?.let { overrideProfile ->
                item(key = "gpu_profile_override_status") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10141E),
                        border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.profile_override_status, "GPU", overrideProfile.uppercase(), state.activeProfile.uppercase()),
                                color = com.noir.lynx.designsystem.theme.TextSecondary.copy(alpha = 0.85f),
                                fontSize = 10.5.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.resetGpuProfileToMaster(context) }) {
                                Text("Ikuti Global", fontSize = 10.5.sp, color = com.noir.lynx.designsystem.theme.AccentCyan)
                            }
                        }
                    }
                }
            }

            // 2. Master GPU Tuner & Telemetri Card (Dual-Pill Clock, Lock, & Waveform)
            item(key = "gpu_master_tuner") {
                GpuMasterTunerCard(
                    gpu = gpu,
                    onSetFreq = { minMhz, maxMhz -> viewModel.setGpuFreq(minMhz, maxMhz) },
                    onSetLock = { locked -> viewModel.setGpuLock(locked) }
                )
            }

            // ── SECTION 2: DRIVER HARDWARE KERNEL ──
            item(key = "section_header_kernel_hardware") {
                GpuSectionHeader(
                    title = "Driver Hardware Kernel",
                    subtitle = "Akselerasi silikon SoC, preemption, power collapse & scheduling",
                    accentColor = com.noir.lynx.designsystem.theme.AccentOrange,
                    badge = "KERNEL HAL"
                )
            }

            // Unified Adaptive Hardware Acceleration Card (SoC-Specific Engine + Deep Silicon)
            item(key = "gpu_adaptive_hw") {
                AdaptiveGpuHardwareCard(
                    gpu = gpu,
                    silicon = uiState.gpuSilicon,
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
                    onSetGovernor = { viewModel.setGpuGovernor(it) },
                    onSetSptpPc = { viewModel.setGpuSptpPc(it) },
                    onSetSkipSaveRestore = { viewModel.setGpuSkipSaveRestore(it) },
                    onSetPreemptLevel = { viewModel.setGpuPreemptLevel(it) },
                    onSetWakeNice = { viewModel.setGpuWakeNice(it) },
                    onSetMinPwrLevel = { viewModel.setGpuMinPwrLevel(it) },
                    onSetGpuLlcSlice = { viewModel.setGpuLlcSlice(it) }
                )
            }

            // ── SECTION 3: COMPOSITOR & PIPELINE HWUI ──
            item(key = "section_header_compositor_pipeline") {
                GpuSectionHeader(
                    title = "Compositor & Pipeline HWUI",
                    subtitle = "Backend render Vulkan, buffer swapchain, offset fase & shader cache",
                    accentColor = com.noir.lynx.designsystem.theme.AccentPurple,
                    badge = "RENDER ENGINE"
                )
            }

            // Unified Graphics Driver & HWUI Engine Card
            item(key = "gpu_graphics_hwui") {
                GraphicsDriverHwuiCard(
                    graphics = graphics,
                    silicon = uiState.gpuSilicon,
                    onSetGameDriver = { mode -> viewModel.setUpdatableGameDriver(mode) },
                    onSetRenderer = { backend -> viewModel.setHwuiRenderer(backend) },
                    onSetLatch = { latch -> viewModel.setSurfaceFlingerLatch(latch) },
                    onSetDisableBackpressure = { viewModel.setSurfaceFlingerDisableBackpressure(it) },
                    onSetEarlyPhase = { viewModel.setSurfaceFlingerEarlyPhase(it) },
                    onSetPhaseOffset = { earlyNs, earlyGlNs -> viewModel.setSurfaceFlingerPhaseOffset(earlyNs, earlyGlNs) },
                    onSetVsyncSwapBypass = { viewModel.setVsyncSwapBypass(it) },
                    onSetMsaa = { msaa -> viewModel.setForceMsaa(msaa) },
                    onSetOemShield = { shield -> viewModel.setOemThrottlerShield(shield) },
                    onRefreshShaderCache = { viewModel.refreshGraphicsHwui() },
                    onSetTextureCacheSize = { mb -> viewModel.setTextureCacheSize(mb) },
                    onSetAsyncShaderPipeline = { enabled -> viewModel.setAsyncShaderPipeline(enabled) },
                    onSetPureHwVsync = { enabled -> viewModel.setPureHwVsync(enabled) },
                    onSetShaderCompilerThreads = { threads -> viewModel.setShaderCompilerThreads(threads) },
                    onSetShaderBlobCache = { mb -> viewModel.setShaderBlobCacheSize(mb) },
                    onSetSwapchainBufferMode = { mode -> viewModel.setSwapchainBufferMode(mode) }
                )
            }

            // GPU Shader Cache & Integrity Rebuilder
            item(key = "gpu_shader_rebuilder") {
                GpuShaderRebuilderCard()
            }

            // ── SECTION 4: PENGATURAN PER-APLIKASI ──
            item(key = "section_header_per_app_hub") {
                GpuSectionHeader(
                    title = "Pengaturan Per-Aplikasi",
                    subtitle = "Konfigurasi driver grafis & refresh rate spesifik per-paket",
                    accentColor = com.noir.lynx.designsystem.theme.AccentCyan,
                    badge = "GAME & EMULATOR HUB"
                )
            }

            // Per-App Rendering Management (Game Driver Hub)
            item(key = "gpu_per_app_hub") {
                PerAppGraphicsHubCard(
                    rules = uiState.perAppGraphicsRules,
                    installedApps = uiState.installedAppList,
                    supportedRates = uiState.supportedRefreshRates.ifEmpty { listOf(uiState.displayRefreshRate.takeIf { it > 0 } ?: 60) },
                    isAngleSupported = graphics.isAngleSupported,
                    onSaveRule = { rule -> viewModel.savePerAppGraphicsRule(rule, context) },
                    onDeleteRule = { pkg -> viewModel.deletePerAppGraphicsRule(pkg, context) }
                )
            }
        }
        1 -> {
            // ── TAB 1: LAYAR (DISPLAY & KALIBRASI PANEL) ──
            item(key = "display_hero_card") {
                DisplayHeroCard(uiState = uiState)
            }

            // ── SECTION 1: REFRESH RATE & RESPON SENTUH ──
            item(key = "section_header_display_refresh") {
                GpuSectionHeader(
                    title = "Refresh Rate & Respon Sentuh",
                    subtitle = "Mode fisik panel HWC, Auto Dynamic, TouchBoost & Render Scale",
                    accentColor = com.noir.lynx.designsystem.theme.AccentGreen,
                    badge = "OUTPUT & TOUCH"
                )
            }

            // Display Refresh Rate & Touch Card
            item(key = "gpu_display_refresh") {
                DisplayRefreshRateTouchCard(
                    currentHz = if (uiState.displayRefreshRate > 0) uiState.displayRefreshRate else 60,
                    isAuto = uiState.isAutoRefreshRate,
                    supportedRates = uiState.supportedRefreshRates.ifEmpty { listOf(uiState.displayRefreshRate.takeIf { it > 0 } ?: 60) },
                    touchBoost = state.displayTouch.touchboost,
                    touchBoostDurationMs = uiState.gpuSilicon.touchBoostDurationMs,
                    dcDimmingSupported = graphics.isDcDimmingSupported,
                    dcDimmingEnabled = graphics.dcDimmingEnabled,
                    isEsportsDownscaleActive = graphics.isEsportsDownscaleActive,
                    nativeResolution = graphics.nativeResolution,
                    activeResolution = graphics.activeResolution,
                    forcedFpsUnlockerHz = uiState.forcedFpsUnlockerHz,
                    onSetRefreshRate = { hz, isAuto -> viewModel.setDisplayRefreshRate(hz, isAuto) },
                    onSetTouchBoost = { viewModel.setTouchboost(it) },
                    onSetTouchBoostDuration = { viewModel.setTouchBoostDuration(it) },
                    onSetDcDimming = { viewModel.setDcDimming(it) },
                    onSetRenderScale = { preset -> viewModel.setDisplayRenderScale(preset) },
                    onSetForcedFpsUnlocker = { fps -> viewModel.setForcedFpsUnlocker(fps) }
                )
            }

            // ── SECTION 2: KALIBRASI WARNA & VISUAL ──
            item(key = "section_header_color_management") {
                GpuSectionHeader(
                    title = "Kalibrasi Warna & Visual",
                    subtitle = "SurfaceFlinger Matrix 1015, D65 White Point, KCAL hardware & HBM",
                    accentColor = com.noir.lynx.designsystem.theme.AccentOrange,
                    badge = "COLOR & D65"
                )
            }

            // Color Management Card (SurfaceFlinger Matrix 1015 & D65 White Point)
            item(key = "gpu_color_management") {
                ColorManagementCard(
                    displayCalibration = uiState.displayCalibration,
                    colorProfile = uiState.colorMatrixProfile,
                    isCalibrationEnabled = uiState.isColorCalibrationEnabled,
                    colorConflict = uiState.colorConflictWarning,
                    onToggleCalibration = { viewModel.setColorCalibrationEnabled(it) },
                    onApplyProfile = { viewModel.applyColorProfile(it) },
                    onResetProfile = { viewModel.resetColorProfile() },
                    onSetUniversalColor = { r, g, b -> viewModel.setUniversalColor(r, g, b) },
                    onSetHbm = { viewModel.setHbmEnabled(it) }
                )
            }

            // Kernel KCAL Hardware Card (If supported by kernel)
            if (uiState.displayCalibration.isKcalSupported) {
                item(key = "gpu_kcal_hardware") {
                    KcalHardwareCard(
                        displayCalibration = uiState.displayCalibration,
                        onSetKcal = { en, r, g, b, sat, v, c, h ->
                            viewModel.setKcalParams(en, r, g, b, sat, v, c, h)
                        }
                    )
                }
            }
        }
        2 -> {
            // ── TAB 2: LAB (PERFORMANCE LAB & IN-GAME OSD) ──
            // ── SECTION 1: BENCHMARK & OVERLAY ──
            item(key = "section_header_lab_benchmark") {
                GpuSectionHeader(
                    title = "Benchmark & Overlay",
                    subtitle = "Uji kestabilan GPU 30s/60s & floating telemetry in-game HUD",
                    accentColor = com.noir.lynx.designsystem.theme.AccentOrange,
                    badge = "REAL-TIME TOOLS"
                )
            }

            item(key = "gpu_stress_test_trigger") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF10141E),
                    border = BorderStroke(0.6.dp, Color(0x1AFFFFFF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = com.noir.lynx.designsystem.theme.AccentOrange.copy(alpha = 0.12f),
                                border = BorderStroke(0.6.dp, com.noir.lynx.designsystem.theme.AccentOrange.copy(alpha = 0.3f)),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = com.noir.lynx.designsystem.theme.AccentOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "GPU Stability Stress Test",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = com.noir.lynx.designsystem.theme.TextPrimary
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Uji siklus beban GPU 30s/60s untuk mengukur thermal throttling & stabilitas clock",
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp,
                                    color = com.noir.lynx.designsystem.theme.TextSecondary.copy(alpha = 0.75f)
                                )
                            }
                        }
                        Button(
                            onClick = onOpenStressTest,
                            shape = RoundedCornerShape(9.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = com.noir.lynx.designsystem.theme.AccentOrange),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "Uji Beban",
                                color = Color.Black,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item(key = "gpu_hud_card") {
                FloatingGameHudCard(
                    isHudRunning = uiState.isGameHudActive,
                    hudMode = uiState.hudMode,
                    hudStyle = uiState.hudStyle,
                    onToggleHud = { viewModel.toggleGameHud(context, !uiState.isGameHudActive) },
                    onSetMode = { viewModel.setHudMode(context, it) },
                    onSetStyle = { viewModel.setHudStyle(context, it) }
                )
            }

            // ── SECTION 2: ANALISIS FRAME SURFACEFLINGER ──
            item(key = "section_header_blast_recorder") {
                GpuSectionHeader(
                    title = "Analisis Frame SurfaceFlinger",
                    subtitle = "Perekam frame BLAST, kalkulasi jank 1% low & log sesi",
                    accentColor = com.noir.lynx.designsystem.theme.AccentPurple,
                    badge = "BLAST RECORDER"
                )
            }

            item(key = "gpu_lab_record") {
                LabRecordCard(
                    isRecording = uiState.isLabRecording,
                    onStartRecording = { viewModel.startLabRecording(it) },
                    onStopRecording = { viewModel.stopLabRecording(context) }
                )
            }

            if (uiState.lastLabReport != null) {
                item(key = "gpu_session_summary") {
                    SessionSummaryCard(
                        report = uiState.lastLabReport,
                        onExportCsv = { viewModel.exportLabReport(context, uiState.lastLabReport) }
                    )
                }
                item(key = "gpu_drop_analysis") {
                    DropAnalysisCard(report = uiState.lastLabReport)
                }
            }

            item(key = "gpu_session_history") {
                SessionHistoryList(
                    sessions = uiState.savedLabSessions,
                    onSelectSession = { session ->
                        viewModel.selectLabReport(session)
                    }
                )
            }
        }
        3 -> {
            // ── TAB 3: INFO (UNIFIED HARDWARE & COMPOSITOR DIAGNOSTICS) ──
            item(key = "gpu_hardware_info") {
                GpuHardwareInfoDashboard(
                    caps = uiState.graphicsCapabilities,
                    pipeline = uiState.displayPipeline,
                    gpuInfo = uiState.gpuInfo,
                    currentHz = if (uiState.displayRefreshRate > 0) uiState.displayRefreshRate else 60,
                    onRefresh = {
                        viewModel.refreshGraphicsCapabilities()
                        viewModel.refreshDisplayPipeline()
                        viewModel.refreshGpuInfo()
                    }
                )
            }
        }
    }
}

/**
 * Subscreen 2: GPU & Display Category Composable
 */
@Composable
fun GpuSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showStressDialog by remember { mutableStateOf(false) }

    // Auto refresh data on load
    LaunchedEffect(Unit) {
        viewModel.refreshGpuInfo()
        viewModel.refreshGpuSilicon()
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

    val lazyState = rememberLazyListState()
    val fling = rememberFluidFlingBehavior()
    LazyColumn(
        state = lazyState,
        flingBehavior = fling,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tuningGpuCategoryItems(
            uiState = uiState,
            viewModel = viewModel,
            context = context,
            onOpenStressTest = { showStressDialog = true }
        )
    }

    if (showStressDialog) {
        GpuStabilityStressDialog(
            silicon = uiState.gpuSilicon,
            onStartStressTest = { dur, onComplete ->
                viewModel.startGpuStabilityBenchmark(dur, onComplete)
            },
            onStopStressTest = {
                viewModel.stopGpuStabilityBenchmark()
            },
            onDismiss = { showStressDialog = false }
        )
    }
}

/**
 * Backward compatibility alias for legacy call sites.
 */
@Composable
fun TuningGpuCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    GpuSubscreen(uiState = uiState, viewModel = viewModel, modifier = modifier)
}

/**
 * Clean Cyberpunk section header for GPU & Display architecture domains.
 * Typographic anchor with accent pillar and category badge.
 */
@Composable
fun GpuSectionHeader(
    title: String,
    subtitle: String,
    accentColor: Color,
    badge: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Column {
                Text(
                    text = title.uppercase(),
                    color = com.noir.lynx.designsystem.theme.TextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = subtitle,
                    color = com.noir.lynx.designsystem.theme.TextSecondary.copy(alpha = 0.75f),
                    fontSize = 9.5.sp
                )
            }
        }
        if (badge != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = accentColor.copy(alpha = 0.12f),
                border = BorderStroke(0.6.dp, accentColor.copy(alpha = 0.35f))
            ) {
                Text(
                    text = badge,
                    color = accentColor,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                )
            }
        }
    }
}
