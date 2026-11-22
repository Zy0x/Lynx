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

                // 2. Hardware radio & settings tuning unified via lynxd Single Source of Truth
                val standbyVal = if (mutableCellularStandby.value) "on" else "off"
                val bin = com.noir.lynx.data.LynxSystemControlRepository.resolveNetworkBinary()
                if (bin != null) {
                    val presetCmd = if (mutableTcpTuning.value) {
                        "'$bin' network preset gaming >/dev/null 2>&1"
                    } else {
                        "'$bin' network feature low_latency on >/dev/null 2>&1; '$bin' network feature scan_guard on >/dev/null 2>&1"
                    }
                    val fullCmd = "$presetCmd; '$bin' network feature mobile_standby $standbyVal >/dev/null 2>&1"
                    Shell.cmd(fullCmd).submit()
                } else {
                    val standbyCmd = "settings put global mobile_data_always_on ${if (mutableCellularStandby.value) 1 else 0} 2>/dev/null"
                    Shell.cmd("cmd wifi force-low-latency-mode enabled 2>/dev/null; settings put global wifi_scan_always_enabled 0 2>/dev/null; $standbyCmd").submit()
                }
            } else {
                runCatching {
                    if (wifiLock?.isHeld == true) {
                        wifiLock?.release()
                    }
                    wifiLock = null
                }

                val bin = com.noir.lynx.data.LynxSystemControlRepository.resolveNetworkBinary()
                if (bin != null) {
                    Shell.cmd("'$bin' network preset oem >/dev/null 2>&1").submit()
                } else {
                    Shell.cmd("cmd wifi force-low-latency-mode disabled 2>/dev/null; settings put global wifi_scan_always_enabled 1 2>/dev/null").submit()
                }
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
