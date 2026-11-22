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
    val isPerformance = mode == "performance"
    val isCustom = mode == "custom"

    val engineMode = when {
        isDominant -> ThermalEngineMode.HARDWARE_SAFETY_DOMINANT
        isDefault -> ThermalEngineMode.DEFAULT_OEM
        isPerformance -> ThermalEngineMode.PERFORMANCE
        isCustom -> ThermalEngineMode.CUSTOM
        else -> ThermalEngineMode.THERMAL_STABLE
    }

    val presetTempLimit = when {
        isDominant -> 75
        isPerformance -> 58
        isDefault -> 45
        isCustom -> _uiState.value.state.thermal.customTempLimitC
        else -> 52
    }

    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
    val timeStr = sdf.format(java.util.Date())

    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                thermal = current.state.thermal.copy(
                    mode = mode,
                    customTempLimitC = presetTempLimit,
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
    setKey("thermal.custom_temp_limit_c", presetTempLimit.toString(), "val")
    setKey("thermal.full_bypass", isDominant.toString(), "bool")

    viewModelScope.launch(Dispatchers.IO) {
        val ok = LynxRepository.applyThermalMode(mode, presetTempLimit)
        com.noir.lynx.hardware.VendorThermalSyncHelper.syncCustomTempLimitToVendorConfig(presetTempLimit)
        if (ok) {
            com.noir.lynx.safety.LynxPersistenceManager.saveBootConfiguration(
                mode = mode,
                tempLimitC = presetTempLimit,
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
                ThermalEngineMode.CUSTOM -> "Mode Kustom (${presetTempLimit}°C) diaktifkan"
            }
            _uiState.update { it.copy(successMessage = label) }
        }
    }
}

fun LynxViewModel.setCustomTempLimit(temp: Int) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.THERMAL)
    val newMode = when (temp) {
        45 -> "default_oem"
        52 -> "stable"
        58 -> "performance"
        75 -> "hardware_safety_dominant"
        else -> "custom"
    }
    val engineMode = when (newMode) {
        "default_oem" -> ThermalEngineMode.DEFAULT_OEM
        "stable" -> ThermalEngineMode.THERMAL_STABLE
        "performance" -> ThermalEngineMode.PERFORMANCE
        "hardware_safety_dominant" -> ThermalEngineMode.HARDWARE_SAFETY_DOMINANT
        else -> ThermalEngineMode.CUSTOM
    }
    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                thermal = current.state.thermal.copy(
                    customTempLimitC = temp,
                    mode = newMode
                )
            ),
            thermalStatusDetails = current.thermalStatusDetails.copy(
                activeMode = engineMode
            )
        )
    }
    setKey("thermal.custom_temp_limit_c", temp.toString(), "val")
    setKey("thermal.mode", newMode, "str")
    viewModelScope.launch(Dispatchers.IO) {
        LynxRepository.applyCustomTempLimitLive(temp)
        com.noir.lynx.hardware.VendorThermalSyncHelper.syncCustomTempLimitToVendorConfig(temp)
    }
}

fun LynxViewModel.setThermalAntiDimming(enabled: Boolean) {
    recordStateMutation(com.noir.lynx.profiles.ProfileDomain.THERMAL)
    _uiState.update { current ->
        current.copy(
            state = current.state.copy(
                thermal = current.state.thermal.copy(antiDimming = enabled)
            )
        )
    }
    setKey("thermal.anti_dimming", enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val res = com.noir.lynx.hardware.VendorThermalSyncHelper.setAntiDimming(enabled)
        if (res.isSuccess) {
            _uiState.update {
                it.copy(successMessage = if (enabled) "Anti-Dimming Termal diaktifkan" else "Anti-Dimming Termal dinonaktifkan")
            }
        }
    }
}

private fun LynxViewModel.syncStateFromEngine(info: NetworkEngineInfo) {
    if (!info.isLoaded) return
    _uiState.update { current ->
        val updatedNet = current.state.network.copy(
            tcpCongestion = info.currentAlgo.ifBlank { current.state.network.tcpCongestion },
            qdisc = info.currentQdisc.ifBlank { current.state.network.qdisc },
            activePreset = info.activePreset.ifBlank { current.state.network.activePreset },
            wifiPingStabilizer = info.lowLatencyActive,
            antiJitterScan = info.scanGuardActive,
            fqCodelEnabled = info.qdiscGuardActive,
            tcpBuffers16m = info.tcpBuffersScaled,
            tcpFastOpen = info.tcpFastopenActive,
            mtkHardwareAggregation = if (info.supportsHwRadio) info.hwRadioActive else false,
            ipv6Disabled = info.ipv6Disabled,
            cellularHotStandby = info.mobileStandbyActive
        )
        current.copy(
            networkEngineInfo = info,
            availableTcpAlgorithms = info.availableAlgos.ifEmpty { current.availableTcpAlgorithms },
            currentTcpCongestion = info.currentAlgo.ifBlank { current.currentTcpCongestion },
            state = current.state.copy(network = updatedNet)
        )
    }
}

