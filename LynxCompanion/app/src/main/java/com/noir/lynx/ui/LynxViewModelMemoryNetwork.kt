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

fun LynxViewModel.refreshKsmStats() {
    viewModelScope.launch {
        try {
            val ksm = LynxRepository.readKsmStats()
            _uiState.update { it.copy(ksmStats = ksm) }
        } catch (_: Exception) {}
    }
}

fun LynxViewModel.setKsmEnabled(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { it.copy(ksmStats = it.ksmStats.copy(enabled = enabled)) }
    viewModelScope.launch {
        val ok = LynxRepository.setKsmEnabled(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = "KSM ${if (enabled) "diaktifkan" else "dinonaktifkan"}") }
        } else {
            _uiState.update { it.copy(errorMessage = "Fitur KSM tidak didukung oleh kernel ini") }
        }
        delay(200L)
        refreshKsmStats()
    }
}

fun LynxViewModel.setKsmTunables(pagesToScan: Int, sleepMs: Int) {
    recordStateMutation()
    _uiState.update { it.copy(ksmStats = it.ksmStats.copy(pagesToScan = pagesToScan, sleepMs = sleepMs)) }
    viewModelScope.launch {
        LynxRepository.setKsmTunables(pagesToScan, sleepMs)
        delay(200L)
        refreshKsmStats()
    }
}

// ----------------------------------------------------------------
//  Phase 1 Quick Wins: I/O Scheduler
// ----------------------------------------------------------------

fun LynxViewModel.refreshIoDevices() {
    viewModelScope.launch {
        try {
            val raw = LynxRepository.readIoDevices()
            val devices = mergeIoDevicesWithIntents(raw)
            _uiState.update { it.copy(ioDevices = devices) }
        } catch (_: Exception) {}
    }
}

fun LynxViewModel.setIoScheduler(device: String, scheduler: String) {
    recordStateMutation()
    activeIoSchedulerIntents[device] = Pair(scheduler, System.currentTimeMillis())
    _uiState.update { current ->
        current.copy(ioDevices = current.ioDevices.map {
            if (it.device == device) it.copy(currentScheduler = scheduler) else it
        })
    }
    viewModelScope.launch {
        LynxRepository.setIoScheduler(device, scheduler)
        delay(250L)
        refreshIoDevices()
    }
}

fun LynxViewModel.setReadAheadKb(device: String, kb: Int) {
    recordStateMutation()
    activeReadAheadIntents[device] = Pair(kb, System.currentTimeMillis())
    _uiState.update { current ->
        current.copy(ioDevices = current.ioDevices.map {
            if (it.device == device) it.copy(readAheadKb = kb) else it
        })
    }
    viewModelScope.launch {
        LynxRepository.setReadAheadKb(device, kb)
        delay(250L)
        refreshIoDevices()
    }
}

// ----------------------------------------------------------------
//  Phase 1 Quick Wins: TCP Congestion Control
// ----------------------------------------------------------------

fun LynxViewModel.refreshTcpAlgorithms() {
    viewModelScope.launch {
        try {
            val algs = LynxRepository.readAvailableTcpAlgorithms()
            val current = LynxRepository.readCurrentTcpCongestion()
            val dns = LynxRepository.readPrivateDns()
            _uiState.update { it.copy(
                availableTcpAlgorithms = algs,
                currentTcpCongestion = current,
                privateDnsMode = dns.first,
                privateDnsSpecifier = dns.second,
                state = if (current.isNotBlank()) it.state.copy(network = it.state.network.copy(
                    tcpCongestion = current,
                    privateDnsMode = dns.first,
                    privateDnsSpecifier = dns.second
                )) else it.state
            ) }
        } catch (_: Exception) {}
    }
}

fun LynxViewModel.setPrivateDns(mode: String, specifier: String) {
    recordStateMutation()
    _uiState.update { it.copy(
        privateDnsMode = mode,
        privateDnsSpecifier = specifier,
        state = it.state.copy(network = it.state.network.copy(privateDnsMode = mode, privateDnsSpecifier = specifier))
    ) }
    viewModelScope.launch {
        val ok = LynxRepository.applyPrivateDns(mode, specifier)
        if (ok) {
            val label = if (mode == "hostname") specifier else "Off (ISP)"
            _uiState.update { it.copy(successMessage = "Private DNS: $label diterapkan") }
            LynxRepository.writeStateKey("network.private_dns_mode", mode, "str")
            LynxRepository.writeStateKey("network.private_dns_specifier", specifier, "str")
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal menerapkan Private DNS") }
        }
    }
}

