package com.noir.lynx.ui

import com.noir.lynx.core.locale.AppLocale

import com.noir.lynx.R

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.*
import com.noir.lynx.feature.battery.BatteryInformationUiState
import com.noir.lynx.hardware.WriteResult
import com.noir.lynx.engine.CpuPolicyManager
import com.noir.lynx.engine.RecoveryManager
import com.noir.lynx.kernel.CpuIdleDetector
import com.noir.lynx.kernel.CpuSetBackendFactory
import com.noir.lynx.kernel.SchedulerBackendFactory
import com.noir.lynx.profiles.CpuControlProfile
import com.noir.lynx.safety.ProtectedTaskManager
import com.noir.lynx.service.LynxAppAutomationService
import com.noir.lynx.sync.StateFileObserver
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * LynxViewModel: Manages UI state and orchestrates root operations.
 *
 * Exposes a single [uiState] StateFlow consumed by MainActivity Compose UI.
 * Uses [StateFileObserver] for real-time inotify-based 2-way sync.
 */

private var gpuFreqDebounceJob: Job? = null

fun LynxViewModel.refreshGpuInfo() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val raw = LynxRepository.readGpuInfo()
            val gpu = mergeGpuInfoWithIntent(raw)
            _uiState.update { it.copy(gpuInfo = gpu) }
        } catch (_: Exception) {}
    }
}

fun LynxViewModel.setGpuBoostLevel(level: Int) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    val curPlat = _uiState.value.gpuInfo.platform
    activeGpuIntent = LynxViewModel.GpuIntent(
        adrenoBoost = if (curPlat == "adreno") level else activeGpuIntent?.adrenoBoost,
        gedBoost = if (curPlat == "mali_ged") level else activeGpuIntent?.gedBoost,
        minMhz = activeGpuIntent?.minMhz,
        maxMhz = activeGpuIntent?.maxMhz,
        timestamp = System.currentTimeMillis()
    )
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(
        adrenoBoostLevel = if (curPlat == "adreno") level else it.gpuInfo.adrenoBoostLevel,
        gedBoostLevel = if (curPlat == "mali_ged") level else it.gpuInfo.gedBoostLevel,
    )) }
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.setGpuBoostLevel(level)
        if (!ok) {
            _uiState.update { it.copy(errorMessage = "GPU Boost tidak didukung kernel ini") }
        }
    }
}

fun LynxViewModel.setGpuFreq(minMhz: Int?, maxMhz: Int?) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    activeGpuIntent = LynxViewModel.GpuIntent(
        adrenoBoost = activeGpuIntent?.adrenoBoost,
        gedBoost = activeGpuIntent?.gedBoost,
        minMhz = minMhz ?: activeGpuIntent?.minMhz ?: _uiState.value.gpuInfo.minFreqMhz,
        maxMhz = maxMhz ?: activeGpuIntent?.maxMhz ?: _uiState.value.gpuInfo.maxFreqMhz,
        timestamp = System.currentTimeMillis()
    )
    _uiState.update { current ->
        current.copy(gpuInfo = current.gpuInfo.copy(
            minFreqMhz = minMhz ?: current.gpuInfo.minFreqMhz,
            maxFreqMhz = maxMhz ?: current.gpuInfo.maxFreqMhz,
        ))
    }
    gpuFreqDebounceJob?.cancel()
    gpuFreqDebounceJob = viewModelScope.launch(Dispatchers.IO) {
        delay(120L)
        val minHz = minMhz?.let { it.toLong() * 1_000_000L }
        val maxHz = maxMhz?.let { it.toLong() * 1_000_000L }
        LynxRepository.setGpuFreq(minHz, maxHz)
    }
}

