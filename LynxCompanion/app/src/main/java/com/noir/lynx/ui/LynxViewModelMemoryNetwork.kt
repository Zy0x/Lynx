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
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val ksm = LynxRepository.readKsmStats()
            _uiState.update { it.copy(ksmStats = ksm) }
        } catch (_: Exception) {}
    }
}

fun LynxViewModel.setKsmEnabled(enabled: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.MEMORY)
    _uiState.update { it.copy(ksmStats = it.ksmStats.copy(enabled = enabled)) }
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.setKsmEnabled(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = "KSM ${if (enabled) "diaktifkan" else "dinonaktifkan"}") }
        } else {
            val freshKsm = LynxRepository.readKsmStats(forceRefresh = true)
            _uiState.update {
                it.copy(
                    ksmStats = freshKsm,
                    errorMessage = "Kernel tidak mendukung/mengizinkan perubahan status KSM"
                )
            }
        }
    }
}

fun LynxViewModel.setKsmTunables(pagesToScan: Int, sleepMs: Int) {
    val safeScan = pagesToScan.coerceIn(10, 10000)
    val safeSleep = sleepMs.coerceIn(5, 10000)
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.MEMORY)
    _uiState.update { it.copy(ksmStats = it.ksmStats.copy(pagesToScan = safeScan, sleepMs = safeSleep)) }
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.setKsmTunables(safeScan, safeSleep)
        if (ok) {
            _uiState.update { it.copy(successMessage = "Parameter KSM disimpan ($safeScan hal • $safeSleep ms)") }
        } else {
            val freshKsm = LynxRepository.readKsmStats(forceRefresh = true)
            _uiState.update {
                it.copy(
                    ksmStats = freshKsm,
                    errorMessage = "Gagal menulis parameter KSM ke sysfs"
                )
            }
        }
    }
}

// ----------------------------------------------------------------
//  Phase 1 Quick Wins: I/O Scheduler
// ----------------------------------------------------------------

fun LynxViewModel.refreshIoDevices(forceRefresh: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val raw = LynxRepository.readIoDevices(forceRefresh = forceRefresh)
            val devices = mergeIoDevicesWithIntents(raw)
            val volumes = LynxRepository.readStorageVolumes(forceRefresh = false)
            _uiState.update { it.copy(ioDevices = devices, storageVolumes = volumes) }
        } catch (_: Exception) {}
    }
}

fun LynxViewModel.setIoScheduler(device: String, scheduler: String) {
    if (_uiState.value.mountXStorageInfo.isDiskProtected(device)) {
        _uiState.update { it.copy(errorMessage = "Blok $device dikelola MountX. Aktifkan Override terlebih dahulu.") }
        return
    }
    recordStateMutation()
    activeIoSchedulerIntents[device] = Pair(scheduler, System.currentTimeMillis())
    _uiState.update { current ->
        current.copy(ioDevices = current.ioDevices.map {
            if (it.device == device) it.copy(currentScheduler = scheduler) else it
        })
    }
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.setIoScheduler(device, scheduler)
        if (ok) {
            _uiState.update { it.copy(successMessage = "Scheduler $device diubah ke $scheduler") }
        } else {
            activeIoSchedulerIntents.remove(device)
            val fresh = LynxRepository.readIoDevices(forceRefresh = true)
            _uiState.update {
                it.copy(
                    ioDevices = mergeIoDevicesWithIntents(fresh),
                    errorMessage = "Kernel menolak perubahan scheduler '$scheduler' pada $device"
                )
            }
        }
    }
}

fun LynxViewModel.setReadAheadKb(device: String, kb: Int) {
    if (_uiState.value.mountXStorageInfo.isDiskProtected(device)) {
        _uiState.update { it.copy(errorMessage = "Blok $device dikelola MountX. Aktifkan Override terlebih dahulu.") }
        return
    }
    recordStateMutation()
    activeReadAheadIntents[device] = Pair(kb, System.currentTimeMillis())
    _uiState.update { current ->
        current.copy(ioDevices = current.ioDevices.map {
            if (it.device == device) it.copy(readAheadKb = kb) else it
        })
    }
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.setReadAheadKb(device, kb)
        if (ok) {
            _uiState.update { it.copy(successMessage = "Read-Ahead $device diatur ke $kb KB") }
        } else {
            activeReadAheadIntents.remove(device)
            val fresh = LynxRepository.readIoDevices(forceRefresh = true)
            _uiState.update {
                it.copy(
                    ioDevices = mergeIoDevicesWithIntents(fresh),
                    errorMessage = "Gagal mengubah Read-Ahead $device ke $kb KB"
                )
            }
        }
    }
}

