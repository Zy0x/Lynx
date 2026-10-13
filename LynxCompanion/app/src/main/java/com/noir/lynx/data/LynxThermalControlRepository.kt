package com.noir.lynx.data
import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
internal object LynxThermalControlRepository {
    suspend fun applyThermalBypass(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        applyThermalMode(if (enabled) "hardware_safety_dominant" else "stable")
    }

    suspend fun applyThermalMode(mode: String, customTempLimit: Int = 52): Boolean = withContext(Dispatchers.IO) {
        try {
            val lynxdPath = "$MODULE_DIR/system/bin/lynxd"
            if (java.io.File(lynxdPath).canExecute()) {
                val rustMode = when (mode.lowercase()) {
                    "default", "default_oem" -> "conservative"
                    "hardware_safety_dominant", "unrestricted", "dominant" -> "gaming"
                    "stable", "thermal_stable" -> "stable"
                    "extreme", "bypass" -> "extreme"
                    else -> mode.lowercase()
                }
                val res = Shell.cmd("$lynxdPath thermal mode $rustMode").exec()
                if (res.isSuccess) return@withContext true
            }
            com.noir.lynx.safety.OEMRestoreManager.captureBaselineIfMissing()

            when (mode.lowercase()) {
                "default", "default_oem" -> {
                    com.noir.lynx.safety.OEMRestoreManager.restoreCapturedBaseline()
                }
                "hardware_safety_dominant", "unrestricted", "dominant" -> {
                    applyHardwareSafetyDominantEngine()
                }
                "stable", "thermal_stable", "performance" -> {
                    applyThermalStableEngine(customTempLimit)
                }
                else -> {
                    applyThermalStableEngine(customTempLimit)
                }
            }
        } catch (_: Exception) { false }
    }

