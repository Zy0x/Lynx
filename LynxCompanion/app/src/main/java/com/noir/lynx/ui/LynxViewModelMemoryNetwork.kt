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
    val targetArg = if (allUnlockedInGroup && groupId in listOf("internal", "sd", "usb", "external", "dm", "loop", "zram", "hw_boot", "ram", "other")) {
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
            "external" -> "Eksternal Lain"
            "dm" -> "DM Mapper"
            "loop" -> "Loopback"
            "zram" -> "ZRAM"
            "hw_boot" -> "Boot/RPMB"
            "ram" -> "RAMDisk"
            else -> groupId.uppercase()
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
            val verified = LynxRepository.readPrivateDns()
            val label = when (verified.first) {
                "hostname" -> verified.second.ifBlank { specifier }
                "off" -> "Off (ISP / DHCP)"
                else -> "Otomatis (Opportunistic DoT)"
            }
            _uiState.update {
                it.copy(
                    privateDnsMode = verified.first,
                    privateDnsSpecifier = verified.second,
                    state = it.state.copy(network = it.state.network.copy(
                        privateDnsMode = verified.first,
                        privateDnsSpecifier = verified.second
                    )),
                    successMessage = "Private DNS: $label diterapkan"
                )
            }
            LynxRepository.writeStateKey("network.private_dns_mode", verified.first, "str")
            LynxRepository.writeStateKey("network.private_dns_specifier", verified.second, "str")
        } else {
            _uiState.update { it.copy(errorMessage = "Gagal memverifikasi perubahan Private DNS pada sistem") }
        }
    }
}

private fun deriveAudioPreset(
    mmap: Boolean,
    deepBypass: Boolean,
    dacOffload: Boolean,
    btGuard: Boolean
): String = when {
    !mmap && !deepBypass && !dacOffload && !btGuard -> "oem_default"
    mmap && deepBypass && !dacOffload -> "gaming_mmap"
    !mmap && !deepBypass && dacOffload && btGuard -> "hires_dac"
    mmap && deepBypass && dacOffload && btGuard -> "full_studio"
    else -> "custom"
}

fun LynxViewModel.refreshAudioSubsystem() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val audioTelem = LynxRepository.readAudioTelemetry()
            val soundCtrl = LynxRepository.readSoundControl()
            _uiState.update { current ->
                val btGuard = audioTelem.isBtCrackleGuardActive || current.state.audio.btCrackleGuard
                val preset = deriveAudioPreset(
                    mmap = audioTelem.isMmapForced,
                    deepBypass = audioTelem.isDeepBufferBypassed,
                    dacOffload = audioTelem.isDirectDacOffload,
                    btGuard = btGuard
                )
                current.copy(
                    audioTelemetry = audioTelem,
                    soundControl = soundCtrl,
                    state = current.state.copy(audio = current.state.audio.copy(
                        lowLatencyMmap = audioTelem.isMmapForced,
                        deepBufferBypass = audioTelem.isDeepBufferBypassed,
                        directDacOffload = audioTelem.isDirectDacOffload,
                        btCrackleGuard = btGuard,
                        hiresResampler24Bit = audioTelem.isHiresResamplerActive,
                        codecIdlePopGuard = audioTelem.isCodecIdlePopGuardActive,
                        spatializerBoost = audioTelem.isSpatializerBoostActive,
                        activePreset = preset,
                        disableA2dpHwOffload = audioTelem.a2dpOffloadDisabledLive,
                        systemBtAudioHalEnabled = audioTelem.systemBtAudioHalLive,
                        disableAbsoluteVolume = audioTelem.absoluteVolumeDisabledLive,
                        ldacQualityMode = audioTelem.ldacQualityModeLive,
                        sbcDualChannelHd = audioTelem.sbcDualChannelHdLive,
                        aptxAdaptiveLowLatency = audioTelem.aptxAdaptiveLive,
                        leAudioLc3Enabled = audioTelem.leAudioLc3Live,
                        btHfpHdVoiceNrecBypass = audioTelem.btHfpHdVoiceNrecBypassLive,
                        avrcpVersion = audioTelem.avrcpVersionLive,
                        disableUsbAudioRouting = audioTelem.usbRoutingDisabledLive,
                        phhDisableAudioEffects = audioTelem.phhDisableAudioEffectsLive,
                        phhCafAudioPolicy = audioTelem.phhCafAudioPolicyLive,
                        phhDisableVolumeListener = audioTelem.phhDisableVolumeListenerLive,
                        phhDisableVoiceCallIn = audioTelem.phhDisableVoiceCallInLive,
                        phhAllowIncomingCallBinder = audioTelem.phhAllowIncomingCallBinderLive,
                        qcomFluenceBypass = audioTelem.qcomFluenceBypassLive,
                        vendorBesLoudnessEnabled = audioTelem.vendorBesLoudnessLive,
                        fineVolumeSteps30 = audioTelem.fineVolumeSteps30Live
                    ))
                )
            }
        } catch (_: Exception) {}
    }
}