fun LynxViewModel.setIoQueueTunable(device: String, key: String, value: Int) {
    if (_uiState.value.mountXStorageInfo.isDiskProtected(device)) {
        _uiState.update { it.copy(errorMessage = "Blok $device dikelola MountX. Aktifkan Override terlebih dahulu.") }
        return
    }
    recordStateMutation()
    _uiState.update { current ->
        current.copy(ioDevices = current.ioDevices.map { info ->
            if (info.device != device) info else when (key) {
                "nr_requests" -> info.copy(nrRequests = value)
                "iostats" -> info.copy(iostats = value)
                "rq_affinity" -> info.copy(rqAffinity = value)
                "add_random" -> info.copy(addRandom = value)
                else -> info
            }
        })
    }
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.setIoQueueTunable(device, key, value)
        if (ok) {
            _uiState.update { it.copy(successMessage = "$key pada $device diatur ke $value") }
        } else {
            val fresh = LynxRepository.readIoDevices(forceRefresh = true)
            _uiState.update {
                it.copy(
                    ioDevices = mergeIoDevicesWithIntents(fresh),
                    errorMessage = "Gagal mengubah parameter I/O $key pada $device"
                )
            }
        }
    }
}

fun LynxViewModel.tuneBlockGroup(
    groupId: String,
    devicesInGroup: List<IoDeviceInfo>,
    param: String,
    value: String
) {
    val mountx = _uiState.value.mountXStorageInfo
    val unlocked = devicesInGroup.filter { !mountx.isDiskProtected(it.device) && !it.mountxLocked && !it.isReadOnly }
    if (unlocked.isEmpty()) {
        _uiState.update {
            it.copy(errorMessage = "Tidak ada blok writable yang dapat diubah (terkunci MountX atau Read-Only).")
        }
        return
    }
    recordStateMutation()
    val memberNames = unlocked.map { it.device }
    val allUnlockedInGroup = unlocked.size == devicesInGroup.size
    val targetArg = if (allUnlockedInGroup && groupId in listOf("internal", "sd", "usb", "dm", "loop", "zram", "ram")) {
        groupId
    } else {
        memberNames.joinToString(",")
    }

    val now = System.currentTimeMillis()
    val numVal = value.toIntOrNull()
    unlocked.forEach { d ->
        if (param == "scheduler") activeIoSchedulerIntents[d.device] = value to now
        if (param == "read_ahead_kb" && numVal != null) activeReadAheadIntents[d.device] = numVal to now
    }

    _uiState.update { current ->
        current.copy(ioDevices = current.ioDevices.map { dev ->
            if (dev.device !in memberNames) dev else when (param) {
                "scheduler" -> dev.copy(currentScheduler = value)
                "read_ahead_kb" -> dev.copy(readAheadKb = numVal ?: dev.readAheadKb)
                "nr_requests" -> dev.copy(nrRequests = numVal)
                "rq_affinity" -> dev.copy(rqAffinity = numVal)
                "iostats" -> dev.copy(iostats = numVal)
                "add_random" -> dev.copy(addRandom = numVal)
                else -> dev
            }
        })
    }

    viewModelScope.launch(Dispatchers.IO) {
        val (ok, count) = LynxRepository.tuneBlockGroup(targetArg, memberNames, param, value)
        if (!ok) {
            memberNames.forEach {
                if (param == "scheduler") activeIoSchedulerIntents.remove(it)
                if (param == "read_ahead_kb") activeReadAheadIntents.remove(it)
            }
        }
        val fresh = LynxRepository.readIoDevices(forceRefresh = true)
        val groupLabel = when (groupId) {
            "internal" -> "Internal"
            "sd" -> "MicroSD"
            "usb" -> "USB OTG"
            "dm" -> "DM Mapper"
            "loop" -> "Loopback"
            "zram" -> "ZRAM"
            "ram" -> "RAMDisk"
            else -> groupId
        }
        val paramLabel = when (param) {
            "scheduler" -> "Scheduler"
            "read_ahead_kb" -> "Read-Ahead"
            "nr_requests" -> "Queue Depth"
            "rq_affinity" -> "RQ Affinity"
            "iostats" -> "I/O Stats"
            "add_random" -> "Add Random"
            else -> param
        }
        _uiState.update {
            if (ok) {
                it.copy(
                    ioDevices = mergeIoDevicesWithIntents(fresh),
                    successMessage = "$paramLabel kelompok $groupLabel diatur ke $value ($count blok)"
                )
            } else {
                it.copy(
                    ioDevices = mergeIoDevicesWithIntents(fresh),
                    errorMessage = "Kernel menolak pengaturan $paramLabel=$value pada kelompok $groupLabel"
                )
            }
        }
    }
}

