package com.noir.lynx.data
import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
internal object LynxCpuSchedulerControlRepository {
    suspend fun setSchedulerArchitectureMode(mode: String, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = when (mode.lowercase()) {
                "hmp" -> """
                    if [ -f /sys/devices/system/cpu/eas/enable ]; then
                        chmod 644 /sys/devices/system/cpu/eas/enable 2>/dev/null
                        echo 0 > /sys/devices/system/cpu/eas/enable 2>/dev/null
                    fi
                    if [ -f /proc/sys/kernel/sched_energy_aware ]; then
                        chmod 644 /proc/sys/kernel/sched_energy_aware 2>/dev/null
                        echo 0 > /proc/sys/kernel/sched_energy_aware 2>/dev/null
                    fi
                    echo ok
                """.trimIndent()
                "eas" -> """
                    if [ -f /sys/devices/system/cpu/eas/enable ]; then
                        chmod 644 /sys/devices/system/cpu/eas/enable 2>/dev/null
                        echo 1 > /sys/devices/system/cpu/eas/enable 2>/dev/null
                    fi
                    if [ -f /proc/sys/kernel/sched_energy_aware ]; then
                        chmod 644 /proc/sys/kernel/sched_energy_aware 2>/dev/null
                        echo 1 > /proc/sys/kernel/sched_energy_aware 2>/dev/null
                    fi
                    echo ok
                """.trimIndent()
                "hybrid" -> """
                    if [ -f /sys/devices/system/cpu/eas/enable ]; then
                        chmod 644 /sys/devices/system/cpu/eas/enable 2>/dev/null
                        echo 2 > /sys/devices/system/cpu/eas/enable 2>/dev/null
                    fi
                    if [ -f /proc/sys/kernel/sched_energy_aware ]; then
                        chmod 644 /proc/sys/kernel/sched_energy_aware 2>/dev/null
                        echo 1 > /proc/sys/kernel/sched_energy_aware 2>/dev/null
                    fi
                    echo ok
                """.trimIndent()
                else -> return@withContext false
            }
            val persistScript = """
                mkdir -p /data/adb/lynx 2>/dev/null
                echo "${mode.lowercase()}" > /data/adb/lynx/preferred_architecture 2>/dev/null
            """.trimIndent()
            val finalScript = "$script\n$persistScript\necho ok"
            val res = Shell.cmd(finalScript).exec()
            val ok = res.isSuccess || res.out.any { it.trim() == "ok" }
            if (ok && context != null) {
                context.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("active_sched_mode", mode.lowercase())
                    .putString("preferred_architecture", mode.lowercase())
                    .putString("active_preset", "custom")
                    .apply()
            }
            ok
        } catch (_: Exception) { false }
    }

    suspend fun setSchedulerTunable(tunable: String, value: Long, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = when (tunable) {
                "sched_latency_ns" -> {
                    val safe = value.coerceIn(1000000L, 24000000L)
                    "echo $safe > /proc/sys/kernel/sched_latency_ns 2>/dev/null; echo ok"
                }
                "sched_min_granularity_ns" -> {
                    val safe = value.coerceIn(500000L, 8000000L)
                    "echo $safe > /proc/sys/kernel/sched_min_granularity_ns 2>/dev/null; echo ok"
                }
                "sched_wakeup_granularity_ns" -> {
                    val safe = value.coerceIn(500000L, 8000000L)
                    "echo $safe > /proc/sys/kernel/sched_wakeup_granularity_ns 2>/dev/null; echo ok"
                }
                "sched_migration_cost_ns" -> {
                    val safe = value.coerceIn(100000L, 3000000L)
                    "echo $safe > /proc/sys/kernel/sched_migration_cost_ns 2>/dev/null; echo ok"
                }
                "sched_nr_migrate" -> {
                    val safe = value.coerceIn(8L, 128L)
                    "echo $safe > /proc/sys/kernel/sched_nr_migrate 2>/dev/null; echo ok"
                }
                "sched_child_runs_first" -> {
                    val safe = if (value > 0) 1 else 0
                    "echo $safe > /proc/sys/kernel/sched_child_runs_first 2>/dev/null; echo ok"
                }
                "up_rate_limit_us" -> {
                    val safe = value.coerceIn(0L, 20000L)
                    "for p in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/policy*/walt /sys/devices/system/cpu/cpufreq/policy*/sugov_ext; do [ -d \"\$p\" ] || continue; pol_id=\$(echo \"\$p\" | tr -dc '0-9'); if [ \"\$pol_id\" = \"0\" ]; then echo $safe > \"\$p/up_rate_limit_us\" 2>/dev/null; else echo ${Math.min(safe, 1000L)} > \"\$p/up_rate_limit_us\" 2>/dev/null; fi; done; echo ok"
                }
                "down_rate_limit_us" -> {
                    val safe = value.coerceIn(500L, 40000L)
                    "for p in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/policy*/walt /sys/devices/system/cpu/cpufreq/policy*/sugov_ext; do [ -d \"\$p\" ] || continue; pol_id=\$(echo \"\$p\" | tr -dc '0-9'); if [ \"\$pol_id\" = \"0\" ]; then echo $safe > \"\$p/down_rate_limit_us\" 2>/dev/null; else echo ${Math.max(500L, safe / 2)} > \"\$p/down_rate_limit_us\" 2>/dev/null; fi; done; echo ok"
                }
                "sched_energy_aware" -> {
                    val safe = if (value > 0) 1 else 0
                    "[ -f /proc/sys/kernel/sched_energy_aware ] && echo $safe > /proc/sys/kernel/sched_energy_aware 2>/dev/null; [ -f /sys/devices/system/cpu/eas/enable ] && echo $safe > /sys/devices/system/cpu/eas/enable 2>/dev/null; echo ok"
                }
                "sched_big_task_rotation" -> {
                    val safe = if (value > 0) 1 else 0
                    "[ -f /proc/sys/kernel/sched_walt_rotate_big_tasks ] && echo $safe > /proc/sys/kernel/sched_walt_rotate_big_tasks 2>/dev/null; [ -f /proc/sys/kernel/sched_big_task_rotation ] && echo $safe > /proc/sys/kernel/sched_big_task_rotation 2>/dev/null; [ -f /proc/perfmgr/boost_ctrl/eas_ctrl/sched_big_task_rotation ] && echo $safe > /proc/perfmgr/boost_ctrl/eas_ctrl/sched_big_task_rotation 2>/dev/null; echo ok"
                }
                "sched_cstate_aware" -> {
                    val safe = if (value > 0) 1 else 0
                    "[ -f /proc/sys/kernel/sched_cstate_aware ] && echo $safe > /proc/sys/kernel/sched_cstate_aware 2>/dev/null; echo ok"
                }
                "iowait_boost_enable" -> {
                    val safe = if (value > 0) 1 else 0
                    "for iow in /sys/devices/system/cpu/cpufreq/policy*/schedutil/iowait_boost_enable /sys/devices/system/cpu/cpufreq/policy*/sugov_ext/iowait_boost_enable /sys/devices/system/cpu/cpufreq/policy*/walt/iowait_boost_enable; do [ -f \"\$iow\" ] && echo $safe > \"\$iow\" 2>/dev/null; done; echo ok"
                }
                "sched_boost" -> {
                    val safe = value.coerceIn(0L, 3L)
                    "[ -f /proc/sys/kernel/sched_boost ] && echo $safe > /proc/sys/kernel/sched_boost 2>/dev/null; echo ok"
                }
                "uclamp_min" -> {
                    val pct = if (value <= 100L) value.coerceIn(0L, 100L) else ((value * 100L) / 1024L).coerceIn(0L, 100L)
                    val uclampRaw = if (value <= 100L) ((pct * 1024L) / 100L).coerceIn(0L, 1024L) else value.coerceIn(0L, 1024L)
                    val stuneBoost = pct.coerceIn(0L, 100L)
                    val prefIdle = if (pct >= 10L) 1 else 0
                    val taBoost = pct.coerceIn(0L, 100L)
                    val fgBoostVal = (pct / 3L).coerceIn(0L, 35L)
                    val fgUclampRaw = ((fgBoostVal * 1024L) / 100L).coerceIn(0L, 1024L)
                    "[ -f /proc/sys/kernel/sched_uclamp_util_min ] && echo $uclampRaw > /proc/sys/kernel/sched_uclamp_util_min 2>/dev/null; [ -f /proc/sys/kernel/sched_util_clamp_min ] && echo $uclampRaw > /proc/sys/kernel/sched_util_clamp_min 2>/dev/null; [ -f /dev/cpuctl/top-app/cpu.uclamp.min ] && (echo $pct > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null || echo $uclampRaw > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null); [ -f /dev/cpuctl/foreground/cpu.uclamp.min ] && (echo $fgBoostVal > /dev/cpuctl/foreground/cpu.uclamp.min 2>/dev/null || echo $fgUclampRaw > /dev/cpuctl/foreground/cpu.uclamp.min 2>/dev/null); [ -d /dev/stune/top-app ] && echo $stuneBoost > /dev/stune/top-app/schedtune.boost 2>/dev/null && echo $prefIdle > /dev/stune/top-app/schedtune.prefer_idle 2>/dev/null; [ -d /dev/stune/foreground ] && echo $fgBoostVal > /dev/stune/foreground/schedtune.boost 2>/dev/null; [ -f /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost ] && echo $taBoost > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost 2>/dev/null; [ -f /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost ] && echo $fgBoostVal > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost 2>/dev/null; echo ok"
                }
                "uclamp_max" -> {
                    val safe = value.coerceIn(0L, 1024L)
                    val pctMax = ((safe * 100L) / 1024L).coerceIn(10L, 100L)
                    "[ -f /proc/sys/kernel/sched_uclamp_util_max ] && echo $safe > /proc/sys/kernel/sched_uclamp_util_max 2>/dev/null; [ -f /proc/sys/kernel/sched_util_clamp_max ] && echo $safe > /proc/sys/kernel/sched_util_clamp_max 2>/dev/null; [ -f /dev/cpuctl/top-app/cpu.uclamp.max ] && (echo $pctMax > /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null || echo $safe > /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null); echo ok"
                }
                "sched_upmigrate" -> {
                    val safe = value.coerceIn(40L, 100L)
                    "if [ -f /proc/sys/kernel/sched_upmigrate ]; then cur=\$(cat /proc/sys/kernel/sched_upmigrate 2>/dev/null); if [ \$(echo \"\$cur\" | wc -w) -gt 1 ]; then sec=\$(echo \"\$cur\" | awk '{print \$2}'); [ \"$safe\" -ge \"\$sec\" ] && sec=\$((safe + 10)); echo \"$safe \$sec\" > /proc/sys/kernel/sched_upmigrate 2>/dev/null; else echo $safe > /proc/sys/kernel/sched_upmigrate 2>/dev/null; fi; fi; echo ok"
                }
                "sched_downmigrate" -> {
                    val safe = value.coerceIn(20L, 95L)
                    "if [ -f /proc/sys/kernel/sched_downmigrate ]; then cur=\$(cat /proc/sys/kernel/sched_downmigrate 2>/dev/null); if [ \$(echo \"\$cur\" | wc -w) -gt 1 ]; then sec=\$(echo \"\$cur\" | awk '{print \$2}'); [ \"$safe\" -ge \"\$sec\" ] && sec=\$((safe + 15)); echo \"$safe \$sec\" > /proc/sys/kernel/sched_downmigrate 2>/dev/null; else echo $safe > /proc/sys/kernel/sched_downmigrate 2>/dev/null; fi; fi; echo ok"
                }
                "sched_init_task_load" -> {
                    val safe = value.coerceIn(5L, 100L)
                    "[ -f /proc/sys/kernel/sched_init_task_load ] && echo $safe > /proc/sys/kernel/sched_init_task_load 2>/dev/null; echo ok"
                }
                "sched_spill_nr_run" -> {
                    val safe = value.coerceIn(1L, 20L)
                    "[ -f /proc/sys/kernel/sched_spill_nr_run ] && echo $safe > /proc/sys/kernel/sched_spill_nr_run 2>/dev/null; echo ok"
                }
                "sched_spill_load" -> {
                    val safe = value.coerceIn(50L, 100L)
                    "[ -f /proc/sys/kernel/sched_spill_load ] && echo $safe > /proc/sys/kernel/sched_spill_load 2>/dev/null; echo ok"
                }
                "sched_schedstats" -> {
                    val safe = if (value > 0) 1 else 0
                    "[ -f /proc/sys/kernel/sched_schedstats ] && echo $safe > /proc/sys/kernel/sched_schedstats 2>/dev/null; echo ok"
                }
                "sched_tunable_scaling" -> {
                    val safe = value.coerceIn(0L, 2L)
                    "[ -f /proc/sys/kernel/sched_tunable_scaling ] && echo $safe > /proc/sys/kernel/sched_tunable_scaling 2>/dev/null; echo ok"
                }
                "sched_rt_runtime_us" -> {
                    val safe = value.coerceIn(500000L, 1000000L)
                    "[ -f /proc/sys/kernel/sched_rt_runtime_us ] && echo $safe > /proc/sys/kernel/sched_rt_runtime_us 2>/dev/null; echo ok"
                }
                "apply_on_boot" -> {
                    context?.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                        ?.edit()
                        ?.putBoolean("apply_on_boot", value == 1L)
                        ?.apply()
                    return@withContext true
                }
                else -> return@withContext false
            }
            val ok = Shell.cmd(cmd).exec().out.firstOrNull()?.trim() == "ok"
            if (ok && context != null) {
                val editor = context.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE).edit()
                editor.putLong(tunable, value)
                if (tunable != "apply_on_boot") {
                    editor.putString("active_preset", "custom")
                }
                editor.apply()
            }
            ok
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setPpmPolicy(policyIdx: Int, enabled: Boolean, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        val valStr = if (enabled) "1" else "0"
        val ynStr = if (enabled) "Y" else "N"
        val extraThermal = if (policyIdx == 4) {
            """
                [ -f /sys/kernel/fpsgo/fbt/thrm_limit_cpu ] && echo $valStr > /sys/kernel/fpsgo/fbt/thrm_limit_cpu 2>/dev/null
                [ -f /sys/kernel/eara_thermal/enable ] && echo $valStr > /sys/kernel/eara_thermal/enable 2>/dev/null
                [ -f /sys/module/msm_thermal/core_control/enabled ] && echo $valStr > /sys/module/msm_thermal/core_control/enabled 2>/dev/null
                [ -f /sys/module/msm_thermal/parameters/enabled ] && echo $ynStr > /sys/module/msm_thermal/parameters/enabled 2>/dev/null
            """.trimIndent()
        } else ""
        val script = """
            if [ -f /proc/ppm/policy_status ]; then
                chmod 664 /proc/ppm/policy_status 2>/dev/null
                echo "$policyIdx $valStr" > /proc/ppm/policy_status 2>/dev/null
            fi
            $extraThermal
            echo "ok"
        """.trimIndent()
        try {
            val res = Shell.cmd(script).exec()
            val ok = res.isSuccess && res.out.any { it.trim() == "ok" }
            if (ok && context != null) {
                context.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("ppm_policy_$policyIdx", enabled)
                    .apply()
            }
            ok
        } catch (_: Exception) { false }
    }

    suspend fun setUniversalBusBandwidthProfile(profile: String, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        val cleanProfile = when (profile.lowercase()) {
            "eco", "efficient", "powersave" -> "eco"
            "balanced", "balance" -> "balanced"
            "max", "performance", "extreme" -> "max"
            else -> "auto"
        }
        val (cciVal, ddrOppVal, cmForceVal, pllVal) = when (cleanProfile) {
            "max" -> listOf("1", "0", "1", "0")
            "balanced" -> listOf("1", "2", "0", "0")
            "eco" -> listOf("0", "3", "0", "1")
            else -> listOf("0", "-1", "0", "1")
        }
        val vcoreOppVal = if (cleanProfile == "max") "0" else "-1"
        val script = """
            if [ -f /proc/cpufreq/cpufreq_cci_mode ]; then
                chmod 644 /proc/cpufreq/cpufreq_cci_mode 2>/dev/null
                echo "$cciVal" > /proc/cpufreq/cpufreq_cci_mode 2>/dev/null
            fi
            for dvfs in /sys/devices/platform/*dvfsrc*/helio-dvfsrc /sys/kernel/helio-dvfsrc; do
                [ -d "${'$'}dvfs" ] || continue
                [ -w "${'$'}dvfs/dvfsrc_req_ddr_opp" ] && echo "$ddrOppVal" > "${'$'}dvfs/dvfsrc_req_ddr_opp" 2>/dev/null
                [ -w "${'$'}dvfs/dvfsrc_force_vcore_dvfs_opp" ] && echo "$vcoreOppVal" > "${'$'}dvfs/dvfsrc_force_vcore_dvfs_opp" 2>/dev/null
            done
            [ -f /proc/perfmgr/boost_ctrl/dram_ctrl/ddr ] && echo "$ddrOppVal" > /proc/perfmgr/boost_ctrl/dram_ctrl/ddr 2>/dev/null
            if [ -f /proc/cm_mgr/dbg_cm_mgr ]; then
                echo "cm_mgr_perf_force_enable $cmForceVal" > /proc/cm_mgr/dbg_cm_mgr 2>/dev/null
                echo "cm_mgr_dram_opp $ddrOppVal" > /proc/cm_mgr/dbg_cm_mgr 2>/dev/null
            fi
            [ -f /proc/cpuidle/control/armpll_mode ] && echo "$pllVal" > /proc/cpuidle/control/armpll_mode 2>/dev/null
            [ -f /proc/cpuidle/control/buck_mode ] && echo "$pllVal" > /proc/cpuidle/control/buck_mode 2>/dev/null
            for bw in /sys/class/devfreq/*; do
                [ -d "${'$'}bw" ] || continue
                case "${'$'}bw" in *kgsl-3d0*|*kgsl-3d*) continue ;; esac
                if [ "$cleanProfile" = "max" ]; then
                    case "${'$'}bw" in
                        *latfloor*) [ -w "${'$'}bw/governor" ] && echo "compute" > "${'$'}bw/governor" 2>/dev/null ;;
                        *) [ -w "${'$'}bw/governor" ] && echo "performance" > "${'$'}bw/governor" 2>/dev/null ;;
                    esac
                    if [ -s "${'$'}bw/available_frequencies" ]; then
                        hi_f=${'$'}(tr ' ' '
' < "${'$'}bw/available_frequencies" | sort -n | tail -n1)
                        [ -n "${'$'}hi_f" ] && echo "${'$'}hi_f" > "${'$'}bw/max_freq" 2>/dev/null && echo "${'$'}hi_f" > "${'$'}bw/min_freq" 2>/dev/null
                    fi
                elif [ "$cleanProfile" = "eco" ]; then
                    case "${'$'}bw" in
                        *latfloor*) [ -w "${'$'}bw/governor" ] && echo "compute" > "${'$'}bw/governor" 2>/dev/null ;;
                        *) [ -w "${'$'}bw/governor" ] && echo "powersave" > "${'$'}bw/governor" 2>/dev/null ;;
                    esac
                    if [ -s "${'$'}bw/available_frequencies" ]; then
                        lo_f=${'$'}(tr ' ' '
' < "${'$'}bw/available_frequencies" | sort -n | sed '/^${'$'}/d' | head -n1)
                        hi_f=${'$'}(tr ' ' '
' < "${'$'}bw/available_frequencies" | sort -n | tail -n1)
                        [ -n "${'$'}lo_f" ] && echo "${'$'}lo_f" > "${'$'}bw/min_freq" 2>/dev/null
                        [ -n "${'$'}hi_f" ] && echo "${'$'}hi_f" > "${'$'}bw/max_freq" 2>/dev/null
                    fi
                else
                    case "${'$'}bw" in
                        *latfloor*) [ -w "${'$'}bw/governor" ] && echo "compute" > "${'$'}bw/governor" 2>/dev/null ;;
                        *cpubw*|*llccbw*|*cpu-ddr-bw*|*ddr-bw*) [ -w "${'$'}bw/governor" ] && echo "bw_hwmon" > "${'$'}bw/governor" 2>/dev/null ;;
                        *l3-cpu*|*memlat-cpu*|*cpu-ddr-lat*) [ -w "${'$'}bw/governor" ] && echo "mem_latency" > "${'$'}bw/governor" 2>/dev/null ;;
                        *gpubw*) [ -w "${'$'}bw/governor" ] && echo "bw_vbif" > "${'$'}bw/governor" 2>/dev/null ;;
                        *mincpubw*) [ -w "${'$'}bw/governor" ] && echo "cpufreq" > "${'$'}bw/governor" 2>/dev/null ;;
                        *ufshc*|*mif*|*int*|*dsu*|*ddr*) [ -w "${'$'}bw/governor" ] && (echo "simple_ondemand" > "${'$'}bw/governor" 2>/dev/null || echo "interactive" > "${'$'}bw/governor" 2>/dev/null) ;;
                    esac
                    if [ -s "${'$'}bw/available_frequencies" ]; then
                        lo_f=${'$'}(tr ' ' '
' < "${'$'}bw/available_frequencies" | sort -n | sed '/^${'$'}/d' | head -n1)
                        hi_f=${'$'}(tr ' ' '
' < "${'$'}bw/available_frequencies" | sort -n | tail -n1)
                        if [ "$cleanProfile" = "balanced" ]; then
                            cnt_f=${'$'}(tr ' ' '
' < "${'$'}bw/available_frequencies" | sed '/^${'$'}/d' | wc -l)
                            mid_idx=${'$'}(((cnt_f + 1) / 2))
                            mid_f=${'$'}(tr ' ' '
' < "${'$'}bw/available_frequencies" | sort -n | sed '/^${'$'}/d' | sed -n "${'$'}{mid_idx}p")
                            [ -n "${'$'}mid_f" ] && lo_f="${'$'}mid_f"
                        fi
                        [ -n "${'$'}hi_f" ] && echo "${'$'}hi_f" > "${'$'}bw/max_freq" 2>/dev/null
                        [ -n "${'$'}lo_f" ] && echo "${'$'}lo_f" > "${'$'}bw/min_freq" 2>/dev/null
                    fi
                fi
            done
            mkdir -p /data/adb/lynx 2>/dev/null
            echo "$cleanProfile" > /data/adb/lynx/bus_profile 2>/dev/null
            echo "ok"
        """.trimIndent()
        try {
            val res = Shell.cmd(script).exec()
            val ok = res.isSuccess && res.out.any { it.trim() == "ok" }
            if (ok && context != null) {
                context.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("bus_bandwidth_profile", cleanProfile)
                    .putBoolean("mtk_cci_ddr_boost", cciVal == "1")
                    .apply()
            }
            ok
        } catch (_: Exception) { false }
    }

    suspend fun setUniversalTouchBoost(enabled: Boolean, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        val valStr = if (enabled) "1" else "0"
        val boolStr = if (enabled) "true" else "false"
        val easBoost = if (enabled) "100" else "80"
        val touchOpp = if (enabled) "0" else "2"
        val qcomMs = if (enabled) "60" else "0"
        val script = """
            if [ -f /proc/perfmgr/tchbst/user/usrtch ]; then
                echo "enable 1" > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null
                echo "eas_boost $easBoost" > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null
                echo "touch_opp $touchOpp" > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null
                echo "deboost_when_render 0" > /proc/perfmgr/tchbst/user/usrtch 2>/dev/null
            fi
            for node in /sys/devices/platform/goodix_ts.0/game_mode \
                        /sys/devices/platform/goodix_ts.*/game_mode \
                        /sys/devices/platform/tp_wake_switch/game_mode \
                        /sys/class/touch/touch_dev/touch_game_mode \
                        /sys/devices/virtual/touch/touch_dev/bump_sample_rate \
                        /proc/touchscreen/game_mode \
                        /sys/module/msm_performance/parameters/touchboost \
                        /sys/power/pbm/touchboost \
                        /proc/perfmgr/boost_ctrl/eas_ctrl/touch_boost \
                        /sys/module/perfmgr/parameters/touch_boost \
                        /proc/ppm/policy/touch_boost; do
                if [ -e "${'$'}node" ]; then
                    chmod 664 "${'$'}node" 2>/dev/null
                    echo "$valStr" > "${'$'}node" 2>/dev/null
                fi
            done
            [ -f /sys/module/cpu_boost/parameters/input_boost_ms ] && echo "$qcomMs" > /sys/module/cpu_boost/parameters/input_boost_ms 2>/dev/null
            [ -f /sys/module/cpu_boost/parameters/sched_boost_on_input ] && echo "$valStr" > /sys/module/cpu_boost/parameters/sched_boost_on_input 2>/dev/null
            setprop vendor.perf.gestureFlingBoost.enable "$valStr" 2>/dev/null
            setprop vendor.perf.gestureflingboost.enable "$boolStr" 2>/dev/null
            setprop persist.sys.lynx.touchboost "$valStr" 2>/dev/null
            mkdir -p /data/adb/lynx 2>/dev/null
            echo "$valStr" > /data/adb/lynx/qcom_touch_boost 2>/dev/null
            echo "ok"
        """.trimIndent()
        try {
            val res = Shell.cmd(script).exec()
            val ok = res.isSuccess && res.out.any { it.trim() == "ok" }
            val effCtx = context ?: LynxRepository.appContext
            if (ok && effCtx != null) {
                effCtx.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("universal_touch_boost", enabled)
                    .putBoolean("qcom_touchboost", enabled)
                    .apply()
            }
            ok
        } catch (_: Exception) { false }
    }

    suspend fun setUniversalAntiThrottlingGuard(enabled: Boolean, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        val ppmLimitVal = if (enabled) "0" else "1"
        val ppmBoostVal = if (enabled) "1" else "0"
        val cfpVal = if (enabled) "0" else "1"
        val sysLimVal = if (enabled) "1" else "0"
        val imaxTherm = if (enabled) "0" else "1"
        val guardFlag = if (enabled) "1" else "0"
        val script = """
            if [ -f /proc/ppm/policy_status ]; then
                chmod 664 /proc/ppm/policy_status 2>/dev/null
                echo "2 $ppmLimitVal" > /proc/ppm/policy_status 2>/dev/null
                echo "3 $ppmLimitVal" > /proc/ppm/policy_status 2>/dev/null
                echo "5 $ppmLimitVal" > /proc/ppm/policy_status 2>/dev/null
                echo "9 $ppmBoostVal" > /proc/ppm/policy_status 2>/dev/null
            fi
            [ -f /proc/perfmgr/boost_ctrl/cpu_ctrl/cfp_enable ] && echo "$cfpVal" > /proc/perfmgr/boost_ctrl/cpu_ctrl/cfp_enable 2>/dev/null
            [ -f /proc/perfmgr/syslimiter/syslimiter_force_disable ] && echo "$sysLimVal" > /proc/perfmgr/syslimiter/syslimiter_force_disable 2>/dev/null
            [ -f /proc/cpufreq/cpufreq_imax_enable ] && echo "0" > /proc/cpufreq/cpufreq_imax_enable 2>/dev/null
            [ -f /proc/cpufreq/cpufreq_imax_thermal_protect ] && echo "$imaxTherm" > /proc/cpufreq/cpufreq_imax_thermal_protect 2>/dev/null
            for ctl in /sys/devices/system/cpu/cpu*/core_ctl; do
                [ -d "${'$'}ctl" ] || continue
                if [ "$guardFlag" = "1" ]; then
                    max_c=${'$'}(cat "${'$'}ctl/max_cpus" 2>/dev/null || echo 4)
                    [ -w "${'$'}ctl/min_cpus" ] && echo "${'$'}max_c" > "${'$'}ctl/min_cpus" 2>/dev/null
                    [ -w "${'$'}ctl/not_preferred" ] && echo "1 1 1 1" > "${'$'}ctl/not_preferred" 2>/dev/null
                    [ -w "${'$'}ctl/enable" ] && echo "0" > "${'$'}ctl/enable" 2>/dev/null
                else
                    [ -w "${'$'}ctl/enable" ] && echo "1" > "${'$'}ctl/enable" 2>/dev/null
                    [ -w "${'$'}ctl/min_cpus" ] && echo "2" > "${'$'}ctl/min_cpus" 2>/dev/null
                    [ -w "${'$'}ctl/not_preferred" ] && echo "0 0 0 0" > "${'$'}ctl/not_preferred" 2>/dev/null
                fi
            done
            mkdir -p /data/adb/lynx 2>/dev/null
            echo "$guardFlag" > /data/adb/lynx/anti_throttling_guard 2>/dev/null
            echo "ok"
        """.trimIndent()
        try {
            val res = Shell.cmd(script).exec()
            val ok = res.isSuccess && res.out.any { it.trim() == "ok" }
            if (ok && context != null) {
                context.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("anti_throttling_guard", enabled)
                    .putBoolean("mtk_dlpt_imax_bypass", enabled)
                    .apply()
            }
            ok
        } catch (_: Exception) { false }
    }

    suspend fun setMtkInterconnectBusBoost(enabled: Boolean, context: Context? = null): Boolean =
        setUniversalBusBandwidthProfile(if (enabled) "max" else "auto", context)

    suspend fun setMtkCpuPowerMode(mode: Int, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        val safeMode = when (mode) {
            1 -> 1
            3 -> 3
            else -> 0
        }
        val perfEn = if (safeMode == 3) "1" else "0"
        val schedBoostVal = when (safeMode) {
            3 -> "2"
            1 -> "0"
            else -> "1"
        }
        val littleLoadBoost = if (safeMode == 1) "-6" else "0"
        val bigLoadBoost = when (safeMode) {
            3 -> "15"
            1 -> "-6"
            else -> "0"
        }
        val script = """
            if [ -f /proc/cpufreq/cpufreq_power_mode ]; then
                chmod 644 /proc/cpufreq/cpufreq_power_mode 2>/dev/null
                echo "$safeMode" > /proc/cpufreq/cpufreq_power_mode 2>/dev/null
            fi
            [ -f /sys/devices/system/cpu/perf/enable ] && echo "$perfEn" > /sys/devices/system/cpu/perf/enable 2>/dev/null
            [ -f /proc/sys/kernel/sched_boost ] && echo "$schedBoostVal" > /proc/sys/kernel/sched_boost 2>/dev/null
            little_cores=${'$'}(cat /sys/devices/system/cpu/cpufreq/policy0/related_cpus 2>/dev/null | tr -s '[:space:]' ' ')
            [ -z "${'$'}little_cores" ] && little_cores="0 1 2 3"
            big_cores=${'$'}(cat /sys/devices/system/cpu/cpufreq/policy[1-9]*/related_cpus 2>/dev/null | tr -s '[:space:]' ' ')
            [ -z "${'$'}big_cores" ] && big_cores="4 5 6 7"
            for c in ${'$'}little_cores; do
                [ -w "/sys/devices/system/cpu/cpu${'$'}c/sched_load_boost" ] && echo "$littleLoadBoost" > "/sys/devices/system/cpu/cpu${'$'}c/sched_load_boost" 2>/dev/null
            done
            for c in ${'$'}big_cores; do
                [ -w "/sys/devices/system/cpu/cpu${'$'}c/sched_load_boost" ] && echo "$bigLoadBoost" > "/sys/devices/system/cpu/cpu${'$'}c/sched_load_boost" 2>/dev/null
            done
            echo "ok"
        """.trimIndent()
        try {
            val res = Shell.cmd(script).exec()
            val ok = res.isSuccess && res.out.any { it.trim() == "ok" }
            if (ok && context != null) {
                context.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("mtk_cpu_power_mode", safeMode)
                    .apply()
            }
            ok
        } catch (_: Exception) { false }
    }

    suspend fun setMtkDlptImaxBypass(enabled: Boolean, context: Context? = null): Boolean =
        setUniversalAntiThrottlingGuard(enabled, context)

    suspend fun setQcomDevfreqBusBoost(enabled: Boolean, context: Context? = null): Boolean =
        setUniversalBusBandwidthProfile(if (enabled) "max" else "auto", context)

    suspend fun readProcessDetail(pid: Int, fallbackName: String = "", fallbackPkg: String = ""): CpuProcessDetail =
        LynxCpuProcessControlRepository.readProcessDetail(pid, fallbackName, fallbackPkg)
    suspend fun setProcessPriority(pid: Int, nice: Int): Boolean = LynxCpuProcessControlRepository.setProcessPriority(pid, nice)
    suspend fun setProcessAffinity(pid: Int, hexMask: String): Boolean = LynxCpuProcessControlRepository.setProcessAffinity(pid, hexMask)
    suspend fun terminateProcess(pid: Int, packageName: String): Boolean = LynxCpuProcessControlRepository.terminateProcess(pid, packageName)

    suspend fun setQcomTouchboost(enabled: Boolean, context: Context? = null): Boolean =
        setUniversalTouchBoost(enabled, context)

    suspend fun setQcomInputBoost(freq: Long, durationMs: Int, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        val safeMs = durationMs.coerceIn(0, 500)
        val script = """
            [ -f /sys/module/cpu_boost/parameters/input_boost_freq ] && echo "$freq" > /sys/module/cpu_boost/parameters/input_boost_freq 2>/dev/null
            [ -f /sys/module/cpu_boost/parameters/input_boost_ms ] && echo "$safeMs" > /sys/module/cpu_boost/parameters/input_boost_ms 2>/dev/null
            echo "ok"
        """.trimIndent()
        try {
            val res = Shell.cmd(script).exec()
            val ok = res.isSuccess && res.out.any { it.trim() == "ok" }
            if (ok && context != null) {
                context.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("qcom_input_boost_freq", freq)
                    .putInt("qcom_input_boost_ms", safeMs)
                    .apply()
            }
            ok
        } catch (_: Exception) { false }
    }

    suspend fun setSchedulerHysteresis(upmigrate: Int, downmigrate: Int, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val safeUp = upmigrate.coerceIn(40, 100)
            val safeDown = downmigrate.coerceIn(20, safeUp - 5)
            val script = """
                [ -f /proc/sys/kernel/sched_upmigrate ] && echo $safeUp > /proc/sys/kernel/sched_upmigrate 2>/dev/null
                [ -f /proc/sys/kernel/sched_downmigrate ] && echo $safeDown > /proc/sys/kernel/sched_downmigrate 2>/dev/null
                echo ok
            """.trimIndent()
            val ok = Shell.cmd(script).exec().out.firstOrNull()?.trim() == "ok"
            if (ok && context != null) {
                context.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("sched_upmigrate", safeUp)
                    .putInt("sched_downmigrate", safeDown)
                    .putString("active_preset", "custom")
                    .apply()
            }
            ok
        } catch (e: Exception) {
            false
        }
    }

    suspend fun applySavedSchedulerConfig(context: Context): Boolean = LynxCpuSchedulerProfilesRepository.applySavedSchedulerConfig(context)
    suspend fun applySchedulerPreset(preset: String, context: Context? = null): Boolean =
        LynxCpuSchedulerProfilesRepository.applySchedulerPreset(preset, context)

    suspend fun setSchedLoadBoost(littleBoost: Int, bigBoost: Int, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        val safeLit = littleBoost.coerceIn(-100, 100)
        val safeBig = bigBoost.coerceIn(-100, 100)
        val script = """
            little_cores=${'$'}(cat /sys/devices/system/cpu/cpufreq/policy0/related_cpus 2>/dev/null | tr -s '[:space:]' ' ')
            [ -z "${'$'}little_cores" ] && little_cores="0 1 2 3"
            big_cores=${'$'}(cat /sys/devices/system/cpu/cpufreq/policy[1-9]*/related_cpus 2>/dev/null | tr -s '[:space:]' ' ')
            [ -z "${'$'}big_cores" ] && big_cores="4 5 6 7"
            for c in ${'$'}little_cores; do
                [ -w "/sys/devices/system/cpu/cpu${'$'}c/sched_load_boost" ] && echo "$safeLit" > "/sys/devices/system/cpu/cpu${'$'}c/sched_load_boost" 2>/dev/null
            done
            for c in ${'$'}big_cores; do
                [ -w "/sys/devices/system/cpu/cpu${'$'}c/sched_load_boost" ] && echo "$safeBig" > "/sys/devices/system/cpu/cpu${'$'}c/sched_load_boost" 2>/dev/null
            done
            echo "ok"
        """.trimIndent()
        try {
            val res = Shell.cmd(script).exec()
            val ok = res.isSuccess && res.out.any { it.trim() == "ok" }
            if (ok && context != null) {
                context.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("sched_load_boost_little", safeLit)
                    .putInt("sched_load_boost_big", safeBig)
                    .apply()
            }
            ok
        } catch (_: Exception) { false }
    }

}