fun LynxViewModel.setAudioDeepBufferBypass(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        val next = current.state.audio.copy(deepBufferBypass = enabled)
        current.copy(
            state = current.state.copy(
                audio = next.copy(
                    activePreset = deriveAudioPreset(
                        next.lowLatencyMmap,
                        next.deepBufferBypass,
                        next.directDacOffload,
                        next.btCrackleGuard
                    )
                )
            )
        )
    }
    setKey("audio.deep_buffer_bypass", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioDeepBufferBypass(enabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (enabled) "Deep Buffer Media Bypass diaktifkan (Real-Time Audio)" else "Deep Buffer Media dipulihkan ke standar"
                } else null,
                errorMessage = if (!ok) "Gagal menerapkan Deep Buffer Media Bypass pada sistem" else null
            )
        }
    }
}

fun LynxViewModel.setAudioDirectDacOffload(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        val next = current.state.audio.copy(directDacOffload = enabled)
        current.copy(
            state = current.state.copy(
                audio = next.copy(
                    activePreset = deriveAudioPreset(
                        next.lowLatencyMmap,
                        next.deepBufferBypass,
                        next.directDacOffload,
                        next.btCrackleGuard
                    )
                )
            )
        )
    }
    setKey("audio.direct_dac_offload", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioDirectDacOffload(enabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (enabled) "Direct DAC 32KB Offload Buffer diaktifkan" else "Direct DAC Offload dipulihkan ke 64KB standar"
                } else null,
                errorMessage = if (!ok) "Gagal menerapkan Direct DAC Offload Buffer" else null
            )
        }
    }
}

fun LynxViewModel.setAudioBtCrackleGuard(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        val next = current.state.audio.copy(btCrackleGuard = enabled)
        current.copy(
            state = current.state.copy(
                audio = next.copy(
                    activePreset = deriveAudioPreset(
                        next.lowLatencyMmap,
                        next.deepBufferBypass,
                        next.directDacOffload,
                        next.btCrackleGuard
                    )
                )
            )
        )
    }
    setKey("audio.bt_crackle_guard", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioBtCrackleGuard(enabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (enabled) "Bluetooth A2DP Crackle & Drift Guard diaktifkan" else "Bluetooth A2DP Drift Guard dinonaktifkan"
                } else null,
                errorMessage = if (!ok) "Gagal menerapkan tuning Bluetooth A2DP" else null
            )
        }
    }
}

