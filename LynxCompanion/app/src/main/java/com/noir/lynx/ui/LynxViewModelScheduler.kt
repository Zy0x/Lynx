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

fun LynxViewModel.refreshSchedulerInfo(context: Context? = null) {
    viewModelScope.launch {
        val sched = safeReadSchedulerInfo(context)
        _uiState.update { it.copy(schedulerInfo = sched) }
    }
}

fun LynxViewModel.setSchedulerTunable(tunable: String, value: Long, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    if (tunable == "uclamp_min") {
        val pctVal = value.toInt()
        activeSchedulerIntent = LynxViewModel.SchedulerIntent(uclampMin = pctVal, topAppSchedtuneBoost = pctVal)
    }
    _uiState.update { current ->
        val updated = when (tunable) {
            "up_rate_limit_us" -> current.schedulerInfo.copy(upRateLimitUs = value)
            "down_rate_limit_us" -> current.schedulerInfo.copy(downRateLimitUs = value)
            "sched_latency_ns" -> current.schedulerInfo.copy(schedLatencyNs = value)
            "sched_min_granularity_ns" -> current.schedulerInfo.copy(schedMinGranularityNs = value)
            "sched_wakeup_granularity_ns" -> current.schedulerInfo.copy(schedWakeupGranularityNs = value)
            "sched_migration_cost_ns" -> current.schedulerInfo.copy(schedMigrationCostNs = value)
            "sched_child_runs_first" -> current.schedulerInfo.copy(schedChildRunsFirst = value == 1L)
            "sched_energy_aware" -> current.schedulerInfo.copy(schedEnergyAware = value == 1L)
            "sched_boost" -> current.schedulerInfo.copy(schedBoost = value.toInt())
            "uclamp_min" -> {
                val pctVal = value.toInt()
                current.schedulerInfo.copy(
                    uclampMin = pctVal,
                    topAppSchedtuneBoost = pctVal,
                    topAppPreferIdle = if (pctVal >= 10) true else current.schedulerInfo.topAppPreferIdle
                )
            }
            "uclamp_max" -> current.schedulerInfo.copy(uclampMax = value.toInt())
            "sched_upmigrate" -> current.schedulerInfo.copy(schedUpmigrate = value.toInt())
            "sched_downmigrate" -> current.schedulerInfo.copy(schedDownmigrate = value.toInt())
            "sched_init_task_load" -> current.schedulerInfo.copy(schedInitTaskLoad = value.toInt())
            "sched_spill_nr_run" -> current.schedulerInfo.copy(schedSpillNrRun = value.toInt())
            "sched_spill_load" -> current.schedulerInfo.copy(schedSpillLoad = value.toInt())
            else -> current.schedulerInfo
        }
        val finalSched = if (tunable != "apply_on_boot") updated.copy(activePreset = "custom") else updated
        current.copy(schedulerInfo = finalSched, isCpuModified = true)
    }
    viewModelScope.launch {
        val ok = LynxRepository.setSchedulerTunable(tunable, value, context)
        if (ok) {
            val fresh = safeReadSchedulerInfo(context)
            _uiState.update {
                it.copy(
                    schedulerInfo = fresh,
                    successMessage = if (tunable != "apply_on_boot") "Parameter $tunable diperbarui (Mode Kustom)" else "Pengaturan boot diperbarui"
                )
            }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal memperbarui $tunable") }
        }
    }
}

