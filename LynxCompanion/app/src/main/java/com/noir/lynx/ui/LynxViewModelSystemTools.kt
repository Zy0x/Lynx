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

fun LynxViewModel.refreshDisplayRefreshRate() {
    viewModelScope.launch {
        try {
            val raw = LynxRepository.readDisplayRefreshRate()
            val isAuto = LynxRepository.readIsAutoRefreshRate()
            val hz = mergeRefreshRateWithIntent(raw)
            _uiState.update { it.copy(displayRefreshRate = hz, isAutoRefreshRate = isAuto) }
        } catch (_: Exception) {}
    }
}

fun LynxViewModel.setDisplayRefreshRate(hz: Int, isAuto: Boolean = false) {
    recordStateMutation()
    activeRefreshRateIntent = Pair(hz, System.currentTimeMillis())
    _uiState.update { it.copy(displayRefreshRate = hz, isAutoRefreshRate = isAuto) }
    viewModelScope.launch {
        val ok = LynxRepository.setDisplayRefreshRate(hz, isAuto)
        if (ok) {
            val freshHz = LynxRepository.readDisplayRefreshRate()
            val freshAuto = LynxRepository.readIsAutoRefreshRate()
            _uiState.update {
                it.copy(
                    displayRefreshRate = if (freshHz > 0) freshHz else hz,
                    isAutoRefreshRate = freshAuto,
                    successMessage = if (isAuto) "Mode Refresh Rate Auto Dinamis diaktifkan" else "Refresh rate diatur ke ${hz}Hz"
                )
            }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal mengatur refresh rate layar") }
        }
    }
}

fun LynxViewModel.setSocOverride(soc: String) {
    recordStateMutation()
    _uiState.update { it.copy(socOverride = soc) }
    viewModelScope.launch {
        LynxRepository.setSocOverride(soc)
        val gpu = LynxRepository.readGpuInfo()
        _uiState.update { it.copy(gpuInfo = gpu, successMessage = "Engine SoC diubah ke: $soc") }
    }
}

fun LynxViewModel.scanKernelCapabilities() {
    viewModelScope.launch {
        val report = LynxRepository.scanKernelCapabilities()
        _uiState.update { it.copy(capabilityReport = report) }
    }
}

// ----------------------------------------------------------------
//  Action Commands
// ----------------------------------------------------------------

fun LynxViewModel.runMaintenance() {
    viewModelScope.launch {
        _uiState.update { it.copy(maintenanceRunning = true) }
        val result = LynxRepository.runMaintenance()
        _uiState.update {
            it.copy(
                maintenanceRunning = false,
                errorMessage = if (result.startsWith("Error")) result else null
            )
        }
    }
}

fun LynxViewModel.exportBugReport() {
    viewModelScope.launch {
        _uiState.update { it.copy(exportRunning = true) }
        val result = LynxRepository.exportBugReport()
        _uiState.update {
            it.copy(
                exportRunning = false,
                errorMessage = if (result.startsWith("Error")) result else null
            )
        }
    }
}

fun LynxViewModel.runCCleaner() {
    viewModelScope.launch {
        LynxRepository.runCCleaner()
    }
}

fun LynxViewModel.dismissError() {
    _uiState.update { it.copy(errorMessage = null) }
}

fun LynxViewModel.dismissSuccess() {
    _uiState.update { it.copy(successMessage = null) }
}

// ----------------------------------------------------------------
//  Navigation & Kernel Flasher Controls
// ----------------------------------------------------------------