fun LynxViewModel.setGpuLock(locked: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    val curGpu = _uiState.value.gpuInfo
    if (locked) {
        val targetFreq = if (curGpu.maxFreqMhz > 0) curGpu.maxFreqMhz else (curGpu.availFreqsMhz.maxOrNull() ?: 850)
        setGpuFreq(targetFreq, targetFreq)
        _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isLocked = true)) }
    } else {
        val minF = curGpu.availFreqsMhz.minOrNull() ?: 300
        val maxF = curGpu.availFreqsMhz.maxOrNull() ?: curGpu.maxFreqMhz
        setGpuFreq(minF, maxF)
        _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isLocked = false)) }
    }
}

fun LynxViewModel.setGpuGovernor(governor: String) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(currentGovernor = governor)) }
    viewModelScope.launch(Dispatchers.IO) {
        LynxRepository.setGpuGovernor(governor)
    }
}

fun LynxViewModel.setGpuPowerPolicy(policy: String) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(maliPowerPolicy = policy)) }
    viewModelScope.launch {
        LynxRepository.setGpuPowerPolicy(policy)
    }
}

fun LynxViewModel.setGpuThermalBypass(enabled: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isThrottlingBypassed = enabled)) }
    viewModelScope.launch {
        LynxRepository.setGpuThermalBypass(enabled)
    }
}

fun LynxViewModel.setGpuBusAlwaysOn(enabled: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isBusAlwaysOn = enabled)) }
    viewModelScope.launch {
        LynxRepository.setGpuBusAlwaysOn(enabled)
    }
}

fun LynxViewModel.setGpuFramePacing(enabled: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isFramePacingActive = enabled)) }
    viewModelScope.launch {
        LynxRepository.setGpuFramePacing(enabled)
    }
}