fun LynxViewModel.setSchedulerHysteresis(upmigrate: Int, downmigrate: Int, context: Context? = null, fromMasterProfile: Boolean = false) {
    if (!fromMasterProfile) recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    val safeUp = upmigrate.coerceIn(40, 100)
    val safeDown = downmigrate.coerceIn(20, safeUp - 5)
    _uiState.update { current ->
        current.copy(schedulerInfo = current.schedulerInfo.copy(
            schedUpmigrate = safeUp,
            schedDownmigrate = safeDown,
            activePreset = if (fromMasterProfile) current.schedulerInfo.activePreset else "custom"
        ), isCpuModified = if (fromMasterProfile) current.isCpuModified else true)
    }
    viewModelScope.launch {
        val ok = LynxRepository.setSchedulerHysteresis(safeUp, safeDown, context)
        if (ok) {
            if (!fromMasterProfile) {
                _uiState.update { it.copy(successMessage = "Hysteresis migrasi diperbarui ($safeUp% / $safeDown% - Mode Kustom)") }
            }
        } else {
            if (!fromMasterProfile) {
                _uiState.update { it.copy(errorMessage = "Gagal memperbarui hysteresis migrasi") }
            }
        }
        if (fromMasterProfile) {
            _uiState.update { it.copy(isCpuModified = false) }
        }
    }
}

fun LynxViewModel.setSchedulerApplyOnBoot(enabled: Boolean, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    _uiState.update { current ->
        current.copy(schedulerInfo = current.schedulerInfo.copy(applyOnBoot = enabled))
    }
    viewModelScope.launch {
        LynxRepository.setSchedulerTunable("apply_on_boot", if (enabled) 1L else 0L, context)
    }
}

fun LynxViewModel.setSchedulerArchitectureMode(mode: String, context: Context? = null, fromMasterProfile: Boolean = false) {
    if (!fromMasterProfile) recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    activeSchedulerIntent = LynxViewModel.SchedulerIntent(architectureMode = mode)
    _uiState.update { current ->
        current.copy(schedulerInfo = current.schedulerInfo.copy(
            activeArchitectureMode = mode,
            activePreset = if (fromMasterProfile) current.schedulerInfo.activePreset else "custom"
        ), isCpuModified = if (fromMasterProfile) current.isCpuModified else true)
    }
    viewModelScope.launch {
        val ok = LynxRepository.setSchedulerArchitectureMode(mode, context)
        if (ok) {
            val fresh = safeReadSchedulerInfo(context)
            if (!fromMasterProfile) {
                _uiState.update { it.copy(schedulerInfo = fresh, successMessage = "Mode arsitektur ${mode.uppercase()} aktif (Mode Kustom)") }
            } else {
                _uiState.update { it.copy(schedulerInfo = fresh) }
            }
        } else {
            if (!fromMasterProfile) {
                _uiState.update { it.copy(errorMessage = "Gagal mengubah mode ke ${mode.uppercase()}") }
            }
        }
        if (fromMasterProfile) {
            _uiState.update { it.copy(isCpuModified = false) }
        }
    }
}

fun LynxViewModel.applySchedulerPreset(preset: String, context: Context? = null, fromMasterProfile: Boolean = false) {
    if (!fromMasterProfile) recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    val boostPct = when (preset.lowercase()) {
        "battery" -> 0
        "gaming" -> 50
        "extreme" -> 80
        else -> 20
    }
    activeSchedulerIntent = LynxViewModel.SchedulerIntent(uclampMin = boostPct, topAppSchedtuneBoost = boostPct)
    _uiState.update {
        it.copy(
            schedulerInfo = it.schedulerInfo.copy(
                activePreset = preset,
                uclampMin = boostPct,
                topAppSchedtuneBoost = boostPct
            ),
            isCpuModified = if (fromMasterProfile) it.isCpuModified else true
        )
    }
    viewModelScope.launch {
        val ok = LynxRepository.applySchedulerPreset(preset, context)
        if (ok) {
            val fresh = safeReadSchedulerInfo(context)
            val presetTitle = when (preset.lowercase()) {
                "extreme" -> "Extreme (Unrestricted)"
                "gaming" -> "Responsif (Gaming)"
                "battery" -> "Efisiensi Daya"
                else -> "Seimbang"
            }
            _uiState.update { it.copy(schedulerInfo = fresh, successMessage = if (fromMasterProfile) it.successMessage else "Preset Penjadwal '$presetTitle' berhasil diterapkan", isCpuModified = if (fromMasterProfile) it.isCpuModified else true) }
        } else {
            if (!fromMasterProfile) {
                _uiState.update { it.copy(errorMessage = "Gagal menerapkan preset penjadwal '$preset'") }
            }
        }
        if (fromMasterProfile) {
            _uiState.update { it.copy(isCpuModified = false) }
        }
    }
}