fun LynxViewModel.refreshAudioSubsystem() {
    viewModelScope.launch {
        try {
            val audioTelem = LynxRepository.readAudioTelemetry()
            val soundCtrl = LynxRepository.readSoundControl()
            _uiState.update { it.copy(
                audioTelemetry = audioTelem,
                soundControl = soundCtrl,
                state = it.state.copy(audio = it.state.audio.copy(
                    lowLatencyMmap = audioTelem.isMmapForced,
                    deepBufferBypass = audioTelem.isDeepBufferBypassed,
                    directDacOffload = audioTelem.isDirectDacOffload
                ))
            ) }
        } catch (_: Exception) {}
    }
}

fun LynxViewModel.setAudioDeepBufferBypass(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(deepBufferBypass = enabled)))
    }
    setKey("audio.deep_buffer_bypass", enabled.toString(), "bool")
    viewModelScope.launch {
        val ok = LynxRepository.applyAudioDeepBufferBypass(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = if (enabled) "Deep Buffer Bypass diaktifkan (Zero Latency)" else "Deep Buffer Bypass dinonaktifkan") }
        }
    }
}

fun LynxViewModel.setAudioDirectDacOffload(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(directDacOffload = enabled)))
    }
    setKey("audio.direct_dac_offload", enabled.toString(), "bool")
    viewModelScope.launch {
        val ok = LynxRepository.applyAudioDirectDacOffload(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = if (enabled) "Direct DAC Offload (32KB buffer) diaktifkan" else "Direct DAC Offload dinonaktifkan") }
        }
    }
}

fun LynxViewModel.setTcpCongestion(algorithm: String) {
    recordStateMutation()
    _uiState.update { it.copy(
        currentTcpCongestion = algorithm,
        state = it.state.copy(network = it.state.network.copy(tcpCongestion = algorithm))
    ) }
    viewModelScope.launch {
        val ok = LynxRepository.setTcpCongestion(algorithm)
        if (ok) {
            val current = LynxRepository.readCurrentTcpCongestion()
            val effective = if (current.isNotBlank()) current else algorithm
            _uiState.update { it.copy(
                currentTcpCongestion = effective,
                state = it.state.copy(network = it.state.network.copy(tcpCongestion = effective)),
                successMessage = "TCP: $algorithm berhasil diterapkan"
            ) }
            LynxRepository.writeStateKey("network.tcp_congestion", algorithm, "str")
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal menerapkan TCP $algorithm (Kernel menolak)") }
        }
    }
}

// ----------------------------------------------------------------
//  Phase 1 Quick Wins: Governor Tunables
// ----------------------------------------------------------------

fun LynxViewModel.loadGovernorTunables(policyId: Int, governor: String) {
    viewModelScope.launch {
        try {
            val tunables = LynxRepository.readGovernorTunables(policyId, governor)
            _uiState.update { it.copy(
                governorTunables = it.governorTunables + (policyId to tunables)
            ) }
        } catch (_: Exception) {}
    }
}

fun LynxViewModel.setGovernorTunable(policyId: Int, governor: String, key: String, value: String) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    _uiState.update { state ->
        val curList = state.governorTunables[policyId] ?: emptyList()
        val updatedList = curList.map { if (it.key == key) it.copy(currentValue = value) else it }
        state.copy(governorTunables = state.governorTunables + (policyId to updatedList))
    }
    viewModelScope.launch {
        LynxRepository.setGovernorTunable(policyId, governor, key, value)
        delay(200L)
        loadGovernorTunables(policyId, governor)
    }
}

// ----------------------------------------------------------------
//  Phase 1 Quick Wins: LMK Minfree Preset
// ----------------------------------------------------------------

