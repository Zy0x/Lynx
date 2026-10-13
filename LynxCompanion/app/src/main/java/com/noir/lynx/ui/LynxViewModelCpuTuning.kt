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
    viewModelScope.launch {
        // Optimistic update for zero-latency touch response
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
        LynxRepository.setClusterFreq(policyId, minFreq, maxFreq)
        refreshClusters()
        refreshCpuCores()
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
    viewModelScope.launch {
        // Optimistic update for zero-latency touch response
        _uiState.update { current ->
            val updated = current.clusters.map { c ->
                if (c.id == policyId) c.copy(curGov = gov) else c
            }
            current.copy(clusters = updated, isCpuModified = if (fromMasterProfile) current.isCpuModified else true)
        }
        LynxRepository.setClusterGov(policyId, gov)
        refreshClusters()
        refreshCpuCores()
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
    viewModelScope.launch {
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
        LynxRepository.setClusterLock(policyId, lock, effectiveMin, effectiveMax)
        refreshClusters()
        refreshCpuCores()
        if (fromMasterProfile) {
            _uiState.update { it.copy(isCpuModified = false) }
        }
    }
}

// ----------------------------------------------------------------
//  Profile Management
// ----------------------------------------------------------------

