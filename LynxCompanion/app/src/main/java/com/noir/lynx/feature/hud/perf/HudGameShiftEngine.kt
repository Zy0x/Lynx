package com.noir.lynx.feature.hud.perf

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Universal GameShift / Heavy Mode Reactive Turbo Engine.
 * Dynamically monitors SurfaceFlinger rendering metrics for frame drops and jank spikes.
 * Instantly injects a 3.0-second hardware governor boost when high render latency is detected,
 * smoothed by an 8-second hysteresis cooldown to preserve thermal stability.
 */
class HudGameShiftEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val prefs: SharedPreferences
) {
    private val mutableEnabled = MutableStateFlow(prefs.getBoolean("perf_gameshift_enabled", false))
    val enabled = mutableEnabled.asStateFlow()

    private val mutableBurstActive = MutableStateFlow(false)
    val isBurstActive = mutableBurstActive.asStateFlow()

    private val mutableSpikeCount = MutableStateFlow(0)
    val spikeCount = mutableSpikeCount.asStateFlow()

    private var watcherJob: Job? = null
    @Volatile private var activePkg: String = ""
    @Volatile private var lastBurstTimeMs: Long = 0L

    init {
        if (mutableEnabled.value) {
            startWatcher()
        }
    }

    fun setEnabled(enable: Boolean) {
        mutableEnabled.value = enable
        prefs.edit().putBoolean("perf_gameshift_enabled", enable).apply()
        if (enable) {
            startWatcher()
        } else {
            stopWatcher()
        }
    }

    fun updateForeground(pkg: String) {
        activePkg = pkg
    }

    private fun startWatcher() {
        if (watcherJob?.isActive == true) return
        watcherJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000L)
                if (!mutableEnabled.value) break
                val currentPkg = activePkg
                if (currentPkg.isBlank() || isSystemApp(currentPkg)) continue

                val now = SystemClock.elapsedRealtime()
                // Hysteresis cooldown: at least 8 seconds between bursts
                if (now - lastBurstTimeMs < 8000L) continue

                val hasJank = checkSurfaceFlingerJank(currentPkg)
                if (hasJank) {
                    lastBurstTimeMs = now
                    mutableSpikeCount.value = mutableSpikeCount.value + 1
                    triggerEmergencyBurst()
                }
            }
        }
    }

    private fun stopWatcher() {
        watcherJob?.cancel()
        watcherJob = null
        mutableBurstActive.value = false
    }

    private fun checkSurfaceFlingerJank(pkg: String): Boolean {
        // Query recent jank from dumpsys gfxinfo or SurfaceFlinger latency
        val res = Shell.cmd("dumpsys SurfaceFlinger --latency '$pkg' 2>/dev/null | tail -n 15").exec()
        if (!res.isSuccess || res.out.size < 5) return false

        var dropCount = 0
        var prevStart = 0L
        for (line in res.out) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 3) {
                val start = parts[0].toLongOrNull() ?: continue
                val present = parts[1].toLongOrNull() ?: continue
                if (start > 0 && present > start) {
                    val frameDurationNs = present - start
                    // Frame took longer than 28ms (~35fps or lower on 60/120Hz display)
                    if (frameDurationNs > 28_000_000L) {
                        dropCount++
                    }
                }
            }
        }
        return dropCount >= 3
    }

    private fun triggerEmergencyBurst() {
        scope.launch(Dispatchers.IO) {
            mutableBurstActive.value = true
            try {
                // Multi-SoC Emergency Burst Script
                val burstScript = """
                    # 1. MediaTek EAS & PPM Turbo
                    [ -f /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost ] && echo 100 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost 2>/dev/null
                    [ -f /proc/ppm/policy_status ] && echo "0 3" > /proc/ppm/policy_status 2>/dev/null
                    [ -f /sys/module/ged/parameters/gx_force_cpu_boost ] && echo 1 > /sys/module/ged/parameters/gx_force_cpu_boost 2>/dev/null
                    
                    # 2. Qualcomm Sched Boost & Devfreq GPU
                    [ -f /sys/module/cpu_boost/parameters/sched_boost_on_input ] && echo 1 > /sys/module/cpu_boost/parameters/sched_boost_on_input 2>/dev/null
                    [ -f /proc/sys/kernel/sched_boost ] && echo 1 > /proc/sys/kernel/sched_boost 2>/dev/null
                    
                    # 3. CPUFreq governor up_rate_limit zero
                    for p in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/policy*/walt; do
                        [ -d "${'$'}p" ] && echo 0 > "${'$'}p/up_rate_limit_us" 2>/dev/null
                    done
                """.trimIndent()
                Shell.cmd(burstScript).submit()

                // Sustain emergency boost for 3.0 seconds
                delay(3000L)

                // Restore to balanced profile
                val restoreScript = """
                    [ -f /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost ] && echo 0 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost 2>/dev/null
                    [ -f /sys/module/ged/parameters/gx_force_cpu_boost ] && echo 0 > /sys/module/ged/parameters/gx_force_cpu_boost 2>/dev/null
                    [ -f /proc/sys/kernel/sched_boost ] && echo 0 > /proc/sys/kernel/sched_boost 2>/dev/null
                    for p in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/policy*/walt; do
                        [ -d "${'$'}p" ] && echo 1000 > "${'$'}p/up_rate_limit_us" 2>/dev/null
                    done
                """.trimIndent()
                Shell.cmd(restoreScript).submit()
            } finally {
                mutableBurstActive.value = false
            }
        }
    }

    private fun isSystemApp(pkg: String): Boolean =
        pkg in setOf("com.android.systemui", "com.android.settings", "me.bmax.apatch", "me.weishu.kernelsu", "com.topjohnwu.magisk")

    fun release() {
        stopWatcher()
    }
}
