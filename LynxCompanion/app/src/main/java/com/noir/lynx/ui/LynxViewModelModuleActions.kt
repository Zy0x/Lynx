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

fun LynxViewModel.switchTab(tabIndex: Int) {
    _uiState.update { it.copy(currentTab = tabIndex) }
    if (tabIndex == 1 && _uiState.value.selectedCpuTab == 2 && isForeground) {
        startCpuMonitorStream()
    } else {
        stopCpuMonitorStream()
    }
    if (tabIndex == 2 || tabIndex == 3) {
        refreshBackups()
    }
}

fun LynxViewModel.setSelectedTuningCategory(category: String?) {
    _uiState.update { it.copy(selectedTuningCategory = category) }
    if (category != null) {
        viewModelScope.launch(Dispatchers.IO) {
            when (category) {
                "thermal" -> loadThermalSubsystemData()
                "memory" -> loadMemorySubsystemData()
                "network" -> {
                    refreshWifiTelemetry()
                    refreshTcpAlgorithms()
                }
                "audio" -> loadAudioSubsystemData()
                "system" -> loadSystemSubsystemData()
                "gpu" -> {
                    refreshGpuInfo()
                    refreshGraphicsCapabilities()
                    refreshDisplayRefreshRate()
                }
            }
        }
    }
}

fun LynxViewModel.loadThermalSubsystemData() {
    viewModelScope.launch(Dispatchers.IO) {
        refreshThermalCapabilities()
        val tz = LynxRepository.readThermalZones()
        if (tz.isNotEmpty()) {
            _uiState.update { it.copy(thermalZones = tz) }
        }
    }
}

fun LynxViewModel.loadMemorySubsystemData() {
    viewModelScope.launch(Dispatchers.IO) {
        val ksmStats = LynxRepository.readKsmStats()
        val ioDevices = LynxRepository.readIoDevices()
        val vmAdvanced = LynxRepository.readVirtualMemoryAdvancedConfig()
        val zramComp = LynxRepository.readZramCompAlgorithms()
        val memoryEntropy = LynxRepository.readMemoryEntropy()
        _uiState.update {
            it.copy(
                ksmStats = ksmStats,
                ioDevices = ioDevices,
                vmAdvanced = vmAdvanced,
                zramCompAlgorithm = zramComp.first,
                availZramCompAlgorithms = zramComp.second,
                memoryEntropy = memoryEntropy
            )
        }
    }
}

fun LynxViewModel.loadSystemSubsystemData() {
    viewModelScope.launch(Dispatchers.IO) {
        val customRules = LynxRepository.readCustomRules()
        val customScripts = LynxRepository.readCustomScripts()
        val voltageInfo = LynxRepository.readVoltageInfo()
        val batteryHealth = LynxRepository.readBatteryHealth()
        val backups = LynxRepository.listBackups()
        _uiState.update {
            it.copy(
                customRulesScript = customRules,
                customScripts = customScripts,
                voltageInfo = voltageInfo,
                batteryHealthStats = batteryHealth,
                backups = backups
            )
        }
    }
}

fun LynxViewModel.loadAudioSubsystemData() {
    viewModelScope.launch(Dispatchers.IO) {
        val sound = LynxRepository.readSoundControl()
        _uiState.update { it.copy(soundControl = sound) }
        refreshAudioSubsystem()
    }
}

fun LynxViewModel.loadWakelocksData() {
    viewModelScope.launch(Dispatchers.IO) {
        val wl = LynxRepository.readWakelocks()
        val wlBlocker = LynxRepository.readWakelockBlockerInfo()
        val topWl = LynxRepository.readTopWakelocks()
        _uiState.update {
            it.copy(
                wakelocks = wl,
                wakelockBlockerInfo = wlBlocker,
                topWakelocks = topWl
            )
        }
    }
}

fun LynxViewModel.refreshBackups() {
    viewModelScope.launch(Dispatchers.IO) {
        val backups = LynxRepository.listBackups()
        _uiState.update { it.copy(backups = backups) }
    }
}

fun LynxViewModel.backupBoot() {
    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update { it.copy(isBackingUp = true) }
        val result = LynxRepository.backupBoot()
        val backups = LynxRepository.listBackups()
        _uiState.update {
            it.copy(
                isBackingUp = false,
                backups = backups,
                successMessage = if (!result.startsWith("Error")) result else null,
                errorMessage = if (result.startsWith("Error")) result else null
            )
        }
    }
}

fun LynxViewModel.restoreBoot(path: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val result = LynxRepository.restoreBoot(path)
        _uiState.update {
            it.copy(
                successMessage = if (!result.startsWith("Error")) result else null,
                errorMessage = if (result.startsWith("Error")) result else null
            )
        }
    }
}

fun LynxViewModel.flashKernel(zipPath: String) {
    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update { it.copy(isFlashing = true, flashLog = "Memulai proses flashing AnyKernel3...\n") }
        val result = LynxRepository.flashKernel(zipPath)
        val backups = LynxRepository.listBackups()
        _uiState.update {
            it.copy(
                isFlashing = false,
                flashLog = it.flashLog + "\n" + result,
                backups = backups,
                successMessage = if (!result.startsWith("Error")) "Flashing kernel berhasil!" else null,
                errorMessage = if (result.startsWith("Error")) result else null
            )
        }
    }
}

fun LynxViewModel.installLynxModule(zipPath: String) {
    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update { it.copy(isFlashing = true, flashLog = "Menginstal modul Lynx Deity...\n") }
        val result = LynxRepository.installLynxModule(zipPath)
        val installed = LynxRepository.isModuleInstalled()
        _uiState.update {
            it.copy(
                isFlashing = false,
                isModuleInstalled = installed,
                flashLog = it.flashLog + "\n" + result,
                successMessage = if (installed) "Modul Lynx Deity berhasil diinstal!" else null
            )
        }
        if (installed) {
            refreshState()
            startFileObserver()
            startPeriodicSync()
        }
    }
}

// ----------------------------------------------------------------
//  Dynamic Zero-Hardcoding Controls
// ----------------------------------------------------------------