// ── CPU Sets & Task Affinity Isolation (Task Shield) ───────────

fun LynxViewModel.loadCpuSetsInfo(context: Context? = null) {
    viewModelScope.launch {
        try {
            val sets = safeReadCpuSetsInfo(context)
            _uiState.update { it.copy(cpuSets = sets) }
        } catch (_: Exception) {
        }
    }
}

fun LynxViewModel.applyCpuSetPreset(preset: String, context: Context? = null, fromMasterProfile: Boolean = false) {
    if (!fromMasterProfile) recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    val totalCores = _uiState.value.cpuSets.totalCoresCount
    _uiState.update { it.copy(cpuSets = it.cpuSets.copy(activePreset = preset), isCpuModified = if (fromMasterProfile) it.isCpuModified else true) }
    viewModelScope.launch {
        val ok = LynxRepository.applyCpuSetPreset(preset, totalCores, context)
        if (ok) {
            val fresh = safeReadCpuSetsInfo(context)
            val presetTitle = when (preset.lowercase()) {
                "gaming" -> "Gaming Isolation (Big Core Reserved)"
                "battery" -> "Hemat Ekstrem"
                else -> "Standar Android"
            }
            _uiState.update { it.copy(cpuSets = fresh, successMessage = if (fromMasterProfile) it.successMessage else "Profil CPU Sets '$presetTitle' berhasil diterapkan", isCpuModified = if (fromMasterProfile) it.isCpuModified else true) }
        } else {
            if (!fromMasterProfile) {
                _uiState.update { it.copy(errorMessage = "Gagal menerapkan profil CPU Sets '$preset'") }
            }
        }
        if (fromMasterProfile) {
            _uiState.update { it.copy(isCpuModified = false) }
        }
    }
}

fun LynxViewModel.toggleCpuSetCore(group: String, coreId: Int, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    val currentSets = _uiState.value.cpuSets
    val currentCores = when (group.lowercase()) {
        "top-app", "top_app", "game" -> currentSets.parseCores(currentSets.topAppCpus)
        "foreground", "fg" -> currentSets.parseCores(currentSets.foregroundCpus)
        "background", "bg" -> currentSets.parseCores(currentSets.backgroundCpus)
        "system-background", "system_background", "sysbg" -> currentSets.parseCores(currentSets.systemBackgroundCpus)
        "restricted" -> currentSets.parseCores(currentSets.restrictedCpus)
        else -> emptySet()
    }.toMutableSet()

    if (currentCores.contains(coreId)) {
        if (currentCores.size > 1) {
            currentCores.remove(coreId)
        }
    } else {
        currentCores.add(coreId)
    }

    val newCoresStr = CpuSetsInfo.formatCoresSet(currentCores)
    activeCpuSetIntent = when (group.lowercase()) {
        "top-app", "top_app", "game" -> LynxViewModel.CpuSetIntent(topAppCpus = newCoresStr)
        "foreground", "fg" -> LynxViewModel.CpuSetIntent(foregroundCpus = newCoresStr)
        "background", "bg" -> LynxViewModel.CpuSetIntent(backgroundCpus = newCoresStr)
        "system-background", "system_background", "sysbg" -> LynxViewModel.CpuSetIntent(systemBackgroundCpus = newCoresStr)
        "restricted" -> LynxViewModel.CpuSetIntent(restrictedCpus = newCoresStr)
        else -> null
    }

    val updatedSets = when (group.lowercase()) {
        "top-app", "top_app", "game" -> currentSets.copy(topAppCpus = newCoresStr, activePreset = "custom")
        "foreground", "fg" -> currentSets.copy(foregroundCpus = newCoresStr, activePreset = "custom")
        "background", "bg" -> currentSets.copy(backgroundCpus = newCoresStr, activePreset = "custom")
        "system-background", "system_background", "sysbg" -> currentSets.copy(systemBackgroundCpus = newCoresStr, activePreset = "custom")
        "restricted" -> currentSets.copy(restrictedCpus = newCoresStr, activePreset = "custom")
        else -> currentSets
    }

    _uiState.update { it.copy(cpuSets = updatedSets, isCpuModified = true) }
    viewModelScope.launch {
        val ok = LynxRepository.setCpuSetCores(group, newCoresStr, context)
        if (ok) {
            val fresh = safeReadCpuSetsInfo(context)
            _uiState.update { it.copy(cpuSets = fresh, isCpuModified = true) }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal memperbarui Core $coreId pada $group") }
        }
    }
}

