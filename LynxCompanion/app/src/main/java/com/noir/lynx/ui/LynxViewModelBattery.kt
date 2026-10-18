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

fun LynxViewModel.setBypassCharging(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                charging = current.state.charging.copy(bypassEnabled = enabled)
            )
        )
    }
    setKey("charging.bypass_enabled", enabled.toString(), "bool")
    viewModelScope.launch {
        val chg = _uiState.value.state.charging
        val isLaptop = _uiState.value.batteryDetails?.isLaptopPort == true
        LynxRepository.applyChargingMode(
            bypass = enabled,
            extremeCharging = chg.extremeChargingEnabled,
            limitMa = chg.limitCurrentMa,
            highTargetPercent = chg.highCurrentTargetPercent,
            lockoutBypass = chg.thermalLockoutBypassEnabled,
            tempGuard = chg.emergencyTempGuardEnabled,
            maxBatteryPercent = chg.maxBatteryPercent,
            isUnconstrainedMaxHw = chg.isUnconstrainedMaxHw,
            isLaptopPort = isLaptop
        )
    }
}

fun LynxViewModel.setAutoCutEnabled(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                charging = current.state.charging.copy(autoCutEnabled = enabled)
            ),
            batteryDetails = if (!enabled) {
                current.batteryDetails?.copy(
                    isSmartLimitLatched = false,
                    isBypassActive = current.batteryDetails?.isBypassActive == true && current.state.charging.bypassEnabled
                )
            } else current.batteryDetails
        )
    }
    setKey("charging.auto_cut_enabled", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.setAutoCutEnabled(enabled)
        refreshBatteryDetails()
        _uiState.update {
            it.copy(
                successMessage = if (enabled) {
                    "Batas Pengisian Cerdas diaktifkan (${_uiState.value.state.charging.maxBatteryPercent}%)."
                } else {
                    "Batas Pengisian Cerdas dinonaktifkan (Pengisian normal hingga 100%)."
                }
            )
        }
    }
}

fun LynxViewModel.setExtremeCharging(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                charging = current.state.charging.copy(extremeChargingEnabled = enabled)
            )
        )
    }
    setKey("charging.extreme_charging_enabled", enabled.toString(), "bool")
    viewModelScope.launch {
        val chg = _uiState.value.state.charging
        val isLaptop = _uiState.value.batteryDetails?.isLaptopPort == true
        LynxRepository.applyChargingMode(
            bypass = chg.bypassEnabled,
            extremeCharging = enabled,
            limitMa = chg.limitCurrentMa,
            highTargetPercent = chg.highCurrentTargetPercent,
            lockoutBypass = chg.thermalLockoutBypassEnabled,
            tempGuard = chg.emergencyTempGuardEnabled,
            maxBatteryPercent = chg.maxBatteryPercent,
            isUnconstrainedMaxHw = chg.isUnconstrainedMaxHw,
            isLaptopPort = isLaptop
        )
    }
}

fun LynxViewModel.setMasterFastCharging(enabled: Boolean) {
    recordStateMutation()
    val curMa = _uiState.value.state.charging.limitCurrentMa
    val targetMa = if (enabled && curMa < 3000) 6000 else curMa
    val isMaxHw = _uiState.value.state.charging.isUnconstrainedMaxHw || (enabled && targetMa >= 6000)
    val isLaptop = _uiState.value.batteryDetails?.isLaptopPort == true

    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                charging = current.state.charging.copy(
                    extremeChargingEnabled = enabled,
                    thermalLockoutBypassEnabled = false,
                    limitCurrentMa = targetMa,
                    isUnconstrainedMaxHw = isMaxHw
                )
            )
        )
    }

    viewModelScope.launch(Dispatchers.IO) {
        recordStateMutation()
        val ok = LynxRepository.applyMasterFastCharging(
            enabled = enabled,
            targetMa = targetMa,
            isUnconstrainedMaxHw = isMaxHw,
            lockoutBypass = false,
            isLaptopPort = isLaptop
        )
        recordStateMutation()
        refreshBatteryDetails()
        _uiState.update {
            it.copy(
                successMessage = if (enabled) {
                    if (ok) "Pengisian cepat OEM aktif; arus mengikuti adaptor, perangkat, suhu, dan level baterai." else "Gagal mengaktifkan Super Fast Charging"
                } else {
                    "Super Fast Charging Dinonaktifkan (Mode Standar)"
                }
            )
        }
    }
}

