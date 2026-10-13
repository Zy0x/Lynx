package com.noir.lynx.data
import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
internal object LynxCpuIdleRepository {
    suspend fun readCpuIdleInfo(context: Context? = null): CpuIdleInfo = withContext(Dispatchers.IO) {
        try {
            // Tier 0: lynxd Native Fast-Path (<1ms, single native binary call)
            if (LynxRepository.isModuleInstalled()) {
                val lynxdPath = "$MODULE_DIR/system/bin/lynxd"
                val res = Shell.cmd("[ -x '$lynxdPath' ] && '$lynxdPath' cpuidle info 2>/dev/null").exec()
                if (res.isSuccess && res.out.isNotEmpty()) {
                    val raw = res.out.joinToString("\n").trim()
                    if (raw.startsWith("{")) {
                        try {
                            val json = JSONObject(raw)
                            val statesArr = json.optJSONArray("states")
                            val statesList = mutableListOf<CpuIdleStateItem>()
                            if (statesArr != null) {
                                for (i in 0 until statesArr.length()) {
                                    val obj = statesArr.optJSONObject(i) ?: continue
                                    val idx = obj.optInt("index", i)
                                    val name = obj.optString("name", "state$idx")
                                    val desc = obj.optString("desc", "")
                                    val disabled = obj.optBoolean("disabled", false)
                                    val latUs = obj.optLong("latency_us", 0L)
                                    statesList.add(
                                        CpuIdleStateItem(
                                            index = idx,
                                            name = name,
                                            desc = desc,
                                            latencyUs = latUs,
                                            residencyUs = 0L,
                                            usageCount = 0L,
                                            timeUs = 0L,
                                            isDisabled = disabled
                                        )
                                    )
                                }
                            }
                            val armpllStr = json.optString("armpll_mode", "-1")
                            val armpll = armpllStr.toIntOrNull() ?: -1
                            val cstateAware = json.optString("sched_cstate_aware", "1") != "0"
                            val totalCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(8)
                            val ctx = context ?: LynxRepository.appContext
                            val prefs = ctx?.getSharedPreferences("lynx_cpuidle_prefs", Context.MODE_PRIVATE)
                            val savedPreset = prefs?.getString("active_preset", null)
                            val savedCoreParkingMode = prefs?.getString("core_parking_mode", "dynamic") ?: "dynamic"
                            val isDeepDisabled = statesList.filter { it.index >= 2 }.all { it.isDisabled } && statesList.isNotEmpty()
                            val activePreset = savedPreset ?: when {
                                isDeepDisabled -> "gaming"
                                else -> "balanced"
                            }
                            val applyOnBoot = prefs?.getBoolean("apply_on_boot", false) ?: false

                            return@withContext CpuIdleInfo(
                                isSupported = statesList.isNotEmpty(),
                                driver = "generic_idle",
                                governor = "menu",
                                states = statesList.sortedBy { it.index },
                                mcdiEnabled = true,
                                armPllMode = armpll == 1,
                                buckMode = false,
                                schedCstateAware = cstateAware,
                                isCstateAwareSupported = true,
                                isArmPllSupported = armpll != -1,
                                coreParkingMode = savedCoreParkingMode,
                                totalCores = totalCores,
                                onlineCoresCount = totalCores,
                                isDeepSleepDisabled = isDeepDisabled,
                                activePreset = activePreset,
                                applyOnBoot = applyOnBoot
                            )
                        } catch (e: Exception) {
                            Log.w("LynxRepository", "lynxd cpuidle info parse failed: ${e.message}")
                        }
                    }
                }
            }

            // Tier 1: Fallback Standalone / Legacy Shell Script
            val script = """
                driver=${'$'}(cat /sys/devices/system/cpu/cpuidle/current_driver 2>/dev/null || echo "generic_idle")
                gov=${'$'}(cat /sys/devices/system/cpu/cpuidle/current_governor_ro 2>/dev/null || cat /sys/devices/system/cpu/cpuidle/current_governor 2>/dev/null || echo "menu")
                armpll=${'$'}(cat /proc/cpuidle/control/armpll_mode 2>/dev/null || echo "-1")
                buck=${'$'}(cat /proc/cpuidle/control/buck_mode 2>/dev/null || echo "-1")
                cstate_aware=${'$'}(cat /proc/sys/kernel/sched_cstate_aware 2>/dev/null || echo "1")
                total_cores=0
                online_cores=0
                for c in /sys/devices/system/cpu/cpu[0-9]*; do
                    [ -d "${'$'}c" ] || continue
                    total_cores=${'$'}((total_cores + 1))
                    if [ -f "${'$'}c/online" ]; then
                        [ "${'$'}(cat ${'$'}c/online 2>/dev/null)" = "1" ] && online_cores=${'$'}((online_cores + 1))
                    else
                        online_cores=${'$'}((online_cores + 1))
                    fi
                done

                echo "driver:${'$'}driver"
                echo "gov:${'$'}gov"
                echo "armpll:${'$'}armpll"
                echo "buck:${'$'}buck"
                echo "cstate_aware:${'$'}cstate_aware"
                echo "total_cores:${'$'}total_cores"
                echo "online_cores:${'$'}online_cores"

                for s in /sys/devices/system/cpu/cpu0/cpuidle/state*; do
                    [ -d "${'$'}s" ] || continue
                    idx=${'$'}(basename "${'$'}s" | sed 's/state//')
                    name=${'$'}(cat "${'$'}s/name" 2>/dev/null || echo "state${'$'}idx")
                    desc=${'$'}(cat "${'$'}s/desc" 2>/dev/null || echo "")
                    dis=${'$'}(cat "${'$'}s/disable" 2>/dev/null || echo "0")
                    lat=${'$'}(cat "${'$'}s/latency" 2>/dev/null || echo "0")
                    res=${'$'}(cat "${'$'}s/residency" 2>/dev/null || echo "0")
                    usg=${'$'}(cat "${'$'}s/usage" 2>/dev/null || echo "0")
                    time=${'$'}(cat "${'$'}s/time" 2>/dev/null || echo "0")
                    echo "state:${'$'}idx|${'$'}name|${'$'}desc|${'$'}dis|${'$'}lat|${'$'}res|${'$'}usg|${'$'}time"
                done
            """.trimIndent()
            val res = Shell.cmd(script).exec()
            var driver = "generic_idle"
            var gov = "menu"
            var armpll = -1
            var buck = -1
            var cstateAware = true
            var totalCores = 8
            var onlineCores = 8
            val states = mutableListOf<CpuIdleStateItem>()

            res.out.forEach { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    val k = parts[0].trim()
                    val v = parts[1].trim()
                    when (k) {
                        "driver" -> driver = v
                        "gov" -> gov = v
                        "armpll" -> armpll = v.toIntOrNull() ?: -1
                        "buck" -> buck = v.toIntOrNull() ?: -1
                        "cstate_aware" -> cstateAware = v == "1"
                        "total_cores" -> totalCores = v.toIntOrNull() ?: 8
                        "online_cores" -> onlineCores = v.toIntOrNull() ?: 8
                        "state" -> {
                            val fields = v.split("|")
                            if (fields.size >= 8) {
                                states.add(
                                    CpuIdleStateItem(
                                        index = fields[0].toIntOrNull() ?: 0,
                                        name = fields[1],
                                        desc = fields[2],
                                        isDisabled = fields[3] == "1",
                                        latencyUs = fields[4].toLongOrNull() ?: 0L,
                                        residencyUs = fields[5].toLongOrNull() ?: 0L,
                                        usageCount = fields[6].toLongOrNull() ?: 0L,
                                        timeUs = fields[7].toLongOrNull() ?: 0L,
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (context != null) LynxRepository.appContext = context.applicationContext
            val ctx = context ?: LynxRepository.appContext
            val prefs = ctx?.getSharedPreferences("lynx_cpuidle_prefs", Context.MODE_PRIVATE)
            val applyOnBoot = prefs?.getBoolean("apply_on_boot", false) ?: false
            val savedPreset = prefs?.getString("active_preset", null)
            val savedCoreParkingMode = prefs?.getString("core_parking_mode", "dynamic") ?: "dynamic"

            val isDeepDisabled = states.filter { it.index >= 2 }.all { it.isDisabled } && states.isNotEmpty()

            val activePreset = savedPreset ?: when {
                isDeepDisabled -> "gaming"
                onlineCores < totalCores -> "battery"
                else -> "balanced"
            }

            CpuIdleInfo(
                isSupported = states.isNotEmpty(),
                driver = driver,
                governor = gov,
                states = states.sortedBy { it.index },
                mcdiEnabled = true,
                armPllMode = armpll == 1,
                buckMode = buck == 1,
                schedCstateAware = cstateAware,
                isCstateAwareSupported = true,
                isArmPllSupported = armpll != -1,
                coreParkingMode = savedCoreParkingMode,
                totalCores = totalCores,
                onlineCoresCount = onlineCores,
                isDeepSleepDisabled = isDeepDisabled,
                activePreset = activePreset,
                applyOnBoot = applyOnBoot
            )
        } catch (e: Exception) {
            CpuIdleInfo()
        }
    }

    suspend fun setCpuIdleStateDisabled(stateIndex: Int, disabled: Boolean, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        val valLinux = if (disabled) "1" else "0"
        val valMtk = if (disabled) "0" else "1"
        val script = """
            for cpu in /sys/devices/system/cpu/cpu[0-9]*; do
                if [ -f "${'$'}cpu/cpuidle/state$stateIndex/disable" ]; then
                    chmod 664 "${'$'}cpu/cpuidle/state$stateIndex/disable" 2>/dev/null
                    echo $valLinux > "${'$'}cpu/cpuidle/state$stateIndex/disable" 2>/dev/null
                fi
            done
            if [ -w /proc/cpuidle/state/enabled ]; then
                echo "100 $stateIndex $valMtk" > /proc/cpuidle/state/enabled 2>/dev/null
            fi
            echo "ok"
        """.trimIndent()
        try {
            val res = Shell.cmd(script).exec()
            val ok = res.isSuccess && res.out.any { it.trim() == "ok" }
            if (ok) {
                if (context != null) LynxRepository.appContext = context.applicationContext
                val ctx = context ?: LynxRepository.appContext
                ctx?.getSharedPreferences("lynx_cpuidle_prefs", Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putBoolean("state_${stateIndex}_disabled", disabled)
                    ?.putString("active_preset", "custom")
                    ?.apply()
            }
            ok
        } catch (e: Exception) {
            false
        }
    }

    suspend fun applyCpuIdlePreset(preset: String, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        val lynxdPath = "$MODULE_DIR/system/bin/lynxd"
        if (java.io.File(lynxdPath).canExecute()) {
            val res = Shell.cmd("$lynxdPath cpuidle apply ${preset.lowercase()}").exec()
            if (res.isSuccess) {
                if (context != null) LynxRepository.appContext = context.applicationContext
                val ctx = context ?: LynxRepository.appContext
                ctx?.getSharedPreferences("lynx_cpuidle_prefs", Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putString("active_preset", preset.lowercase())
                    ?.apply()
                return@withContext true
            }
        }
        val script = when (preset.lowercase()) {
            "gaming" -> """
                # Zero Latency: Disable deeper states (2..9) across all clusters
                for s in 2 3 4 5 6 7 8 9; do
                    for cpu in /sys/devices/system/cpu/cpu[0-9]*; do
                        [ -f "${'$'}cpu/cpuidle/state${'$'}s/disable" ] && echo 1 > "${'$'}cpu/cpuidle/state${'$'}s/disable" 2>/dev/null
                    done
                    [ -w /proc/cpuidle/state/enabled ] && echo "100 ${'$'}s 0" > /proc/cpuidle/state/enabled 2>/dev/null
                done
                # State 0 (WFI) & State 1 (cpuoff_l / ret) kept enabled
                for s in 0 1; do
                    for cpu in /sys/devices/system/cpu/cpu[0-9]*; do
                        [ -f "${'$'}cpu/cpuidle/state${'$'}s/disable" ] && echo 0 > "${'$'}cpu/cpuidle/state${'$'}s/disable" 2>/dev/null
                    done
                    [ -w /proc/cpuidle/state/enabled ] && echo "100 ${'$'}s 1" > /proc/cpuidle/state/enabled 2>/dev/null
                done
                [ -f /proc/cpuidle/control/armpll_mode ] && echo 0 > /proc/cpuidle/control/armpll_mode 2>/dev/null
                [ -f /proc/sys/kernel/sched_cstate_aware ] && echo 0 > /proc/sys/kernel/sched_cstate_aware 2>/dev/null
                [ -f /sys/module/lpm_levels/parameters/sleep_disabled ] && echo 1 > /sys/module/lpm_levels/parameters/sleep_disabled 2>/dev/null
                # Unpark all cores
                for c in /sys/devices/system/cpu/cpu[0-9]*; do
                    [ -d "${'$'}c" ] || continue
                    [ -f "${'$'}c/online" ] && echo 1 > "${'$'}c/online" 2>/dev/null
                done
                echo "ok"
            """.trimIndent()
            "battery" -> """
                # Full Deep Sleep: Enable all states (0..9)
                for s in 0 1 2 3 4 5 6 7 8 9; do
                    for cpu in /sys/devices/system/cpu/cpu[0-9]*; do
                        [ -f "${'$'}cpu/cpuidle/state${'$'}s/disable" ] && echo 0 > "${'$'}cpu/cpuidle/state${'$'}s/disable" 2>/dev/null
                    done
                    [ -w /proc/cpuidle/state/enabled ] && echo "100 ${'$'}s 1" > /proc/cpuidle/state/enabled 2>/dev/null
                done
                [ -f /proc/cpuidle/control/armpll_mode ] && echo 1 > /proc/cpuidle/control/armpll_mode 2>/dev/null
                [ -f /proc/cpuidle/control/buck_mode ] && echo 0 > /proc/cpuidle/control/buck_mode 2>/dev/null
                [ -f /proc/sys/kernel/sched_cstate_aware ] && echo 1 > /proc/sys/kernel/sched_cstate_aware 2>/dev/null
                [ -f /sys/module/lpm_levels/parameters/sleep_disabled ] && echo 0 > /sys/module/lpm_levels/parameters/sleep_disabled 2>/dev/null
                # Safe Parking: Guarantee at least 4 base cores remain online (Supports 4+4, 6+2, 1+3+4, 2+5+1, 2+2, 10-core)
                little_cores=""
                big_cores=""
                core_cnt=0
                for idx in 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
                    pol="/sys/devices/system/cpu/cpufreq/policy${'$'}{idx}"
                    [ -d "${'$'}pol" ] || continue
                    rel=${'$'}(cat "${'$'}pol/related_cpus" 2>/dev/null | tr -s '[:space:]' ' ')
                    n_cores=${'$'}(echo "${'$'}rel" | wc -w)
                    if [ -z "${'$'}little_cores" ] || [ ${'$'}core_cnt -lt 4 ]; then
                        little_cores="${'$'}little_cores ${'$'}rel"
                        core_cnt=${'$'}((core_cnt + n_cores))
                    else
                        big_cores="${'$'}big_cores ${'$'}rel"
                    fi
                done
                [ -z "${'$'}little_cores" ] && little_cores="0 1 2 3"
                for c in ${'$'}little_cores; do
                    [ "${'$'}c" = "0" ] && continue
                    [ -f "/sys/devices/system/cpu/cpu${'$'}c/online" ] && echo 1 > "/sys/devices/system/cpu/cpu${'$'}c/online" 2>/dev/null
                done
                for c in ${'$'}big_cores; do
                    [ -f "/sys/devices/system/cpu/cpu${'$'}c/online" ] && echo 0 > "/sys/devices/system/cpu/cpu${'$'}c/online" 2>/dev/null
                done
                echo "ok"
            """.trimIndent()
            else -> """
                # Balanced Default: Enable all states (0..9)
                for s in 0 1 2 3 4 5 6 7 8 9; do
                    for cpu in /sys/devices/system/cpu/cpu[0-9]*; do
                        [ -f "${'$'}cpu/cpuidle/state${'$'}s/disable" ] && echo 0 > "${'$'}cpu/cpuidle/state${'$'}s/disable" 2>/dev/null
                    done
                    [ -w /proc/cpuidle/state/enabled ] && echo "100 ${'$'}s 1" > /proc/cpuidle/state/enabled 2>/dev/null
                done
                [ -f /proc/cpuidle/control/armpll_mode ] && echo 1 > /proc/cpuidle/control/armpll_mode 2>/dev/null
                [ -f /proc/sys/kernel/sched_cstate_aware ] && echo 1 > /proc/sys/kernel/sched_cstate_aware 2>/dev/null
                [ -f /sys/module/lpm_levels/parameters/sleep_disabled ] && echo 0 > /sys/module/lpm_levels/parameters/sleep_disabled 2>/dev/null
                # Unpark all cores for standard dynamic use
                for c in /sys/devices/system/cpu/cpu[0-9]*; do
                    [ -d "${'$'}c" ] || continue
                    [ -f "${'$'}c/online" ] && echo 1 > "${'$'}c/online" 2>/dev/null
                done
                echo "ok"
            """.trimIndent()
        }
        try {
            val res = Shell.cmd(script).exec()
            val ok = res.isSuccess && res.out.any { it.trim() == "ok" }
            if (ok) {
                if (context != null) LynxRepository.appContext = context.applicationContext
                val ctx = context ?: LynxRepository.appContext
                ctx?.getSharedPreferences("lynx_cpuidle_prefs", Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putString("active_preset", preset.lowercase())
                    ?.putString("core_parking_mode", if (preset.equals("battery", true)) "park_big" else if (preset.equals("gaming", true)) "unpark_all" else "dynamic")
                    ?.apply()
            }
            ok
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setCoreParkingMode(mode: String, totalCores: Int = 8, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        val script = when (mode.lowercase()) {
            "unpark_all" -> """
                for c in /sys/devices/system/cpu/cpu[0-9]*; do
                    [ -d "${'$'}c" ] || continue
                    [ -f "${'$'}c/online" ] && echo 1 > "${'$'}c/online" 2>/dev/null
                done
                echo "ok"
            """.trimIndent()
            "park_big" -> """
                little_cores=""
                big_cores=""
                core_cnt=0
                for idx in 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
                    pol="/sys/devices/system/cpu/cpufreq/policy${'$'}{idx}"
                    [ -d "${'$'}pol" ] || continue
                    rel=${'$'}(cat "${'$'}pol/related_cpus" 2>/dev/null | tr -s '[:space:]' ' ')
                    n_cores=${'$'}(echo "${'$'}rel" | wc -w)
                    if [ -z "${'$'}little_cores" ] || [ ${'$'}core_cnt -lt 4 ]; then
                        little_cores="${'$'}little_cores ${'$'}rel"
                        core_cnt=${'$'}((core_cnt + n_cores))
                    else
                        big_cores="${'$'}big_cores ${'$'}rel"
                    fi
                done
                [ -z "${'$'}little_cores" ] && little_cores="0 1 2 3"
                for c in ${'$'}little_cores; do
                    [ "${'$'}c" = "0" ] && continue
                    [ -f "/sys/devices/system/cpu/cpu${'$'}c/online" ] && echo 1 > "/sys/devices/system/cpu/cpu${'$'}c/online" 2>/dev/null
                done
                for c in ${'$'}big_cores; do
                    [ -f "/sys/devices/system/cpu/cpu${'$'}c/online" ] && echo 0 > "/sys/devices/system/cpu/cpu${'$'}c/online" 2>/dev/null
                done
                echo "ok"
            """.trimIndent()
            else -> """
                for c in /sys/devices/system/cpu/cpu[0-9]*; do
                    [ -d "${'$'}c" ] || continue
                    [ -f "${'$'}c/online" ] && echo 1 > "${'$'}c/online" 2>/dev/null
                done
                echo "ok"
            """.trimIndent()
        }
        try {
            val res = Shell.cmd(script).exec()
            val ok = res.isSuccess && res.out.any { it.trim() == "ok" }
            if (ok) {
                if (context != null) LynxRepository.appContext = context.applicationContext
                val ctx = context ?: LynxRepository.appContext
                ctx?.getSharedPreferences("lynx_cpuidle_prefs", Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putString("core_parking_mode", mode.lowercase())
                    ?.putString("active_preset", "custom")
                    ?.apply()
            }
            ok
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setCpuIdleApplyOnBoot(enabled: Boolean, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        if (context != null) LynxRepository.appContext = context.applicationContext
        val ctx = context ?: LynxRepository.appContext
        try {
            ctx?.getSharedPreferences("lynx_cpuidle_prefs", Context.MODE_PRIVATE)
                ?.edit()
                ?.putBoolean("apply_on_boot", enabled)
                ?.apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setArmPllMode(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val valStr = if (enabled) "1" else "0"
        val script = """
            if [ -f /proc/cpuidle/control/armpll_mode ]; then
                echo $valStr > /proc/cpuidle/control/armpll_mode 2>/dev/null
                echo "ok"
            else
                echo "fail"
            fi
        """.trimIndent()
        try {
            val res = Shell.cmd(script).exec()
            res.isSuccess && res.out.any { it.trim() == "ok" }
        } catch (_: Exception) { false }
    }
}
