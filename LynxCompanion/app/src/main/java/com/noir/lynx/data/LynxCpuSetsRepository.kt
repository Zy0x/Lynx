package com.noir.lynx.data
import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
internal object LynxCpuSetsRepository {
    suspend fun readCpuSetsInfo(context: Context? = null): CpuSetsInfo = withContext(Dispatchers.IO) {
        try {
            // Tier 0: lynxd Native Fast-Path (<1ms, single native binary call)
            if (LynxRepository.isModuleInstalled()) {
                val lynxdPath = "$MODULE_DIR/system/bin/lynxd"
                val res = Shell.cmd("[ -x '$lynxdPath' ] && '$lynxdPath' cpuset info 2>/dev/null").exec()
                if (res.isSuccess && res.out.isNotEmpty()) {
                    val raw = res.out.joinToString("\n").trim()
                    if (raw.startsWith("{")) {
                        try {
                            val json = JSONObject(raw)
                            val arr = json.optJSONArray("cpusets")
                            if (arr != null && arr.length() > 0) {
                                var topApp = "0-7"
                                var foreground = "0-7"
                                var background = "0-2"
                                var systemBackground = "0-2"
                                var restricted = "0-3"
                                for (i in 0 until arr.length()) {
                                    val obj = arr.optJSONObject(i) ?: continue
                                    val grp = obj.optString("group", "")
                                    val cpus = obj.optString("cpus", "")
                                    if (cpus.isNotBlank() && cpus != "unsupported") {
                                        when (grp) {
                                            "top-app" -> topApp = cpus
                                            "foreground" -> foreground = cpus
                                            "background" -> background = cpus
                                            "system-background" -> systemBackground = cpus
                                            "restricted" -> restricted = cpus
                                        }
                                    }
                                }
                                val dummy = CpuSetsInfo()
                                val bgSet = dummy.parseCores(background)
                                val fgSet = dummy.parseCores(foreground)
                                val taSet = dummy.parseCores(topApp)
                                val detectedPreset = when {
                                    bgSet.size <= 2 && fgSet.size <= 4 && taSet.size >= 7 -> "gaming"
                                    bgSet.size <= 2 && fgSet.size >= 6 -> "balanced"
                                    bgSet.size >= 4 && fgSet.size >= 7 -> "multitasking"
                                    taSet.size <= 4 -> "powersave"
                                    else -> "balanced"
                                }
                                if (context != null) LynxRepository.appContext = context.applicationContext
                                val ctx = context ?: LynxRepository.appContext
                                val prefs = ctx?.getSharedPreferences("lynx_cpuset_prefs", Context.MODE_PRIVATE)
                                val activePreset = prefs?.getString("active_preset", detectedPreset) ?: detectedPreset
                                val applyOnBoot = prefs?.getBoolean("apply_on_boot", false) ?: false
                                val totalCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(8)
                                return@withContext CpuSetsInfo(
                                    isSupported = true,
                                    topAppCpus = topApp,
                                    foregroundCpus = foreground,
                                    backgroundCpus = background,
                                    systemBackgroundCpus = systemBackground,
                                    restrictedCpus = restricted,
                                    totalCoresCount = totalCount,
                                    activePreset = activePreset,
                                    applyOnBoot = applyOnBoot
                                )
                            }
                        } catch (e: Exception) {
                            Log.w("LynxRepository", "lynxd cpuset info parse failed: ${e.message}")
                        }
                    }
                }
            }

            // Tier 1: Fallback Standalone / Legacy Shell Script
            val script = """
                cpuset_root=""
                if [ -d /dev/cpuset ]; then
                    cpuset_root="/dev/cpuset"
                elif [ -d /sys/fs/cgroup/cpuset ]; then
                    cpuset_root="/sys/fs/cgroup/cpuset"
                fi
                if [ -n "${'$'}cpuset_root" ]; then
                    echo "supported=1"
                    echo "top_app=${'$'}(cat "${'$'}cpuset_root/top-app/cpus" "${'$'}cpuset_root/top-app/cpuset.cpus" 2>/dev/null | head -n 1)"
                    echo "foreground=${'$'}(cat "${'$'}cpuset_root/foreground/cpus" "${'$'}cpuset_root/foreground/cpuset.cpus" 2>/dev/null | head -n 1)"
                    echo "background=${'$'}(cat "${'$'}cpuset_root/background/cpus" "${'$'}cpuset_root/background/cpuset.cpus" 2>/dev/null | head -n 1)"
                    echo "system_background=${'$'}(cat "${'$'}cpuset_root/system-background/cpus" "${'$'}cpuset_root/system-background/cpuset.cpus" 2>/dev/null | head -n 1)"
                    echo "restricted=${'$'}(cat "${'$'}cpuset_root/restricted/cpus" "${'$'}cpuset_root/restricted/cpuset.cpus" 2>/dev/null | head -n 1)"
                    tot=${'$'}(cat /dev/cpuset/cpus /dev/cpuset/effective_cpus /sys/devices/system/cpu/present /sys/fs/cgroup/cpuset/cpus.effective 2>/dev/null | grep -v '^$' | head -n 1)
                    echo "total_cores=${'$'}tot"
                else
                    echo "supported=0"
                fi
            """.trimIndent()
            val result = Shell.cmd(script).exec()
            var supported = false
            var topApp = "0-7"
            var foreground = "0-7"
            var background = "0-2"
            var systemBackground = "0-2"
            var restricted = "0-3"
            var totalCoresStr = "0-7"

            result.out.forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    when (key) {
                        "supported" -> supported = value == "1"
                        "top_app" -> if (value.isNotEmpty()) topApp = value
                        "foreground" -> if (value.isNotEmpty()) foreground = value
                        "background" -> if (value.isNotEmpty()) background = value
                        "system_background" -> if (value.isNotEmpty()) systemBackground = value
                        "restricted" -> if (value.isNotEmpty()) restricted = value
                        "total_cores" -> if (value.isNotEmpty()) totalCoresStr = value
                    }
                }
            }