fun LynxViewModel.applyAudioPreset(preset: String) {
    recordStateMutation()
    val (mmap, deep, dac, bt) = when (preset) {
        "oem_default" -> listOf(false, false, false, false)
        "gaming_mmap" -> listOf(true, true, false, true)
        "hires_dac" -> listOf(false, false, true, true)
        "full_studio" -> listOf(true, true, true, true)
        else -> return
    }
    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                audio = current.state.audio.copy(
                    lowLatencyMmap = mmap,
                    deepBufferBypass = deep,
                    directDacOffload = dac,
                    btCrackleGuard = bt,
                    activePreset = preset
                )
            )
        )
    }
    setKey("audio.low_latency_mmap", mmap.toString(), "bool")
    setKey("audio.deep_buffer_bypass", deep.toString(), "bool")
    setKey("audio.direct_dac_offload", dac.toString(), "bool")
    setKey("audio.bt_crackle_guard", bt.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok1 = LynxRepository.applyAudioMmap(mmap)
        val ok2 = LynxRepository.applyAudioDeepBufferBypass(deep)
        val ok3 = LynxRepository.applyAudioDirectDacOffload(dac)
        val ok4 = LynxRepository.applyAudioBtCrackleGuard(bt)
        val fresh = LynxRepository.readAudioTelemetry()
        val label = when (preset) {
            "oem_default" -> "OEM Default (Standar Pabrik)"
            "gaming_mmap" -> "Low-Latency Gaming (MMAP Direct)"
            "hires_dac" -> "Hi-Res & DAC Offload"
            "full_studio" -> "Ultra Studio & Gaming (All Active)"
            else -> preset
        }
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok1 && ok2 && ok3 && ok4) "Profil Audio '$label' diterapkan" else "Profil Audio '$label' diterapkan sebagian"
            )
        }
    }
}

fun LynxViewModel.setAudioA2dpHwOffloadDisabled(disabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(disableA2dpHwOffload = disabled)))
    }
    setKey("audio.disable_a2dp_hw_offload", disabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioA2dpHwOffloadDisabled(disabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (disabled) "Bluetooth A2DP Hardware Offload dinonaktifkan (Host Stack Aktif)"
                    else "Bluetooth A2DP Hardware Offload dikembalikan ke DSP Vendor"
                } else null,
                errorMessage = if (!ok) "Gagal mengubah status A2DP Hardware Offload" else null
            )
        }
    }
}

fun LynxViewModel.setAudioSystemBtAudioHal(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                audio = current.state.audio.copy(
                    systemBtAudioHalEnabled = enabled,
                    disableA2dpHwOffload = if (enabled) true else current.state.audio.disableA2dpHwOffload
                )
            )
        )
    }
    setKey("audio.system_bt_audio_hal", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioSystemBtAudioHal(enabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (enabled) "System Bluetooth Audio HAL (GSI A2DP Fix) diaktifkan & AudioServer dimuat ulang"
                    else "System Bluetooth Audio HAL dikembalikan ke Vendor HAL"
                } else null,
                errorMessage = if (!ok) "Gagal menerapkan System Bluetooth Audio HAL" else null
            )
        }
    }
}

fun LynxViewModel.setAudioDisableAbsoluteVolume(disabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(disableAbsoluteVolume = disabled)))
    }
    setKey("audio.disable_absolute_volume", disabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioDisableAbsoluteVolume(disabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (disabled) "Bluetooth Absolute Volume dinonaktifkan (Volume HP & TWS terpisah)"
                    else "Bluetooth Absolute Volume diaktifkan kembali"
                } else null,
                errorMessage = if (!ok) "Gagal mengubah pengaturan Bluetooth Absolute Volume" else null
            )
        }
    }
}

fun LynxViewModel.setAudioLdacQualityMode(mode: String) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(ldacQualityMode = mode)))
    }
    setKey("audio.ldac_quality_mode", mode, "str")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioLdacQualityMode(mode)
        val fresh = LynxRepository.readAudioTelemetry()
        val label = when (mode) {
            "hires_990" -> "Hi-Res 990 kbps (Audio Quality)"
            "balanced" -> "Balanced 660 kbps"
            else -> "Adaptive Bitrate (Best Effort)"
        }
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) "LDAC Playback Quality diatur ke $label" else null,
                errorMessage = if (!ok) "Gagal mengatur LDAC Playback Quality" else null
            )
        }
    }
}