fun LynxViewModel.setCpuSetApplyOnBoot(enabled: Boolean, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    _uiState.update { it.copy(cpuSets = it.cpuSets.copy(applyOnBoot = enabled)) }
    viewModelScope.launch {
        LynxRepository.setCpuSetApplyOnBoot(enabled, context)
    }
}

// ── Schedtune & Scheduler Hints Controls ─────────────────────────
fun LynxViewModel.setSchedtuneBoost(group: String, boost: Int, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    if (group.lowercase() in listOf("top-app", "topapp")) {
        activeSchedulerIntent = LynxViewModel.SchedulerIntent(uclampMin = boost, topAppSchedtuneBoost = boost)
    }
    _uiState.update { current ->
        val s = current.schedulerInfo
        val updated = when (group.lowercase()) {
            "top-app", "topapp" -> s.copy(topAppSchedtuneBoost = boost, uclampMin = boost, activePreset = "custom")
            "foreground", "fg" -> s.copy(fgSchedtuneBoost = boost, activePreset = "custom")
            "background", "bg" -> s.copy(bgSchedtuneBoost = boost, activePreset = "custom")
            else -> s
        }
        current.copy(schedulerInfo = updated)
    }
    viewModelScope.launch {
        val ok = LynxRepository.setSchedtuneBoost(group, boost, context)
        if (ok) {
            val fresh = safeReadSchedulerInfo(context)
            _uiState.update { it.copy(schedulerInfo = fresh) }
        }
    }
}

fun LynxViewModel.setSchedtunePreferIdle(group: String, preferIdle: Boolean, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    if (group.lowercase() in listOf("top-app", "topapp")) {
        activeSchedulerIntent = LynxViewModel.SchedulerIntent(schedtunePreferIdle = preferIdle)
    }
    _uiState.update { current ->
        val s = current.schedulerInfo
        val updated = when (group.lowercase()) {
            "top-app", "topapp" -> s.copy(topAppPreferIdle = preferIdle, activePreset = "custom")
            "foreground", "fg" -> s.copy(fgPreferIdle = preferIdle, activePreset = "custom")
            "background", "bg" -> s.copy(bgPreferIdle = preferIdle, activePreset = "custom")
            else -> s
        }
        current.copy(schedulerInfo = updated)
    }
    viewModelScope.launch {
        val ok = LynxRepository.setSchedtunePreferIdle(group, preferIdle, context)
        if (ok) {
            val fresh = safeReadSchedulerInfo(context)
            _uiState.update { it.copy(schedulerInfo = fresh) }
        }
    }
}

