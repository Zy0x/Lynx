package com.noir.lynx.ui

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

fun LynxViewModel.refreshGpuInfo() {
    viewModelScope.launch {
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
    viewModelScope.launch {
        val ok = LynxRepository.setGpuBoostLevel(level)
        if (!ok) {
            _uiState.update { it.copy(errorMessage = "GPU Boost tidak didukung kernel ini") }
        }
        delay(200L)
        refreshGpuInfo()
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
    viewModelScope.launch {
        val minHz = minMhz?.let { it.toLong() * 1_000_000L }
        val maxHz = maxMhz?.let { it.toLong() * 1_000_000L }
        LynxRepository.setGpuFreq(minHz, maxHz)
        delay(200L)
        refreshGpuInfo()
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
    viewModelScope.launch {
        LynxRepository.setGpuGovernor(governor)
        delay(200L)
        refreshGpuInfo()
    }
}

fun LynxViewModel.setGpuPowerPolicy(policy: String) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(maliPowerPolicy = policy)) }
    viewModelScope.launch {
        LynxRepository.setGpuPowerPolicy(policy)
        delay(200L)
        refreshGpuInfo()
    }
}

fun LynxViewModel.setGpuThermalBypass(enabled: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isThrottlingBypassed = enabled)) }
    viewModelScope.launch {
        LynxRepository.setGpuThermalBypass(enabled)
        delay(200L)
        refreshGpuInfo()
    }
}

fun LynxViewModel.setGpuBusAlwaysOn(enabled: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isBusAlwaysOn = enabled)) }
    viewModelScope.launch {
        LynxRepository.setGpuBusAlwaysOn(enabled)
        delay(200L)
        refreshGpuInfo()
    }
}

fun LynxViewModel.setGpuFramePacing(enabled: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isFramePacingActive = enabled)) }
    viewModelScope.launch {
        LynxRepository.setGpuFramePacing(enabled)
        delay(200L)
        refreshGpuInfo()
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
                delay(200L)
                refreshGpuInfo()
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
        delay(200L)
        refreshGpuInfo()
    }
}

fun LynxViewModel.setMaliDvfsMargin(margin: Int) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(maliDvfsMargin = margin)) }
    viewModelScope.launch {
        LynxRepository.setMaliDvfsMargin(margin)
        delay(200L)
        refreshGpuInfo()
    }
}

fun LynxViewModel.setAdrenoPwrLevel(pwrLevel: Int) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(adrenoPwrLevel = pwrLevel)) }
    viewModelScope.launch {
        LynxRepository.setAdrenoPwrLevel(pwrLevel)
        delay(200L)
        refreshGpuInfo()
    }
}

fun LynxViewModel.setAdrenoTzTargetLoad(targetLoad: Int) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(adrenoTzTargetLoad = targetLoad)) }
    viewModelScope.launch {
        LynxRepository.setAdrenoTzTargetLoad(targetLoad)
        delay(200L)
        refreshGpuInfo()
    }
}

fun LynxViewModel.setAdrenoForceRail(enabled: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(adrenoForceRail = enabled)) }
    viewModelScope.launch {
        LynxRepository.setAdrenoForceRail(enabled)
        delay(200L)
        refreshGpuInfo()
    }
}

fun LynxViewModel.setFpsgoUltraRescue(enabled: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isFpsgoUltraRescue = enabled)) }
    viewModelScope.launch {
        LynxRepository.setFpsgoUltraRescue(enabled)
        delay(200L)
        refreshGpuInfo()
    }
}

fun LynxViewModel.setMaliCoreMask(unmaskAll: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(isMaliAllCoresActive = unmaskAll)) }
    viewModelScope.launch {
        LynxRepository.setMaliCoreMask(unmaskAll)
        delay(200L)
        refreshGpuInfo()
    }
}

fun LynxViewModel.setMaliPowerPolicy(policy: String) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GPU)
    _uiState.update { it.copy(gpuInfo = it.gpuInfo.copy(maliPowerPolicy = policy)) }
    viewModelScope.launch {
        LynxRepository.setMaliPowerPolicy(policy)
        delay(200L)
        refreshGpuInfo()
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
        delay(200L)
        refreshGraphicsHwui()
        refreshGpuInfo()
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
    gpuProfileJob = viewModelScope.launch {
        val ok = LynxRepository.applyGpuProfile(profile, curGpu)
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
            triggerUniversalToast(
                title = label,
                statusTag = "GPU",
                type = com.noir.lynx.data.ToastType.PRESET,
                accentColorHex = accentHex,
                iconType = "gpu"
            )
        }
        delay(300L)
        refreshGpuInfo()
        refreshGraphicsHwui()
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
        delay(200L)
        refreshGraphicsHwui()
    }
}

fun LynxViewModel.setHwuiRenderer(backend: String) {
    recordStateMutation()
    _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(hwuiRenderer = backend)) }
    viewModelScope.launch {
        LynxRepository.setHwuiRenderer(backend)
        delay(200L)
        refreshGraphicsHwui()
    }
}

fun LynxViewModel.setSurfaceFlingerLatch(enabled: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.GRAPHICS)
    _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(surfaceFlingerLatchUnsignaled = enabled)) }
    viewModelScope.launch {
        LynxRepository.setSurfaceFlingerLatch(enabled)
        delay(200L)
        refreshGraphicsHwui()
    }
}

fun LynxViewModel.setForceMsaa(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(force4xMsaa = enabled)) }
    viewModelScope.launch {
        LynxRepository.setForceMsaa(enabled)
        delay(200L)
        refreshGraphicsHwui()
    }
}

fun LynxViewModel.setOemThrottlerShield(disabled: Boolean) {
    recordStateMutation()
    val oem = _uiState.value.graphicsHwui.detectedOemThrottler
    _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(isOemThrottlerDisabled = disabled)) }
    viewModelScope.launch {
        LynxRepository.setOemThrottlerShield(oem, disabled)
        delay(200L)
        refreshGraphicsHwui()
    }
}

fun LynxViewModel.clearShaderCache(context: android.content.Context) {
    viewModelScope.launch {
        val ok = LynxRepository.clearShaderCache()
        if (ok) {
            android.widget.Toast.makeText(context, "Shader Cache Grafis Berhasil Dibersihkan", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, "Gagal membersihkan cache shader", android.widget.Toast.LENGTH_SHORT).show()
        }
        delay(300L)
        refreshGraphicsHwui()
    }
}

fun LynxViewModel.prewarmShaderCache(context: android.content.Context) {
    viewModelScope.launch {
        android.widget.Toast.makeText(context, "Memulai pra-kompilasi shader AOT...", android.widget.Toast.LENGTH_SHORT).show()
        val ok = LynxRepository.prewarmShaderCache()
        if (ok) {
            android.widget.Toast.makeText(context, "Pra-kompilasi Shader Selesai (Anti-Stutter Aktif)", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, "Pra-kompilasi Shader Selesai", android.widget.Toast.LENGTH_SHORT).show()
        }
        delay(300L)
        refreshGraphicsHwui()
    }
}

fun LynxViewModel.setSurfaceFlingerEarlyPhase(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { it.copy(graphicsHwui = it.graphicsHwui.copy(isEarlyPhaseOffset = enabled)) }
    viewModelScope.launch {
        LynxRepository.setSurfaceFlingerEarlyPhase(enabled)
        delay(200L)
        refreshGraphicsHwui()
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
        delay(200L)
        refreshGraphicsHwui()
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

// ============================================================
//  GPU & DISPLAY INTELLIGENCE FRAMEWORK
// ============================================================

