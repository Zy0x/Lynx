package com.noir.lynx.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.noir.lynx.LynxApp
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/** Low-level Android system toggles and wireless ADB controls. */
object LynxSystemControlRepository {
    suspend fun readSelinuxMode(): String = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("getenforce 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "Enforcing"
        } catch (e: Exception) { "Enforcing" }
    }

    suspend fun setSelinuxMode(enforcing: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val code = if (enforcing) "1" else "0"
            Shell.cmd("setenforce $code 2>/dev/null; echo ok")
                .exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    suspend fun readPrintkSilent(): Boolean = withContext(Dispatchers.IO) {
        try {
            val out = Shell.cmd("cat /proc/sys/kernel/printk 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            out.startsWith("0")
        } catch (e: Exception) { true }
    }

    suspend fun setPrintkSilent(silent: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (silent) "0 0 0 0" else "7 4 1 7"
            val cmd = "echo '$v' > /proc/sys/kernel/printk 2>/dev/null && [ \"$(cat /proc/sys/kernel/printk 2>/dev/null | awk '{print \$1}')\" = \"${if (silent) "0" else "7"}\" ] && echo ok"
            Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
        } catch (e: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  Unified Network Engine (lynxd Single Source of Truth + Multi-Transport)
    // ----------------------------------------------------------------

    internal fun resolveNetworkBinary(): String? {
        val primary = LynxRepository.getLynxdPath()
        val res = Shell.cmd("[ -x '$primary' ] && echo ok").exec()
        if (res.out.firstOrNull()?.trim() == "ok") return primary
        return try {
            val bundled = File(LynxApp.instance.applicationInfo.nativeLibraryDir, "liblynxhud.so")
            if (bundled.exists() && bundled.canExecute()) bundled.absolutePath else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseFeatureResult(raw: String, fallbackMsg: String): NetworkFeatureResult {
        val trimmed = raw.trim()
        val jsonStart = trimmed.indexOf('{')
        if (jsonStart >= 0) {
            return try {
                val obj = JSONObject(trimmed.substring(jsonStart))
                NetworkFeatureResult(
                    ok = obj.optBoolean("ok", false),
                    status = obj.optString("status", "UNSUPPORTED"),
                    attempted = obj.optInt("attempted", 0),
                    verified = obj.optInt("verified", 0),
                    unsupported = obj.optInt("unsupported", 0),
                    message = obj.optString("message", fallbackMsg)
                )
            } catch (_: Exception) {
                NetworkFeatureResult(ok = false, status = "ERROR", message = fallbackMsg)
            }
        }
        val isOk = trimmed.isNotEmpty()
        return NetworkFeatureResult(
            ok = isOk,
            status = if (isOk) "PARTIAL" else "UNSUPPORTED",
            message = trimmed.ifBlank { fallbackMsg }
        )
    }

    suspend fun readNetworkEngineInfo(): NetworkEngineInfo = withContext(Dispatchers.IO) {
        try {
            val bin = resolveNetworkBinary()
            if (bin != null) {
                val out = Shell.cmd("'$bin' network discover 2>/dev/null").exec().out.joinToString("\n").trim()
                val idx = out.indexOf('{')
                if (idx >= 0) {
                    val obj = JSONObject(out.substring(idx))
                    val algosArr = obj.optJSONArray("available_algos")
                    val algos = buildList {
                        if (algosArr != null) {
                            for (i in 0 until algosArr.length()) {
                                algosArr.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                            }
                        }
                    }
                    val qdiscArr = obj.optJSONArray("available_qdiscs")
                    val qdiscs = buildList {
                        if (qdiscArr != null) {
                            for (i in 0 until qdiscArr.length()) {
                                qdiscArr.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                            }
                        }
                    }
                    val ifacesArr = obj.optJSONArray("interfaces")
                    val ifaces = buildList {
                        if (ifacesArr != null) {
                            for (i in 0 until ifacesArr.length()) {
                                val itObj = ifacesArr.optJSONObject(i) ?: continue
                                add(
                                    NetworkIfaceInfo(
                                        name = itObj.optString("name", ""),
                                        kind = itObj.optString("kind", ""),
                                        state = itObj.optString("state", ""),
                                        mtu = itObj.optInt("mtu", 1500),
                                        rxQueues = itObj.optInt("rx_queues", 0),
                                        txQueues = itObj.optInt("tx_queues", 0),
                                        rpsWritable = itObj.optBoolean("rps_writable", false),
                                        qdisc = itObj.optString("qdisc", "")
                                    )
                                )
                            }
                        }
                    }
                    return@withContext NetworkEngineInfo(
                        isLoaded = true,
                        currentAlgo = obj.optString("current_algo", ""),
                        bestAlgo = obj.optString("best_algo", ""),
                        availableAlgos = algos,
                        currentQdisc = obj.optString("current_qdisc", ""),
                        activeQdisc = obj.optString("active_qdisc", ""),
                        availableQdiscs = qdiscs,
                        activeIface = obj.optString("active_iface", ""),
                        activeTransport = obj.optString("active_transport", "none"),
                        activeMtu = obj.optInt("active_mtu", 1500),
                        gatewayIp = obj.optString("gateway_ip", ""),
                        srcIp = obj.optString("src_ip", ""),
                        radioVendor = obj.optString("radio_vendor", "unsupported"),
                        radioLabel = obj.optString("radio_label", "Generic"),
                        supportsHwRadio = obj.optBoolean("supports_hw_radio", false),
                        hasIw = obj.optBoolean("has_iw", false),
                        hasIwpriv = obj.optBoolean("has_iwpriv", false),
                        hasWl = obj.optBoolean("has_wl", false),
                        hasTc = obj.optBoolean("has_tc", false),
                        hasWpaCli = obj.optBoolean("has_wpa_cli", false),
                        androidSdk = obj.optInt("android_sdk", android.os.Build.VERSION.SDK_INT),
                        supportsHalLowLatency = obj.optBoolean("supports_hal_low_latency", android.os.Build.VERSION.SDK_INT >= 29),
                        supportsTcpLowLatency = obj.optBoolean("supports_tcp_low_latency", false),
                        supportsRpsXps = obj.optBoolean("supports_rps_xps", false),
                        fastopenNodesPresent = obj.optInt("fastopen_nodes_present", 0),
                        ramTierLabel = obj.optString("ram_tier_label", ""),
                        effectiveRmemMaxMb = obj.optDouble("effective_rmem_max_mb", 0.0).toFloat(),
                        hasBaseline = obj.optBoolean("has_baseline", false),
                        activePreset = obj.optString("active_preset", "custom"),
                        lowLatencyActive = obj.optBoolean("low_latency_active", false),
                        scanGuardActive = obj.optBoolean("scan_guard_active", false),
                        qdiscGuardActive = obj.optBoolean("qdisc_guard_active", false),
                        tcpBuffersScaled = obj.optBoolean("tcp_buffers_scaled", false),
                        tcpFastopenActive = obj.optBoolean("tcp_fastopen_active", false),
                        hwRadioActive = obj.optBoolean("hw_radio_active", false),
                        ipv6Disabled = obj.optBoolean("ipv6_disabled", false),
                        mobileStandbyActive = obj.optBoolean("mobile_standby_active", false),
                        interfaces = ifaces
                    )
                }
            }

            // Minimal read-only sysfs fallback if binary cannot execute
            val curAlgo = Shell.cmd("cat /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null").exec().out.firstOrNull()?.trim().orEmpty()
            val availAlgos = Shell.cmd("cat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null")
                .exec().out.firstOrNull()?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()
            val curQdisc = Shell.cmd("cat /proc/sys/net/core/default_qdisc 2>/dev/null").exec().out.firstOrNull()?.trim().orEmpty()
            NetworkEngineInfo(
                isLoaded = true,
                currentAlgo = curAlgo,
                availableAlgos = availAlgos,
                currentQdisc = curQdisc,
                activeQdisc = curQdisc,
                androidSdk = android.os.Build.VERSION.SDK_INT,
                supportsHalLowLatency = android.os.Build.VERSION.SDK_INT >= 29
            )
        } catch (_: Exception) {
            NetworkEngineInfo()
        }
    }

    suspend fun applyNetworkFeature(key: String, enabled: Boolean): NetworkFeatureResult = withContext(Dispatchers.IO) {
        try {
            val safeKey = key.replace(Regex("[^a-zA-Z0-9_-]"), "")
            val bin = resolveNetworkBinary()
                ?: return@withContext NetworkFeatureResult(
                    ok = false,
                    status = "UNSUPPORTED",
                    message = "Biner engine lynxd belum tersedia pada sistem ini."
                )
            val arg = if (enabled) "on" else "off"
            val out = Shell.cmd("'$bin' network feature '$safeKey' $arg 2>/dev/null").exec().out.joinToString("\n")
            parseFeatureResult(out, "Parameter $safeKey gagal diterapkan.")
        } catch (e: Exception) {
            NetworkFeatureResult(ok = false, status = "ERROR", message = e.message ?: "Error")
        }
    }

    suspend fun applyNetworkPreset(preset: String): NetworkFeatureResult = withContext(Dispatchers.IO) {
        try {
            val safe = preset.replace(Regex("[^a-zA-Z0-9_-]"), "").lowercase()
            val bin = resolveNetworkBinary()
                ?: return@withContext NetworkFeatureResult(
                    ok = false,
                    status = "UNSUPPORTED",
                    message = "Biner engine lynxd belum tersedia pada sistem ini."
                )
            val out = Shell.cmd("'$bin' network preset '$safe' 2>/dev/null").exec().out.joinToString("\n")
            parseFeatureResult(out, "Preset $safe gagal diterapkan.")
        } catch (e: Exception) {
            NetworkFeatureResult(ok = false, status = "ERROR", message = e.message ?: "Error")
        }
    }

    suspend fun setNetworkQdisc(qdisc: String): NetworkFeatureResult = withContext(Dispatchers.IO) {
        try {
            val safe = qdisc.replace(Regex("[^a-zA-Z0-9_-]"), "")
            val bin = resolveNetworkBinary()
            if (bin != null) {
                val res = Shell.cmd("'$bin' network set-qdisc '$safe' 2>&1").exec()
                val raw = res.out.joinToString("\n")
                if (res.isSuccess) return@withContext parseFeatureResult(raw, "Qdisc $safe diterapkan.")
                return@withContext NetworkFeatureResult(ok = false, status = "UNSUPPORTED", message = raw.ifBlank { "Kernel menolak qdisc $safe" })
            }
            val cmd = "echo '$safe' > /proc/sys/net/core/default_qdisc 2>/dev/null && [ \"$(cat /proc/sys/net/core/default_qdisc 2>/dev/null)\" = \"$safe\" ] && echo ok"
            val ok = Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
            NetworkFeatureResult(
                ok = ok,
                status = if (ok) "VERIFIED" else "UNSUPPORTED",
                message = if (ok) "Default qdisc diubah ke $safe" else "Kernel menolak qdisc $safe"
            )
        } catch (e: Exception) {
            NetworkFeatureResult(ok = false, status = "ERROR", message = e.message ?: "Error")
        }
    }

    suspend fun applyWifiPingStabilizer(enabled: Boolean): Boolean =
        applyNetworkFeature("low_latency", enabled).ok

    suspend fun applyAntiJitterScan(enabled: Boolean): Boolean =
        applyNetworkFeature("scan_guard", enabled).ok

    suspend fun applyFqCodel(enabled: Boolean): Boolean =
        applyNetworkFeature("qdisc_rps", enabled).ok

    suspend fun applyTcpBuffers16m(enabled: Boolean): Boolean =
        applyNetworkFeature("tcp_buffers", enabled).ok

    suspend fun applyTcpFastOpen(enabled: Boolean): Boolean =
        applyNetworkFeature("tcp_fastopen", enabled).ok

    suspend fun applyMtkHardwareAggregation(enabled: Boolean): Boolean =
        applyNetworkFeature("hw_radio", enabled).ok

    suspend fun probeLatencyViaEngine(): WifiTelemetryInfo = withContext(Dispatchers.IO) {
        readWifiTelemetry(includePing = true)
    }

    suspend fun readWifiTelemetry(includePing: Boolean = true): WifiTelemetryInfo = withContext(Dispatchers.IO) {
        try {
            val ctx = LynxApp.instance
            val wifiOut = Shell.cmd("cmd wifi status 2>/dev/null").exec().out.joinToString("\n")
            val wifiConnected = wifiOut.contains("Wifi is connected to") || wifiOut.contains("SSID: \"")

            var activeTransport = if (wifiConnected) "wifi" else "none"
            var cellularConnected = false
            runCatching {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val net = cm?.activeNetwork
                val caps = cm?.getNetworkCapabilities(net)
                if (caps != null) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        activeTransport = "wifi"
                    } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        activeTransport = "cellular"
                        cellularConnected = true
                    } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        activeTransport = "ethernet"
                    }
                }
            }

            // Active route + cellular radio info via root shell / lynxd
            var routeIface = ""
            var gatewayIp = ""
            var routeSrcIp = ""
            var activeMtu = 1500
            val bin = resolveNetworkBinary()
            var reachable = false
            var pingMs = 0f
            var jitterMs = 0f
            var lossPct = 100f

            if (includePing && bin != null) {
                val probeOut = Shell.cmd("'$bin' network probe 2>/dev/null").exec().out.joinToString("\n").trim()
                val idx = probeOut.indexOf('{')
                if (idx >= 0) {
                    runCatching {
                        val p = JSONObject(probeOut.substring(idx))
                        reachable = p.optBoolean("reachable", false)
                        pingMs = p.optDouble("avg_ms", 0.0).toFloat()
                        jitterMs = p.optDouble("jitter_ms", 0.0).toFloat()
                        lossPct = p.optDouble("loss_pct", 100.0).toFloat()
                        routeIface = p.optString("iface", "")
                        gatewayIp = p.optString("target", "")
                        val tr = p.optString("transport", "")
                        if (activeTransport == "none" && tr.isNotBlank() && tr != "other") {
                            activeTransport = tr
                            if (tr == "cellular") cellularConnected = true
                        }
                    }
                }
            } else {
                val rLine = Shell.cmd("ip -4 route get 1.1.1.1 2>/dev/null | head -n1").exec().out.firstOrNull().orEmpty()
                Regex("""\bdev\s+(\S+)""").find(rLine)?.let { routeIface = it.groupValues[1] }
                Regex("""\bvia\s+([0-9.]+)""").find(rLine)?.let { gatewayIp = it.groupValues[1] }
                Regex("""\bsrc\s+([0-9.]+)""").find(rLine)?.let { routeSrcIp = it.groupValues[1] }
                if (activeTransport == "none" && routeIface.isNotBlank()) {
                    activeTransport = when {
                        routeIface.startsWith("wlan") || routeIface.startsWith("swlan") -> "wifi"
                        routeIface.startsWith("rmnet") || routeIface.startsWith("ccmni") || routeIface.startsWith("seth") -> {
                            cellularConnected = true
                            "cellular"
                        }
                        routeIface.startsWith("eth") -> "ethernet"
                        else -> "other"
                    }
                }
            }

            if (routeIface.isNotBlank()) {
                activeMtu = Shell.cmd("cat /sys/class/net/$routeIface/mtu 2>/dev/null").exec()
                    .out.firstOrNull()?.trim()?.toIntOrNull() ?: 1500
            }

            var ssid = "Tidak Terhubung"
            var ip = routeSrcIp
            var rssi = 0
            var linkSpeed = 0
            var maxSpeed = 0
            var freq = 0
            var wifiStandard = "Wi-Fi"

            if (wifiConnected) {
                val ssidMatch = Regex("""Wifi is connected to "([^"]+)"""").find(wifiOut)
                    ?: Regex("""SSID: "([^"]+)"""").find(wifiOut)
                ssid = ssidMatch?.groupValues?.get(1) ?: "Wi-Fi Terhubung"
                if (ip.isBlank()) {
                    ip = Regex("""IP: /?([0-9.]+)""").find(wifiOut)?.groupValues?.get(1) ?: ""
                }
                rssi = Regex("""RSSI: (-?[0-9]+)""").find(wifiOut)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                linkSpeed = Regex("""Link speed: ([0-9]+)Mbps""").find(wifiOut)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                maxSpeed = Regex("""Max Supported Tx Link speed: ([0-9]+)Mbps""").find(wifiOut)?.groupValues?.get(1)?.toIntOrNull() ?: linkSpeed
                freq = Regex("""Frequency: ([0-9]+)MHz""").find(wifiOut)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val stdNum = Regex("""Wi-Fi standard: ([0-9]+)""").find(wifiOut)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                wifiStandard = when (stdNum) {
                    4 -> "Wi-Fi 4 (802.11n)"
                    5 -> "Wi-Fi 5 (802.11ac)"
                    6 -> "Wi-Fi 6 (802.11ax)"
                    7 -> "Wi-Fi 7 (802.11be)"
                    else -> if (freq >= 5925) "Wi-Fi 6E/7" else if (freq >= 4900) "Wi-Fi 5/6 (5 GHz)" else "Wi-Fi (2.4 GHz)"
                }
            }

            var cellNetType = ""
            var cellOp = ""
            if (cellularConnected || activeTransport == "cellular") {
                val propsOut = Shell.cmd("getprop gsm.network.type; echo '---'; getprop gsm.operator.alpha").exec().out.joinToString("\n")
                val parts = propsOut.split("---")
                cellNetType = parts.getOrNull(0)?.trim()?.split(',')?.firstOrNull { it.isNotBlank() && it != "Unknown" } ?: "Seluler Data"
                cellOp = parts.getOrNull(1)?.trim()?.split(',')?.firstOrNull { it.isNotBlank() } ?: ""
            }

            WifiTelemetryInfo(
                isConnected = wifiConnected,
                ssid = ssid,
                ipAddress = ip,
                frequencyMhz = freq,
                linkSpeedMbps = linkSpeed,
                maxLinkSpeedMbps = maxSpeed,
                rssiDbm = rssi,
                wifiStandard = wifiStandard,
                gatewayPingMs = pingMs,
                isLowLatencyActive = wifiOut.contains("low-latency", ignoreCase = true) || linkSpeed > 200,
                cellularConnected = cellularConnected,
                cellularNetworkType = cellNetType,
                cellularOperator = cellOp,
                activeTransport = activeTransport,
                activeIface = routeIface,
                gatewayIp = gatewayIp,
                gatewayJitterMs = jitterMs,
                gatewayLossPct = lossPct,
                probeReachable = reachable,
                activeMtu = activeMtu
            )
        } catch (_: Exception) {
            WifiTelemetryInfo(isConnected = false)
        }
    }

    suspend fun benchmarkDnsLatency(hosts: List<String>): Map<String, Float> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Float>()
        hosts.distinct().forEach { host ->
            val safeHost = host.trim()
            if (safeHost.isBlank()) return@forEach
            val rttMs = try {
                val startNs = System.nanoTime()
                Socket().use { sock ->
                    sock.connect(InetSocketAddress(safeHost, 853), 1500)
                }
                ((System.nanoTime() - startNs) / 1_000_000.0).toFloat()
            } catch (_: Exception) {
                -1f
            }
            results[safeHost] = rttMs
        }
        results
    }

    suspend fun readPrivateDns(): Pair<String, String> = withContext(Dispatchers.IO) {
        try {
            val rawMode = Shell.cmd("settings get global private_dns_mode 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            val rawSpec = Shell.cmd("settings get global private_dns_specifier 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            val mode = when (rawMode.lowercase()) {
                "hostname" -> "hostname"
                "off" -> "off"
                else -> "opportunistic"
            }
            val spec = if (rawSpec.equals("null", ignoreCase = true)) "" else rawSpec
            Pair(mode, spec)
        } catch (_: Exception) {
            Pair("off", "")
        }
    }

    suspend fun applyPrivateDns(mode: String, specifier: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val safeMode = when (mode.lowercase()) {
                "hostname" -> "hostname"
                "off" -> "off"
                else -> "opportunistic"
            }
            val safeSpec = specifier.trim().replace(Regex("[^a-zA-Z0-9.-]"), "")
            val cmd = if (safeMode == "hostname" && safeSpec.isNotBlank()) {
                "settings put global private_dns_mode hostname && settings put global private_dns_specifier '$safeSpec'"
            } else {
                "settings put global private_dns_mode '$safeMode' && settings delete global private_dns_specifier 2>/dev/null || true"
            }
            Shell.cmd(cmd).exec()
            val verifiedMode = Shell.cmd("settings get global private_dns_mode 2>/dev/null").exec().out.firstOrNull()?.trim()?.lowercase()
            if (safeMode == "hostname") {
                val verifiedSpec = Shell.cmd("settings get global private_dns_specifier 2>/dev/null").exec().out.firstOrNull()?.trim()
                verifiedMode == "hostname" && verifiedSpec == safeSpec
            } else {
                verifiedMode == safeMode || (safeMode == "opportunistic" && (verifiedMode == "opportunistic" || verifiedMode == "null"))
            }
        } catch (_: Exception) { false }
    }

    suspend fun readAudioTelemetry(): AudioTelemetryInfo = withContext(Dispatchers.IO) {
        try {
            val deviceOut = Shell.cmd("dumpsys audio 2>/dev/null | grep -m 1 -E 'Devices: speaker|Devices: wired_headset|Devices: bluetooth|Devices: usb_headset'").exec().out.firstOrNull()?.trim() ?: ""
            val activeDevice = when {
                deviceOut.contains("bluetooth", ignoreCase = true) -> "Bluetooth (A2DP)"
                deviceOut.contains("wired_headset", ignoreCase = true) -> "Headphone (3.5mm)"
                deviceOut.contains("usb", ignoreCase = true) -> "USB Audio DAC"
                deviceOut.contains("speaker", ignoreCase = true) -> "Internal Speaker"
                else -> "Speaker / Default"
            }
            val mmapPolicy = Shell.cmd("getprop aaudio.mmap_policy 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "2"
            val isMmapForced = mmapPolicy == "3"
            val deepBuffer = Shell.cmd("getprop audio.deep_buffer.media 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "true"
            val isDeepBufferBypassed = deepBuffer.equals("false", ignoreCase = true)
            val offloadKb = Shell.cmd("getprop vendor.audio.offload.buffer.size.kb 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "64"
            val isDirectDac = offloadKb == "32"

            AudioTelemetryInfo(
                activeDevice = activeDevice,
                sampleRate = "48000 Hz",
                isMmapForced = isMmapForced,
                isDeepBufferBypassed = isDeepBufferBypassed,
                isDirectDacOffload = isDirectDac
            )
        } catch (_: Exception) {
            AudioTelemetryInfo()
        }
    }

    suspend fun applyAudioMmap(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val p = if (enabled) "3" else "2"
            val burst = if (enabled) "2000" else "4000"
            val cmd = "setprop aaudio.mmap_policy $p; setprop aaudio.mmap_exclusive_policy $p; setprop aaudio.hw_burst_min_usec $burst; [ \"$(getprop aaudio.mmap_policy)\" = \"$p\" ] && echo ok"
            Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioDeepBufferBypass(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val valStr = if (enabled) "false" else "true"
            Shell.cmd("setprop audio.deep_buffer.media $valStr; setprop persist.sys.phh.disable_fast_audio 0").exec()
            true
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioDirectDacOffload(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val kb = if (enabled) "32" else "64"
            val mult = if (enabled) "1" else "2"
            Shell.cmd("setprop vendor.audio.offload.buffer.size.kb $kb; setprop af.fast_track_multiplier $mult").exec()
            true
        } catch (_: Exception) { false }
    }

    // ----------------------------------------------------------------
    //  ADB Wireless Keep-Alive & Port Lock Engine
    // ----------------------------------------------------------------

    suspend fun readAdbKeepAliveConfig(): Pair<Boolean, Int> = withContext(Dispatchers.IO) {
        try {
            val enabledStr = Shell.cmd("$LYNXD_PATH state get adb_keepalive.enabled").exec().out.firstOrNull()?.trim() ?: "false"
            val portStr = Shell.cmd("$LYNXD_PATH state get adb_keepalive.port 5555").exec().out.firstOrNull()?.trim() ?: "5555"
            val enabled = enabledStr == "true" || enabledStr == "1"
            val port = portStr.toIntOrNull()?.coerceIn(1024, 65535) ?: 5555
            Pair(enabled, port)
        } catch (_: Exception) {
            Pair(false, 5555)
        }
    }

    suspend fun setAdbKeepAlive(enabled: Boolean, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val clampedPort = port.coerceIn(1024, 65535)
            val sub = if (enabled) "enable $clampedPort" else "disable"
            val result = Shell.cmd("$LYNXD_PATH adb $sub").exec()
            result.isSuccess
        } catch (_: Exception) { false }
    }

    suspend fun setAdbPort(port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val clampedPort = port.coerceIn(1024, 65535)
            val result = Shell.cmd("$LYNXD_PATH adb set-port $clampedPort").exec()
            result.isSuccess
        } catch (_: Exception) { false }
    }

    suspend fun getAdbStatus(): String = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd("$LYNXD_PATH adb status").exec()
            result.out.joinToString("\n").trim()
        } catch (_: Exception) { "{}" }
    }

    suspend fun applyJoyoseNeutralizer(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = if (enabled) {
                """
                killall -STOP com.xiaomi.joyose 2>/dev/null
                am force-stop com.xiaomi.joyose 2>/dev/null
                pm disable-user --user 0 com.xiaomi.joyose 2>/dev/null
                pm disable-user --user 0 com.samsung.android.game.gos 2>/dev/null
                echo ok
                """.trimIndent()
            } else {
                """
                pm enable com.xiaomi.joyose 2>/dev/null
                pm enable com.samsung.android.game.gos 2>/dev/null
                killall -CONT com.xiaomi.joyose 2>/dev/null
                echo ok
                """.trimIndent()
            }
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }


}
