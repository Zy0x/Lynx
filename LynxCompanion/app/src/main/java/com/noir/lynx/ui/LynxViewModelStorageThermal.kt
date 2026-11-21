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
    val liveOrConfigMb = _uiState.value.telemetry?.zramTotalMb?.takeIf { it > 0 }
        ?: _uiState.value.state.memory.zramSizeMb
    applyZramConfiguration(algo, liveOrConfigMb)
}

fun LynxViewModel.applyZramConfiguration(algo: String, mb: Int) {
    if (_uiState.value.isApplyingZram) return
    val targetMb = if (mb <= 0) 0 else mb.coerceAtLeast(128)
    _uiState.update { it.copy(isApplyingZram = true, errorMessage = null) }
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.MEMORY)
    viewModelScope.launch(Dispatchers.IO) {
        val result = LynxRepository.applyZramConfiguration(algo, targetMb)
        val tel = LynxRepository.readTelemetry()
        val zramComp = LynxRepository.readZramCompAlgorithms()
        if (result.success) {
            _uiState.update { current ->
                current.copy(
                    isApplyingZram = false,
                    telemetry = tel,
                    zramCompAlgorithm = result.appliedAlgo.ifBlank { zramComp.first.ifBlank { algo } },
                    availZramCompAlgorithms = zramComp.second.ifEmpty { current.availZramCompAlgorithms },
                    state = current.state.copy(memory = current.state.memory.copy(zramSizeMb = result.appliedSizeMb)),
                    successMessage = result.message
                )
            }
            refreshState()
        } else {
            _uiState.update { current ->
                current.copy(
                    isApplyingZram = false,
                    telemetry = tel,
                    zramCompAlgorithm = result.appliedAlgo.ifBlank { zramComp.first.ifBlank { current.zramCompAlgorithm } },
                    state = if (result.appliedSizeMb > 0) {
                        current.state.copy(memory = current.state.memory.copy(zramSizeMb = result.appliedSizeMb))
                    } else {
                        current.state
                    },
                    errorMessage = result.message
                )
            }
        }
    }
}

fun LynxViewModel.loadCustomRules() {
    viewModelScope.launch(Dispatchers.IO) {
        val script = LynxRepository.readCustomRules()
        _uiState.update { it.copy(customRulesScript = script) }
    }
}

fun LynxViewModel.saveCustomRules(script: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.saveCustomRules(script)
        if (ok) {
            _uiState.update { it.copy(customRulesScript = script, successMessage = "Aturan kustom custom_rules.sh berhasil disimpan!") }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal menyimpan custom_rules.sh") }
        }
    }
}

fun LynxViewModel.executeCustomRules() {
    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update { it.copy(customRulesRunning = true, customRulesOutput = "Mengeksekusi aturan kustom...\n") }
        val output = LynxRepository.executeCustomRules()
        _uiState.update { it.copy(customRulesRunning = false, customRulesOutput = output, successMessage = "Eksekusi skrip kustom selesai!") }
    }
}

fun LynxViewModel.refreshThermalZones() {
    viewModelScope.launch(Dispatchers.IO) {
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
    viewModelScope.launch(Dispatchers.IO) {
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

fun LynxViewModel.cleanRam() {
    if (_uiState.value.isCleaningRam) return
    _uiState.update { it.copy(isCleaningRam = true) }
    viewModelScope.launch(Dispatchers.IO) {
        val result = LynxRepository.cleanRam()
        val tel = LynxRepository.readTelemetry()
        val availableDeltaMb = result.availableDeltaMb
        val msg = if (result.success) {
            when {
                availableDeltaMb == null -> "Pembersihan proses latar aman selesai."
                availableDeltaMb > 0 -> "Setelah pembersihan, MemAvailable naik +${availableDeltaMb} MB sementara."
                else -> "Pembersihan proses latar selesai. Memori tersedia tidak bertambah pada pengukuran ini."
            }
        } else {
            "Gagal meminta Android membersihkan proses latar. Pastikan akses root aktif."
        }
        _uiState.update {
            it.copy(
                isCleaningRam = false,
                telemetry = tel,
                successMessage = if (result.success) msg else null,
                errorMessage = if (!result.success) msg else null
            )
        }
    }
}

fun LynxViewModel.dropCaches() {
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.dropCaches()
        if (ok) {
            val tel = LynxRepository.readTelemetry()
            _uiState.update { it.copy(telemetry = tel, successMessage = "Cache RAM berhasil dibersihkan (Drop Caches)!") }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal membersihkan cache RAM.") }
        }
    }
}

private fun LynxViewModel.applyVmTunable(
    optimisticUpdate: (LynxUiState) -> LynxUiState,
    persistWrite: suspend () -> Boolean,
    successText: String,
    errorText: String
) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.MEMORY)
    _uiState.update(optimisticUpdate)
    viewModelScope.launch(Dispatchers.IO) {
        val ok = persistWrite()
        if (ok) {
            _uiState.update { it.copy(successMessage = successText) }
        } else {
            val freshVm = LynxRepository.readVirtualMemoryAdvancedConfig(forceRefresh = true)
            _uiState.update {
                it.copy(
                    vmAdvanced = freshVm,
                    dirtyRatio = freshVm.dirtyRatio,
                    vfsCachePressure = freshVm.vfsCachePressure,
                    errorMessage = errorText
                )
            }
        }
    }
}

