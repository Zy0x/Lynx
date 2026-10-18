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

fun LynxViewModel.setUclampGameMin(ratio: Int) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    _uiState.update { current ->
        current.copy(state = current.state.copy(uclamp = current.state.uclamp.copy(gameMinRatio = ratio)))
    }
    setKey("uclamp.game_min_ratio", ratio.toString(), "val")
}

fun LynxViewModel.setThermalMode(mode: String) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.THERMAL)
    val isDominant = mode == "hardware_safety_dominant" || mode == "unrestricted"
    val isDefault = mode == "default" || mode == "default_oem"
    val engineMode = when {
        isDominant -> ThermalEngineMode.HARDWARE_SAFETY_DOMINANT
        isDefault -> ThermalEngineMode.DEFAULT_OEM
        mode == "performance" -> ThermalEngineMode.PERFORMANCE
        else -> ThermalEngineMode.THERMAL_STABLE
    }

    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
    val timeStr = sdf.format(java.util.Date())

    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                thermal = current.state.thermal.copy(
                    mode = mode,
                    fullBypass = isDominant,
                    hardwareSafetyDominant = isDominant
                )
            ),
            thermalStatusDetails = current.thermalStatusDetails.copy(
                activeMode = engineMode,
                softwareThrottlingActive = isDefault,
                hardwareSafetyActive = true,
                batteryGuardActive = true,
                lastAppliedTimestamp = timeStr
            )
        )
    }

    setKey("thermal.mode", mode, "str")
    setKey("thermal.full_bypass", isDominant.toString(), "bool")

    viewModelScope.launch(Dispatchers.IO) {
        val limit = _uiState.value.state.thermal.customTempLimitC
        val ok = LynxRepository.applyThermalMode(mode, limit)
        if (ok) {
            com.noir.lynx.safety.LynxPersistenceManager.saveBootConfiguration(
                mode = mode,
                tempLimitC = limit,
                cpuFloorRatio = _uiState.value.state.overclock.cpuFloorRatio
            )
            try {
                com.noir.lynx.service.LynxThermalGuardService.start(com.noir.lynx.LynxApp.instance)
            } catch (_: Exception) {}

            val label = when (engineMode) {
                ThermalEngineMode.HARDWARE_SAFETY_DOMINANT -> "Mode Hardware Safety Dominant diaktifkan"
                ThermalEngineMode.THERMAL_STABLE -> "Mode Thermal Stabil diaktifkan (Sustained FPS)"
                ThermalEngineMode.PERFORMANCE -> "Mode Performa Termal diaktifkan"
                ThermalEngineMode.DEFAULT_OEM -> "Baseline Pabrik OEM dipulihkan"
            }
            _uiState.update { it.copy(successMessage = label) }
        }
    }
}

fun LynxViewModel.setCustomTempLimit(temp: Int) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.THERMAL)
    _uiState.update { current ->
        current.copy(state = current.state.copy(thermal = current.state.thermal.copy(customTempLimitC = temp)))
    }
    setKey("thermal.custom_temp_limit_c", temp.toString(), "val")
    viewModelScope.launch(Dispatchers.IO) {
        LynxRepository.applyCustomTempLimitLive(temp)
    }
}

fun LynxViewModel.setWifiPingStabilizer(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(network = current.state.network.copy(wifiPingStabilizer = enabled)))
    }
    setKey("network.wifi_ping_stabilizer", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyWifiPingStabilizer(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = if (enabled) "Wi-Fi Ping Stabilizer diaktifkan" else "Wi-Fi Ping Stabilizer dinonaktifkan") }
        }
    }
}

fun LynxViewModel.setAntiJitterScan(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(network = current.state.network.copy(antiJitterScan = enabled)))
    }
    setKey("network.anti_jitter_scan", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAntiJitterScan(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = if (enabled) "Anti-Lag Wi-Fi Scan Guard aktif" else "Anti-Lag Scan Guard dinonaktifkan") }
        }
    }
}

fun LynxViewModel.setFqCodelEnabled(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(network = current.state.network.copy(fqCodelEnabled = enabled)))
    }
    setKey("network.fq_codel_enabled", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyFqCodel(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = if (enabled) "FQ-CoDel Bufferbloat Killer aktif" else "FQ-CoDel dinonaktifkan (Default FIFO)") }
        }
    }
}

fun LynxViewModel.setTcpBuffers16m(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(network = current.state.network.copy(tcpBuffers16m = enabled)))
    }
    setKey("network.tcp_buffers_16m", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyTcpBuffers16m(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = if (enabled) "TCP High-Bandwidth 16MB Buffers aktif" else "TCP Buffers dikembalikan ke bawaan") }
        }
    }
}

fun LynxViewModel.setTcpFastOpen(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(network = current.state.network.copy(tcpFastOpen = enabled)))
    }
    setKey("network.tcp_fast_open", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyTcpFastOpen(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = if (enabled) "TCP Fast Open & Anti-Corking aktif" else "TCP Fast Open dinonaktifkan") }
        }
    }
}

fun LynxViewModel.setMtkHardwareAggregation(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(network = current.state.network.copy(mtkHardwareAggregation = enabled)))
    }
    setKey("network.mtk_hw_aggregation", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyMtkHardwareAggregation(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = if (enabled) "MediaTek Hardware Aggregation aktif" else "MediaTek Aggregation dinonaktifkan") }
        }
    }
}

fun LynxViewModel.refreshWifiTelemetry() {
    viewModelScope.launch(Dispatchers.IO) {
        val tele = LynxRepository.readWifiTelemetry()
        _uiState.update { it.copy(wifiTelemetry = tele) }
    }
}

