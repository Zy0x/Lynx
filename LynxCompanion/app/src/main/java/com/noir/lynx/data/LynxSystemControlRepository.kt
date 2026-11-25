package com.noir.lynx.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.noir.lynx.LynxApp
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    suspend fun benchmarkDnsLatency(hosts: List<String>): Map<String, Float> = coroutineScope {
        hosts.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .map { host ->
                async(Dispatchers.IO) {
                    val rttMs = try {
                        val startNs = System.nanoTime()
                        Socket().use { sock ->
                            sock.connect(InetSocketAddress(host, 853), 1200)
                        }
                        ((System.nanoTime() - startNs) / 1_000_000.0).toFloat()
                    } catch (_: Exception) {
                        -1f
                    }
                    host to rttMs
                }
            }
            .awaitAll()
            .toMap()
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

    private const val AUDIO_BASELINE_FILE = "/data/adb/lynx/audio_baseline.env"

    private fun ensureAudioBaselineScript(): String = """
        mkdir -p /data/adb/lynx 2>/dev/null || true
        if [ ! -f "$AUDIO_BASELINE_FILE" ]; then
            echo "ORIG_MMAP=$(getprop aaudio.mmap_policy 2>/dev/null)" > "$AUDIO_BASELINE_FILE"
            echo "ORIG_MMAP_EXCL=$(getprop aaudio.mmap_exclusive_policy 2>/dev/null)" >> "$AUDIO_BASELINE_FILE"
            echo "ORIG_BURST=$(getprop aaudio.hw_burst_min_usec 2>/dev/null)" >> "$AUDIO_BASELINE_FILE"
            echo "ORIG_DEEP=$(getprop audio.deep_buffer.media 2>/dev/null)" >> "$AUDIO_BASELINE_FILE"
            echo "ORIG_OFFLOAD_KB=$(getprop vendor.audio.offload.buffer.size.kb 2>/dev/null)" >> "$AUDIO_BASELINE_FILE"
            echo "ORIG_FAST_MULT=$(getprop af.fast_track_multiplier 2>/dev/null)" >> "$AUDIO_BASELINE_FILE"
            echo "ORIG_A2DP_OFFLOAD_DIS=$(getprop persist.bluetooth.a2dp_offload.disabled 2>/dev/null)" >> "$AUDIO_BASELINE_FILE"
            echo "ORIG_SYS_BT_HAL=$(getprop persist.bluetooth.system_audio_hal.enabled 2>/dev/null)" >> "$AUDIO_BASELINE_FILE"
            echo "ORIG_ABSVOL_DIS=$(getprop persist.bluetooth.disableabsvol 2>/dev/null)" >> "$AUDIO_BASELINE_FILE"
            echo "ORIG_AVRCP_VER=$(getprop persist.bluetooth.avrcpversion 2>/dev/null)" >> "$AUDIO_BASELINE_FILE"
            echo "ORIG_LDAC_ABR=$(getprop vendor.bluetooth.ldac.abr 2>/dev/null)" >> "$AUDIO_BASELINE_FILE"
            echo "ORIG_SBC_HD=$(getprop persist.bluetooth.sbc_hd_higher_bitrate 2>/dev/null)" >> "$AUDIO_BASELINE_FILE"
            echo "ORIG_PHH_SFX=$(getprop persist.sys.phh.disable_audio_effects 2>/dev/null)" >> "$AUDIO_BASELINE_FILE"
            echo "ORIG_PHH_CAF=$(getprop persist.sys.phh.caf.audio_policy 2>/dev/null)" >> "$AUDIO_BASELINE_FILE"
            echo "ORIG_PHH_VOLLIST=$(getprop persist.sys.phh.disable_soundvolume_effect 2>/dev/null)" >> "$AUDIO_BASELINE_FILE"
            echo "ORIG_PHH_VCALL=$(getprop persist.sys.phh.disable_voice_call_in 2>/dev/null)" >> "$AUDIO_BASELINE_FILE"
            echo "ORIG_BESLOUD=$(getprop persist.vendor.audiohal.besloudness_state 2>/dev/null)" >> "$AUDIO_BASELINE_FILE"
        fi
        . "$AUDIO_BASELINE_FILE" 2>/dev/null || true
    """.trimIndent()

    private fun setPropOrResetPropScript(): String = """
        apply_prop() {
            local k="${'$'}1"
            local v="${'$'}2"
            setprop "${'$'}k" "${'$'}v" 2>/dev/null
            if [ "$(getprop "${'$'}k" 2>/dev/null)" != "${'$'}v" ]; then
                if command -v resetprop_phh >/dev/null 2>&1; then
                    resetprop_phh "${'$'}k" "${'$'}v" 2>/dev/null
                elif command -v resetprop >/dev/null 2>&1; then
                    resetprop -n "${'$'}k" "${'$'}v" 2>/dev/null
                elif [ -x /data/adb/ksu/bin/resetprop ]; then
                    /data/adb/ksu/bin/resetprop -n "${'$'}k" "${'$'}v" 2>/dev/null
                elif [ -x /data/adb/ap/bin/resetprop ]; then
                    /data/adb/ap/bin/resetprop -n "${'$'}k" "${'$'}v" 2>/dev/null
                fi
            fi
        }
    """.trimIndent()

    suspend fun readAudioTelemetry(): AudioTelemetryInfo = withContext(Dispatchers.IO) {
        try {
            val script = """
                DEV_OUT=$(dumpsys audio 2>/dev/null | grep -m 1 -E 'Devices: speaker|Devices: wired_headset|Devices: wired_headphone|Devices: bluetooth|Devices: usb_headset|Devices: usb_device|Devices: earpiece')
                FLINGER=$(dumpsys media.audio_flinger 2>/dev/null | head -n 110)
                RATE=$(echo "${'$'}FLINGER" | grep -m 1 -i "Sample rate:" | awk '{print ${'$'}3}')
                FMT=$(echo "${'$'}FLINGER" | grep -m 1 -i "HAL format:")
                CARD=$(cat /proc/asound/cards 2>/dev/null | head -n 1 | sed 's/.*- //')
                if [ -z "${'$'}CARD" ]; then
                    CARD=$(cat /sys/kernel/debug/asoc/cards 2>/dev/null | head -n 1)
                fi
                if [ -z "${'$'}CARD" ]; then
                    PLAT=$(getprop ro.board.platform 2>/dev/null)
                    [ -n "${'$'}PLAT" ] && CARD="${'$'}PLAT-audio-hal" || CARD="AudioFlinger HAL"
                fi
                BT_CODEC=$(dumpsys bluetooth_manager 2>/dev/null | grep -m 1 -iE "codecName|mCodecType|LDAC|aptX|AAC|SBC|LC3" | head -n 1)
                MMAP=$(getprop aaudio.mmap_policy 2>/dev/null)
                DEEP=$(getprop audio.deep_buffer.media 2>/dev/null)
                OFFLOAD=$(getprop vendor.audio.offload.buffer.size.kb 2>/dev/null)
                BT_GUARD=$(getprop lynx.audio.bt_crackle_guard 2>/dev/null)
                HIRES_RESAMP=$(getprop af.resampler.quality 2>/dev/null)
                POP_GUARD=$(getprop lynx.audio.codec_pop_guard 2>/dev/null)
                SPATIAL_PROP=$(getprop persist.lynx.audio.spatializer_boost 2>/dev/null)
                SVC=$(getprop init.svc.audioserver 2>/dev/null)
                PHH_DIR=0
                [ -d /system/phh ] || [ -x /system/bin/phh-prop-handler.sh ] && PHH_DIR=1
                PHH_HANDLER=0
                [ -x /system/bin/phh-prop-handler.sh ] && PHH_HANDLER=1
                TREBLE=$(getprop ro.treble.enabled 2>/dev/null)
                DISP_ID=$(getprop ro.build.display.id 2>/dev/null)
                BRAND=$(getprop ro.product.vendor.brand 2>/dev/null)
                [ -z "${'$'}BRAND" ] && BRAND=$(getprop ro.product.brand 2>/dev/null)
                SOC_TYPE="generic"
                PLAT_LOW=$(getprop ro.board.platform 2>/dev/null | tr '[:upper:]' '[:lower:]')
                SOC_MANUF=$(getprop ro.soc.manufacturer 2>/dev/null | tr '[:upper:]' '[:lower:]')
                if [ -d /proc/ppm ] || [ -d /proc/ged ] || [ -c /dev/ged ]; then
                    SOC_TYPE="mtk"
                elif [ -d /sys/class/kgsl ] || [ -c /dev/kgsl-3d0 ] || [ -d /sys/devices/soc0 ]; then
                    SOC_TYPE="qcom"
                elif echo "${'$'}PLAT_LOW ${'$'}SOC_MANUF" | grep -qE "gs101|gs201|zuma|tensor|google"; then
                    SOC_TYPE="tensor"
                elif echo "${'$'}PLAT_LOW ${'$'}SOC_MANUF" | grep -qE "exynos|s5e|universal|samsung"; then
                    SOC_TYPE="exynos"
                elif echo "${'$'}PLAT_LOW ${'$'}SOC_MANUF" | grep -qE "ums|sp9|unisoc|spreadtrum"; then
                    SOC_TYPE="unisoc"
                fi
                AHAL_SVC=$(getprop | sed -nE 's/.*init\.svc\.(.*audio-hal[^]]*).*/\1/p' | head -n 1)
                BTHAL_SVC=$(getprop | sed -nE 's/.*init\.svc\.(.*bt-audio-hal[^]]*).*/\1/p' | head -n 1)
                VOLLIST_LIB=0
                [ -f /vendor/lib64/soundfx/libvolumelistener.so ] || [ -f /vendor/lib/soundfx/libvolumelistener.so ] && VOLLIST_LIB=1
                BESLOUD_SUP=$(getprop ro.vendor.mtk_besloudness_support 2>/dev/null)
                ASOC_PM=0
                ls /sys/module/snd_soc*/parameters/pmdown_time /sys/module/snd_*/parameters/power_save >/dev/null 2>&1 && ASOC_PM=1
                APTX_SUP=0
                ls /system/lib*/libaptX* /vendor/lib*/libaptX* /system_ext/lib*/libaptX* >/dev/null 2>&1 && APTX_SUP=1
                [ -n "$(getprop persist.vendor.qcom.bluetooth.aptxadaptiver2_2_support 2>/dev/null)" ] && APTX_SUP=1
                LE_SUP=$(getprop ro.bluetooth.leaudio_offload.supported 2>/dev/null)
                [ -z "${'$'}LE_SUP" ] && LE_SUP=$(getprop bluetooth.profile.bap.unicast.client.enabled 2>/dev/null)
                SFX_CHAIN="Standard AOSP AudioFX"
                if [ -n "$(getprop ro.vendor.dolby.dax.version 2>/dev/null)" ] || ls /vendor/lib*/soundfx/libswdap.so /vendor/lib*/soundfx/libhwdap.so >/dev/null 2>&1; then
                    SFX_CHAIN="Dolby Atmos DAX + AOSP FX"
                elif [ -n "$(getprop ro.vendor.dirac.acs.controller 2>/dev/null)" ] || ls /vendor/lib*/soundfx/libdirac*.so >/dev/null 2>&1; then
                    SFX_CHAIN="Dirac HD Sound + AOSP FX"
                elif [ -n "$(getprop ro.vendor.audio.sdk.fluencetype 2>/dev/null)" ]; then
                    SFX_CHAIN="Qualcomm QTI AudioFX + Fluence"
                elif [ "${'$'}BESLOUD_SUP" = "1" ]; then
                    SFX_CHAIN="MediaTek BesLoudness + AudioFX"
                fi
                A2DP_DIS=$(getprop persist.bluetooth.a2dp_offload.disabled 2>/dev/null)
                SYS_BTHAL=$(getprop persist.bluetooth.system_audio_hal.enabled 2>/dev/null)
                ABSVOL_PROP=$(getprop persist.bluetooth.disableabsvol 2>/dev/null)
                ABSVOL_SET=$(settings get global bluetooth_disable_absolute_volume 2>/dev/null)
                LDAC_MODE=$(getprop persist.lynx.audio.ldac_mode 2>/dev/null)
                LDAC_ABR=$(getprop vendor.bluetooth.ldac.abr 2>/dev/null)
                SBC_HD=$(getprop persist.bluetooth.sbc_hd_higher_bitrate 2>/dev/null)
                APTX_LL=$(getprop persist.lynx.audio.aptx_adaptive_ll 2>/dev/null)
                LE_LC3=$(getprop persist.lynx.audio.le_audio_lc3 2>/dev/null)
                HFP_HD=$(getprop persist.lynx.audio.hfp_hd_nrec_bypass 2>/dev/null)
                AVRCP_VER=$(getprop persist.bluetooth.avrcpversion 2>/dev/null)
                USB_DIS=$(settings get secure usb_audio_automatic_routing_disabled 2>/dev/null)
                PHH_SFX=$(getprop persist.sys.phh.disable_audio_effects 2>/dev/null)
                IGNORE_SFX=$(getprop ro.audio.ignore_effects 2>/dev/null)
                PHH_CAF=$(getprop persist.sys.phh.caf.audio_policy 2>/dev/null)
                PHH_VOL=$(getprop persist.sys.phh.disable_soundvolume_effect 2>/dev/null)
                PHH_VCALL=$(getprop persist.sys.phh.disable_voice_call_in 2>/dev/null)
                PHH_BINDER=$(getprop persist.sys.phh.allow_binder_thread_on_incoming_calls 2>/dev/null)
                FLUENCE_BYP=$(getprop persist.lynx.audio.fluence_bypass 2>/dev/null)
                BESLOUD_CUR=$(getprop persist.vendor.audiohal.besloudness_state 2>/dev/null)
                VOL_STEPS=$(getprop ro.config.media_vol_steps 2>/dev/null)
                echo "DEV=${'$'}DEV_OUT"
                echo "RATE=${'$'}RATE"
                echo "FMT=${'$'}FMT"
                echo "CARD=${'$'}CARD"
                echo "BTC=${'$'}BT_CODEC"
                echo "MMAP=${'$'}MMAP"
                echo "DEEP=${'$'}DEEP"
                echo "OFFLOAD=${'$'}OFFLOAD"
                echo "BTGUARD=${'$'}BT_GUARD"
                echo "HIRES_RESAMP=${'$'}HIRES_RESAMP"
                echo "POP_GUARD=${'$'}POP_GUARD"
                echo "SPATIAL_PROP=${'$'}SPATIAL_PROP"
                echo "SVC=${'$'}SVC"
                echo "PHH_DIR=${'$'}PHH_DIR"
                echo "PHH_HANDLER=${'$'}PHH_HANDLER"
                echo "TREBLE=${'$'}TREBLE"
                echo "DISP_ID=${'$'}DISP_ID"
                echo "BRAND=${'$'}BRAND"
                echo "SOC_TYPE=${'$'}SOC_TYPE"
                echo "AHAL_SVC=${'$'}AHAL_SVC"
                echo "BTHAL_SVC=${'$'}BTHAL_SVC"
                echo "VOLLIST_LIB=${'$'}VOLLIST_LIB"
                echo "BESLOUD_SUP=${'$'}BESLOUD_SUP"
                echo "ASOC_PM=${'$'}ASOC_PM"
                echo "APTX_SUP=${'$'}APTX_SUP"
                echo "LE_SUP=${'$'}LE_SUP"
                echo "SFX_CHAIN=${'$'}SFX_CHAIN"
                echo "A2DP_DIS=${'$'}A2DP_DIS"
                echo "SYS_BTHAL=${'$'}SYS_BTHAL"
                echo "ABSVOL_PROP=${'$'}ABSVOL_PROP"
                echo "ABSVOL_SET=${'$'}ABSVOL_SET"
                echo "LDAC_MODE=${'$'}LDAC_MODE"
                echo "LDAC_ABR=${'$'}LDAC_ABR"
                echo "SBC_HD=${'$'}SBC_HD"
                echo "APTX_LL=${'$'}APTX_LL"
                echo "LE_LC3=${'$'}LE_LC3"
                echo "HFP_HD=${'$'}HFP_HD"
                echo "AVRCP_VER=${'$'}AVRCP_VER"
                echo "USB_DIS=${'$'}USB_DIS"
                echo "PHH_SFX=${'$'}PHH_SFX"
                echo "IGNORE_SFX=${'$'}IGNORE_SFX"
                echo "PHH_CAF=${'$'}PHH_CAF"
                echo "PHH_VOL=${'$'}PHH_VOL"
                echo "PHH_VCALL=${'$'}PHH_VCALL"
                echo "PHH_BINDER=${'$'}PHH_BINDER"
                echo "FLUENCE_BYP=${'$'}FLUENCE_BYP"
                echo "BESLOUD_CUR=${'$'}BESLOUD_CUR"
                echo "VOL_STEPS=${'$'}VOL_STEPS"
                getprop | grep -iE "\[(persist\.sys\.phh\.|persist\.bluetooth\.|persist\.vendor\.audio|ro\.vendor\.audio|ro\.vendor\.mtk_audio|vendor\.audio\.feature)" | head -n 18 | sed 's/^/PROP_SCAN=/'
            """.trimIndent()
            val lines = Shell.cmd(script).exec().out
            val map = mutableMapOf<String, String>()
            val scannedProps = mutableListOf<Pair<String, String>>()
            for (line in lines) {
                if (line.startsWith("PROP_SCAN=")) {
                    val raw = line.removePrefix("PROP_SCAN=").trim()
                    val m = Regex("\\[([^]]+)]:\\s*\\[([^]]*)]").find(raw)
                    if (m != null) {
                        scannedProps.add(m.groupValues[1] to m.groupValues[2])
                    }
                    continue
                }
                val idx = line.indexOf('=')
                if (idx > 0) {
                    map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
                }
            }

            val deviceOut = map["DEV"].orEmpty()
            val btRaw = map["BTC"].orEmpty()
            val btCodec = when {
                btRaw.contains("LDAC", ignoreCase = true) -> "LDAC"
                btRaw.contains("aptX Adaptive", ignoreCase = true) -> "aptX Adaptive"
                btRaw.contains("aptX HD", ignoreCase = true) -> "aptX HD"
                btRaw.contains("aptX", ignoreCase = true) -> "aptX"
                btRaw.contains("LC3", ignoreCase = true) -> "LE Audio LC3"
                btRaw.contains("AAC", ignoreCase = true) -> "AAC"
                btRaw.contains("SBC", ignoreCase = true) -> "SBC"
                else -> ""
            }
            val activeDevice = when {
                deviceOut.contains("bluetooth", ignoreCase = true) ->
                    if (btCodec.isNotBlank()) "Bluetooth A2DP ($btCodec)" else "Bluetooth A2DP"
                deviceOut.contains("wired_head", ignoreCase = true) -> "Wired Headset (3.5mm)"
                deviceOut.contains("usb", ignoreCase = true) -> "USB Audio DAC"
                deviceOut.contains("earpiece", ignoreCase = true) -> "Earpiece / Speaker"
                deviceOut.contains("speaker", ignoreCase = true) -> "Internal Speaker"
                else -> "Internal Speaker"
            }

            val rateHz = map["RATE"]?.toIntOrNull() ?: 48000
            val sampleRateLabel = if (rateHz >= 1000) {
                String.format(java.util.Locale.US, "%.1f kHz", rateHz / 1000f)
            } else {
                "$rateHz Hz"
            }

            val fmtRaw = map["FMT"].orEmpty()
            val bitDepthLabel = when {
                fmtRaw.contains("32_BIT", ignoreCase = true) -> "32-bit PCM"
                fmtRaw.contains("24_BIT", ignoreCase = true) -> "24-bit PCM"
                fmtRaw.contains("FLOAT", ignoreCase = true) -> "32-bit Float"
                else -> "16-bit PCM"
            }

            val codecDriver = map["CARD"].orEmpty().ifBlank { "AudioFlinger HAL" }
            val mmapPolicy = map["MMAP"].orEmpty()
            val isMmapForced = mmapPolicy == "3"
            val deepBuffer = map["DEEP"].orEmpty()
            val isDeepBufferBypassed = deepBuffer.equals("false", ignoreCase = true) || deepBuffer == "0"
            val offloadKb = map["OFFLOAD"].orEmpty()
            val isDirectDac = offloadKb == "32"
            val isBtGuard = map["BTGUARD"] == "1" || map["BTGUARD"].equals("true", ignoreCase = true)
            val isHiresResamp = map["HIRES_RESAMP"] == "7" || map["HIRES_RESAMP"] == "255"
            val isPopGuard = map["POP_GUARD"] == "1"
            val isSpatialBoost = map["SPATIAL_PROP"] == "1"
            val svc = map["SVC"].orEmpty()
            val serverStatus = if (svc.equals("running", ignoreCase = true) || svc.isBlank()) {
                "audioserver (Active)"
            } else {
                "audioserver ($svc)"
            }

            val isPhhGsi = map["PHH_DIR"] == "1" || map["DISP_ID"].orEmpty().contains("treble_", ignoreCase = true)
            val hasPhhHandler = map["PHH_HANDLER"] == "1"
            val isTreble = map["TREBLE"].orEmpty().equals("true", ignoreCase = true)
            val dispId = map["DISP_ID"].orEmpty().trim()
            val cleanBuildTag = dispId.split(Regex("\\s+")).firstOrNull().orEmpty().take(28)
            val romTypeLabel = when {
                isPhhGsi -> "PHH Project Treble GSI"
                isTreble -> "Custom / OEM Treble ROM"
                else -> "Standard Android ROM"
            }.let { base ->
                if (cleanBuildTag.isNotBlank()) "$base ($cleanBuildTag)" else base
            }
            val socVendor = map["SOC_TYPE"].orEmpty().ifBlank { "generic" }
            val oemBrand = map["BRAND"].orEmpty()
            val audioHalSvc = map["AHAL_SVC"].orEmpty().ifBlank { "vendor.audio-hal" }
            val btHalSvc = map["BTHAL_SVC"].orEmpty()
            val sfxChain = map["SFX_CHAIN"].orEmpty().ifBlank { "Standard AOSP AudioFX" }
            val hasAsocPm = map["ASOC_PM"] == "1"
            val hasVolListener = map["VOLLIST_LIB"] == "1"
            val hasBesLoudness = map["BESLOUD_SUP"] == "1" || socVendor == "mtk"
            val hasAptx = map["APTX_SUP"] == "1" || socVendor == "qcom"
            val hasLeAudio = map["LE_SUP"].orEmpty().equals("true", ignoreCase = true)

            val a2dpDisLive = map["A2DP_DIS"].equals("true", ignoreCase = true) || map["A2DP_DIS"] == "1"
            val sysBtHalLive = map["SYS_BTHAL"].equals("true", ignoreCase = true) || map["SYS_BTHAL"] == "1"
            val absVolDisLive = map["ABSVOL_PROP"].equals("true", ignoreCase = true) || map["ABSVOL_SET"] == "1"
            val ldacModeLive = when (map["LDAC_MODE"].orEmpty().lowercase()) {
                "hires_990", "990" -> "hires_990"
                "balanced", "660" -> "balanced"
                else -> if (map["LDAC_ABR"].equals("false", ignoreCase = true)) "hires_990" else "adaptive"
            }
            val sbcHdLive = map["SBC_HD"] == "1" || map["SBC_HD"].equals("true", ignoreCase = true)
            val aptxLlLive = map["APTX_LL"] == "1"
            val leLc3Live = map["LE_LC3"] == "1" || hasLeAudio
            val hfpHdLive = map["HFP_HD"] == "1"
            val avrcpVerLive = when (map["AVRCP_VER"].orEmpty().lowercase()) {
                "avrcp14" -> "avrcp14"
                "avrcp15" -> "avrcp15"
                "avrcp16" -> "avrcp16"
                else -> "default"
            }
            val usbDisLive = map["USB_DIS"] == "1"
            val phhSfxLive = map["PHH_SFX"] == "1" || map["IGNORE_SFX"].equals("true", ignoreCase = true)
            val phhCafLive = map["PHH_CAF"] == "1"
            val phhVolLive = map["PHH_VOL"] == "1"
            val phhVcallLive = map["PHH_VCALL"].equals("true", ignoreCase = true) || map["PHH_VCALL"] == "1"
            val phhBinderLive = map["PHH_BINDER"] == "1"
            val fluenceBypLive = map["FLUENCE_BYP"] == "1"
            val besLoudLive = map["BESLOUD_CUR"] == "1"
            val volSteps30Live = (map["VOL_STEPS"]?.toIntOrNull() ?: 15) >= 25

            AudioTelemetryInfo(
                activeDevice = activeDevice,
                sampleRate = sampleRateLabel,
                bitDepthLabel = bitDepthLabel,
                codecDriverLabel = codecDriver,
                btCodecLabel = btCodec,
                isMmapForced = isMmapForced,
                isDeepBufferBypassed = isDeepBufferBypassed,
                isDirectDacOffload = isDirectDac,
                isBtCrackleGuardActive = isBtGuard,
                isHiresResamplerActive = isHiresResamp,
                isCodecIdlePopGuardActive = isPopGuard,
                isSpatializerBoostActive = isSpatialBoost,
                serverStatus = serverStatus,
                isPhhGsiDetected = isPhhGsi,
                hasPhhPropHandler = hasPhhHandler,
                isTrebleEnabled = isTreble,
                romTypeLabel = romTypeLabel,
                socVendor = socVendor,
                oemBrand = oemBrand,
                audioHalServiceLabel = audioHalSvc,
                btAudioHalServiceLabel = btHalSvc,
                detectedAudioEffectsLabel = sfxChain,
                hasAsocPmdownNode = hasAsocPm,
                hasVolumeListenerLib = hasVolListener,
                hasBesLoudnessSupport = hasBesLoudness,
                hasAptxEncoderSupport = hasAptx,
                hasLeAudioSupport = hasLeAudio,
                a2dpOffloadDisabledLive = a2dpDisLive,
                systemBtAudioHalLive = sysBtHalLive,
                absoluteVolumeDisabledLive = absVolDisLive,
                ldacQualityModeLive = ldacModeLive,
                sbcDualChannelHdLive = sbcHdLive,
                aptxAdaptiveLive = aptxLlLive,
                leAudioLc3Live = leLc3Live,
                btHfpHdVoiceNrecBypassLive = hfpHdLive,
                avrcpVersionLive = avrcpVerLive,
                usbRoutingDisabledLive = usbDisLive,
                phhDisableAudioEffectsLive = phhSfxLive,
                phhCafAudioPolicyLive = phhCafLive,
                phhDisableVolumeListenerLive = phhVolLive,
                phhDisableVoiceCallInLive = phhVcallLive,
                phhAllowIncomingCallBinderLive = phhBinderLive,
                qcomFluenceBypassLive = fluenceBypLive,
                vendorBesLoudnessLive = besLoudLive,
                fineVolumeSteps30Live = volSteps30Live,
                discoveredVendorAudioProps = scannedProps,
            )
        } catch (_: Exception) {
            AudioTelemetryInfo()
        }
    }

    suspend fun applyAudioMmap(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                if [ "$enabled" = "true" ]; then
                    apply_prop aaudio.mmap_policy 3
                    apply_prop aaudio.mmap_exclusive_policy 3
                    apply_prop aaudio.hw_burst_min_usec 2000
                    [ "$(getprop aaudio.mmap_policy 2>/dev/null)" = "3" ] && echo ok
                else
                    DEF_MMAP="${'$'}{ORIG_MMAP:-2}"
                    DEF_EXCL="${'$'}{ORIG_MMAP_EXCL:-2}"
                    DEF_BURST="${'$'}{ORIG_BURST:-4000}"
                    [ "${'$'}DEF_MMAP" = "3" ] && DEF_MMAP="2"
                    apply_prop aaudio.mmap_policy "${'$'}DEF_MMAP"
                    apply_prop aaudio.mmap_exclusive_policy "${'$'}DEF_EXCL"
                    apply_prop aaudio.hw_burst_min_usec "${'$'}DEF_BURST"
                    [ "$(getprop aaudio.mmap_policy 2>/dev/null)" != "3" ] && echo ok
                fi
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioDeepBufferBypass(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                if [ "$enabled" = "true" ]; then
                    apply_prop audio.deep_buffer.media false
                    apply_prop vendor.audio.deep_buffer.media false
                    apply_prop persist.sys.phh.disable_fast_audio 0
                    [ "$(getprop audio.deep_buffer.media 2>/dev/null)" = "false" ] && echo ok
                else
                    DEF_DEEP="${'$'}{ORIG_DEEP:-true}"
                    [ "${'$'}DEF_DEEP" = "false" ] && DEF_DEEP="true"
                    apply_prop audio.deep_buffer.media "${'$'}DEF_DEEP"
                    apply_prop vendor.audio.deep_buffer.media "${'$'}DEF_DEEP"
                    [ "$(getprop audio.deep_buffer.media 2>/dev/null)" != "false" ] && echo ok
                fi
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioDirectDacOffload(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                if [ "$enabled" = "true" ]; then
                    apply_prop vendor.audio.offload.buffer.size.kb 32
                    apply_prop audio.offload.buffer.size.kb 32
                    apply_prop af.fast_track_multiplier 1
                    [ "$(getprop vendor.audio.offload.buffer.size.kb 2>/dev/null)" = "32" ] && echo ok
                else
                    DEF_KB="${'$'}{ORIG_OFFLOAD_KB:-64}"
                    DEF_MULT="${'$'}{ORIG_FAST_MULT:-2}"
                    [ "${'$'}DEF_KB" = "32" ] && DEF_KB="64"
                    apply_prop vendor.audio.offload.buffer.size.kb "${'$'}DEF_KB"
                    apply_prop audio.offload.buffer.size.kb "${'$'}DEF_KB"
                    apply_prop af.fast_track_multiplier "${'$'}DEF_MULT"
                    [ "$(getprop vendor.audio.offload.buffer.size.kb 2>/dev/null)" != "32" ] && echo ok
                fi
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioBtCrackleGuard(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = """
                ${setPropOrResetPropScript()}
                if [ "$enabled" = "true" ]; then
                    apply_prop lynx.audio.bt_crackle_guard 1
                    apply_prop persist.vendor.bt.a2dp.hal.implementation true
                    apply_prop vendor.bluetooth.ldac.abr true
                    for pid in $(pidof audioserver com.android.bluetooth android.hardware.audio.service 2>/dev/null); do
                        renice -n -16 -p "${'$'}pid" 2>/dev/null || true
                    done
                    [ "$(getprop lynx.audio.bt_crackle_guard 2>/dev/null)" = "1" ] && echo ok
                else
                    apply_prop lynx.audio.bt_crackle_guard 0
                    for pid in $(pidof com.android.bluetooth 2>/dev/null); do
                        renice -n 0 -p "${'$'}pid" 2>/dev/null || true
                    done
                    [ "$(getprop lynx.audio.bt_crackle_guard 2>/dev/null)" = "0" ] && echo ok
                fi
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioA2dpHwOffloadDisabled(disabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val boolStr = if (disabled) "true" else "false"
            val supStr = if (disabled) "false" else "true"
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop persist.bluetooth.a2dp_offload.disabled $boolStr
                apply_prop ro.bluetooth.a2dp_offload.supported $supStr
                [ "$(getprop persist.bluetooth.a2dp_offload.disabled 2>/dev/null)" = "$boolStr" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioSystemBtAudioHal(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val boolStr = if (enabled) "true" else "false"
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop persist.bluetooth.system_audio_hal.enabled $boolStr
                if [ -x /system/bin/phh-prop-handler.sh ]; then
                    /system/bin/phh-prop-handler.sh persist.bluetooth.system_audio_hal.enabled >/dev/null 2>&1 || true
                else
                    if [ "$enabled" = "true" ]; then
                        apply_prop persist.bluetooth.bluetooth_audio_hal.disabled false
                        apply_prop persist.bluetooth.a2dp_offload.disabled true
                        apply_prop ro.bluetooth.a2dp_offload.supported false
                    else
                        apply_prop persist.bluetooth.a2dp_offload.disabled "${'$'}{ORIG_A2DP_OFFLOAD_DIS:-false}"
                    fi
                fi
                [ "$(getprop persist.bluetooth.system_audio_hal.enabled 2>/dev/null)" = "$boolStr" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioDisableAbsoluteVolume(disabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val boolStr = if (disabled) "true" else "false"
            val intStr = if (disabled) "1" else "0"
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop persist.bluetooth.disableabsvol $boolStr
                settings put global bluetooth_disable_absolute_volume $intStr 2>/dev/null || true
                [ "$(getprop persist.bluetooth.disableabsvol 2>/dev/null)" = "$boolStr" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioLdacQualityMode(mode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val safeMode = when (mode.lowercase()) {
                "hires_990" -> "hires_990"
                "balanced" -> "balanced"
                else -> "adaptive"
            }
            val abrStr = if (safeMode == "hires_990") "false" else "true"
            val bitpool = when (safeMode) {
                "hires_990" -> "990"
                "balanced" -> "660"
                else -> "auto"
            }
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop persist.lynx.audio.ldac_mode $safeMode
                apply_prop vendor.bluetooth.ldac.abr $abrStr
                apply_prop persist.bluetooth.ldac.abr $abrStr
                apply_prop persist.vendor.bt.a2dp.ldac_bitrate $bitpool
                [ "$(getprop persist.lynx.audio.ldac_mode 2>/dev/null)" = "$safeMode" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioSbcDualChannelHd(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (enabled) "1" else "0"
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop persist.bluetooth.sbc_hd_higher_bitrate $v
                apply_prop persist.sys.phh.sbc_hd $v
                [ "$(getprop persist.bluetooth.sbc_hd_higher_bitrate 2>/dev/null)" = "$v" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioAvrcpVersion(version: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val safeVer = when (version.lowercase()) {
                "avrcp14" -> "avrcp14"
                "avrcp15" -> "avrcp15"
                "avrcp16" -> "avrcp16"
                else -> "default"
            }
            val propVal = if (safeVer == "default") "avrcp15" else safeVer
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop persist.bluetooth.avrcpversion $propVal
                [ "$(getprop persist.bluetooth.avrcpversion 2>/dev/null)" = "$propVal" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioDisableUsbRouting(disabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (disabled) "1" else "0"
            val cmd = """
                settings put secure usb_audio_automatic_routing_disabled $v 2>/dev/null
                [ "$(settings get secure usb_audio_automatic_routing_disabled 2>/dev/null)" = "$v" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioPhhDisableEffects(disabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (disabled) "1" else "0"
            val ignoreBool = if (disabled) "true" else "false"
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop persist.sys.phh.disable_audio_effects $v
                if [ -x /system/bin/phh-prop-handler.sh ]; then
                    /system/bin/phh-prop-handler.sh persist.sys.phh.disable_audio_effects >/dev/null 2>&1 || true
                else
                    apply_prop ro.audio.ignore_effects $ignoreBool
                    apply_prop audio.offload.disable $v
                fi
                [ "$(getprop persist.sys.phh.disable_audio_effects 2>/dev/null)" = "$v" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioPhhCafPolicy(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (enabled) "1" else "0"
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop persist.sys.phh.caf.audio_policy $v
                if [ -x /system/bin/phh-prop-handler.sh ]; then
                    /system/bin/phh-prop-handler.sh persist.sys.phh.caf.audio_policy >/dev/null 2>&1 || true
                fi
                [ "$(getprop persist.sys.phh.caf.audio_policy 2>/dev/null)" = "$v" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioPhhDisableVolumeListener(disabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (disabled) "1" else "0"
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop persist.sys.phh.disable_soundvolume_effect $v
                if [ -x /system/bin/phh-prop-handler.sh ]; then
                    /system/bin/phh-prop-handler.sh persist.sys.phh.disable_soundvolume_effect >/dev/null 2>&1 || true
                fi
                [ "$(getprop persist.sys.phh.disable_soundvolume_effect 2>/dev/null)" = "$v" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioPhhVoiceCallRouteFix(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val boolStr = if (enabled) "true" else "false"
            val fluenceStr = if (enabled) "false" else "true"
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop persist.sys.phh.disable_voice_call_in $boolStr
                apply_prop persist.vendor.audio.fluence.voicecall $fluenceStr
                apply_prop ro.telephony.call_ring.multiple false
                [ "$(getprop persist.sys.phh.disable_voice_call_in 2>/dev/null)" = "$boolStr" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioVendorBesLoudness(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (enabled) "1" else "0"
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop persist.vendor.audiohal.besloudness_state $v
                [ "$(getprop persist.vendor.audiohal.besloudness_state 2>/dev/null)" = "$v" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioHiresResampler24Bit(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val q = if (enabled) "7" else "4"
            val pcm24 = if (enabled) "true" else "false"
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop af.resampler.quality $q
                apply_prop audio.offload.pcm.24bit.enable $pcm24
                apply_prop vendor.audio.offload.pcm.24bit.enable $pcm24
                [ "$(getprop af.resampler.quality 2>/dev/null)" = "$q" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioCodecIdlePopGuard(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (enabled) "1" else "0"
            val pmdown = if (enabled) "0" else "5000"
            val standbyMs = if (enabled) "15000" else "3000"
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop lynx.audio.codec_pop_guard $v
                apply_prop ro.audio.flinger_standbytime_ms $standbyMs
                for n in /sys/module/snd_soc*/parameters/pmdown_time; do
                    [ -w "${'$'}n" ] && echo "$pmdown" > "${'$'}n" 2>/dev/null || true
                done
                for n in /sys/module/snd_*/parameters/power_save; do
                    [ -w "${'$'}n" ] && echo "0" > "${'$'}n" 2>/dev/null || true
                done
                [ "$(getprop lynx.audio.codec_pop_guard 2>/dev/null)" = "$v" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioSpatializerBoost(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (enabled) "1" else "0"
            val boolStr = if (enabled) "true" else "false"
            val uclamp = if (enabled) "300" else "0"
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop persist.lynx.audio.spatializer_boost $v
                apply_prop ro.audio.spatializer_enabled $boolStr
                apply_prop audio.spatializer.effect.util_clamp_min $uclamp
                [ "$(getprop persist.lynx.audio.spatializer_boost 2>/dev/null)" = "$v" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioAptxAdaptiveLowLatency(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (enabled) "1" else "0"
            val boolStr = if (enabled) "true" else "false"
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop persist.lynx.audio.aptx_adaptive_ll $v
                apply_prop persist.vendor.qcom.bluetooth.aptxadaptiver2_2_support $boolStr
                apply_prop persist.vendor.qcom.bluetooth.twsp_state.enabled $boolStr
                [ "$(getprop persist.lynx.audio.aptx_adaptive_ll 2>/dev/null)" = "$v" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioLeAudioLc3(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (enabled) "1" else "0"
            val boolStr = if (enabled) "true" else "false"
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop persist.lynx.audio.le_audio_lc3 $v
                apply_prop bluetooth.profile.bap.unicast.client.enabled $boolStr
                apply_prop persist.bluetooth.leaudio.notify.idle.during.call $boolStr
                [ "$(getprop persist.lynx.audio.le_audio_lc3 2>/dev/null)" = "$v" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioBtHfpHdVoiceNrecBypass(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (enabled) "1" else "0"
            val nrec = if (enabled) "0" else "1"
            val swb = if (enabled) "true" else "false"
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop persist.lynx.audio.hfp_hd_nrec_bypass $v
                apply_prop persist.vendor.audiohal.bt_headset_nrec_on $nrec
                apply_prop bluetooth.hfp.swb.supported $swb
                [ "$(getprop persist.lynx.audio.hfp_hd_nrec_bypass 2>/dev/null)" = "$v" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioPhhIncomingCallBinder(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (enabled) "1" else "0"
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop persist.sys.phh.allow_binder_thread_on_incoming_calls $v
                if [ -x /system/bin/phh-prop-handler.sh ]; then
                    /system/bin/phh-prop-handler.sh persist.sys.phh.allow_binder_thread_on_incoming_calls >/dev/null 2>&1 || true
                else
                    if [ "$enabled" = "true" ]; then
                        apply_prop ro.telephony.block_binder_thread_on_incoming_calls false
                    fi
                fi
                [ "$(getprop persist.sys.phh.allow_binder_thread_on_incoming_calls 2>/dev/null)" = "$v" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioQcomFluenceBypass(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (enabled) "1" else "0"
            val ftype = if (enabled) "none" else "fluence"
            val fbool = if (enabled) "false" else "true"
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop persist.lynx.audio.fluence_bypass $v
                apply_prop ro.qc.sdk.audio.fluencetype $ftype
                apply_prop persist.vendor.audio.fluence.voicecall $fbool
                apply_prop persist.vendor.audio.fluence.voicerec $fbool
                [ "$(getprop persist.lynx.audio.fluence_bypass 2>/dev/null)" = "$v" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun applyAudioFineVolumeSteps30(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val steps = if (enabled) "30" else "15"
            val vcSteps = if (enabled) "10" else "7"
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop ro.config.media_vol_steps $steps
                apply_prop ro.config.vc_call_vol_steps $vcSteps
                [ "$(getprop ro.config.media_vol_steps 2>/dev/null)" = "$steps" ] && echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun reloadAudioServerAndHal(): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = """
                setprop ctl.restart audioserver 2>/dev/null
                for hal in $(getprop | sed -nE 's/.*init\.svc\.(.*audio-hal[^]]*).*/\1/p'); do
                    setprop ctl.restart "${'$'}hal" 2>/dev/null || true
                done
                setprop ctl.restart vendor.audio-hal-2-0 2>/dev/null || true
                setprop ctl.restart audio-hal-2-0 2>/dev/null || true
                sleep 1
                [ "$(getprop init.svc.audioserver 2>/dev/null)" = "running" ] && echo ok || echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }

    suspend fun restoreAudioOemBaseline(): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = """
                ${ensureAudioBaselineScript()}
                ${setPropOrResetPropScript()}
                apply_prop aaudio.mmap_policy "${'$'}{ORIG_MMAP:-2}"
                apply_prop aaudio.mmap_exclusive_policy "${'$'}{ORIG_MMAP_EXCL:-2}"
                apply_prop aaudio.hw_burst_min_usec "${'$'}{ORIG_BURST:-4000}"
                apply_prop audio.deep_buffer.media "${'$'}{ORIG_DEEP:-true}"
                apply_prop vendor.audio.offload.buffer.size.kb "${'$'}{ORIG_OFFLOAD_KB:-64}"
                apply_prop af.fast_track_multiplier "${'$'}{ORIG_FAST_MULT:-2}"
                apply_prop lynx.audio.bt_crackle_guard 0
                [ -n "${'$'}ORIG_A2DP_OFFLOAD_DIS" ] && apply_prop persist.bluetooth.a2dp_offload.disabled "${'$'}ORIG_A2DP_OFFLOAD_DIS"
                [ -n "${'$'}ORIG_SYS_BT_HAL" ] && apply_prop persist.bluetooth.system_audio_hal.enabled "${'$'}ORIG_SYS_BT_HAL"
                [ -n "${'$'}ORIG_ABSVOL_DIS" ] && apply_prop persist.bluetooth.disableabsvol "${'$'}ORIG_ABSVOL_DIS"
                [ -n "${'$'}ORIG_AVRCP_VER" ] && apply_prop persist.bluetooth.avrcpversion "${'$'}ORIG_AVRCP_VER"
                [ -n "${'$'}ORIG_LDAC_ABR" ] && apply_prop vendor.bluetooth.ldac.abr "${'$'}ORIG_LDAC_ABR"
                [ -n "${'$'}ORIG_SBC_HD" ] && apply_prop persist.bluetooth.sbc_hd_higher_bitrate "${'$'}ORIG_SBC_HD"
                [ -n "${'$'}ORIG_PHH_SFX" ] && apply_prop persist.sys.phh.disable_audio_effects "${'$'}ORIG_PHH_SFX"
                [ -n "${'$'}ORIG_PHH_CAF" ] && apply_prop persist.sys.phh.caf.audio_policy "${'$'}ORIG_PHH_CAF"
                [ -n "${'$'}ORIG_PHH_VOLLIST" ] && apply_prop persist.sys.phh.disable_soundvolume_effect "${'$'}ORIG_PHH_VOLLIST"
                [ -n "${'$'}ORIG_PHH_VCALL" ] && apply_prop persist.sys.phh.disable_voice_call_in "${'$'}ORIG_PHH_VCALL"
                [ -n "${'$'}ORIG_BESLOUD" ] && apply_prop persist.vendor.audiohal.besloudness_state "${'$'}ORIG_BESLOUD"
                settings put global bluetooth_disable_absolute_volume 0 2>/dev/null || true
                settings put secure usb_audio_automatic_routing_disabled 0 2>/dev/null || true
                echo ok
            """.trimIndent()
            Shell.cmd(cmd).exec().out.any { it.trim() == "ok" }
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

    // ============================================================
    //  PURE ANDROID OS, FRAMEWORK UI & GSI TREBLE CONTROLS
    // ============================================================

    suspend fun readSystemFrameworkSummary(): SystemFrameworkSummary = withContext(Dispatchers.IO) {
        try {
            val androidVer = android.os.Build.VERSION.RELEASE ?: "Unknown"
            val apiLevel = android.os.Build.VERSION.SDK_INT
            val securityPatch = try { android.os.Build.VERSION.SECURITY_PATCH ?: "" } catch (_: Exception) { "" }

            val flavor = Shell.cmd("getprop ro.system.build.flavor 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            val incr = Shell.cmd("getprop ro.build.version.incremental 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            val treble = Shell.cmd("getprop ro.treble.enabled 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""

            val isGsi = flavor.contains("gsi", ignoreCase = true) ||
                    incr.contains("phh", ignoreCase = true) ||
                    flavor.contains("aosp", ignoreCase = true)

            val framework = when {
                isGsi -> "Android GSI / Treble (PHH AOSP)"
                treble == "true" -> "Project Treble Framework"
                else -> "Vendor Stock / OEM Framework"
            }

            val selinux = readSelinuxMode()

            val verity = Shell.cmd("getprop ro.boot.veritymode 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            val protection = if (verity.isNotBlank()) "dm-verity ($verity)" else "dm-verity Enforced"

            SystemFrameworkSummary(
                androidVersion = androidVer,
                apiLevel = apiLevel,
                securityPatch = securityPatch,
                frameworkType = framework,
                isGsiTreble = isGsi,
                selinuxMode = selinux,
                partitionProtection = protection,
                isLoading = false
            )
        } catch (e: Exception) {
            SystemFrameworkSummary(
                androidVersion = android.os.Build.VERSION.RELEASE ?: "",
                apiLevel = android.os.Build.VERSION.SDK_INT,
                frameworkType = "Android System",
                isLoading = false
            )
        }
    }

    suspend fun readAnimationScale(): Float = withContext(Dispatchers.IO) {
        try {
            val out = Shell.cmd("settings get global window_animation_scale 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "1.0"
            out.toFloatOrNull() ?: 1.0f
        } catch (_: Exception) { 1.0f }
    }

    suspend fun applyAnimationScale(scale: Float): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.cmd(
                "settings put global window_animation_scale $scale 2>/dev/null",
                "settings put global transition_animation_scale $scale 2>/dev/null",
                "settings put global animator_duration_scale $scale 2>/dev/null"
            ).exec().isSuccess
        } catch (_: Exception) { false }
    }

    suspend fun readDisableWindowBlurs(): Boolean = withContext(Dispatchers.IO) {
        try {
            val sfBlur = Shell.cmd("getprop persist.sys.sf.disable_blurs 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            val globalBlur = Shell.cmd("settings get global disable_window_blurs 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            sfBlur == "1" || globalBlur == "1"
        } catch (_: Exception) { false }
    }

    suspend fun applyDisableWindowBlurs(disabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (disabled) "1" else "0"
            Shell.cmd(
                "setprop persist.sys.sf.disable_blurs $v 2>/dev/null",
                "settings put global disable_window_blurs $v 2>/dev/null",
                "wm disable-blur $v 2>/dev/null || true"
            ).exec().isSuccess
        } catch (_: Exception) { false }
    }

    suspend fun readDisablePhantomProcesses(): Boolean = withContext(Dispatchers.IO) {
        try {
            val maxPhantom = Shell.cmd("device_config get activity_manager max_phantom_processes 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            val prop = Shell.cmd("getprop persist.sys.fflag.override.settings_enable_monitor_phantom_procs 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            maxPhantom == "2147483647" || prop == "false"
        } catch (_: Exception) { false }
    }

    suspend fun applyDisablePhantomProcesses(disabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            if (disabled) {
                Shell.cmd(
                    "device_config set_sync_disabled_for_tests persistent 2>/dev/null",
                    "device_config put activity_manager max_phantom_processes 2147483647 2>/dev/null",
                    "setprop persist.sys.fflag.override.settings_enable_monitor_phantom_procs false 2>/dev/null"
                ).exec().isSuccess
            } else {
                Shell.cmd(
                    "device_config put activity_manager max_phantom_processes 32 2>/dev/null",
                    "setprop persist.sys.fflag.override.settings_enable_monitor_phantom_procs true 2>/dev/null"
                ).exec().isSuccess
            }
        } catch (_: Exception) { false }
    }

    suspend fun readQemuMainkeys(): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = Shell.cmd("getprop qemu.hw.mainkeys 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            val pv = Shell.cmd("getprop persist.sys.qemu.hw.mainkeys 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
            v == "0" || pv == "0"
        } catch (_: Exception) { false }
    }

    suspend fun applyQemuMainkeys(showNavbar: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val v = if (showNavbar) "0" else "1"
            Shell.cmd(
                "setprop qemu.hw.mainkeys $v 2>/dev/null",
                "setprop persist.sys.qemu.hw.mainkeys $v 2>/dev/null"
            ).exec().isSuccess
        } catch (_: Exception) { false }
    }

    suspend fun executeArtSpeedProfileDexopt(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val res = Shell.cmd("cmd package compile -m speed-profile -a 2>&1").exec()
            val duration = (System.currentTimeMillis() - startTime) / 1000
            if (res.isSuccess) {
                Pair(true, "Kompilasi ART Speed-Profile selesai dalam ${duration} detik.")
            } else {
                val err = res.out.joinToString("\n").ifBlank { "Gagal menjalankan dexopt." }
                Pair(false, err)
            }
        } catch (e: Exception) {
            Pair(false, "Gagal menjalankan dexopt: ${e.message}")
        }
    }

    suspend fun triggerDozeDeepSleep(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val res = Shell.cmd("dumpsys deviceidle force-idle 2>&1").exec()
            if (res.isSuccess) {
                Pair(true, "Sistem dipaksa masuk ke status Doze Deep Sleep.")
            } else {
                Pair(false, res.out.joinToString("\n").ifBlank { "Gagal memicu Doze Deep Sleep." })
            }
        } catch (e: Exception) {
            Pair(false, "Gagal memicu Doze: ${e.message}")
        }
    }

    suspend fun restoreDozeTimer(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val res = Shell.cmd("dumpsys deviceidle unforce 2>&1").exec()
            if (res.isSuccess) {
                Pair(true, "Status Doze dipulihkan ke siklus timer normal.")
            } else {
                Pair(false, res.out.joinToString("\n").ifBlank { "Gagal memulihkan status Doze." })
            }
        } catch (e: Exception) {
            Pair(false, "Gagal memulihkan Doze: ${e.message}")
        }
    }
}