fun LynxViewModel.setSchedulerHint(hintKey: String, enabled: Boolean, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    _uiState.update { current ->
        val s = current.schedulerInfo
        val updated = when (hintKey) {
            "sched_big_task_rotation" -> s.copy(schedBigTaskRotation = enabled)
            "sched_sync_hint_enable" -> s.copy(schedSyncHintEnable = enabled)
            "sched_cstate_aware" -> s.copy(schedCstateAware = enabled)
            else -> s
        }
        current.copy(schedulerInfo = updated)
    }
    viewModelScope.launch {
        val ok = LynxRepository.setSchedulerHint(hintKey, enabled, context)
        if (ok) {
            val fresh = safeReadSchedulerInfo(context)
            _uiState.update { it.copy(schedulerInfo = fresh) }
        }
    }
}

fun LynxViewModel.setWorkqueuePowerEfficient(enabled: Boolean, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    activeSchedulerIntent = LynxViewModel.SchedulerIntent(workqueuePowerEfficient = enabled)
    _uiState.update { current ->
        current.copy(
            schedulerInfo = current.schedulerInfo.copy(workqueuePowerEfficient = enabled),
            isCpuModified = true
        )
    }
    viewModelScope.launch {
        val ok = LynxRepository.setWorkqueuePowerEfficient(enabled, context)
        if (ok) {
            val fresh = safeReadSchedulerInfo(context)
            _uiState.update {
                it.copy(
                    schedulerInfo = fresh,
                    successMessage = "Power-Efficient Workqueue ${if (enabled) "diaktifkan (Hemat Daya)" else "dinonaktifkan (Latensi Rendah)"}"
                )
            }
        }
    }
}

// ── Platform Hardware Engine (MediaTek PPM & Qualcomm Boost) ──
fun LynxViewModel.setPpmPolicy(policyIdx: Int, enabled: Boolean, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    val intent = when (policyIdx) {
        0 -> LynxViewModel.SchedulerIntent(ppmPwrThrottlingEnabled = enabled)
        1 -> LynxViewModel.SchedulerIntent(ppmThermalThrottlingEnabled = enabled)
        2 -> LynxViewModel.SchedulerIntent(ppmSysBoostEnabled = enabled)
        4 -> LynxViewModel.SchedulerIntent(ppmDlptBypassEnabled = enabled)
        else -> null
    }
    if (intent != null) activeSchedulerIntent = intent
    _uiState.update { current ->
        val curS = current.schedulerInfo
        val updated = when (policyIdx) {
            0 -> curS.copy(ppmPwrThrottlingEnabled = enabled)
            1 -> curS.copy(ppmThermalThrottlingEnabled = enabled)
            2 -> curS.copy(ppmSysBoostEnabled = enabled)
            4 -> curS.copy(ppmDlptBypassEnabled = enabled)
            else -> curS
        }
        current.copy(schedulerInfo = updated, isCpuModified = true)
    }
    viewModelScope.launch {
        val ok = LynxRepository.setPpmPolicy(policyIdx, enabled, context)
        if (ok) {
            val fresh = safeReadSchedulerInfo(context)
            _uiState.update { it.copy(schedulerInfo = fresh, successMessage = "Kebijakan PPM diperbarui") }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal mengubah kebijakan PPM") }
        }
    }
}

fun LynxViewModel.setQcomTouchboost(enabled: Boolean, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    activeSchedulerIntent = LynxViewModel.SchedulerIntent(qcomTouchboostEnabled = enabled, universalTouchBoostEnabled = enabled)
    _uiState.update { current ->
        current.copy(
            schedulerInfo = current.schedulerInfo.copy(
                qcomTouchboostEnabled = enabled,
                universalTouchBoostEnabled = enabled
            ),
            isCpuModified = true
        )
    }
    viewModelScope.launch {
        val ok = LynxRepository.setQcomTouchboost(enabled, context)
        if (ok) {
            val fresh = safeReadSchedulerInfo(context)
            _uiState.update { it.copy(schedulerInfo = fresh, successMessage = "Qualcomm Touchboost ${if (enabled) "diaktifkan" else "dinonaktifkan"}") }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal mengubah status Touchboost") }
        }
    }
}

