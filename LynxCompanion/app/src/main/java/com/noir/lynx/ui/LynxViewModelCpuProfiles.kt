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
import com.noir.lynx.profiles.CpuProfileBoostPolicies
import com.noir.lynx.profiles.ProfileDomain
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

fun LynxViewModel.applyComprehensiveCpuProfile(
    profile: String,
    context: Context? = null,
    asMasterProfile: Boolean = false,
    recordDomainOverride: Boolean = !asMasterProfile,
) {
    val rawLower = profile.lowercase().trim()
    val normalized = mapModuleProfileToCpuProfile(rawLower)
    if (asMasterProfile) {
        recordStateMutation()
        profileOverrideSaveJob?.cancel()
    } else if (recordDomainOverride) {
        recordStateMutation(ProfileDomain.CPU, normalized)
    } else {
        recordStateMutation()
    }

    // 1. Cancel any active in-flight profile transition job to prevent race conditions & IO choke
    cpuProfileJob?.cancel()

    val clusters = _uiState.value.clusters
    val (schedPreset, schedHystUp, schedHystDown) = when (normalized) {
        "battery" -> Triple("battery", 95, 75)
        "performance" -> Triple("gaming", 70, 50)
        "extreme" -> Triple("extreme", 50, 30)
        else -> Triple("balanced", 85, 65)
    }
    val cpuSetPreset = when (normalized) {
        "battery" -> "battery"
        "performance", "extreme" -> "gaming"
        else -> "standard"
    }
    val idlePreset = when (normalized) {
        "battery" -> "battery"
        "performance", "extreme" -> "gaming"
        else -> "balanced"
    }
    val parkingMode = when (normalized) {
        "battery" -> "park_big"
        "extreme" -> "unpark_all"
        else -> "dynamic"
    }
    val boostPolicy = CpuProfileBoostPolicies.forProfile(normalized)
    val targetBoostPct = boostPolicy.topAppMinPercent
    val companionModuleProfile = if (rawLower == "auto") {
        "auto"
    } else {
        when (normalized) {
            "battery" -> "powersave"
            "performance" -> "performance"
            "extreme" -> "extreme"
            else -> "balance"
        }
    }
    val successMsg = when {
        rawLower == "auto" -> "Mode AI Otomatis aktif: sistem menyesuaikan profil secara dinamis."
        normalized == "battery" -> "Mode Efisiensi diterapkan: hemat daya maksimal."
        normalized == "performance" -> "Mode Performa diterapkan: responsivitas tinggi & gaming stabil."
        normalized == "extreme" -> "Mode Ekstrem diterapkan: frekuensi puncak terkunci tanpa batas."
        else -> "Mode Seimbang diterapkan: boost latar dikurangi, akselerasi dinamis tetap tersedia saat dibutuhkan."
    }

    // Calculate targets for each cluster
    val clusterTargets = computeClusterTargetsForProfile(clusters, normalized)

    // Register active intents immediately so any concurrent read telemetry maintains target values
    val now = System.currentTimeMillis()
    if (normalized != "extreme") {
        activeClusterIntents.clear()
    }
    clusterTargets.forEach { (cid, tgt) ->
        activeClusterIntents[cid] = LynxViewModel.ClusterIntent(
            minFreq = tgt.minFreq,
            maxFreq = tgt.maxFreq,
            gov = tgt.gov,
            isLocked = tgt.isLocked,
            timestamp = now
        )
    }

    // 2. Synchronous Optimistic UI State Update (Zero Latency / 0ms touch responsiveness)
    _uiState.update { current ->
        val updatedClusters = current.clusters.map { c ->
            val tgt = clusterTargets[c.id]
            if (tgt != null) {
                c.copy(curMin = tgt.minFreq, curMax = tgt.maxFreq, curGov = tgt.gov, isLocked = tgt.isLocked)
            } else c
        }
        val updatedCores = current.cpuCores.map { core ->
            val parent = updatedClusters.find { it.containsCore(core.coreId) }
            if (parent != null) {
                core.copy(minFreqKhz = parent.curMin, maxFreqKhz = parent.curMax, isLocked = parent.isLocked)
            } else core
        }
        current.copy(
            cpuComprehensiveProfile = normalized,
            isCpuModified = recordDomainOverride && !asMasterProfile,
            clusters = updatedClusters,
            cpuCores = updatedCores,
            schedulerInfo = current.schedulerInfo.copy(
                activePreset = schedPreset,
                uclampMin = targetBoostPct,
                topAppSchedtuneBoost = targetBoostPct,
                schedUpmigrate = schedHystUp,
                schedDownmigrate = schedHystDown,
                mtkCciPerfMode = (normalized == "performance" || normalized == "extreme"),
                mtkDvfsrcBoostEnabled = (normalized == "performance" || normalized == "extreme"),
                busBandwidthProfile = when (normalized) {
                    "battery" -> "eco"
                    "performance", "extreme" -> "max"
                    else -> "auto"
                },
                mtkCpuPowerMode = when (normalized) {
                    "battery" -> 1
                    "performance", "extreme" -> 3
                    else -> 0
                },
                universalTouchBoostEnabled = (normalized == "performance" || normalized == "extreme"),
                antiThrottlingGuardEnabled = (normalized == "performance" || normalized == "extreme"),
                ppmDlptBypassEnabled = (normalized == "performance" || normalized == "extreme"),
                ppmPwrThrottlingEnabled = (normalized == "battery" || normalized == "balanced"),
                ppmThermalThrottlingEnabled = (normalized != "extreme"),
                ppmSysBoostEnabled = (normalized == "performance" || normalized == "extreme"),
                qcomDevfreqBusBoostEnabled = (normalized == "performance" || normalized == "extreme"),
                workqueuePowerEfficient = (normalized == "battery" || normalized == "balanced")
            ),
            cpuSets = current.cpuSets.copy(activePreset = cpuSetPreset),
            cpuIdle = current.cpuIdle.copy(
                activePreset = idlePreset,
                coreParkingMode = parkingMode
            ),
            state = current.state.copy(
                activeProfile = if (asMasterProfile) companionModuleProfile else current.state.activeProfile,
                profileOverrides = when {
                    asMasterProfile -> emptyMap()
                    recordDomainOverride -> current.state.profileOverrides + (ProfileDomain.CPU to normalized)
                    else -> current.state.profileOverrides - ProfileDomain.CPU
                }
            )
        )
    }

    // 3. Batched Atomic Single-Pass Background Root Execution (<100ms, zero lag, zero revert)
    cpuProfileJob = viewModelScope.launch(Dispatchers.IO) {
        try {
            var effectiveClusterTargets = clusterTargets
            if (effectiveClusterTargets.isEmpty()) {
                val fallbackClusters = LynxRepository.readClusters()
                effectiveClusterTargets = computeClusterTargetsForProfile(fallbackClusters, normalized)
            }
            val totalCores = _uiState.value.cpuSets.totalCoresCount.coerceAtLeast(8)
            val params = CpuBatchProfileParams(
                companionProfile = companionModuleProfile,
                clusterTargets = effectiveClusterTargets,
                schedPreset = schedPreset,
                schedHystUp = schedHystUp,
                schedHystDown = schedHystDown,
                cpuSetPreset = cpuSetPreset,
                idlePreset = idlePreset,
                parkingMode = parkingMode,
                totalCores = totalCores,
                updateMasterProfile = asMasterProfile
            )

            ensureActive()
            if (asMasterProfile) clearProfileOverridesForMasterSelection()
            val ok = LynxRepository.applyCpuBatchProfile(params, context)
            if (ok) {
                if (companionModuleProfile == "auto") {
                    context?.let { LynxRepository.startAppAutomation(it) }
                } else {
                    context?.let { LynxAppAutomationService.updateBaselineProfile(it, companionModuleProfile) }
                }
            }

            // Single consolidated read pass after settling
            delay(250L)
            ensureActive()
            val freshClusters = LynxRepository.readClusters()
            val freshSched = LynxRepository.readSchedulerInfo(context)
            val freshIdle = LynxRepository.readCpuIdleInfo(context)
            val freshSets = LynxRepository.readCpuSetsInfo(context)
            val freshGpu = mergeGpuInfoWithIntent(LynxRepository.readGpuInfo())
            val freshRr = mergeRefreshRateWithIntent(LynxRepository.readDisplayRefreshRate())

            withContext(Dispatchers.Main) {
                _uiState.update { current ->
                    val finalClusters = if (freshClusters.isNotEmpty()) mergeClustersWithActiveIntents(freshClusters) else current.clusters
                    val syncedCores = current.cpuCores.map { core ->
                        val parent = finalClusters.find { it.containsCore(core.coreId) }
                        if (parent != null) {
                            core.copy(minFreqKhz = parent.curMin, maxFreqKhz = parent.curMax, isLocked = parent.isLocked)
                        } else core
                    }
                    current.copy(
                        clusters = finalClusters,
                        cpuCores = syncedCores,
                        schedulerInfo = freshSched,
                        cpuIdle = freshIdle,
                        cpuSets = freshSets,
                        gpuInfo = freshGpu,
                        displayRefreshRate = freshRr,
                        isCpuModified = current.state.profileOverrides.containsKey(ProfileDomain.CPU),
                        successMessage = successMsg
                    )
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Preempted by a newer profile selection; clean exit without fighting
        } catch (e: Exception) {
            Log.e("LynxViewModel", "Error applying CPU profile: ${e.message}")
        }
    }
}

fun LynxViewModel.resetCpuToActiveProfile(context: Context) {
    val masterCpuProfile = mapModuleProfileToCpuProfile(masterDomainPreset(ProfileDomain.CPU))
    profileOverrideSaveJob?.cancel()
    _uiState.update { current ->
        current.copy(
            cpuComprehensiveProfile = masterCpuProfile,
            isCpuModified = false,
            state = current.state.copy(profileOverrides = current.state.profileOverrides - ProfileDomain.CPU)
        )
    }
    viewModelScope.launch {
        LynxRepository.writeStateKey("profile_overrides", com.noir.lynx.profiles.ProfileOverridesCodec.encode(_uiState.value.state.profileOverrides), "str")
    }
    applyComprehensiveCpuProfile(masterCpuProfile, context, recordDomainOverride = false)
}