fun LynxViewModel.setGpuFeature(feature: GpuHardwareFeature, targetValue: String) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    val prevValue = feature.currentValue
    val boolVal = targetValue == "1" || targetValue.equals("true", ignoreCase = true) || targetValue.equals("always_on", ignoreCase = true)
    val intVal = targetValue.toIntOrNull()

    // Optimistic UI update for both hardwareFeatures and top-level gpuInfo properties
    val updatedFeatures = _uiState.value.gpuInfo.hardwareFeatures.map {
        if (it.id == feature.id) it.copy(currentValue = targetValue) else it
    }
    _uiState.update { state ->
        var nextGpu = state.gpuInfo.copy(hardwareFeatures = updatedFeatures)
        nextGpu = when (feature.id) {
            "kgsl_force_bus_on" -> nextGpu.copy(isBusAlwaysOn = boolVal)
            "adreno_force_rail" -> nextGpu.copy(adrenoForceRail = boolVal)
            "adreno_thermal_bypass", "mtk_gpufreq_thermal_bypass" -> nextGpu.copy(isThrottlingBypassed = boolVal)
            "adreno_idle_timer" -> nextGpu.copy(idleTimerMs = intVal ?: nextGpu.idleTimerMs)
            "adreno_tz_target_load" -> nextGpu.copy(adrenoTzTargetLoad = intVal ?: nextGpu.adrenoTzTargetLoad)
            "adreno_pwrlevel" -> nextGpu.copy(adrenoPwrLevel = intVal ?: nextGpu.adrenoPwrLevel)
            "adrenoboost_level" -> nextGpu.copy(adrenoBoostLevel = intVal ?: nextGpu.adrenoBoostLevel)
            "mtk_ged_boost_level" -> nextGpu.copy(gedBoostLevel = intVal ?: nextGpu.gedBoostLevel)
            "mtk_frame_pacing", "ged_frame_pacing" -> nextGpu.copy(isFramePacingActive = boolVal)
            "mtk_ultra_rescue", "fpsgo_ultra_rescue" -> nextGpu.copy(isFpsgoUltraRescue = boolVal)
            "mtk_dvfs_margin", "ged_dvfs_margin" -> nextGpu.copy(maliDvfsMargin = intVal ?: nextGpu.maliDvfsMargin)
            "mali_core_mask" -> nextGpu.copy(isMaliAllCoresActive = boolVal)
            "mali_power_policy" -> nextGpu.copy(maliPowerPolicy = targetValue)
            "devfreq_governor", "kernel_gpu_governor" -> nextGpu.copy(currentGovernor = targetValue)
            else -> nextGpu
        }
        state.copy(gpuInfo = nextGpu)
    }

    viewModelScope.launch {
        when (feature.id) {
            "kgsl_force_bus_on" -> LynxRepository.setGpuBusAlwaysOn(boolVal)
            "adreno_force_rail" -> LynxRepository.setAdrenoForceRail(boolVal)
            "adreno_thermal_bypass", "mtk_gpufreq_thermal_bypass" -> LynxRepository.setGpuThermalBypass(boolVal)
            "adreno_idle_timer" -> intVal?.let { LynxRepository.setGpuIdleTimer(it) }
            "adreno_tz_target_load" -> intVal?.let { LynxRepository.setAdrenoTzTargetLoad(it) }
            "adreno_pwrlevel" -> intVal?.let { LynxRepository.setAdrenoPwrLevel(it) }
            "adrenoboost_level", "mtk_ged_boost_level" -> intVal?.let { LynxRepository.setGpuBoostLevel(it) }
            "mtk_frame_pacing", "ged_frame_pacing" -> LynxRepository.setGpuFramePacing(boolVal)
            "mtk_ultra_rescue", "fpsgo_ultra_rescue" -> LynxRepository.setFpsgoUltraRescue(boolVal)
            "mtk_dvfs_margin", "ged_dvfs_margin" -> intVal?.let { LynxRepository.setMaliDvfsMargin(it) }
            "mali_core_mask" -> LynxRepository.setMaliCoreMask(boolVal)
            "mali_power_policy" -> LynxRepository.setMaliPowerPolicy(targetValue)
            "devfreq_governor", "kernel_gpu_governor" -> LynxRepository.setGpuGovernor(targetValue)
        }

        val actualNodeValue = when (feature.id) {
            "adreno_thermal_bypass", "adreno_bus_split_disable" -> if (boolVal) "0" else "1"
            "mali_core_mask" -> if (boolVal) "0xFF" else "0x0F"
            else -> targetValue
        }
        val result = LynxRepository.writeGpuFeature(feature.nodePath, actualNodeValue)
        when (result) {
            is WriteResult.Applied -> {
                // Feature applied successfully, UI is updated optimistically
            }
            is WriteResult.Rejected -> {
                // Rollback optimistic update and mark as locked
                val rolledBack = _uiState.value.gpuInfo.hardwareFeatures.map {
                    if (it.id == feature.id) it.copy(
                        currentValue = prevValue,
                        accessState = FeatureAccessState.READ_ONLY_LOCKED
                    ) else it
                }
                _uiState.update {
                    it.copy(
                        gpuInfo = it.gpuInfo.copy(hardwareFeatures = rolledBack),
                        errorMessage = "Kernel menolak perubahan (nilai terbaca: ${result.readBack}). Parameter dikunci read-only."
                    )
                }
            }
            is WriteResult.Unsupported -> {
                val rolledBack = _uiState.value.gpuInfo.hardwareFeatures.map {
                    if (it.id == feature.id) it.copy(
                        currentValue = prevValue,
                        accessState = FeatureAccessState.UNSUPPORTED
                    ) else it
                }
                _uiState.update {
                    it.copy(
                        gpuInfo = it.gpuInfo.copy(hardwareFeatures = rolledBack),
                        errorMessage = "Fitur tidak didukung oleh kernel."
                    )
                }
            }
        }
    }
}

fun LynxViewModel.setGpuIdleTimer(ms: Int) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(idleTimerMs = ms)) }
    viewModelScope.launch {
        LynxRepository.setGpuIdleTimer(ms)
    }
}

fun LynxViewModel.setMaliDvfsMargin(margin: Int) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(maliDvfsMargin = margin)) }
    viewModelScope.launch {
        LynxRepository.setMaliDvfsMargin(margin)
    }
}