fun LynxViewModel.setQcomInputBoost(freq: Long, durationMs: Int, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    viewModelScope.launch {
        val ok = LynxRepository.setQcomInputBoost(freq, durationMs, context)
        if (ok) {
            val fresh = safeReadSchedulerInfo(context)
            _uiState.update { it.copy(schedulerInfo = fresh, successMessage = "Qualcomm Input Boost diperbarui") }
        }
    }
}

fun LynxViewModel.setMtkInterconnectBusBoost(enabled: Boolean, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    activeSchedulerIntent = LynxViewModel.SchedulerIntent(mtkCciPerfMode = enabled)
    _uiState.update {
        it.copy(
            schedulerInfo = it.schedulerInfo.copy(
                mtkCciPerfMode = enabled,
                mtkDvfsrcBoostEnabled = enabled
            ),
            isCpuModified = true
        )
    }
    viewModelScope.launch {
        val ok = LynxRepository.setMtkInterconnectBusBoost(enabled, context)
        if (ok) {
            val fresh = safeReadSchedulerInfo(context)
            _uiState.update {
                it.copy(
                    schedulerInfo = fresh,
                    successMessage = "Akselerasi Bus CCI & Memori DDR ${if (enabled) "diaktifkan" else "dinonaktifkan"}"
                )
            }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal mengubah mode Bus CCI & Memori") }
        }
    }
}

fun LynxViewModel.setMtkCpuPowerMode(mode: Int, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    val safeMode = when (mode) {
        1 -> 1
        3 -> 3
        else -> 0
    }
    activeSchedulerIntent = LynxViewModel.SchedulerIntent(mtkCpuPowerMode = safeMode)
    _uiState.update {
        it.copy(
            schedulerInfo = it.schedulerInfo.copy(mtkCpuPowerMode = safeMode),
            isCpuModified = true
        )
    }
    viewModelScope.launch {
        val ok = LynxRepository.setMtkCpuPowerMode(safeMode, context)
        if (ok) {
            val fresh = safeReadSchedulerInfo(context)
            val label = when (safeMode) {
                1 -> "Hemat [1]"
                3 -> "Performa [3]"
                else -> "Default [0]"
            }
            _uiState.update {
                it.copy(
                    schedulerInfo = fresh,
                    successMessage = "Mode Daya DVFS Silikon diatur ke $label"
                )
            }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal mengubah Mode Daya DVFS Silikon") }
        }
    }
}

fun LynxViewModel.setMtkDlptImaxBypass(enabled: Boolean, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    activeSchedulerIntent = LynxViewModel.SchedulerIntent(ppmDlptBypassEnabled = enabled)
    _uiState.update {
        it.copy(
            schedulerInfo = it.schedulerInfo.copy(ppmDlptBypassEnabled = enabled),
            isCpuModified = true
        )
    }
    viewModelScope.launch {
        val ok = LynxRepository.setMtkDlptImaxBypass(enabled, context)
        if (ok) {
            val fresh = safeReadSchedulerInfo(context)
            _uiState.update {
                it.copy(
                    schedulerInfo = fresh,
                    successMessage = "Bypass Limit Arus Puncak (DLPT & Imax) ${if (enabled) "diaktifkan" else "dinonaktifkan"}"
                )
            }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal mengubah Bypass DLPT & Imax") }
        }
    }
}

fun LynxViewModel.setQcomDevfreqBusBoost(enabled: Boolean, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    activeSchedulerIntent = LynxViewModel.SchedulerIntent(qcomDevfreqBusBoostEnabled = enabled)
    _uiState.update {
        it.copy(
            schedulerInfo = it.schedulerInfo.copy(qcomDevfreqBusBoostEnabled = enabled),
            isCpuModified = true
        )
    }
    viewModelScope.launch {
        val ok = LynxRepository.setQcomDevfreqBusBoost(enabled, context)
        if (ok) {
            val fresh = safeReadSchedulerInfo(context)
            _uiState.update {
                it.copy(
                    schedulerInfo = fresh,
                    successMessage = "Akselerasi Bus Memori & Cache L3 (Devfreq) ${if (enabled) "diaktifkan" else "dinonaktifkan"}"
                )
            }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal mengubah Akselerasi Bus Devfreq") }
        }
    }
}