fun LynxViewModel.setQuickCurrentPreset(ma: Int, isMaxHw: Boolean = false) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                charging = current.state.charging.copy(
                    limitCurrentMa = ma,
                    isUnconstrainedMaxHw = isMaxHw
                )
            )
        )
    }

    viewModelScope.launch(Dispatchers.IO) {
        recordStateMutation()
        LynxRepository.writeStateKey("charging.limit_current_ma", ma.toString(), "val")
        LynxRepository.writeStateKey("charging.is_unconstrained_max_hw", isMaxHw.toString(), "bool")
        recordStateMutation()
        val chg = _uiState.value.state.charging
        val isLaptop = _uiState.value.batteryDetails?.isLaptopPort == true
        LynxRepository.applyChargingMode(
            bypass = chg.bypassEnabled,
            extremeCharging = chg.extremeChargingEnabled,
            limitMa = ma,
            highTargetPercent = chg.highCurrentTargetPercent,
            lockoutBypass = chg.thermalLockoutBypassEnabled,
            tempGuard = chg.emergencyTempGuardEnabled,
            maxBatteryPercent = chg.maxBatteryPercent,
            isUnconstrainedMaxHw = isMaxHw,
            isLaptopPort = isLaptop
        )
        recordStateMutation()
        refreshBatteryDetails()
        _uiState.update {
            it.copy(
                successMessage = if (isMaxHw) "Mode arus maksimum OEM dipilih; proteksi perangkat tetap aktif." else "Batas permintaan arus: ${ma} mA"
            )
        }
    }
}

fun LynxViewModel.setChargingMode(mode: String, customMa: Int = 2000) {
    recordStateMutation()
    val normalized = when (mode.lowercase()) {
        "cool" -> "cool"
        "hyper" -> "hyper"
        "custom" -> "custom"
        else -> "standard"
    }
    val targetMa = when (normalized) {
        "cool" -> 1000
        "custom" -> customMa.coerceIn(500, 25000)
        "hyper" -> 12000
        else -> 2000
    }
    val isExtreme = normalized == "hyper"
    val isMaxHw = normalized == "hyper"

    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                charging = current.state.charging.copy(
                    chargingMode = normalized,
                    extremeChargingEnabled = isExtreme,
                    limitCurrentMa = targetMa,
                    isUnconstrainedMaxHw = isMaxHw,
                    bypassEnabled = false
                )
            ),
            batteryDetails = current.batteryDetails?.copy(
                isBypassActive = false,
                isSmartLimitLatched = false
            )
        )
    }

    viewModelScope.launch(Dispatchers.IO) {
        recordStateMutation()
        setKey("charging.bypass_enabled", "false", "bool")
        val ok = LynxRepository.setChargingMode(normalized, targetMa)
        recordStateMutation()
        refreshBatteryDetails()
        _uiState.update {
            it.copy(
                successMessage = if (ok) {
                    when (normalized) {
                        "cool" -> "Mode Sejuk diaktifkan (~1.0A). Suhu minimal & menjaga keawetan baterai."
                        "standard" -> "Mode Standar OEM diaktifkan. Regulasi pabrik diterapkan."
                        "hyper" -> "Mode Hyper diaktifkan! Batas arus dibuka penuh mengikuti potensi adaptor."
                        "custom" -> "Mode Kustom diaktifkan. Batas arus diatur ke ${targetMa} mA."
                        else -> "Mode pengisian diperbarui."
                    }
                } else {
                    "Gagal menerapkan mode pengisian daya."
                }
            )
        }
    }
}