fun LynxViewModel.setAudioSbcDualChannelHd(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(sbcDualChannelHd = enabled)))
    }
    setKey("audio.sbc_dual_channel_hd", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioSbcDualChannelHd(enabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (enabled) "SBC Dual-Channel HD (SBC XQ High Bitrate) diaktifkan"
                    else "SBC Dual-Channel HD dinonaktifkan"
                } else null,
                errorMessage = if (!ok) "Gagal mengubah mode SBC Dual-Channel HD" else null
            )
        }
    }
}

fun LynxViewModel.setAudioAvrcpVersion(version: String) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(avrcpVersion = version)))
    }
    setKey("audio.avrcp_version", version, "str")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioAvrcpVersion(version)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) "Versi protokol Bluetooth AVRCP diatur ke ${version.uppercase()}" else null,
                errorMessage = if (!ok) "Gagal mengubah versi Bluetooth AVRCP" else null
            )
        }
    }
}

fun LynxViewModel.setAudioDisableUsbRouting(disabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(disableUsbAudioRouting = disabled)))
    }
    setKey("audio.disable_usb_routing", disabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioDisableUsbRouting(disabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (disabled) "USB Audio Automatic Routing dinonaktifkan"
                    else "USB Audio Automatic Routing diaktifkan"
                } else null,
                errorMessage = if (!ok) "Gagal mengubah pengaturan USB Audio Routing" else null
            )
        }
    }
}

fun LynxViewModel.setAudioPhhDisableEffects(disabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(phhDisableAudioEffects = disabled)))
    }
    setKey("audio.phh_disable_audio_effects", disabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioPhhDisableEffects(disabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (disabled) "SoundFX & Audio Effects Offload dinonaktifkan (ro.audio.ignore_effects=true)"
                    else "SoundFX & Audio Effects dikembalikan ke standar"
                } else null,
                errorMessage = if (!ok) "Gagal mengubah status Audio Effects" else null
            )
        }
    }
}

fun LynxViewModel.setAudioPhhCafPolicy(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(phhCafAudioPolicy = enabled)))
    }
    setKey("audio.phh_caf_audio_policy", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioPhhCafPolicy(enabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (enabled) "Alternate CAF / Vendor Audio Policy diaktifkan"
                    else "Audio Policy dikembalikan ke konfigurasi default"
                } else null,
                errorMessage = if (!ok) "Gagal menerapkan Alternate CAF Audio Policy" else null
            )
        }
    }
}

fun LynxViewModel.setAudioPhhDisableVolumeListener(disabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(phhDisableVolumeListener = disabled)))
    }
    setKey("audio.phh_disable_volume_listener", disabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioPhhDisableVolumeListener(disabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (disabled) "SoundVolume Effect Listener (libvolumelistener.so) dinonaktifkan"
                    else "SoundVolume Effect Listener diaktifkan kembali"
                } else null,
                errorMessage = if (!ok) "Gagal mengubah status SoundVolume Effect Listener" else null
            )
        }
    }
}

fun LynxViewModel.setAudioPhhVoiceCallRouteFix(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(phhDisableVoiceCallIn = enabled)))
    }
    setKey("audio.phh_voice_call_route_fix", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioPhhVoiceCallRouteFix(enabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (enabled) "In-Call Voice & Fluence Routing Fix diaktifkan"
                    else "In-Call Voice Routing dikembalikan ke standar"
                } else null,
                errorMessage = if (!ok) "Gagal mengubah pengaturan In-Call Voice Routing" else null
            )
        }
    }
}

fun LynxViewModel.setAudioVendorBesLoudness(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(vendorBesLoudnessEnabled = enabled)))
    }
    setKey("audio.vendor_besloudness", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioVendorBesLoudness(enabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (enabled) "MediaTek BesLoudness Audio Enhancer diaktifkan"
                    else "MediaTek BesLoudness Audio Enhancer dinonaktifkan (Bypass)"
                } else null,
                errorMessage = if (!ok) "Gagal mengubah status MediaTek BesLoudness" else null
            )
        }
    }
}