fun LynxViewModel.applyLmkPreset(preset: String) {
    recordStateMutation()
    viewModelScope.launch {
        val ok = LynxRepository.applyLmkPreset(preset)
        _uiState.update {
            if (ok) it.copy(successMessage = "LMK preset '$preset' diterapkan")
            else it.copy(errorMessage = "LMK tidak didukung — kernel ini menggunakan LMKD userspace")
        }
    }
}

// ----------------------------------------------------------------
//  Phase 2: Game Mode & Per-App Profiles (applist_perf)
// ----------------------------------------------------------------

fun LynxViewModel.refreshApplistPerf() {
    viewModelScope.launch {
        try {
            val list = LynxRepository.readApplistPerf()
            _uiState.update { it.copy(applistPerf = list) }
        } catch (_: Exception) {}
    }
}

fun LynxViewModel.addAppToPerf(pkg: String) {
    viewModelScope.launch {
        val ok = LynxRepository.addAppToPerf(pkg)
        if (ok) {
            refreshApplistPerf()
            _uiState.update { it.copy(successMessage = "Ditambahkan ke Game Mode: $pkg") }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal menambahkan package") }
        }
    }
}

fun LynxViewModel.removeAppFromPerf(pkg: String) {
    viewModelScope.launch {
        val ok = LynxRepository.removeAppFromPerf(pkg)
        if (ok) {
            refreshApplistPerf()
            _uiState.update { it.copy(successMessage = "Dihapus dari Game Mode: $pkg") }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal menghapus package") }
        }
    }
}

fun LynxViewModel.refreshInstalledApps() {
    viewModelScope.launch {
        try {
            val apps = LynxRepository.readInstalledApps()
            val appList = LynxRepository.readInstalledAppInfos()
            _uiState.update { it.copy(installedApps = apps, installedAppList = appList) }
        } catch (_: Exception) {}
    }
}

// ----------------------------------------------------------------
//  Phase 2: Wakelocks & Deep Sleep Monitor
// ----------------------------------------------------------------

fun LynxViewModel.refreshWakelocks() {
    viewModelScope.launch {
        try {
            val w = LynxRepository.readWakelocks()
            val top = LynxRepository.readTopWakelocks()
            val blocker = LynxRepository.readWakelockBlockerInfo()
            _uiState.update { it.copy(
                wakelocks = w,
                topWakelocks = top,
                wakelockBlockerInfo = blocker,
                successMessage = "Wakelock kernel diperbarui (${top.size} sumber)"
            ) }
        } catch (_: Exception) {}
    }
}

fun LynxViewModel.toggleWakelockBlocked(name: String, blocked: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        val blocker = current.wakelockBlockerInfo
        val updated = if (blocked) blocker.blockedWakelocks + name else blocker.blockedWakelocks - name
        current.copy(wakelockBlockerInfo = blocker.copy(blockedWakelocks = updated))
    }
    viewModelScope.launch {
        val ok = LynxRepository.setWakelockBlocked(name, blocked)
        if (ok) {
            val blocker = LynxRepository.readWakelockBlockerInfo()
            _uiState.update { it.copy(
                wakelockBlockerInfo = blocker,
                successMessage = if (blocked) "Wakelock '$name' diblokir" else "Wakelock '$name' dibuka"
            ) }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal mengubah blokir wakelock (Driver Boeffla tidak tersedia di kernel ini)") }
        }
    }
}

fun LynxViewModel.toggleAggressiveDoze(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        val blocker = current.wakelockBlockerInfo
        current.copy(wakelockBlockerInfo = blocker.copy(aggressiveDozeEnabled = enabled))
    }
    viewModelScope.launch {
        val ok = LynxRepository.toggleAggressiveDoze(enabled)
        if (ok) {
            val blocker = LynxRepository.readWakelockBlockerInfo()
            _uiState.update { it.copy(
                wakelockBlockerInfo = blocker.copy(aggressiveDozeEnabled = enabled),
                successMessage = if (enabled) "Aggressive Doze: Paksa Idle aktif" else "Aggressive Doze: Standar"
            ) }
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal mengatur status Doze sistem") }
        }
    }
}


// ----------------------------------------------------------------
//  Modern M3 & Multi-SoC Flexibility
// ----------------------------------------------------------------

fun LynxViewModel.setAccentColor(accent: String) {
    _uiState.update { it.copy(selectedAccent = accent) }
}