private fun LynxViewModel.launchNetworkFeatureUpdate(
    featureKey: String,
    stateKey: String,
    enabled: Boolean,
    optimisticUpdate: (NetworkConfig) -> NetworkConfig
) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(network = optimisticUpdate(current.state.network)))
    }
    setKey(stateKey, enabled.toString(), "bool")
    viewModelScope.launch(Dispatchers.IO) {
        val result = LynxRepository.applyNetworkFeature(featureKey, enabled)
        val freshInfo = LynxRepository.readNetworkEngineInfo()
        syncStateFromEngine(freshInfo)
        _uiState.update {
            if (result.ok) {
                it.copy(successMessage = "${result.message} [${result.status}]")
            } else {
                it.copy(errorMessage = result.message.ifBlank { "Fitur tidak didukung pada kernel/ROM ini." })
            }
        }
    }
}

fun LynxViewModel.setWifiPingStabilizer(enabled: Boolean) {
    launchNetworkFeatureUpdate("low_latency", "network.wifi_ping_stabilizer", enabled) {
        it.copy(wifiPingStabilizer = enabled)
    }
}

fun LynxViewModel.setAntiJitterScan(enabled: Boolean) {
    launchNetworkFeatureUpdate("scan_guard", "network.anti_jitter_scan", enabled) {
        it.copy(antiJitterScan = enabled)
    }
}

fun LynxViewModel.setFqCodelEnabled(enabled: Boolean) {
    launchNetworkFeatureUpdate("qdisc_rps", "network.fq_codel_enabled", enabled) {
        it.copy(fqCodelEnabled = enabled)
    }
}

fun LynxViewModel.setTcpBuffers16m(enabled: Boolean) {
    launchNetworkFeatureUpdate("tcp_buffers", "network.tcp_buffers_16m", enabled) {
        it.copy(tcpBuffers16m = enabled)
    }
}

fun LynxViewModel.setTcpFastOpen(enabled: Boolean) {
    launchNetworkFeatureUpdate("tcp_fastopen", "network.tcp_fast_open", enabled) {
        it.copy(tcpFastOpen = enabled)
    }
}

fun LynxViewModel.setMtkHardwareAggregation(enabled: Boolean) {
    launchNetworkFeatureUpdate("hw_radio", "network.mtk_hardware_aggregation", enabled) {
        it.copy(mtkHardwareAggregation = enabled)
    }
}

fun LynxViewModel.setIpv6Disabled(enabled: Boolean) {
    launchNetworkFeatureUpdate("ipv6_disable", "network.ipv6_disabled", enabled) {
        it.copy(ipv6Disabled = enabled)
    }
}

fun LynxViewModel.setCellularHotStandby(enabled: Boolean) {
    launchNetworkFeatureUpdate("mobile_standby", "network.cellular_hot_standby", enabled) {
        it.copy(cellularHotStandby = enabled)
    }
}

fun LynxViewModel.applyNetworkPreset(preset: String) {
    recordStateMutation()
    _uiState.update { current ->
        current.copy(state = current.state.copy(network = current.state.network.copy(activePreset = preset)))
    }
    setKey("network.active_preset", preset, "str")
    viewModelScope.launch(Dispatchers.IO) {
        val result = LynxRepository.applyNetworkPreset(preset)
        val freshInfo = LynxRepository.readNetworkEngineInfo()
        val freshTele = LynxRepository.readWifiTelemetry(includePing = false)
        syncStateFromEngine(freshInfo)
        _uiState.update {
            it.copy(
                wifiTelemetry = freshTele,
                successMessage = if (result.ok) "${result.message} [${result.status}]" else null,
                errorMessage = if (!result.ok) result.message else null
            )
        }
    }
}

fun LynxViewModel.setNetworkQdisc(qdisc: String) {
    recordStateMutation()
    setKey("network.qdisc", qdisc, "str")
    viewModelScope.launch(Dispatchers.IO) {
        val result = LynxRepository.setNetworkQdisc(qdisc)
        val freshInfo = LynxRepository.readNetworkEngineInfo()
        syncStateFromEngine(freshInfo)
        _uiState.update {
            if (result.ok) {
                it.copy(successMessage = "Qdisc '$qdisc' diterapkan [${result.status}]")
            } else {
                it.copy(errorMessage = result.message.ifBlank { "Kernel menolak qdisc '$qdisc'" })
            }
        }
    }
}

