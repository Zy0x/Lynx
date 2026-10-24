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
            val lynxdPath = LynxRepository.getLynxdPath()
            if (LynxRepository.isLynxdAvailable()) {
                val rustMode = when (mode.lowercase()) {
                    "default", "default_oem" -> "conservative"
                    "hardware_safety_dominant", "unrestricted", "dominant" -> "gaming"
                    "stable", "thermal_stable" -> "stable"
                    "extreme", "bypass" -> "extreme"
                    else -> mode.lowercase()
                }
                val res = Shell.cmd("'$lynxdPath' thermal mode $rustMode").exec()
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
            // On Qualcomm, NEVER freeze thermal-engine / thermal-engine-v2 or system_server thermal IPC deadlocks!
            val isQcom = caps.socVendor == "qualcomm" || caps.socVendor == "qcom"
            val daemonCmd = if (isQcom) {
                """
                    killall -STOP mi_thermald ituxd com.xiaomi.joyose com.samsung.android.game.gos 2>/dev/null
                    killall -CONT thermal-engine thermal-engine-v2 2>/dev/null
                    cmd thermalservice override-status 0 2>/dev/null
                    for c in /sys/devices/system/cpu/cpu[0-9]*; do
                        [ -f "${'$'}c/isolate" ] && echo 0 > "${'$'}c/isolate" 2>/dev/null
                        [ -f "${'$'}c/online" ] && echo 1 > "${'$'}c/online" 2>/dev/null
                    done
                    for cd in /sys/class/thermal/cooling_device*; do
                        if [ -f "${'$'}cd/type" ]; then
                            read -r t < "${'$'}cd/type" 2>/dev/null
                            case "${'$'}t" in
                                *isolate*|*thermal-cpufreq*|battery|*batt*) echo 0 > "${'$'}cd/cur_state" 2>/dev/null ;;
                            esac
                        fi
                    done
                """.trimIndent()
            } else {
                """
                    killall -STOP mi_thermald ituxd com.xiaomi.joyose com.samsung.android.game.gos 2>/dev/null
                    cmd thermalservice override-status 0 2>/dev/null
                """.trimIndent()
            }
            Shell.cmd(daemonCmd).exec()

            txRes is com.noir.lynx.safety.ThermalTransactionResult.Success
        } catch (_: Exception) { false }
    }

    suspend fun applyCustomTempLimitLive(tempC: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val lynxdPath = LynxRepository.getLynxdPath()
            if (LynxRepository.isLynxdAvailable()) {
                val res = Shell.cmd("'$lynxdPath' thermal set-limit $tempC").exec()
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

    private data class ThermalMeta(
        val id: Int,
        val type: String,
        val isWritable: Boolean,
        val tripPointsCount: Int,
        val writableTripPointsCount: Int,
        val mode: String,
        val policy: String,
        val tempFile: File
    )

    @Volatile
    private var cachedZonesMeta: List<ThermalMeta>? = null
    @Volatile
    private var lastKnownZones: List<ThermalZoneInfo> = emptyList()

    fun getLastKnownThermalZones(): List<ThermalZoneInfo> = lastKnownZones

    suspend fun readThermalZones(): List<ThermalZoneInfo> = withContext(Dispatchers.IO) {
        try {
            // Fast-path: If static metadata is already discovered, sample temp files directly (<0.2ms, 0 shell commands)
            val cached = cachedZonesMeta
            if (cached != null && cached.isNotEmpty()) {
                val list = mutableListOf<ThermalZoneInfo>()
                for (meta in cached) {
                    val rawTemp = runCatching { meta.tempFile.readText().trim().toFloatOrNull() }.getOrNull() ?: continue
                    val tempC = if (rawTemp > 1000f) rawTemp / 1000f else rawTemp
                    if (tempC in 10f..120f) {
                        list.add(
                            ThermalZoneInfo(
                                id = meta.id,
                                type = meta.type,
                                tempC = tempC,
                                isWritable = meta.isWritable,
                                tripPointsCount = meta.tripPointsCount,
                                writableTripPointsCount = meta.writableTripPointsCount,
                                mode = meta.mode,
                                policy = meta.policy
                            )
                        )
                    }
                }
                if (list.isNotEmpty()) {
                    val sorted = list.sortedBy { it.type }
                    lastKnownZones = sorted
                    return@withContext sorted
                }
            }

            // Tier 0 Discovery: Direct Kotlin Sysfs Read (<0.5ms, zero root shell execution)
            val thermalDir = File("/sys/class/thermal")
            val tzDirs = if (thermalDir.isDirectory) {
                thermalDir.listFiles { f -> f.name.startsWith("thermal_zone") }?.toList()
            } else null

            val candidates = if (!tzDirs.isNullOrEmpty()) {
                tzDirs
            } else {
                (0..35).mapNotNull { idx ->
                    val f = File("/sys/class/thermal/thermal_zone$idx")
                    if (f.exists()) f else null
                }
            }

            if (candidates.isNotEmpty()) {
                val metaList = mutableListOf<ThermalMeta>()
                val resultList = mutableListOf<ThermalZoneInfo>()

                for (tz in candidates) {
                    val idStr = tz.name.removePrefix("thermal_zone")
                    val id = idStr.toIntOrNull() ?: continue
                    val type = runCatching { File(tz, "type").readText().trim() }.getOrNull() ?: continue
                    val tempFile = File(tz, "temp")
                    val rawTemp = runCatching { tempFile.readText().trim().toFloatOrNull() }.getOrNull() ?: continue
                    val tempC = if (rawTemp > 1000f) rawTemp / 1000f else rawTemp
                    if (tempC !in 10f..120f) continue

                    val mode = "enabled"
                    val policy = "step_wise"
                    val isW = false

                    val meta = ThermalMeta(
                        id = id,
                        type = type,
                        isWritable = isW,
                        tripPointsCount = 0,
                        writableTripPointsCount = 0,
                        mode = mode,
                        policy = policy,
                        tempFile = tempFile
                    )
                    metaList.add(meta)
                    resultList.add(
                        ThermalZoneInfo(
                            id = id,
                            type = type,
                            tempC = tempC,
                            isWritable = isW,
                            tripPointsCount = 0,
                            writableTripPointsCount = 0,
                            mode = mode,
                            policy = policy
                        )
                    )
                }

                if (metaList.isNotEmpty()) {
                    cachedZonesMeta = metaList
                    val sorted = resultList.sortedBy { it.type }
                    lastKnownZones = sorted
                    return@withContext sorted
                }
            }

            // Tier 1: Ultra-fast zero-fork Shell Script (<30ms, builtin read -r, zero process forks)
            val script = """
                for tz in /sys/class/thermal/thermal_zone[0-9]*; do
                    [ -d "${'$'}tz" ] || continue
                    read -r type < "${'$'}tz/type" 2>/dev/null || continue
                    [ -z "${'$'}type" ] && continue
                    read -r temp < "${'$'}tz/temp" 2>/dev/null || continue
                    [ -z "${'$'}temp" ] && continue
                    id=${'$'}{tz##*thermal_zone}
                    echo "${'$'}id|${'$'}type|${'$'}temp"
                done
            """.trimIndent()
            val r = Shell.cmd(script).exec()
            val list = mutableListOf<ThermalZoneInfo>()
            val metaList = mutableListOf<ThermalMeta>()
            for (line in r.out) {
                val parts = line.split("|")
                if (parts.size >= 3) {
                    val id = parts[0].toIntOrNull() ?: continue
                    val type = parts[1].trim()
                    val rawTemp = parts[2].trim().toFloatOrNull() ?: continue
                    val tempC = if (rawTemp > 1000f) rawTemp / 1000f else rawTemp

                    if (tempC in 10f..120f) {
                        metaList.add(
                            ThermalMeta(
                                id = id,
                                type = type,
                                isWritable = false,
                                tripPointsCount = 0,
                                writableTripPointsCount = 0,
                                mode = "enabled",
                                policy = "step_wise",
                                tempFile = File("/sys/class/thermal/thermal_zone$id/temp")
                            )
                        )
                        list.add(
                            ThermalZoneInfo(
                                id = id,
                                type = type,
                                tempC = tempC,
                                isWritable = false,
                                tripPointsCount = 0,
                                writableTripPointsCount = 0,
                                mode = "enabled",
                                policy = "step_wise"
                            )
                        )
                    }
                }
            }
            if (metaList.isNotEmpty()) {
                cachedZonesMeta = metaList
            }
            val sorted = list.sortedBy { it.type }
            lastKnownZones = sorted
            sorted
        } catch (e: Exception) {
            emptyList()
        }
    }
}
