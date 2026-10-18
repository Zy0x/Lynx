package com.noir.lynx.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noir.lynx.data.*
import com.noir.lynx.R
import com.noir.lynx.core.locale.AppLocale
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

fun LynxViewModel.loadCpuIdleInfo(context: Context? = null) {
    viewModelScope.launch {
        val fresh = safeReadCpuIdleInfo(context)
        _uiState.update { current ->
            current.copy(
                cpuIdle = fresh.copy(
                    applyOnBoot = if (fresh.applyOnBoot) true else current.cpuIdle.applyOnBoot
                )
            )
        }
    }
}

fun LynxViewModel.setCpuIdleStateDisabled(stateIndex: Int, disabled: Boolean, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    val currentDisabledMap = _uiState.value.cpuIdle.states.associate { it.index to it.isDisabled }.toMutableMap()
    currentDisabledMap[stateIndex] = disabled
    activeCpuIdleIntent = LynxViewModel.CpuIdleIntent(disabledStates = currentDisabledMap)

    _uiState.update { current ->
        val updatedStates = current.cpuIdle.states.map { s ->
            if (s.index == stateIndex) s.copy(isDisabled = disabled) else s
        }
        current.copy(
            cpuIdle = current.cpuIdle.copy(
                states = updatedStates,
                activePreset = "custom"
            ),
            isCpuModified = true
        )
    }
    viewModelScope.launch {
        val ok = LynxRepository.setCpuIdleStateDisabled(stateIndex, disabled, context)
        if (!ok) {
            _uiState.update { it.copy(errorMessage = "Gagal mengubah status C-State $stateIndex") }
        }
    }
}

fun LynxViewModel.applyCpuIdlePreset(preset: String, context: Context? = null, fromMasterProfile: Boolean = false) {
    if (!fromMasterProfile) recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    val defaultParking = when (preset.lowercase()) {
        "battery" -> "park_big"
        "gaming" -> "unpark_all"
        else -> "dynamic"
    }
    activeCpuIdleIntent = LynxViewModel.CpuIdleIntent(activePreset = preset, coreParkingMode = defaultParking)
    _uiState.update { current ->
        current.copy(
            cpuIdle = current.cpuIdle.copy(activePreset = preset, coreParkingMode = defaultParking),
            isCpuModified = if (fromMasterProfile) current.isCpuModified else true
        )
    }
    viewModelScope.launch {
        val ok = LynxRepository.applyCpuIdlePreset(preset, context)
        if (ok) {
            val presetTitle = when (preset.lowercase()) {
                "gaming" -> context?.let { AppLocale.getString(it, R.string.cpu_idle_preset_gaming) } ?: "Gaming (Zero Latency)"
                "battery" -> context?.let { AppLocale.getString(it, R.string.cpu_idle_preset_battery) } ?: "Hemat Baterai (Deep Sleep)"
                else -> context?.let { AppLocale.getString(it, R.string.cpu_idle_preset_balanced) } ?: "Standar Seimbang"
            }
            _uiState.update { current ->
                current.copy(
                    successMessage = if (fromMasterProfile) current.successMessage else context?.let { AppLocale.getString(it, R.string.dynamic_cpu_idle_preset_applied, presetTitle) } ?: "Profil Efisiensi '$presetTitle' berhasil diterapkan",
                    isCpuModified = if (fromMasterProfile) current.isCpuModified else true
                )
            }
        } else {
            if (!fromMasterProfile) {
                _uiState.update { it.copy(errorMessage = context?.let { AppLocale.getString(it, R.string.dynamic_cpu_idle_preset_failed, preset) } ?: "Gagal menerapkan preset CPU Idle '$preset'") }
            }
        }
        if (fromMasterProfile) {
            _uiState.update { it.copy(isCpuModified = false) }
        }
    }
}

fun LynxViewModel.setCoreParkingMode(mode: String, totalCores: Int = 8, context: Context? = null, fromMasterProfile: Boolean = false) {
    if (!fromMasterProfile) recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    activeCpuIdleIntent = LynxViewModel.CpuIdleIntent(coreParkingMode = mode)
    _uiState.update { current ->
        current.copy(
            cpuIdle = current.cpuIdle.copy(coreParkingMode = mode, activePreset = if (fromMasterProfile) current.cpuIdle.activePreset else "custom"),
            isCpuModified = if (fromMasterProfile) current.isCpuModified else true
        )
    }
    viewModelScope.launch {
        val ok = LynxRepository.setCoreParkingMode(mode, totalCores, context)
        if (ok) {
            val modeLabel = when (mode.lowercase()) {
                "unpark_all" -> context?.let { AppLocale.getString(it, R.string.cpu_parking_unpark_all) } ?: "Unpark Semua Core (Anti-Stutter)"
                "park_big" -> context?.let { AppLocale.getString(it, R.string.cpu_parking_park_big) } ?: "Park Big Core (Hemat Daya)"
                else -> context?.let { AppLocale.getString(it, R.string.cpu_parking_dynamic) } ?: "Dinamis (OEM Default)"
            }
            _uiState.update { current ->
                current.copy(
                    successMessage = if (fromMasterProfile) current.successMessage else context?.let { AppLocale.getString(it, R.string.cpu_parking_mode_changed, modeLabel) } ?: "Mode Core Parking diubah ke '$modeLabel'",
                    isCpuModified = if (fromMasterProfile) current.isCpuModified else true
                )
            }
        } else {
            if (!fromMasterProfile) {
                _uiState.update { current -> current.copy(errorMessage = context?.let { AppLocale.getString(it, R.string.cpu_parking_mode_failed, mode) } ?: "Gagal mengubah mode Core Parking ke '$mode'") }
            }
        }
        if (fromMasterProfile) {
            _uiState.update { it.copy(isCpuModified = false) }
        }
    }
}

fun LynxViewModel.setCpuIdleApplyOnBoot(enabled: Boolean, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    _uiState.update { it.copy(cpuIdle = it.cpuIdle.copy(applyOnBoot = enabled)) }
    viewModelScope.launch {
        LynxRepository.setCpuIdleApplyOnBoot(enabled, context)
    }
}

fun LynxViewModel.setArmPllMode(enabled: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    _uiState.update { it.copy(cpuIdle = it.cpuIdle.copy(armPllMode = enabled, activePreset = "custom")) }
    viewModelScope.launch {
        val ok = LynxRepository.setArmPllMode(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = if (enabled) "ARMPLL Mode Aktif (Hemat Daya)" else "ARMPLL Mode Dimatikan (Performa)") }
        }
    }
}

