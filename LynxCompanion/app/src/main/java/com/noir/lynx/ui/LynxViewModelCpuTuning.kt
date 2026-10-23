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
import java.util.concurrent.ConcurrentHashMap

/**
 * LynxViewModel: Manages UI state and orchestrates root operations.
 *
 * Exposes a single [uiState] StateFlow consumed by MainActivity Compose UI.
 * Uses [StateFileObserver] for real-time inotify-based 2-way sync.
 */

private val clusterFreqDebounceJobs = ConcurrentHashMap<Int, Job>()

fun LynxViewModel.setClusterFrequency(policyId: Int, minFreq: Long?, maxFreq: Long?, fromMasterProfile: Boolean = false) {
    if (!fromMasterProfile) recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    val curCluster = _uiState.value.clusters.find { it.id == policyId }
    val newMin = minFreq ?: curCluster?.curMin
    val newMax = maxFreq ?: curCluster?.curMax
    val prev = activeClusterIntents[policyId]
    activeClusterIntents[policyId] = LynxViewModel.ClusterIntent(
        minFreq = newMin,
        maxFreq = newMax,
        gov = prev?.gov ?: curCluster?.curGov,
        isLocked = prev?.isLocked ?: curCluster?.isLocked,
        timestamp = System.currentTimeMillis()
    )

    // 1. Synchronous optimistic UI update for instant zero-latency touch response (60/120 FPS)
    _uiState.update { current ->
        val updatedClusters = current.clusters.map { c ->
            if (c.id == policyId) {
                c.copy(
                    curMin = newMin ?: c.curMin,
                    curMax = newMax ?: c.curMax
                )
            } else c
        }
        val updatedCores = current.cpuCores.map { core ->
            val parent = updatedClusters.find { it.containsCore(core.coreId) }
            if (parent != null) {
                core.copy(
                    minFreqKhz = parent.curMin,
                    maxFreqKhz = parent.curMax,
                    isLocked = parent.isLocked
                )
            } else core
        }
        current.copy(
            clusters = updatedClusters,
            cpuCores = updatedCores,
            isCpuModified = if (fromMasterProfile) current.isCpuModified else true
        )
    }

    // 2. Trailing debounce on root execution to prevent I/O queue flood during slider drag
    clusterFreqDebounceJobs[policyId]?.cancel()
    clusterFreqDebounceJobs[policyId] = viewModelScope.launch(Dispatchers.IO) {
        delay(120L)
        LynxRepository.setClusterFreq(policyId, minFreq, maxFreq)
        if (fromMasterProfile) {
            _uiState.update { it.copy(isCpuModified = false) }
        }
    }
}

fun LynxViewModel.setClusterGovernor(policyId: Int, gov: String, fromMasterProfile: Boolean = false) {
    if (!fromMasterProfile) recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    val curCluster = _uiState.value.clusters.find { it.id == policyId }
    val prev = activeClusterIntents[policyId]
    activeClusterIntents[policyId] = LynxViewModel.ClusterIntent(
        minFreq = prev?.minFreq ?: curCluster?.curMin,
        maxFreq = prev?.maxFreq ?: curCluster?.curMax,
        gov = gov,
        isLocked = prev?.isLocked ?: curCluster?.isLocked,
        timestamp = System.currentTimeMillis()
    )

    // Optimistic update for zero-latency touch response
    _uiState.update { current ->
        val updated = current.clusters.map { c ->
            if (c.id == policyId) c.copy(curGov = gov) else c
        }
        current.copy(clusters = updated, isCpuModified = if (fromMasterProfile) current.isCpuModified else true)
    }

    viewModelScope.launch(Dispatchers.IO) {
        LynxRepository.setClusterGov(policyId, gov)
        if (fromMasterProfile) {
            _uiState.update { it.copy(isCpuModified = false) }
        }
    }
}

fun LynxViewModel.setClusterLock(policyId: Int, lock: Boolean, minFreq: Long? = null, maxFreq: Long? = null, fromMasterProfile: Boolean = false) {
    if (!fromMasterProfile) recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    val curCluster = _uiState.value.clusters.find { it.id == policyId }
    val effectiveMin = minFreq ?: curCluster?.curMin
    val effectiveMax = maxFreq ?: curCluster?.curMax
    val prev = activeClusterIntents[policyId]
    activeClusterIntents[policyId] = LynxViewModel.ClusterIntent(
        minFreq = effectiveMin,
        maxFreq = effectiveMax,
        gov = prev?.gov ?: curCluster?.curGov,
        isLocked = lock,
        timestamp = System.currentTimeMillis()
    )

    // Optimistic update for zero-latency touch response
    _uiState.update { current ->
        val updatedClusters = current.clusters.map { c ->
            if (c.id == policyId) {
                c.copy(
                    isLocked = lock,
                    curMin = effectiveMin ?: c.curMin,
                    curMax = effectiveMax ?: c.curMax
                )
            } else c
        }
        val updatedCores = current.cpuCores.map { core ->
            val parent = updatedClusters.find { it.containsCore(core.coreId) }
            if (parent != null) {
                core.copy(
                    minFreqKhz = parent.curMin,
                    maxFreqKhz = parent.curMax,
                    isLocked = parent.isLocked
                )
            } else core
        }
        current.copy(
            clusters = updatedClusters,
            cpuCores = updatedCores,
            isCpuModified = if (fromMasterProfile) current.isCpuModified else true
        )
    }

    viewModelScope.launch(Dispatchers.IO) {
        LynxRepository.setClusterLock(policyId, lock, effectiveMin, effectiveMax)
        if (fromMasterProfile) {
            _uiState.update { it.copy(isCpuModified = false) }
        }
    }
}

fun LynxViewModel.setSchedLoadBoost(littleBoost: Int, bigBoost: Int, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    _uiState.update {
        it.copy(
            schedulerInfo = it.schedulerInfo.copy(
                littleSchedLoadBoost = littleBoost,
                bigSchedLoadBoost = bigBoost
            ),
            isCpuModified = true
        )
    }
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.setSchedLoadBoost(littleBoost, bigBoost, context)
        if (ok) {
            LynxSchedulerInfoRepository.updateCache {
                it.copy(
                    littleSchedLoadBoost = littleBoost,
                    bigSchedLoadBoost = bigBoost
                )
            }
            _uiState.update {
                it.copy(successMessage = "Bias Kapasitas Tugas: Little $littleBoost / Big $bigBoost")
            }
            refreshSchedulerInfo(context ?: LynxRepository.appContext)
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal menyetel Bias Kapasitas Tugas") }
        }
    }
}