fun LynxViewModel.setAdrenoPwrLevel(pwrLevel: Int) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(adrenoPwrLevel = pwrLevel)) }
    viewModelScope.launch {
        LynxRepository.setAdrenoPwrLevel(pwrLevel)
    }
}

fun LynxViewModel.setAdrenoTzTargetLoad(targetLoad: Int) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(adrenoTzTargetLoad = targetLoad)) }
    viewModelScope.launch {
        LynxRepository.setAdrenoTzTargetLoad(targetLoad)
    }
}

fun LynxViewModel.setAdrenoForceRail(enabled: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(adrenoForceRail = enabled)) }
    viewModelScope.launch {
        LynxRepository.setAdrenoForceRail(enabled)
    }
}

fun LynxViewModel.setFpsgoUltraRescue(enabled: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isFpsgoUltraRescue = enabled)) }
    viewModelScope.launch {
        LynxRepository.setFpsgoUltraRescue(enabled)
    }
}

fun LynxViewModel.setMaliCoreMask(unmaskAll: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isMaliAllCoresActive = unmaskAll)) }
    viewModelScope.launch {
        LynxRepository.setMaliCoreMask(unmaskAll)
    }
}

fun LynxViewModel.setMaliPowerPolicy(policy: String) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(maliPowerPolicy = policy)) }
    viewModelScope.launch {
        LynxRepository.setMaliPowerPolicy(policy)
    }
}

fun LynxViewModel.setSurfaceFlingerDisableBackpressure(enabled: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GRAPHICS)
    _uiState.update { it.copy(
        gpuInfo = it.gpuInfo.copy(isDisableBackpressure = enabled),
        graphicsHwui = it.graphicsHwui.copy(surfaceFlingerDisableBackpressure = enabled)
    ) }
    viewModelScope.launch {
        LynxRepository.setSurfaceFlingerDisableBackpressure(enabled)
    }
}

fun LynxViewModel.applyGpuProfile(profile: String, context: android.content.Context, fromMasterProfile: Boolean = false) {
    if (fromMasterProfile) {
        recordStateMutation()
    } else {
        recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU, profile)
        recordProfileOverride(com.noir.lynx.profiles.ProfileDomain.GRAPHICS, profile)
    }
    val curGpu = _uiState.value.gpuInfo
    val freqs = curGpu.availFreqsMhz.sorted()
    val minAvail = freqs.firstOrNull() ?: 300
    val maxAvail = freqs.lastOrNull() ?: 850
    val targetMin = when (profile) {
        "esports" -> freqs.filter { it >= (maxAvail * 0.65).toInt() }.firstOrNull() ?: minAvail
        "extreme" -> maxAvail
        else -> minAvail
    }
    val targetMax = when (profile) {
        "battery" -> freqs.filter { it <= (maxAvail * 0.65).toInt() }.lastOrNull() ?: minAvail
        else -> maxAvail
    }
    val targetBoost = when (profile) {
        "battery" -> 0
        "balanced" -> 1
        else -> 2
    }
    val targetThermalBypass = profile == "extreme"
    val isGaming = profile == "esports" || profile == "extreme"
    activeGpuIntent = LynxViewModel.GpuIntent(
        adrenoBoost = if (curGpu.platform == "adreno") targetBoost else null,
        gedBoost = if (curGpu.platform == "mali_ged") targetBoost else null,
        minMhz = targetMin,
        maxMhz = targetMax,
        timestamp = System.currentTimeMillis()
    )
    _uiState.update {
        it.copy(
            gpuInfo = it.gpuInfo.copy(
                activeProfile = profile,
                minFreqMhz = targetMin,
                maxFreqMhz = targetMax,
                isLocked = targetMin == targetMax && targetMax > 0,
                adrenoBoostLevel = if (it.gpuInfo.platform == "adreno") targetBoost else it.gpuInfo.adrenoBoostLevel,
                gedBoostLevel = if (it.gpuInfo.platform == "mali_ged") targetBoost else it.gpuInfo.gedBoostLevel,
                isThrottlingBypassed = targetThermalBypass,
                isBusAlwaysOn = isGaming,
                isFramePacingActive = isGaming,
                maliDvfsMargin = when (profile) {
                    "battery" -> 0
                    "balanced" -> 10
                    "esports" -> 20
                    else -> 30
                },
                maliPowerPolicy = if (isGaming) "always_on" else "coarse_demand",
                isFpsgoUltraRescue = isGaming
            )
        )
    }
    gpuProfileJob = viewModelScope.launch(Dispatchers.IO) {
        val ok = if (fromMasterProfile && LynxRepository.isModuleInstalled()) {
            true // Already applied natively by lynxd profile apply (<5ms)
        } else {
            LynxRepository.applyGpuProfile(profile, curGpu)
        }
        if (ok) {
            val label = when (profile) {
                "battery" -> "Profil GPU Hemat Daya Aktif"
                "balanced" -> "Profil GPU Seimbang Aktif"
                "esports" -> "Profil GPU Esports Aktif"
                "extreme" -> "Profil GPU Ekstrem Aktif"
                else -> "Profil GPU Diterapkan"
            }
            val accentHex = when (profile) {
                "battery" -> 0xFF00E676
                "balanced" -> 0xFF00E5FF
                "esports" -> 0xFFFF9100
                else -> 0xFFFF5252
            }
            withContext(Dispatchers.Main) {
                triggerUniversalToast(
                    title = label,
                    statusTag = "GPU",
                    type = com.noir.lynx.data.ToastType.PRESET,
                    accentColorHex = accentHex,
                    iconType = "gpu"
                )
            }
        }
    }
}