fun LynxViewModel.refreshAdbConfig() {
    viewModelScope.launch(Dispatchers.IO) {
        val (enabled, port) = LynxRepository.readAdbKeepAliveConfig()
        val status = LynxRepository.getAdbStatus()
        val isListening = status.contains("\"is_listening\":true")
        _uiState.update {
            it.copy(
                adbKeepAliveEnabled = enabled,
                isAdbListening = isListening,
                adbPort = port,
                adbPortInput = port.toString(),
                adbStatus = status
            )
        }
        if (enabled && !isListening) {
            withContext(Dispatchers.IO) {
                LynxRepository.setAdbKeepAlive(true, port)
            }
            val healedStatus = LynxRepository.getAdbStatus()
            _uiState.update {
                it.copy(
                    isAdbListening = healedStatus.contains("\"is_listening\":true"),
                    adbStatus = healedStatus
                )
            }
        }
    }
}

fun LynxViewModel.setAdbPortInput(input: String) {
    val filtered = input.filter { it.isDigit() }.take(5)
    _uiState.update { it.copy(adbPortInput = filtered) }
}

fun LynxViewModel.setAdbKeepAlive(enabled: Boolean) {
    recordStateMutation()
    val currentPort = _uiState.value.adbPortInput.toIntOrNull()?.coerceIn(1024, 65535) ?: _uiState.value.adbPort
    _uiState.update {
        it.copy(
            adbKeepAliveEnabled = enabled,
            isAdbListening = if (enabled) it.isAdbListening else false,
            adbPort = currentPort,
            adbPortInput = currentPort.toString()
        )
    }
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.setAdbKeepAlive(enabled, currentPort)
        val status = LynxRepository.getAdbStatus()
        val isListening = status.contains("\"is_listening\":true")
        _uiState.update {
            it.copy(
                isAdbListening = isListening,
                adbStatus = status,
                successMessage = if (ok) {
                    if (enabled) "ADB Wireless Keep-Alive diaktifkan & dikunci pada port $currentPort"
                    else "ADB Wireless Keep-Alive dinonaktifkan"
                } else null,
                errorMessage = if (!ok) "Gagal menerapkan konfigurasi ADB Keep-Alive" else null
            )
        }
    }
}

fun LynxViewModel.applyAdbCustomPort(portStr: String) {
    val parsed = portStr.trim().toIntOrNull()
    if (parsed == null || parsed !in 1024..65535) {
        _uiState.update { it.copy(errorMessage = "Port tidak valid! Masukkan angka antara 1024 hingga 65535 (contoh: 5555)") }
        return
    }
    recordStateMutation()
    _uiState.update {
        it.copy(
            adbPort = parsed,
            adbPortInput = parsed.toString()
        )
    }
    viewModelScope.launch(Dispatchers.IO) {
        val isEnabled = _uiState.value.adbKeepAliveEnabled
        val ok = if (isEnabled) {
            LynxRepository.setAdbKeepAlive(true, parsed)
        } else {
            LynxRepository.setAdbPort(parsed)
        }
        val status = LynxRepository.getAdbStatus()
        val isListening = status.contains("\"is_listening\":true")
        _uiState.update {
            it.copy(
                isAdbListening = isListening,
                adbStatus = status,
                successMessage = if (ok) "Port ADB berhasil dikunci ke $parsed" else null,
                errorMessage = if (!ok) "Gagal menyimpan port ADB $parsed" else null
            )
        }
    }
}

fun LynxViewModel.setTouchboost(enabled: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.CPU)
    _uiState.update { current ->
        current.copy(state = current.state.copy(displayTouch = current.state.displayTouch.copy(touchboost = enabled)))
    }
    setKey("display_touch.touchboost", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyTouchboost(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = if (enabled) "TouchBoost diaktifkan" else "TouchBoost dinonaktifkan") }
        }
    }
}

fun LynxViewModel.setAudioMmap(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(audio = current.state.audio.copy(lowLatencyMmap = enabled)))
    }
    setKey("audio.low_latency_mmap", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyAudioMmap(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = if (enabled) "Audio MMAP Low-Latency diaktifkan" else "Audio MMAP Low-Latency dinonaktifkan") }
        }
    }
}

fun LynxViewModel.setJoyoseNeutralize(enabled: Boolean) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(oemNeutralizer = current.state.oemNeutralizer.copy(joyoseNeutralize = enabled)))
    }
    setKey("oem_neutralizer.joyose_neutralize", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyJoyoseNeutralizer(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = if (enabled) "OEM Neutralizer diaktifkan" else "OEM Neutralizer dinonaktifkan") }
        }
    }
}

fun LynxViewModel.setThermalBypass(enabled: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.THERMAL)
    _uiState.update { current ->
        current.copy(state = current.state.copy(thermal = current.state.thermal.copy(fullBypass = enabled)))
    }
    setKey("thermal.full_bypass", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyThermalBypass(enabled)
        if (ok) {
            _uiState.update { it.copy(successMessage = if (enabled) "Thermal Bypass diaktifkan (Unrestricted)" else "Thermal Bypass dinonaktifkan") }
        }
    }
}

fun LynxViewModel.setMaxBatteryPercent(percent: Int) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(charging = current.state.charging.copy(maxBatteryPercent = percent)))
    }
    setKey("charging.max_battery_percent", percent.toString(), "val")
    viewModelScope.launch(Dispatchers.IO) {
        val applied = LynxRepository.setMaxBatteryPercent(percent)
        _uiState.update {
            it.copy(
                successMessage = if (applied) {
                    "Batas Pengisian Cerdas ${percent}% diterapkan."
                } else {
                    "Batas ${percent}% tersimpan, tetapi kernel tidak mengonfirmasi kontrol pengisian."
                }
            )
        }
    }
}