fun LynxViewModel.applyBlockGroupPreset(
    groupId: String,
    devicesInGroup: List<IoDeviceInfo>,
    preset: String
) {
    val mountx = _uiState.value.mountXStorageInfo
    val unlocked = devicesInGroup.filter { !mountx.isDiskProtected(it.device) && !it.mountxLocked && !it.isReadOnly }
    if (unlocked.isEmpty()) {
        _uiState.update {
            it.copy(errorMessage = "Tidak ada blok writable di kelompok ini (terkunci MountX atau Read-Only).")
        }
        return
    }
    recordStateMutation()
    viewModelScope.launch(Dispatchers.IO) {
        val (ok, count) = LynxRepository.applyBlockGroupPreset(groupId, unlocked, preset)
        val fresh = LynxRepository.readIoDevices(forceRefresh = true)
        val volumes = LynxRepository.readStorageVolumes(forceRefresh = false)
        val presetName = when (preset) {
            "powersave" -> "Standar Aman"
            "performance" -> "Performa"
            else -> "Responsif"
        }
        _uiState.update {
            if (ok) {
                it.copy(
                    ioDevices = fresh,
                    storageVolumes = volumes,
                    successMessage = "Preset $presetName diterapkan ke $count blok kelompok ${groupId.uppercase()}"
                )
            } else {
                it.copy(
                    ioDevices = fresh,
                    errorMessage = "Gagal menerapkan preset $presetName pada kelompok ${groupId.uppercase()}"
                )
            }
        }
    }
}

fun LynxViewModel.setMountXOverride(unlock: Boolean) {
    recordStateMutation()
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.setMountXOverride(unlock)
        if (ok) {
            _uiState.update {
                it.copy(mountXStorageInfo = it.mountXStorageInfo.copy(overrideUnlocked = unlock))
            }
        }
        refreshMountXStorageInfo()
        val freshDevices = LynxRepository.readIoDevices(forceRefresh = true)
        val freshVolumes = LynxRepository.readStorageVolumes(forceRefresh = true)
        _uiState.update {
            if (ok) {
                it.copy(
                    ioDevices = freshDevices,
                    storageVolumes = freshVolumes,
                    successMessage = if (unlock) {
                        "Override MountX diaktifkan untuk sesi ini"
                    } else {
                        "Disk target dikunci kembali dan dikembalikan ke MountX"
                    }
                )
            } else {
                it.copy(
                    ioDevices = freshDevices,
                    errorMessage = "Gagal mengubah status override MountX"
                )
            }
        }
    }
}

// ----------------------------------------------------------------
//  Phase 1 Quick Wins: TCP Congestion Control
// ----------------------------------------------------------------

fun LynxViewModel.refreshTcpAlgorithms() {
    viewModelScope.launch(Dispatchers.IO) {
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
    recordSettingIntent("network.private_dns_mode", mode)
    recordSettingIntent("network.private_dns_specifier", specifier)
    _uiState.update { it.copy(
        privateDnsMode = mode,
        privateDnsSpecifier = specifier,
        state = it.state.copy(network = it.state.network.copy(privateDnsMode = mode, privateDnsSpecifier = specifier))
    ) }
    viewModelScope.launch(Dispatchers.IO) {
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
    viewModelScope.launch(Dispatchers.IO) {
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
    viewModelScope.launch(Dispatchers.IO) {
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
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioDirectDacOffload(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = if (enabled) "Direct DAC Offload (32KB buffer) diaktifkan" else "Direct DAC Offload dinonaktifkan") }
        }
    }
}

fun LynxViewModel.setTcpCongestion(algorithm: String) {
    recordSettingIntent("network.tcp_congestion", algorithm)
    _uiState.update { it.copy(
        currentTcpCongestion = algorithm,
        state = it.state.copy(network = it.state.network.copy(tcpCongestion = algorithm))
    ) }
    viewModelScope.launch(Dispatchers.IO) {
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
    viewModelScope.launch(Dispatchers.IO) {
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
    viewModelScope.launch(Dispatchers.IO) {
        LynxRepository.setGovernorTunable(policyId, governor, key, value)
    }
}

// ----------------------------------------------------------------
//  Phase 1 Quick Wins: LMK Minfree Preset
// ----------------------------------------------------------------

fun LynxViewModel.applyLmkPreset(preset: String) {
    recordStateMutation()
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyLmkPreset(preset)
        if (ok) {
            val freshEntropy = LynxRepository.readMemoryEntropy()
            _uiState.update {
                it.copy(
                    memoryEntropy = freshEntropy,
                    successMessage = "Preset LMK '$preset' diterapkan"
                )
            }
        } else {
            _uiState.update {
                it.copy(errorMessage = "LMK tidak didukung — kernel ini menggunakan LMKD userspace")
            }
        }
    }
}

// ----------------------------------------------------------------
//  Phase 2: Game Mode & Per-App Profiles (applist_perf)
// ----------------------------------------------------------------

fun LynxViewModel.refreshApplistPerf() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val list = LynxRepository.readApplistPerf()
            _uiState.update { it.copy(applistPerf = list) }
        } catch (_: Exception) {}
    }
}

fun LynxViewModel.addAppToPerf(pkg: String) {
    viewModelScope.launch(Dispatchers.IO) {
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
    viewModelScope.launch(Dispatchers.IO) {
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
    viewModelScope.launch(Dispatchers.IO) {
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
    viewModelScope.launch(Dispatchers.IO) {
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
    viewModelScope.launch(Dispatchers.IO) {
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
    viewModelScope.launch(Dispatchers.IO) {
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