fun LynxViewModel.setCustomCurrentLimit(customMa: Int) {
    val clamped = customMa.coerceIn(500, 25000)
    setChargingMode("custom", clamped)
    closeCustomCurrentDialog()
}

fun LynxViewModel.openCustomCurrentDialog() {
    _uiState.update { it.copy(isCustomCurrentDialogOpen = true) }
}

fun LynxViewModel.closeCustomCurrentDialog() {
    _uiState.update { it.copy(isCustomCurrentDialogOpen = false) }
}

fun LynxViewModel.openBatteryDetailSheet() {
    _uiState.update { it.copy(isBatteryDetailSheetOpen = true) }
    refreshBatteryDetails(forceDeepStats = true)
}

fun LynxViewModel.closeBatteryDetailSheet() {
    _uiState.update { it.copy(isBatteryDetailSheetOpen = false) }
}

fun LynxViewModel.setBatterySubTab(tab: Int) {
    _uiState.update { it.copy(batterySubTab = tab) }
    if (tab == 1) {
        refreshBatteryDetails(forceDeepStats = true)
    }
}

fun LynxViewModel.setNightSleepGuard(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                charging = current.state.charging.copy(nightSleepGuardEnabled = enabled)
            )
        )
    }
    setKey("charging.night_sleep_guard_enabled", enabled.toString(), "bool")
    viewModelScope.launch {
        LynxRepository.setNightSleepGuard(enabled)
        _uiState.update {
            it.copy(
                successMessage = if (enabled) "Night Sleep Guard Aktif (Auto-Bypass 80% pada 23:00 - 06:00)" else "Night Sleep Guard Dinonaktifkan"
            )
        }
    }
}

fun LynxViewModel.setDualCellMultiplier(multiplier: Int) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                charging = current.state.charging.copy(dualCellMultiplier = multiplier)
            )
        )
    }
    setKey("charging.dual_cell_multiplier", multiplier.toString(), "val")
    viewModelScope.launch {
        LynxRepository.setDualCellMultiplier(multiplier)
        refreshBatteryDetails()
        _uiState.update {
            it.copy(
                successMessage = "Kalibrasi Sel Baterai: ${multiplier}x (${if (multiplier == 2) "Dual-Cell 2S Series" else "Single Cell Standar"})"
            )
        }
    }
}

fun LynxViewModel.runCableQualityBenchmark() {
    if (_uiState.value.isCableBenchmarkRunning) return
    _uiState.update { it.copy(isCableBenchmarkRunning = true) }
    viewModelScope.launch {
        try {
            val mult = _uiState.value.state.charging.dualCellMultiplier
            val res = LynxRepository.runCableQualityBenchmark(multiplier = mult)
            _uiState.update { current ->
                current.copy(
                    batteryInfoStats = current.batteryInfoStats.copy(cableBenchmark = res),
                    successMessage = "Pengujian Selesai: ${res.starRating}★ (${res.resistanceMohm} mΩ) - ${res.qualityVerdict}"
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Gagal menjalankan pengujian kabel: ${e.message}") }
        } finally {
            _uiState.update { it.copy(isCableBenchmarkRunning = false) }
        }
    }
}

fun LynxViewModel.calibrateBatteryStats() {
    if (_uiState.value.isCalibratingBattery) return
    _uiState.update { it.copy(isCalibratingBattery = true) }
    viewModelScope.launch {
        val ok = LynxRepository.calibrateBatteryStats()
        delay(1200)
        _uiState.update { current ->
            current.copy(
                isCalibratingBattery = false,
                successMessage = if (ok) "Indikator Baterai & Fuel Gauge Berhasil Dikalibrasi!" else "Gagal mereset statistik baterai"
            )
        }
        refreshBatteryDetails()
    }
}

fun LynxViewModel.forceMaxSuperCharge() {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                charging = current.state.charging.copy(
                    extremeChargingEnabled = true,
                    thermalLockoutBypassEnabled = false,
                    limitCurrentMa = 6000,
                    highCurrentTargetPercent = 100,
                    isUnconstrainedMaxHw = true
                )
            )
        )
    }
    viewModelScope.launch {
        val ok = LynxRepository.forceMaxSuperCharge()
        LynxRepository.reapplyExtremeChargingLock()
        val details = LynxRepository.readBatteryDetails()
        if (details != null) {
            _uiState.update { it.copy(batteryDetails = details, successMessage = if (ok) "Mode pengisian cepat OEM maksimal aktif; proteksi perangkat tetap bekerja." else "Gagal mengaktifkan mode pengisian cepat") }
        }
    }
}