fun LynxViewModel.refreshGraphicsHwui() {
    viewModelScope.launch {
        try {
            val info = LynxRepository.readGraphicsHwuiInfo()
            _uiState.update { it.copy(graphicsHwui = info) }
        } catch (_: Exception) {}
    }
}

fun LynxViewModel.setUpdatableGameDriver(mode: String) {
    recordStateMutation()
    _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(updatableGameDriver = mode)) }
    viewModelScope.launch {
        LynxRepository.setUpdatableGameDriver(mode)
    }
}

fun LynxViewModel.setHwuiRenderer(backend: String) {
    recordStateMutation()
    _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(hwuiRenderer = backend)) }
    viewModelScope.launch {
        LynxRepository.setHwuiRenderer(backend)
    }
}

fun LynxViewModel.setSurfaceFlingerLatch(enabled: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GRAPHICS)
    _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(surfaceFlingerLatchUnsignaled = enabled)) }
    viewModelScope.launch {
        LynxRepository.setSurfaceFlingerLatch(enabled)
    }
}

fun LynxViewModel.setForceMsaa(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(force4xMsaa = enabled)) }
    viewModelScope.launch {
        LynxRepository.setForceMsaa(enabled)
    }
}

fun LynxViewModel.setOemThrottlerShield(disabled: Boolean) {
    recordStateMutation()
    val oem = _uiState.value.graphicsHwui.detectedOemThrottler
    _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(isOemThrottlerDisabled = disabled)) }
    viewModelScope.launch {
        LynxRepository.setOemThrottlerShield(oem, disabled)
    }
}

fun LynxViewModel.setSurfaceFlingerEarlyPhase(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(isEarlyPhaseOffset = enabled)) }
    viewModelScope.launch {
        LynxRepository.setSurfaceFlingerEarlyPhase(enabled)
    }
}

fun LynxViewModel.setDcDimming(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(dcDimmingEnabled = enabled)) }
    viewModelScope.launch {
        val ok = LynxRepository.setDcDimming(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = "DC Dimming ${if (enabled) "diaktifkan" else "dinonaktifkan"}") }
        } else {
            _uiState.update { it.copy(errorMessage = "Hardware DC Dimming tidak didukung oleh panel ini") }
        }
    }
}

fun LynxViewModel.loadPerAppGraphicsRules(context: Context) {
    viewModelScope.launch {
        try {
            val rules = LynxRepository.readPerAppGraphicsRules(context)
            _uiState.update { it.copy(perAppGraphicsRules = rules) }
        } catch (_: Exception) {}
    }
}

