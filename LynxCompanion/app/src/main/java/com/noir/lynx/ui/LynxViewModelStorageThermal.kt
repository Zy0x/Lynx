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

fun LynxViewModel.setZramCompAlgorithm(algo: String) {
    recordStateMutation()
    _uiState.update { it.copy(zramCompAlgorithm = algo) }
    viewModelScope.launch {
        val zramMb = _uiState.value.state.memory.zramSizeMb
        val ok = LynxRepository.setZramCompAlgorithm(algo, zramMb)
        if (ok) {
            _uiState.update { it.copy(successMessage = "Algoritma ZRAM berhasil diubah ke $algo") }
            refreshState()
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal mengubah algoritma ZRAM ke $algo") }
        }
    }
}

fun LynxViewModel.loadCustomRules() {
    viewModelScope.launch {
        val script = LynxRepository.readCustomRules()
        _uiState.update { it.copy(customRulesScript = script) }
    }
}

fun LynxViewModel.saveCustomRules(script: String) {
    viewModelScope.launch {
        val ok = LynxRepository.saveCustomRules(script)
        if (ok) {
            _uiState.update { it.copy(customRulesScript = script, successMessage = "Aturan kustom custom_rules.sh berhasil disimpan!") }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal menyimpan custom_rules.sh") }
        }
    }
}

fun LynxViewModel.executeCustomRules() {
    viewModelScope.launch {
        _uiState.update { it.copy(customRulesRunning = true, customRulesOutput = "Mengeksekusi aturan kustom...\n") }
        val output = LynxRepository.executeCustomRules()
        _uiState.update { it.copy(customRulesRunning = false, customRulesOutput = output, successMessage = "Eksekusi skrip kustom selesai!") }
    }
}

fun LynxViewModel.refreshThermalZones() {
    viewModelScope.launch {
        val zones = LynxRepository.readThermalZones()
        _uiState.update { current ->
            val writableCount = zones.count { it.isWritable }
            current.copy(
                thermalZones = zones,
                thermalStatusDetails = current.thermalStatusDetails.copy(
                    writableZonesCount = writableCount,
                    totalZonesCount = zones.size
                )
            )
        }
    }
}

fun LynxViewModel.refreshThermalCapabilities() {
    viewModelScope.launch {
        com.noir.lynx.safety.LynxPersistenceManager.checkKernelChangeAndInvalidate()
        val rootInfo = com.noir.lynx.hardware.RootCapabilityChecker.check()
        val caps = com.noir.lynx.hardware.ThermalCapabilityDetector.detect()
        val zones = LynxRepository.readThermalZones()
        _uiState.update { current ->
            current.copy(
                rootEnvironment = rootInfo,
                thermalCapabilities = caps,
                thermalZones = zones,
                thermalStatusDetails = current.thermalStatusDetails.copy(
                    writableZonesCount = caps.writableZonesCount,
                    totalZonesCount = caps.totalZones
                )
            )
        }
    }
}

fun LynxViewModel.dropCaches() {
    viewModelScope.launch {
        val ok = LynxRepository.dropCaches()
        if (ok) {
            val tel = LynxRepository.readTelemetry()
            _uiState.update { it.copy(telemetry = tel, successMessage = "Cache RAM berhasil dibersihkan (Drop Caches)!") }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal membersihkan cache RAM.") }
        }
    }
}

fun LynxViewModel.setDirtyRatio(ratio: Int) {
    recordStateMutation()
    _uiState.update { it.copy(
        dirtyRatio = ratio,
        vmAdvanced = it.vmAdvanced.copy(dirtyRatio = ratio, activePreset = "custom")
    ) }
    viewModelScope.launch {
        val ok = LynxRepository.setDirtyRatio(ratio)
        if (ok) {
            _uiState.update { it.copy(successMessage = "VM Dirty Ratio diatur ke $ratio%") }
        }
    }
}

fun LynxViewModel.setDirtyBackgroundRatio(ratio: Int) {
    recordStateMutation()
    _uiState.update { it.copy(
        vmAdvanced = it.vmAdvanced.copy(dirtyBackgroundRatio = ratio, activePreset = "custom")
    ) }
    viewModelScope.launch {
        val ok = LynxRepository.setDirtyBackgroundRatio(ratio)
        if (ok) {
            _uiState.update { it.copy(successMessage = "VM Dirty Background: $ratio%") }
        }
    }
}

fun LynxViewModel.setVfsCachePressure(pressure: Int) {
    recordStateMutation()
    _uiState.update { it.copy(
        vfsCachePressure = pressure,
        vmAdvanced = it.vmAdvanced.copy(vfsCachePressure = pressure, activePreset = "custom")
    ) }
    viewModelScope.launch {
        val ok = LynxRepository.setVfsCachePressure(pressure)
        if (ok) {
            _uiState.update { it.copy(successMessage = "VFS Cache Pressure diatur ke $pressure") }
        }
    }
}

fun LynxViewModel.setDirtyExpireCentisecs(cs: Int) {
    recordStateMutation()
    _uiState.update { it.copy(
        vmAdvanced = it.vmAdvanced.copy(dirtyExpireCentisecs = cs, activePreset = "custom")
    ) }
    viewModelScope.launch {
        val ok = LynxRepository.setDirtyExpireCentisecs(cs)
        if (ok) {
            _uiState.update { it.copy(successMessage = "VM Expire Time: ${cs / 100}s") }
        }
    }
}

fun LynxViewModel.setDirtyWritebackCentisecs(cs: Int) {
    recordStateMutation()
    _uiState.update { it.copy(
        vmAdvanced = it.vmAdvanced.copy(dirtyWritebackCentisecs = cs, activePreset = "custom")
    ) }
    viewModelScope.launch {
        val ok = LynxRepository.setDirtyWritebackCentisecs(cs)
        if (ok) {
            _uiState.update { it.copy(successMessage = "VM Writeback Interval: ${cs / 100}s") }
        }
    }
}

fun LynxViewModel.setVmStatInterval(interval: Int) {
    recordStateMutation()
    _uiState.update { it.copy(
        vmAdvanced = it.vmAdvanced.copy(statInterval = interval, activePreset = "custom")
    ) }
    viewModelScope.launch {
        val ok = LynxRepository.setVmStatInterval(interval)
        if (ok) {
            _uiState.update { it.copy(successMessage = "VM Stat Interval: ${interval}s") }
        }
    }
}

fun LynxViewModel.applyVmPreset(preset: String) {
    recordStateMutation()
    _uiState.update { it.copy(vmAdvanced = it.vmAdvanced.copy(activePreset = preset)) }
    viewModelScope.launch {
        val ok = LynxRepository.applyVmPreset(preset)
        if (ok) {
            val freshVm = LynxRepository.readVirtualMemoryAdvancedConfig()
            _uiState.update { it.copy(
                vmAdvanced = freshVm,
                dirtyRatio = freshVm.dirtyRatio,
                vfsCachePressure = freshVm.vfsCachePressure,
                successMessage = "Preset VM '$preset' berhasil diterapkan"
            ) }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal menerapkan preset VM '$preset'") }
        }
    }
}