fun LynxViewModel.setAudioHiresResampler24Bit(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(hiresResampler24Bit = enabled)))
    }
    setKey("audio.hires_resampler_24bit", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioHiresResampler24Bit(enabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (enabled) "Hi-Res 24-Bit PCM Offload & Sinc Resampler (Quality 7) diaktifkan"
                    else "AudioFlinger Resampler dikembalikan ke standar"
                } else null,
                errorMessage = if (!ok) "Gagal mengubah konfigurasi Hi-Res Resampler" else null
            )
        }
    }
}

fun LynxViewModel.setAudioCodecIdlePopGuard(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(codecIdlePopGuard = enabled)))
    }
    setKey("audio.codec_idle_pop_guard", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioCodecIdlePopGuard(enabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (enabled) "ASoC Codec Pop/Click Idle Guard (pmdown_time=0) diaktifkan"
                    else "ASoC Codec Idle Power-Down dikembalikan ke standar"
                } else null,
                errorMessage = if (!ok) "Gagal menerapkan ASoC Codec Pop/Click Guard" else null
            )
        }
    }
}

fun LynxViewModel.setAudioSpatializerBoost(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(spatializerBoost = enabled)))
    }
    setKey("audio.spatializer_boost", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioSpatializerBoost(enabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (enabled) "Spatial Audio & Multi-Channel Surround Engine diaktifkan"
                    else "Spatial Audio Engine dinonaktifkan"
                } else null,
                errorMessage = if (!ok) "Gagal mengubah pengaturan Spatializer Engine" else null
            )
        }
    }
}

fun LynxViewModel.setAudioAptxAdaptiveLowLatency(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(aptxAdaptiveLowLatency = enabled)))
    }
    setKey("audio.aptx_adaptive_ll", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioAptxAdaptiveLowLatency(enabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (enabled) "Qualcomm aptX Adaptive R2.2 & TWS+ Low-Latency Mode diaktifkan"
                    else "Qualcomm aptX Adaptive R2.2 & TWS+ dinonaktifkan"
                } else null,
                errorMessage = if (!ok) "Gagal mengubah pengaturan aptX Adaptive" else null
            )
        }
    }
}

fun LynxViewModel.setAudioLeAudioLc3(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(leAudioLc3Enabled = enabled)))
    }
    setKey("audio.le_audio_lc3", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioLeAudioLc3(enabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (enabled) "Bluetooth LE Audio (LC3 Unicast BAP) diaktifkan"
                    else "Bluetooth LE Audio (LC3 Unicast BAP) dinonaktifkan"
                } else null,
                errorMessage = if (!ok) "Gagal mengubah profil Bluetooth LE Audio" else null
            )
        }
    }
}

fun LynxViewModel.setAudioBtHfpHdVoiceNrecBypass(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(btHfpHdVoiceNrecBypass = enabled)))
    }
    setKey("audio.bt_hfp_hd_nrec_bypass", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioBtHfpHdVoiceNrecBypass(enabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (enabled) "Bluetooth HFP SWB HD Voice & NREC Bypass diaktifkan"
                    else "Bluetooth HFP Voice & NREC dikembalikan ke standar"
                } else null,
                errorMessage = if (!ok) "Gagal menerapkan tuning Bluetooth HFP Voice" else null
            )
        }
    }
}

fun LynxViewModel.setAudioPhhIncomingCallBinder(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(phhAllowIncomingCallBinder = enabled)))
    }
    setKey("audio.phh_incoming_call_binder", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioPhhIncomingCallBinder(enabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (enabled) "Incoming Call Binder Thread Fix (GSI Telephony Audio) diaktifkan"
                    else "Incoming Call Binder Thread dikembalikan ke standar"
                } else null,
                errorMessage = if (!ok) "Gagal menerapkan Incoming Call Binder Fix" else null
            )
        }
    }
}

fun LynxViewModel.setAudioQcomFluenceBypass(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(qcomFluenceBypass = enabled)))
    }
    setKey("audio.qcom_fluence_bypass", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioQcomFluenceBypass(enabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (enabled) "Qualcomm Fluence Dual-Mic Noise Suppression Bypass diaktifkan"
                    else "Qualcomm Fluence Dual-Mic diaktifkan kembali"
                } else null,
                errorMessage = if (!ok) "Gagal mengubah pengaturan Qualcomm Fluence" else null
            )
        }
    }
}