fun LynxViewModel.savePerAppGraphicsRule(rule: PerAppGraphicsRule, context: Context) {
    recordStateMutation()
    viewModelScope.launch {
        val ok = LynxRepository.savePerAppGraphicsRule(rule, context)
        if (ok) {
            val rules = LynxRepository.readPerAppGraphicsRules(context)
            com.noir.lynx.service.LynxAppAutomationService.reloadRules()
            _uiState.update { it.copy(perAppGraphicsRules = rules, successMessage = "Aturan grafis untuk ${rule.appName} disimpan") }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal menyimpan aturan grafis") }
        }
    }
}

fun LynxViewModel.deletePerAppGraphicsRule(packageName: String, context: Context) {
    recordStateMutation()
    viewModelScope.launch {
        val ok = LynxRepository.deletePerAppGraphicsRule(packageName, context)
        if (ok) {
            val rules = LynxRepository.readPerAppGraphicsRules(context)
            com.noir.lynx.service.LynxAppAutomationService.reloadRules()
            _uiState.update { it.copy(perAppGraphicsRules = rules, successMessage = "Aturan grafis dihapus") }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal menghapus aturan grafis") }
        }
    }
}

fun LynxViewModel.setTextureCacheSize(mb: Int) {
    recordStateMutation()
    _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(textureCacheMb = mb)) }
    viewModelScope.launch {
        val ok = LynxRepository.setTextureCacheSize(mb)
        if (ok) {
            _uiState.update { it.copy(successMessage = "HWUI Texture Cache diatur ke ${mb}MB — restart app untuk efek penuh") }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal mengatur texture cache size") }
        }
    }
}

fun LynxViewModel.setAsyncShaderPipeline(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(asyncShaderPipelineEnabled = enabled)) }
    viewModelScope.launch {
        val ok = LynxRepository.setAsyncShaderPipeline(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = if (enabled) "Asynchronous Shader Pipeline diaktifkan" else "Shader Pipeline dikembalikan ke mode standar") }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal mengatur shader pipeline") }
        }
    }
}

fun LynxViewModel.setPureHwVsync(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(pureHwVsyncEnabled = enabled)) }
    viewModelScope.launch {
        val ok = LynxRepository.setPureHwVsync(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = if (enabled) "Pure Hardware VSync diaktifkan" else "VSync dikembalikan ke mode hybrid") }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal mengatur VSync mode") }
        }
    }
}

fun LynxViewModel.setDisplayRenderScale(preset: String) {
    recordStateMutation()
    viewModelScope.launch {
        val isDownscale = preset != "native"
        _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(isEsportsDownscaleActive = isDownscale)) }
        val ok = LynxRepository.setDisplayRenderScale(preset)
        if (ok) {
            // Refresh resolution state
            val info = LynxRepository.readGraphicsHwuiInfo()
            _uiState.update { it.copy(
                graphicsHwui = it.graphicsHwui.copy(
                    isEsportsDownscaleActive = info.isEsportsDownscaleActive,
                    nativeResolution = info.nativeResolution,
                    activeResolution = info.activeResolution
                ),
                successMessage = when (preset) {
                    "native" -> "Resolusi dikembalikan ke Native (${info.nativeResolution})"
                    "720p_esports" -> "Esports 720p Mode aktif — FPS boost ±40%"
                    "900p_balanced" -> "Balanced 900p Mode aktif — FPS boost ±20%"
                    else -> "Render scale diatur"
                }
            ) }
        } else {
            _uiState.update { it.copy(
                graphicsHwui = it.graphicsHwui.copy(isEsportsDownscaleActive = false),
                errorMessage = "Gagal mengatur render scale (wm size/density tidak tersedia)"
            ) }
        }
    }
}

// ============================================================
//  GPU & DISPLAY INTELLIGENCE FRAMEWORK
// ============================================================