fun LynxViewModel.refreshNetworkEngineAll(runPing: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
        val engine = LynxRepository.readNetworkEngineInfo()
        val tele = LynxRepository.readWifiTelemetry(includePing = runPing)
        val dns = LynxRepository.readPrivateDns()
        syncStateFromEngine(engine)
        _uiState.update {
            it.copy(
                wifiTelemetry = tele,
                privateDnsMode = dns.first,
                privateDnsSpecifier = dns.second,
                state = it.state.copy(
                    network = it.state.network.copy(
                        privateDnsMode = dns.first,
                        privateDnsSpecifier = dns.second
                    )
                )
            )
        }
    }
}

fun LynxViewModel.probeNetworkLatency() {
    if (_uiState.value.isProbingNetwork) return
    _uiState.update { it.copy(isProbingNetwork = true) }
    viewModelScope.launch(Dispatchers.IO) {
        val tele = LynxRepository.readWifiTelemetry(includePing = true)
        _uiState.update {
            it.copy(
                wifiTelemetry = tele,
                isProbingNetwork = false,
                successMessage = if (tele.probeReachable) {
                    "Ping ${tele.gatewayIp.ifBlank { "1.1.1.1" }}: ${"%.1f".format(java.util.Locale.US, tele.gatewayPingMs)} ms (jitter ${"%.1f".format(java.util.Locale.US, tele.gatewayJitterMs)} ms)"
                } else {
                    "Host target tidak merespons ICMP saat uji latensi"
                }
            )
        }
    }
}

fun LynxViewModel.benchmarkDnsLatency(
    hosts: List<String>,
    autoSelectFastest: Boolean = false
) {
    if (_uiState.value.isTestingDns) return
    _uiState.update { it.copy(isTestingDns = true) }
    viewModelScope.launch(Dispatchers.IO) {
        val map = LynxRepository.benchmarkDnsLatency(hosts)
        val mergedMap = _uiState.value.dnsLatencyMs + map
        val fastest = map.entries
            .filter { it.value > 0f }
            .minByOrNull { it.value }
        _uiState.update {
            it.copy(
                dnsLatencyMs = mergedMap,
                isTestingDns = false,
                successMessage = if (!autoSelectFastest || fastest == null) {
                    "Benchmark TLS/853 DNS selesai (${map.count { entry -> entry.value > 0f }}/${hosts.size} merespons)"
                } else {
                    "DNS tercepat: ${fastest.key} (${"%.0f".format(java.util.Locale.US, fastest.value)} ms)"
                }
            )
        }
        if (autoSelectFastest && fastest != null) {
            setPrivateDns("hostname", fastest.key)
        }
    }
}

fun LynxViewModel.openSpeedtestSheet() {
    _uiState.update { current ->
        val eng = current.networkEngineInfo
        val tele = current.wifiTelemetry
        val iface = tele.activeIface.ifBlank { eng.activeIface }
        val transport = if (tele.isConnected) "Wi-Fi" else eng.activeTransport.uppercase().ifBlank { "NET" }
        current.copy(
            speedtestState = current.speedtestState.copy(
                isSheetOpen = true,
                activeIfaceLabel = listOf(transport, iface).filter { it.isNotBlank() }.joinToString(" • "),
                tcpAlgoSnapshot = eng.currentAlgo.ifBlank { current.currentTcpCongestion }.uppercase(),
                qdiscSnapshot = eng.currentQdisc.ifBlank { current.state.network.qdisc }
            )
        )
    }
}

fun LynxViewModel.closeSpeedtestSheet() {
    _uiState.update { current ->
        current.copy(speedtestState = current.speedtestState.copy(isSheetOpen = false))
    }
}

fun LynxViewModel.selectSpeedtestServer(serverId: String) {
    if (_uiState.value.speedtestState.isRunning) return
    _uiState.update { current ->
        current.copy(
            speedtestState = current.speedtestState.copy(
                selectedServerId = serverId,
                errorMessage = null
            )
        )
    }
}

fun LynxViewModel.setSpeedtestCustomUrl(url: String) {
    _uiState.update { current ->
        current.copy(
            speedtestState = current.speedtestState.copy(
                customBaseUrl = url,
                errorMessage = null
            )
        )
    }
}