fun LynxViewModel.setHighCurrentTarget(percent: Int) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                charging = current.state.charging.copy(highCurrentTargetPercent = percent)
            )
        )
    }
    setKey("charging.high_current_target_percent", percent.toString(), "val")
    viewModelScope.launch {
        val chg = _uiState.value.state.charging
        LynxRepository.applyChargingMode(
            bypass = chg.bypassEnabled,
            extremeCharging = chg.extremeChargingEnabled,
            limitMa = chg.limitCurrentMa,
            highTargetPercent = percent,
            lockoutBypass = chg.thermalLockoutBypassEnabled,
            tempGuard = chg.emergencyTempGuardEnabled,
            maxBatteryPercent = chg.maxBatteryPercent
        )
    }
}

fun LynxViewModel.setEmergencyTempGuard(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                charging = current.state.charging.copy(emergencyTempGuardEnabled = enabled)
            )
        )
    }
    setKey("charging.emergency_temp_guard_enabled", enabled.toString(), "bool")
}

@Suppress("UNUSED_PARAMETER")
fun LynxViewModel.setThermalLockoutBypass(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                charging = current.state.charging.copy(thermalLockoutBypassEnabled = false)
            )
        )
    }
    viewModelScope.launch {
        LynxRepository.setThermalLockoutBypass(false)
        val chg = _uiState.value.state.charging
        LynxRepository.applyChargingMode(
            bypass = chg.bypassEnabled,
            extremeCharging = chg.extremeChargingEnabled,
            limitMa = chg.limitCurrentMa,
            highTargetPercent = chg.highCurrentTargetPercent,
            lockoutBypass = false,
            tempGuard = chg.emergencyTempGuardEnabled,
            maxBatteryPercent = chg.maxBatteryPercent
        )
    }
}

fun LynxViewModel.setSmartTapering(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                charging = current.state.charging.copy(smartTaperingEnabled = enabled)
            )
        )
    }
    setKey("charging.smart_tapering_enabled", enabled.toString(), "bool")
    viewModelScope.launch {
        LynxRepository.setSmartTapering(enabled)
    }
}

fun LynxViewModel.setTempCutoff(temp: Int) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                charging = current.state.charging.copy(tempCutoffC = temp)
            )
        )
    }
    setKey("charging.temp_cutoff_c", temp.toString(), "val")
}

fun LynxViewModel.setChargeCurrentLimit(ma: Int) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                charging = current.state.charging.copy(limitCurrentMa = ma)
            )
        )
    }
    setKey("charging.limit_current_ma", ma.toString(), "val")
    viewModelScope.launch {
        val chg = _uiState.value.state.charging
        LynxRepository.applyChargingMode(
            bypass = chg.bypassEnabled,
            extremeCharging = chg.extremeChargingEnabled,
            limitMa = ma,
            highTargetPercent = chg.highCurrentTargetPercent,
            lockoutBypass = chg.thermalLockoutBypassEnabled,
            tempGuard = chg.emergencyTempGuardEnabled,
            maxBatteryPercent = chg.maxBatteryPercent
        )
    }
}

