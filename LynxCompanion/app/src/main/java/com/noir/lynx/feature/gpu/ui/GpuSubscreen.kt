package com.noir.lynx.feature.gpu.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.noir.lynx.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
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
    targetTab: Int = uiState.selectedGpuTab
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

            // 1. One-Click GPU Quick Profiles (Harmonic Presets)
            item(key = "gpu_quick_profiles") {
                GpuQuickProfilesCard(
                    activeProfile = gpu.activeProfile,
                    onSelectProfile = { viewModel.applyGpuProfile(it, context) }
                )
            }

            gpuOverride?.let { overrideProfile ->
                item(key = "gpu_profile_override_status") {
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), color = com.noir.lynx.designsystem.theme.BgSurfaceLowest) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.profile_override_status, "GPU", overrideProfile.uppercase(), state.activeProfile.uppercase()),
                                color = com.noir.lynx.designsystem.theme.TextSecondary,
                                fontSize = 10.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.resetGpuProfileToMaster(context) }) {
                                Text("Ikuti Global", fontSize = 10.sp)
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

            // 2b. Adaptive Hardware Acceleration Card (SoC-Specific Engine)
            item(key = "gpu_adaptive_hw") {
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
            }

            // 3. Graphics Driver & HWUI Engine Card (Game Driver, SkiaVK, Latch, Backpressure, Shader Cache, Universal Compositor)
            item(key = "gpu_graphics_hwui") {
                GraphicsDriverHwuiCard(
                    graphics = graphics,
                    onSetGameDriver = { mode -> viewModel.setUpdatableGameDriver(mode) },
                    onSetRenderer = { backend -> viewModel.setHwuiRenderer(backend) },
                    onSetLatch = { latch -> viewModel.setSurfaceFlingerLatch(latch) },
                    onSetDisableBackpressure = { viewModel.setSurfaceFlingerDisableBackpressure(it) },
                    onSetEarlyPhase = { viewModel.setSurfaceFlingerEarlyPhase(it) },
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

            // 2b. Per-App Rendering Management (Game Driver Hub)
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

            // 3. Display Refresh Rate & Touch Card (Ground Truth Hardware Supported Modes + Esports Render Scaler)
            item(key = "gpu_display_refresh") {
                DisplayRefreshRateTouchCard(
                    currentHz = if (uiState.displayRefreshRate > 0) uiState.displayRefreshRate else 60,
                    isAuto = uiState.isAutoRefreshRate,
                    supportedRates = uiState.supportedRefreshRates.ifEmpty { listOf(uiState.displayRefreshRate.takeIf { it > 0 } ?: 60) },
                    touchBoost = state.displayTouch.touchboost,
                    dcDimmingSupported = graphics.isDcDimmingSupported,
                    dcDimmingEnabled = graphics.dcDimmingEnabled,
                    isEsportsDownscaleActive = graphics.isEsportsDownscaleActive,
                    nativeResolution = graphics.nativeResolution,
                    activeResolution = graphics.activeResolution,
                    forcedFpsUnlockerHz = uiState.forcedFpsUnlockerHz,
                    onSetRefreshRate = { hz, isAuto -> viewModel.setDisplayRefreshRate(hz, isAuto) },
                    onSetTouchBoost = { viewModel.setTouchboost(it) },
                    onSetDcDimming = { viewModel.setDcDimming(it) },
                    onSetRenderScale = { preset -> viewModel.setDisplayRenderScale(preset) },
                    onSetForcedFpsUnlocker = { fps -> viewModel.setForcedFpsUnlocker(fps) }
                )
            }

            // 4. Color Management Card (SurfaceFlinger Matrix 1015 & D65 White Point)
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

            // 5. Kernel KCAL Hardware Card (If supported by kernel)
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
        1 -> {
            // ── TAB 1: LAB (PERFORMANCE LAB & IN-GAME OSD) ──
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
        2 -> {
            // ── TAB 2: INFO (UNIFIED HARDWARE & COMPOSITOR DIAGNOSTICS) ──
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

    val lazyState = rememberLazyListState()
    val fling = rememberFluidFlingBehavior()
    LazyColumn(
        state = lazyState,
        flingBehavior = fling,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tuningGpuCategoryItems(uiState, viewModel, context)
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