fun LynxViewModel.saveSpeedtestBaseline() {
    val st = _uiState.value.speedtestState
    if (!st.hasResult || st.isRunning) return
    val eng = _uiState.value.networkEngineInfo
    val snap = com.noir.lynx.feature.network.model.SpeedtestSnapshot(
        serverLabel = st.serverEdgeLabel.ifBlank { st.selectedServerId },
        idleLatencyMs = st.idleLatencyMs,
        idleJitterMs = st.idleJitterMs,
        downloadMbps = st.downloadMbps,
        downloadLoadedLatencyMs = st.downloadLoadedLatencyMs,
        uploadMbps = st.uploadMbps,
        uploadLoadedLatencyMs = st.uploadLoadedLatencyMs,
        bufferbloatGrade = st.bufferbloatGrade,
        tcpAlgo = st.tcpAlgoSnapshot.ifBlank { eng.currentAlgo.uppercase() },
        qdisc = st.qdiscSnapshot.ifBlank { eng.currentQdisc },
        timestampMs = st.lastTestedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
    )
    _uiState.update {
        it.copy(
            speedtestState = it.speedtestState.copy(baselineResult = snap),
            successMessage = "Baseline Speedtest disimpan untuk perbandingan tuning"
        )
    }
}

fun LynxViewModel.clearSpeedtestBaseline() {
    _uiState.update {
        it.copy(speedtestState = it.speedtestState.copy(baselineResult = null))
    }
}

fun LynxViewModel.cancelNetworkSpeedtest() {
    speedtestJob?.cancel()
    speedtestJob = null
    _uiState.update { current ->
        val prev = current.speedtestState
        current.copy(
            speedtestState = prev.copy(
                phase = com.noir.lynx.feature.network.model.SpeedtestPhase.IDLE,
                progress = 0f,
                statusText = "Pengujian dibatalkan",
                errorMessage = null
            )
        )
    }
}

fun LynxViewModel.startNetworkSpeedtest() {
    if (_uiState.value.speedtestState.isRunning) return
    speedtestJob?.cancel()
    val eng = _uiState.value.networkEngineInfo
    val tele = _uiState.value.wifiTelemetry
    val iface = tele.activeIface.ifBlank { eng.activeIface }
    val transport = if (tele.isConnected) "Wi-Fi" else eng.activeTransport.uppercase().ifBlank { "NET" }
    val seedState = _uiState.value.speedtestState.copy(
        isSheetOpen = true,
        activeIfaceLabel = listOf(transport, iface).filter { it.isNotBlank() }.joinToString(" • "),
        tcpAlgoSnapshot = eng.currentAlgo.ifBlank { _uiState.value.currentTcpCongestion }.uppercase(),
        qdiscSnapshot = eng.currentQdisc.ifBlank { _uiState.value.state.network.qdisc }
    )

    speedtestJob = viewModelScope.launch(Dispatchers.IO) {
        try {
            com.noir.lynx.feature.network.engine.LynxSpeedtestEngine.runSpeedtest(seedState) { updated ->
                _uiState.update { current ->
                    current.copy(
                        speedtestState = updated.copy(
                            isSheetOpen = current.speedtestState.isSheetOpen,
                            baselineResult = current.speedtestState.baselineResult
                        )
                    )
                }
            }
            val done = _uiState.value.speedtestState
            if (done.phase == com.noir.lynx.feature.network.model.SpeedtestPhase.COMPLETED) {
                _uiState.update {
                    it.copy(
                        successMessage = "Speedtest selesai: ↓ ${"%.1f".format(java.util.Locale.US, done.downloadMbps)} / ↑ ${"%.1f".format(java.util.Locale.US, done.uploadMbps)} Mbps • Ping ${"%.0f".format(java.util.Locale.US, done.idleLatencyMs)} ms"
                    )
                }
            } else if (done.phase == com.noir.lynx.feature.network.model.SpeedtestPhase.ERROR && !done.errorMessage.isNullOrBlank()) {
                _uiState.update { it.copy(errorMessage = done.errorMessage) }
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Cancelled cleanly by user
        } catch (t: Throwable) {
            _uiState.update { current ->
                current.copy(
                    speedtestState = current.speedtestState.copy(
                        phase = com.noir.lynx.feature.network.model.SpeedtestPhase.ERROR,
                        progress = 0f,
                        statusText = "Pengujian gagal",
                        errorMessage = t.message ?: "Terjadi kesalahan jaringan saat uji speedtest"
                    ),
                    errorMessage = t.message ?: "Gagal menjalankan speedtest"
                )
            }
        }
    }
}

fun LynxViewModel.refreshWifiTelemetry() {
    viewModelScope.launch(Dispatchers.IO) {
        val tele = LynxRepository.readWifiTelemetry(includePing = true)
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