fun LynxViewModel.setDirtyRatio(ratio: Int) {
    val safe = ratio.coerceIn(1, 95)
    applyVmTunable(
        optimisticUpdate = { it.copy(dirtyRatio = safe, vmAdvanced = it.vmAdvanced.copy(dirtyRatio = safe, activePreset = "custom")) },
        persistWrite = { LynxRepository.setDirtyRatio(safe) },
        successText = "VM Dirty Ratio diatur ke $safe%",
        errorText = "Kernel menolak perubahan VM Dirty Ratio ke $safe%"
    )
}

fun LynxViewModel.setDirtyBackgroundRatio(ratio: Int) {
    val safe = ratio.coerceIn(1, 80)
    applyVmTunable(
        optimisticUpdate = { it.copy(vmAdvanced = it.vmAdvanced.copy(dirtyBackgroundRatio = safe, activePreset = "custom")) },
        persistWrite = { LynxRepository.setDirtyBackgroundRatio(safe) },
        successText = "VM Dirty Background diatur ke $safe%",
        errorText = "Kernel menolak perubahan Dirty Background ke $safe%"
    )
}

fun LynxViewModel.setVfsCachePressure(pressure: Int) {
    val safe = pressure.coerceIn(1, 500)
    applyVmTunable(
        optimisticUpdate = { it.copy(vfsCachePressure = safe, vmAdvanced = it.vmAdvanced.copy(vfsCachePressure = safe, activePreset = "custom")) },
        persistWrite = { LynxRepository.setVfsCachePressure(safe) },
        successText = "VFS Cache Pressure diatur ke $safe%",
        errorText = "Kernel menolak perubahan VFS Cache Pressure ke $safe%"
    )
}

fun LynxViewModel.setDirtyExpireCentisecs(cs: Int) {
    val safe = cs.coerceIn(100, 60000)
    applyVmTunable(
        optimisticUpdate = { it.copy(vmAdvanced = it.vmAdvanced.copy(dirtyExpireCentisecs = safe, activePreset = "custom")) },
        persistWrite = { LynxRepository.setDirtyExpireCentisecs(safe) },
        successText = "VM Dirty Expire diatur ke ${safe / 100} detik",
        errorText = "Kernel menolak perubahan Dirty Expire"
    )
}

fun LynxViewModel.setDirtyWritebackCentisecs(cs: Int) {
    val safe = cs.coerceIn(100, 30000)
    applyVmTunable(
        optimisticUpdate = { it.copy(vmAdvanced = it.vmAdvanced.copy(dirtyWritebackCentisecs = safe, activePreset = "custom")) },
        persistWrite = { LynxRepository.setDirtyWritebackCentisecs(safe) },
        successText = "VM Writeback Interval diatur ke ${safe / 100} detik",
        errorText = "Kernel menolak perubahan Writeback Interval"
    )
}

fun LynxViewModel.setVmStatInterval(interval: Int) {
    val safe = interval.coerceIn(1, 60)
    applyVmTunable(
        optimisticUpdate = { it.copy(vmAdvanced = it.vmAdvanced.copy(statInterval = safe, activePreset = "custom")) },
        persistWrite = { LynxRepository.setVmStatInterval(safe) },
        successText = "VM Stat Interval diatur ke ${safe}s",
        errorText = "Kernel menolak perubahan VM Stat Interval"
    )
}

fun LynxViewModel.applyVmPreset(preset: String) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.MEMORY)
    _uiState.update { it.copy(vmAdvanced = it.vmAdvanced.copy(activePreset = preset)) }
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyVmPreset(preset)
        val freshVm = LynxRepository.readVirtualMemoryAdvancedConfig(forceRefresh = true)
        if (ok) {
            _uiState.update {
                it.copy(
                    vmAdvanced = freshVm,
                    dirtyRatio = freshVm.dirtyRatio,
                    vfsCachePressure = freshVm.vfsCachePressure,
                    successMessage = "Preset VM '$preset' berhasil diterapkan"
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    vmAdvanced = freshVm,
                    dirtyRatio = freshVm.dirtyRatio,
                    vfsCachePressure = freshVm.vfsCachePressure,
                    errorMessage = "Gagal menerapkan preset VM '$preset'"
                )
            }
        }
    }
}