fun LynxViewModel.setAudioFineVolumeSteps30(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(fineVolumeSteps30 = enabled)))
    }
    setKey("audio.fine_volume_steps_30", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioFineVolumeSteps30(enabled)
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) {
                    if (enabled) "Fine Media Volume Steps (30-Step Precision) diaktifkan"
                    else "Media Volume Steps dikembalikan ke 15-Step standar"
                } else null,
                errorMessage = if (!ok) "Gagal mengubah jumlah langkah volume media" else null
            )
        }
    }
}

fun LynxViewModel.setForceShowGsiControls(force: Boolean) {
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(forceShowGsiControls = force)))
    }
}

fun LynxViewModel.reloadAudioServerAndHal() {
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.reloadAudioServerAndHal()
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update {
            it.copy(
                audioTelemetry = fresh,
                successMessage = if (ok) "AudioServer & Vendor Audio HAL berhasil dimuat ulang tanpa reboot" else null,
                errorMessage = if (!ok) "Gagal memuat ulang layanan AudioServer" else null
            )
        }
    }
}

fun LynxViewModel.restoreAudioOemBaseline() {
    recordStateMutation()
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.restoreAudioOemBaseline()
        val fresh = LynxRepository.readAudioTelemetry()
        _uiState.update { current ->
            current.copy(
                audioTelemetry = fresh,
                state = current.state.copy(
                    audio = current.state.audio.copy(
                        lowLatencyMmap = fresh.isMmapForced,
                        deepBufferBypass = fresh.isDeepBufferBypassed,
                        directDacOffload = fresh.isDirectDacOffload,
                        btCrackleGuard = fresh.isBtCrackleGuardActive,
                        activePreset = "oem_default",
                        disableA2dpHwOffload = fresh.a2dpOffloadDisabledLive,
                        systemBtAudioHalEnabled = fresh.systemBtAudioHalLive,
                        disableAbsoluteVolume = fresh.absoluteVolumeDisabledLive,
                        ldacQualityMode = fresh.ldacQualityModeLive,
                        sbcDualChannelHd = fresh.sbcDualChannelHdLive,
                        avrcpVersion = fresh.avrcpVersionLive,
                        disableUsbAudioRouting = fresh.usbRoutingDisabledLive,
                        phhDisableAudioEffects = fresh.phhDisableAudioEffectsLive,
                        phhCafAudioPolicy = fresh.phhCafAudioPolicyLive,
                        phhDisableVolumeListener = fresh.phhDisableVolumeListenerLive,
                        phhDisableVoiceCallIn = fresh.phhDisableVoiceCallInLive,
                        vendorBesLoudnessEnabled = fresh.vendorBesLoudnessLive
                    )
                ),
                successMessage = if (ok) "Baseline Audio OEM berhasil dipulihkan" else null,
                errorMessage = if (!ok) "Gagal memulihkan baseline Audio OEM" else null
            )
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
            val freshEngine = LynxRepository.readNetworkEngineInfo()
            val effective = freshEngine.currentAlgo.ifBlank { LynxRepository.readCurrentTcpCongestion() }.ifBlank { algorithm }
            _uiState.update { it.copy(
                networkEngineInfo = if (freshEngine.isLoaded) freshEngine else it.networkEngineInfo,
                currentTcpCongestion = effective,
                state = it.state.copy(network = it.state.network.copy(
                    tcpCongestion = effective,
                    qdisc = freshEngine.currentQdisc.ifBlank { it.state.network.qdisc }
                )),
                successMessage = "TCP '$effective' diterapkan" +
                    if (freshEngine.currentQdisc.isNotBlank()) " (qdisc ${freshEngine.currentQdisc})" else ""
            ) }
            LynxRepository.writeStateKey("network.tcp_congestion", effective, "str")
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

