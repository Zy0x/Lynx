package com.noir.lynx.feature.hud.network

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.Build
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Universal Dual-Channel Network Turbo Manager.
 * Orchestrates Wi-Fi low-latency lock, radio power save disable, background scan throttle,
 * cellular hot-standby failover, and Linux kernel TCP stack anti-bufferbloat tuning.
 */
class HudNetworkStabilizer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val prefs: SharedPreferences
) {
    private val mutableTurbo = MutableStateFlow(prefs.getBoolean("net_turbo_enabled", false))
    val turboEnabled = mutableTurbo.asStateFlow()

    private val mutableCellularStandby = MutableStateFlow(prefs.getBoolean("net_cellular_standby", true))
    val cellularStandby = mutableCellularStandby.asStateFlow()

    private val mutableTcpTuning = MutableStateFlow(prefs.getBoolean("net_tcp_tuning", true))
    val tcpTuning = mutableTcpTuning.asStateFlow()

    private var wifiLock: WifiManager.WifiLock? = null

    init {
        if (mutableTurbo.value) {
            applyNetworkTurbo(true)
        }
    }

    fun setTurboEnabled(enabled: Boolean) {
        mutableTurbo.value = enabled
        prefs.edit().putBoolean("net_turbo_enabled", enabled).apply()
        applyNetworkTurbo(enabled)
    }

    fun setCellularStandby(enabled: Boolean) {
        mutableCellularStandby.value = enabled
        prefs.edit().putBoolean("net_cellular_standby", enabled).apply()
        if (mutableTurbo.value) {
            applyNetworkTurbo(true)
        }
    }

    fun setTcpTuning(enabled: Boolean) {
        mutableTcpTuning.value = enabled
        prefs.edit().putBoolean("net_tcp_tuning", enabled).apply()
        if (mutableTurbo.value) {
            applyNetworkTurbo(true)
        }
    }

    private fun applyNetworkTurbo(enable: Boolean) {
        scope.launch(Dispatchers.IO) {
            if (enable) {
                // 1. Android Wi-Fi Low-Latency Lock
                if (Build.VERSION.SDK_INT >= 29 && wifiLock?.isHeld != true) {
                    runCatching {
                        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
                        wifiLock = wifi?.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Lynx:NetworkTurbo")?.apply {
                            setReferenceCounted(false)
                            acquire()
                        }
                    }
                }

                // 2. Hardware radio & settings tuning
                val cellularCmd = if (mutableCellularStandby.value) {
                    "settings put global mobile_data_always_on 1 2>/dev/null"
                } else ""

                val tcpCmd = if (mutableTcpTuning.value) {
                    """
                    [ -e /proc/sys/net/ipv4/tcp_low_latency ] && echo 1 > /proc/sys/net/ipv4/tcp_low_latency 2>/dev/null
                    [ -e /proc/sys/net/ipv4/tcp_notsent_lowat ] && echo 16384 > /proc/sys/net/ipv4/tcp_notsent_lowat 2>/dev/null
                    [ -e /proc/sys/net/ipv4/tcp_fastopen ] && echo 3 > /proc/sys/net/ipv4/tcp_fastopen 2>/dev/null
                    [ -e /proc/sys/net/core/netdev_max_backlog ] && echo 10000 > /proc/sys/net/core/netdev_max_backlog 2>/dev/null
                    """.trimIndent()
                } else ""

                val script = """
                    # Disable Wi-Fi power saving on all detected wireless adapters
                    for iface in ${'$'}(ls /sys/class/net 2>/dev/null | grep -E '^wlan|^wlp|^mlan'); do
                        iw dev "${'$'}iface" set power_save off 2>/dev/null
                    done

                    # Throttle background scans during gameplay
                    settings put global wifi_scan_always_enabled 0 2>/dev/null

                    $cellularCmd
                    $tcpCmd
                """.trimIndent()

                Shell.cmd(script).submit()
            } else {
                runCatching {
                    if (wifiLock?.isHeld == true) {
                        wifiLock?.release()
                    }
                    wifiLock = null
                }

                val restoreScript = """
                    for iface in ${'$'}(ls /sys/class/net 2>/dev/null | grep -E '^wlan|^wlp|^mlan'); do
                        iw dev "${'$'}iface" set power_save on 2>/dev/null
                    done
                    settings put global wifi_scan_always_enabled 1 2>/dev/null
                    [ -e /proc/sys/net/ipv4/tcp_low_latency ] && echo 0 > /proc/sys/net/ipv4/tcp_low_latency 2>/dev/null
                    [ -e /proc/sys/net/ipv4/tcp_notsent_lowat ] && echo 4294967295 > /proc/sys/net/ipv4/tcp_notsent_lowat 2>/dev/null
                """.trimIndent()

                Shell.cmd(restoreScript).submit()
            }
        }
    }

    fun release() {
        runCatching {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
            wifiLock = null
        }
    }
}