fun LynxViewModel.setUniversalBusBandwidthProfile(profile: String, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    val safeProfile = when (profile.lowercase()) {
        "eco", "powersave" -> "eco"
        "balanced", "balance" -> "balanced"
        "max", "performance", "extreme" -> "max"
        else -> "auto"
    }
    val isBoost = (safeProfile == "max")
    _uiState.update {
        it.copy(
            schedulerInfo = it.schedulerInfo.copy(
                busBandwidthProfile = safeProfile,
                mtkCciPerfMode = isBoost,
                mtkDvfsrcBoostEnabled = isBoost,
                qcomDevfreqBusBoostEnabled = isBoost
            ),
            isCpuModified = true
        )
    }
    viewModelScope.launch {
        val ok = LynxRepository.setUniversalBusBandwidthProfile(safeProfile, context)
        if (ok) {
            val fresh = safeReadSchedulerInfo(context)
            val label = when (safeProfile) {
                "eco" -> "Efisien"
                "balanced" -> "Seimbang"
                "max" -> "Maksimum"
                else -> "Otomatis"
            }
            _uiState.update {
                it.copy(
                    schedulerInfo = fresh,
                    successMessage = "Frekuensi Bus Memori & Interkoneksi diatur ke $label"
                )
            }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal mengubah profil Bus Memori & Interkoneksi") }
        }
    }
}

fun LynxViewModel.setUniversalTouchBoost(enabled: Boolean, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    activeSchedulerIntent = LynxViewModel.SchedulerIntent(universalTouchBoostEnabled = enabled, qcomTouchboostEnabled = enabled)
    _uiState.update {
        it.copy(
            schedulerInfo = it.schedulerInfo.copy(
                universalTouchBoostEnabled = enabled,
                qcomTouchboostEnabled = enabled
            ),
            isCpuModified = true
        )
    }
    viewModelScope.launch {
        val ok = LynxRepository.setUniversalTouchBoost(enabled, context)
        if (ok) {
            val fresh = safeReadSchedulerInfo(context)
            _uiState.update {
                it.copy(
                    schedulerInfo = fresh,
                    successMessage = "Akselerasi Respons Sentuhan ${if (enabled) "diaktifkan" else "dinonaktifkan"}"
                )
            }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal mengubah Akselerasi Respons Sentuhan") }
        }
    }
}

fun LynxViewModel.setUniversalAntiThrottlingGuard(enabled: Boolean, context: Context? = null) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    activeSchedulerIntent = LynxViewModel.SchedulerIntent(antiThrottlingGuardEnabled = enabled, ppmDlptBypassEnabled = enabled)
    _uiState.update {
        it.copy(
            schedulerInfo = it.schedulerInfo.copy(
                antiThrottlingGuardEnabled = enabled,
                ppmDlptBypassEnabled = enabled,
                ppmPwrThrottlingEnabled = !enabled,
                ppmSysBoostEnabled = enabled
            ),
            isCpuModified = true
        )
    }
    viewModelScope.launch {
        val ok = LynxRepository.setUniversalAntiThrottlingGuard(enabled, context)
        if (ok) {
            val fresh = safeReadSchedulerInfo(context)
            _uiState.update {
                it.copy(
                    schedulerInfo = fresh,
                    successMessage = "Pertahankan Plafon Clock Puncak ${if (enabled) "diaktifkan" else "dinonaktifkan"}"
                )
            }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal mengubah Pertahankan Plafon Clock Puncak") }
        }
    }
}

// ── CPU Idle & C-States / Core Parking Controls ───────────────────