    private suspend fun applyThermalStableEngine(customTempLimit: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetWrites = mutableListOf<Pair<String, String>>()
            val caps = com.noir.lynx.hardware.ThermalCapabilityDetector.detect()

            // 1. Restore Lowest OPP Floor for each CPU policy to ensure true cool idle (<400mA)
            val oppScript = """
                for p in /sys/devices/system/cpu/cpufreq/policy*; do
                    [ -d "${'$'}p" ] || continue
                    freqs=${'$'}(cat "${'$'}p/scaling_available_frequencies" 2>/dev/null)
                    if [ -n "${'$'}freqs" ]; then
                        min_f=${'$'}(echo "${'$'}freqs" | tr ' ' '\n' | sort -n | head -n 1)
                        [ -n "${'$'}min_f" ] && echo "OPP|${'$'}p/scaling_min_freq|${'$'}min_f"
                    fi
                    # Schedutil rate limits (fast ramp up for multitasking, smooth downclock for low heat)
                    [ -d "${'$'}p/schedutil" ] && echo "SCHED|${'$'}p/schedutil/up_rate_limit_us|500"
                    [ -d "${'$'}p/schedutil" ] && echo "SCHED|${'$'}p/schedutil/down_rate_limit_us|10000"
                done
            """.trimIndent()

            val oppLines = Shell.cmd(oppScript).exec().out
            for (line in oppLines) {
                val parts = line.split("|")
                if (parts.size >= 3) {
                    targetWrites.add(parts[1].trim() to parts[2].trim())
                }
            }

            // 2. Calibrate Writable Trip Points to 52°C - 55°C
            val targetTripMilliC = (customTempLimit * 1000).toString()
            for (z in caps.zones) {
                if (z.isModeWritable) {
                    targetWrites.add("/sys/class/thermal/thermal_zone${z.id}/mode" to "enabled")
                }
                for (tp in z.tripPoints) {
                    if (tp.isWritable) {
                        targetWrites.add(tp.path to targetTripMilliC)
                    }
                }
            }

            // 3. Vendor Optimizations for Thermal Stable
            if (caps.socVendor == "qcom") {
                if (caps.hasSconfig) {
                    targetWrites.add("/sys/class/thermal/thermal_message/sconfig" to "9")
                }
                targetWrites.add("/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel" to "1")
            } else if (caps.socVendor == "mediatek") {
                targetWrites.add("/proc/perfmgr/syslimiter/syslimiter_force_disable" to "1")
                if (caps.hasClatm) {
                    targetWrites.add("/proc/driver/thermal/clatm_gpu_threshold" to "85 80")
                }
            }

            // Execute Transaction
            val txRes = com.noir.lynx.safety.ThermalTransaction.execute("THERMAL_STABLE", targetWrites)
            
            // Framework and Daemon neutralizing (safe in background)
            Shell.cmd("cmd thermalservice override-status 0 2>/dev/null").exec()
            if (caps.hasJoyose) Shell.cmd("killall -STOP com.xiaomi.joyose 2>/dev/null").exec()
            if (caps.hasGos) Shell.cmd("killall -STOP com.samsung.android.game.gos 2>/dev/null").exec()

            txRes is com.noir.lynx.safety.ThermalTransactionResult.Success
        } catch (_: Exception) { false }
    }

    private suspend fun applyHardwareSafetyDominantEngine(): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetWrites = mutableListOf<Pair<String, String>>()
            val caps = com.noir.lynx.hardware.ThermalCapabilityDetector.detect()

            // 1. Elevate writable trip points to Hardware Safety Dominant boundary (75°C - 80°C)
            // Preserving physical silicon emergency shutdown at 95°C-105°C
            for (z in caps.zones) {
                if (z.isPolicyWritable) {
                    targetWrites.add("/sys/class/thermal/thermal_zone${z.id}/policy" to "user_space")
                }
                for (tp in z.tripPoints) {
                    if (tp.isWritable) {
                        targetWrites.add(tp.path to "80000")
                    }
                }
            }

            // 2. Vendor Specific Hardware Safety Dominant
            if (caps.socVendor == "qcom") {
                targetWrites.add("/sys/class/kgsl/kgsl-3d0/throttling" to "0")
                targetWrites.add("/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel" to "0")
                if (caps.hasMsmThermal) {
                    targetWrites.add("/sys/module/msm_thermal/parameters/enabled" to "0")
                    targetWrites.add("/sys/module/msm_thermal/core_control/enabled" to "0")
                }
                if (caps.hasSconfig) {
                    targetWrites.add("/sys/class/thermal/thermal_message/sconfig" to "10")
                }
            } else if (caps.socVendor == "mediatek") {
                targetWrites.add("/proc/cpufreq/cpufreq_imax_thermal_protect" to "0")
                if (caps.hasPpm) {
                    targetWrites.add("/proc/ppm/policy_status" to "3 0")
                    targetWrites.add("/proc/ppm/policy_status" to "4 0")
                    targetWrites.add("/proc/ppm/policy_status" to "5 0")
                }
                if (caps.hasEara) {
                    targetWrites.add("/sys/kernel/eara_thermal/enable" to "0")
                    targetWrites.add("/sys/kernel/eara_thermal/fake_throttle" to "0")
                }
                if (caps.hasClatm) {
                    targetWrites.add("/proc/driver/thermal/clatm_gpu_threshold" to "150 149")
                }
            }

            // Execute Transaction
            val txRes = com.noir.lynx.safety.ThermalTransaction.execute("HARDWARE_SAFETY_DOMINANT", targetWrites)

            // Stop/freeze user-space throttling daemons without killing hardware watchdog
            val daemonCmd = """
                killall -STOP mi_thermald thermal-engine thermal-engine-v2 ituxd com.xiaomi.joyose com.samsung.android.game.gos 2>/dev/null
                cmd thermalservice override-status 0 2>/dev/null
            """.trimIndent()
            Shell.cmd(daemonCmd).exec()

            txRes is com.noir.lynx.safety.ThermalTransactionResult.Success
        } catch (_: Exception) { false }
    }

    suspend fun applyCustomTempLimitLive(tempC: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val lynxdPath = "$MODULE_DIR/system/bin/lynxd"
            if (java.io.File(lynxdPath).canExecute()) {
                val res = Shell.cmd("$lynxdPath thermal set-limit $tempC").exec()
                if (res.isSuccess) return@withContext true
            }
            val milliC = (tempC * 1000).toString()
            val writes = mutableListOf<Pair<String, String>>()
            val caps = com.noir.lynx.hardware.ThermalCapabilityDetector.detect()
            for (z in caps.zones) {
                for (tp in z.tripPoints) {
                    if (tp.isWritable) {
                        writes.add(tp.path to milliC)
                    }
                }
            }
            if (writes.isNotEmpty()) {
                val res = com.noir.lynx.safety.ThermalTransaction.execute("CUSTOM_TEMP_LIMIT", writes)
                res is com.noir.lynx.safety.ThermalTransactionResult.Success
            } else {
                false
            }
        } catch (_: Exception) { false }
    }

    suspend fun applyOverclock(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = if (enabled) {
                """
                for p in /sys/devices/system/cpu/cpufreq/policy*; do
                    [ -d "${'$'}p" ] || continue
                    max_f=${'$'}(cat "${'$'}p/cpuinfo_max_freq" 2>/dev/null)
                    [ -n "${'$'}max_f" ] && echo "${'$'}max_f" > "${'$'}p/scaling_max_freq" 2>/dev/null
                done
                echo ok
                """.trimIndent()
            } else {
                "echo ok"
            }
            Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
        } catch (_: Exception) { false }
    }

    suspend fun readThermalZones(): List<ThermalZoneInfo> = withContext(Dispatchers.IO) {
        try {
            val script = """
                for tz in /sys/class/thermal/thermal_zone*; do
                    [ -d "${'$'}tz" ] || continue
                    id=${'$'}(basename "${'$'}tz")
                    id=${'$'}{id#thermal_zone}
                    type=${'$'}(cat "${'$'}tz/type" 2>/dev/null)
                    temp=${'$'}(cat "${'$'}tz/temp" 2>/dev/null)
                    mode=${'$'}(cat "${'$'}tz/mode" 2>/dev/null || echo "enabled")
                    policy=${'$'}(cat "${'$'}tz/policy" 2>/dev/null || echo "step_wise")

                    mode_w=0; [ -w "${'$'}tz/mode" ] && mode_w=1
                    tp_tot=0; tp_w=0
                    for tp in "${'$'}tz"/trip_point_*_temp; do
                        [ -f "${'$'}tp" ] || continue
                        tp_tot=${'$'}((tp_tot + 1))
                        [ -w "${'$'}tp" ] && tp_w=${'$'}((tp_w + 1))
                    done

                    is_w=0
                    if [ "${'$'}mode_w" = "1" ] || [ "${'$'}tp_w" -gt 0 ]; then is_w=1; fi

                    [ -n "${'$'}type" ] && [ -n "${'$'}temp" ] && echo "${'$'}id|${'$'}type|${'$'}temp|${'$'}is_w|${'$'}tp_tot|${'$'}tp_w|${'$'}mode|${'$'}policy"
                done
            """.trimIndent()
            val r = Shell.cmd(script).exec()
            val list = mutableListOf<ThermalZoneInfo>()
            for (line in r.out) {
                val parts = line.split("|")
                if (parts.size >= 3) {
                    val id = parts[0].toIntOrNull() ?: continue
                    val type = parts[1].trim()
                    val rawTemp = parts[2].trim().toFloatOrNull() ?: continue
                    val tempC = if (rawTemp > 1000f) rawTemp / 1000f else rawTemp
                    val isW = parts.getOrNull(3) == "1"
                    val tpTot = parts.getOrNull(4)?.toIntOrNull() ?: 0
                    val tpW = parts.getOrNull(5)?.toIntOrNull() ?: 0
                    val mode = parts.getOrNull(6)?.trim() ?: "enabled"
                    val policy = parts.getOrNull(7)?.trim() ?: "step_wise"

                    if (tempC in 15f..115f) {
                        list.add(
                            ThermalZoneInfo(
                                id = id,
                                type = type,
                                tempC = tempC,
                                isWritable = isW,
                                tripPointsCount = tpTot,
                                writableTripPointsCount = tpW,
                                mode = mode,
                                policy = policy
                            )
                        )
                    }
                }
            }
            list.sortedBy { it.type }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