            val dummy = CpuSetsInfo()
            val totalCoresSet = dummy.parseCores(totalCoresStr)
            val totalCount = if (totalCoresSet.isNotEmpty()) (totalCoresSet.maxOrNull() ?: 7) + 1 else 8

            val bgSet = dummy.parseCores(background)
            val fgSet = dummy.parseCores(foreground)
            val taSet = dummy.parseCores(topApp)

            val activePreset = if (taSet.size >= totalCount && fgSet.size < totalCount && !bgSet.contains(totalCount - 1)) {
                "gaming"
            } else if (taSet.size >= totalCount && fgSet.size >= totalCount) {
                "standard"
            } else if (taSet.size < totalCount) {
                "battery"
            } else {
                "custom"
            }

            if (context != null) LynxRepository.appContext = context.applicationContext
            val ctx = context ?: LynxRepository.appContext
            val applyOnBoot = ctx?.getSharedPreferences("lynx_cpuset_prefs", Context.MODE_PRIVATE)
                ?.getBoolean("apply_on_boot", false) ?: false

            CpuSetsInfo(
                isSupported = supported,
                topAppCpus = topApp,
                foregroundCpus = foreground,
                backgroundCpus = background,
                systemBackgroundCpus = systemBackground,
                restrictedCpus = restricted,
                totalCoresCount = totalCount,
                activePreset = activePreset,
                applyOnBoot = applyOnBoot
            )
        } catch (e: Exception) {
            Log.w("LynxRepository", "Failed to read cpu sets info: ${e.message}")
            CpuSetsInfo()
        }
    }

    suspend fun applyCpuSetPreset(preset: String, totalCores: Int = 8, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val lynxdPath = "$MODULE_DIR/system/bin/lynxd"
            if (java.io.File(lynxdPath).canExecute()) {
                val res = Shell.cmd("$lynxdPath cpuset apply ${preset.lowercase()}").exec()
                if (res.isSuccess) {
                    context?.let { ctx ->
                        val prefs = ctx.getSharedPreferences("lynx_cpusets", Context.MODE_PRIVATE)
                        prefs.edit().putString("active_preset", preset.lowercase()).apply()
                    }
                    return@withContext true
                }
            }
            val maxCore = (totalCores - 1).coerceAtLeast(1)
            val fallbackLittleMax = if (totalCores >= 8) (totalCores - 3).coerceAtLeast(3) else (totalCores / 2).coerceAtLeast(1)
            val bgMax = if (totalCores >= 8) 2 else 1

            val safeSplitHelper = """
                little_cores=""
                core_cnt=0
                for idx in 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
                    pol="/sys/devices/system/cpu/cpufreq/policy${'$'}{idx}"
                    [ -d "${'$'}pol" ] || continue
                    rel=${'$'}(cat "${'$'}pol/related_cpus" 2>/dev/null | tr -s '[:space:]' ' ')
                    n_cores=${'$'}(echo "${'$'}rel" | wc -w)
                    if [ -z "${'$'}little_cores" ] || [ ${'$'}core_cnt -lt 4 ]; then
                        little_cores="${'$'}little_cores ${'$'}rel"
                        core_cnt=${'$'}((core_cnt + n_cores))
                    fi
                done
                little_last=${'$'}(echo "${'$'}little_cores" | awk '{print ${'$'}NF}')
                [ -z "${'$'}little_last" ] && little_last=$fallbackLittleMax
                write_cpuset() {
                    local grp="${'$'}1"
                    local val="${'$'}2"
                    for root in /dev/cpuset /sys/fs/cgroup/cpuset; do
                        for leaf in cpus cpuset.cpus; do
                            [ -f "${'$'}root/${'$'}grp/${'$'}leaf" ] && echo "${'$'}val" > "${'$'}root/${'$'}grp/${'$'}leaf" 2>/dev/null
                        done
                    done
                }
            """.trimIndent()

            val script = when (preset.lowercase()) {
                "gaming" -> """
                    $safeSplitHelper
                    if [ -d /dev/cpuset ] || [ -d /sys/fs/cgroup/cpuset ]; then
                        write_cpuset "top-app" "0-$maxCore"
                        write_cpuset "foreground" "0-${'$'}little_last"
                        write_cpuset "background" "0-$bgMax"
                        write_cpuset "system-background" "0-$bgMax"
                        write_cpuset "restricted" "0-1"
                        echo "ok"
                    else
                        echo "fail"
                    fi
                """.trimIndent()
                "battery" -> """
                    $safeSplitHelper
                    if [ -d /dev/cpuset ] || [ -d /sys/fs/cgroup/cpuset ]; then
                        write_cpuset "top-app" "0-${'$'}little_last"
                        write_cpuset "foreground" "0-${'$'}little_last"
                        write_cpuset "background" "0-1"
                        write_cpuset "system-background" "0-1"
                        write_cpuset "restricted" "0"
                        echo "ok"
                    else
                        echo "fail"
                    fi
                """.trimIndent()
                else -> """
                    $safeSplitHelper
                    if [ -d /dev/cpuset ] || [ -d /sys/fs/cgroup/cpuset ]; then
                        write_cpuset "top-app" "0-$maxCore"
                        write_cpuset "foreground" "0-$maxCore"
                        write_cpuset "background" "0-$bgMax"
                        write_cpuset "system-background" "0-$bgMax"
                        write_cpuset "restricted" "0-${'$'}little_last"
                        echo "ok"
                    else
                        echo "fail"
                    fi
                """.trimIndent()
            }

            val res = Shell.cmd(script).exec()
            val ok = res.isSuccess && res.out.any { it.trim() == "ok" }
            if (ok && context != null) {
                context.getSharedPreferences("lynx_cpuset_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("active_preset", preset.lowercase())
                    .apply()
            }
            ok
        } catch (e: Exception) {
            Log.e("LynxRepository", "Failed to apply cpuset preset $preset: ${e.message}")
            false
        }
    }

    suspend fun setCpuSetCores(group: String, cores: String, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val folder = when (group.lowercase()) {
                "top-app", "top_app", "game" -> "top-app"
                "foreground", "fg" -> "foreground"
                "background", "bg" -> "background"
                "system-background", "system_background", "sysbg" -> "system-background"
                "restricted" -> "restricted"
                else -> group
            }
            val script = """
                wrote=0
                for root in /dev/cpuset /sys/fs/cgroup/cpuset; do
                    for leaf in cpus cpuset.cpus; do
                        if [ -f "${'$'}root/$folder/${'$'}leaf" ]; then
                            echo "$cores" > "${'$'}root/$folder/${'$'}leaf" 2>/dev/null
                            wrote=1
                        fi
                    done
                done
                [ "${'$'}wrote" = "1" ] && echo "ok" || echo "fail"
            """.trimIndent()
            val res = Shell.cmd(script).exec()
            val ok = res.isSuccess && res.out.any { it.trim() == "ok" }
            if (ok && context != null) {
                context.getSharedPreferences("lynx_cpuset_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString(folder, cores)
                    .putString("active_preset", "custom")
                    .apply()
            }
            ok
        } catch (e: Exception) {
            Log.e("LynxRepository", "Failed to set cpuset cores for $group: ${e.message}")
            false
        }
    }

    suspend fun setCpuSetApplyOnBoot(enabled: Boolean, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        if (context != null) LynxRepository.appContext = context.applicationContext
        val ctx = context ?: LynxRepository.appContext
        try {
            ctx?.getSharedPreferences("lynx_cpuset_prefs", Context.MODE_PRIVATE)
                ?.edit()
                ?.putBoolean("apply_on_boot", enabled)
                ?.apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ----------------------------------------------------------------
    //  SCHEDTUNE & SCHEDULER HINTS REPOSITORY METHODS
    // ----------------------------------------------------------------

    suspend fun setSchedtuneBoost(group: String, boost: Int, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        val safeBoost = boost.coerceIn(0, 100)
        val (folder, perfmgrNode) = when (group.lowercase()) {
            "top-app", "topapp" -> "top-app" to "perfserv_ta_boost"
            "foreground", "fg" -> "foreground" to "perfserv_fg_boost"
            "background", "bg" -> "background" to "perfserv_bg_boost"
            else -> "top-app" to "perfserv_ta_boost"
        }
        val script = """
            wrote="0"
            if [ -f /dev/stune/$folder/schedtune.boost ]; then
                chmod 664 /dev/stune/$folder/schedtune.boost 2>/dev/null
                echo $safeBoost > /dev/stune/$folder/schedtune.boost 2>/dev/null
                wrote="1"
            fi
            if [ -f /proc/perfmgr/boost_ctrl/eas_ctrl/$perfmgrNode ]; then
                echo $safeBoost > /proc/perfmgr/boost_ctrl/eas_ctrl/$perfmgrNode 2>/dev/null
                wrote="1"
            fi
            if [ -f /dev/cpuctl/$folder/cpu.uclamp.min ]; then
                val_uclamp=${'$'}((safeBoost * 1024 / 100))
                chmod 664 /dev/cpuctl/$folder/cpu.uclamp.min 2>/dev/null
                (echo ${'$'}val_uclamp > /dev/cpuctl/$folder/cpu.uclamp.min 2>/dev/null || echo $safeBoost > /dev/cpuctl/$folder/cpu.uclamp.min 2>/dev/null)
                wrote="1"
            fi
            [ "${'$'}wrote" = "1" ] && echo "ok" || echo "fail"
        """.trimIndent()
        try {
            val res = Shell.cmd(script).exec()
            val ok = res.isSuccess && res.out.any { it.trim() == "ok" }
            if (ok) {
                if (context != null) LynxRepository.appContext = context.applicationContext
                val ctx = context ?: LynxRepository.appContext
                ctx?.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putInt("schedtune_boost_$folder", safeBoost)
                    ?.putString("active_preset", "custom")
                    ?.apply()
            }
            ok
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setSchedtunePreferIdle(group: String, preferIdle: Boolean, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        val (folder, idx) = when (group.lowercase()) {
            "top-app", "topapp" -> "top-app" to "3"
            "foreground", "fg" -> "foreground" to "1"
            "background", "bg" -> "background" to "2"
            else -> "top-app" to "3"
        }
        val valStr = if (preferIdle) "1" else "0"
        val script = """
            wrote="0"
            if [ -f /dev/stune/$folder/schedtune.prefer_idle ]; then
                chmod 664 /dev/stune/$folder/schedtune.prefer_idle 2>/dev/null
                echo $valStr > /dev/stune/$folder/schedtune.prefer_idle 2>/dev/null
                wrote="1"
            fi
            if [ -f /dev/stune/$folder/schedtune.prefer_high_cap ]; then
                chmod 664 /dev/stune/$folder/schedtune.prefer_high_cap 2>/dev/null
                echo $valStr > /dev/stune/$folder/schedtune.prefer_high_cap 2>/dev/null
                wrote="1"
            fi
            if [ -f /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_prefer_idle ]; then
                echo "$idx $valStr" > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_prefer_idle 2>/dev/null
                wrote="1"
            fi
            if [ -f /dev/cpuctl/$folder/cpu.uclamp.latency_sensitive ]; then
                chmod 664 /dev/cpuctl/$folder/cpu.uclamp.latency_sensitive 2>/dev/null
                echo $valStr > /dev/cpuctl/$folder/cpu.uclamp.latency_sensitive 2>/dev/null
                wrote="1"
            fi
            [ "${'$'}wrote" = "1" ] && echo "ok" || echo "fail"
        """.trimIndent()
        try {
            val res = Shell.cmd(script).exec()
            val ok = res.isSuccess && res.out.any { it.trim() == "ok" }
            if (ok) {
                if (context != null) LynxRepository.appContext = context.applicationContext
                val ctx = context ?: LynxRepository.appContext
                ctx?.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putBoolean("schedtune_idle_$folder", preferIdle)
                    ?.putString("active_preset", "custom")
                    ?.apply()
            }
            ok
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setSchedulerHint(hintKey: String, enabled: Boolean, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        val node = when (hintKey) {
            "sched_big_task_rotation" -> "/proc/sys/kernel/sched_big_task_rotation"
            "sched_sync_hint_enable" -> "/proc/sys/kernel/sched_sync_hint_enable"
            "sched_cstate_aware" -> "/proc/sys/kernel/sched_cstate_aware"
            else -> return@withContext false
        }
        val valStr = if (enabled) "1" else "0"
        val extraPerfmgr = if (hintKey == "sched_big_task_rotation") {
            "[ -f /proc/perfmgr/boost_ctrl/eas_ctrl/sched_big_task_rotation ] && echo $valStr > /proc/perfmgr/boost_ctrl/eas_ctrl/sched_big_task_rotation 2>/dev/null"
        } else ""
        val script = """
            if [ -f $node ] || [ -f /proc/perfmgr/boost_ctrl/eas_ctrl/$hintKey ]; then
                chmod 644 $node 2>/dev/null
                echo $valStr > $node 2>/dev/null
                $extraPerfmgr
                echo "ok"
            else
                echo "fail"
            fi
        """.trimIndent()
        try {
            val res = Shell.cmd(script).exec()
            val ok = res.isSuccess && res.out.any { it.trim() == "ok" }
            if (ok) {
                if (context != null) LynxRepository.appContext = context.applicationContext
                val ctx = context ?: LynxRepository.appContext
                ctx?.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putBoolean(hintKey, enabled)
                    ?.apply()
            }
            ok
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setWorkqueuePowerEfficient(enabled: Boolean, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        val valStr = if (enabled) "Y" else "N"
        val script = """
            if [ -f /sys/module/workqueue/parameters/power_efficient ]; then
                chmod 644 /sys/module/workqueue/parameters/power_efficient 2>/dev/null
                echo "$valStr" > /sys/module/workqueue/parameters/power_efficient 2>/dev/null
                echo "ok"
            else
                echo "fail"
            fi
        """.trimIndent()
        try {
            val res = Shell.cmd(script).exec()
            val ok = res.isSuccess && res.out.any { it.trim() == "ok" }
            if (ok) {
                if (context != null) LynxRepository.appContext = context.applicationContext
                val ctx = context ?: LynxRepository.appContext
                ctx?.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putBoolean("workqueue_power_efficient", enabled)
                    ?.apply()
            }
            ok
        } catch (e: Exception) {
            false
        }
    }

    // ----------------------------------------------------------------
    //  CPU IDLE & C-STATES / CORE PARKING REPOSITORY METHODS
    // ----------------------------------------------------------------


}
