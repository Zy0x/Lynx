package com.noir.lynx.data
import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal object LynxCpuSchedulerProfilesRepository {
    suspend fun applySavedSchedulerConfig(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
            val applyOnBoot = prefs.getBoolean("apply_on_boot", false)
            if (!applyOnBoot) return@withContext false

            val savedMode = prefs.getString("active_sched_mode", null)
            if (savedMode != null) {
                LynxCpuSchedulerControlRepository.setSchedulerArchitectureMode(savedMode, null)
            }

            val up = prefs.getInt("sched_upmigrate", -1)
            val down = prefs.getInt("sched_downmigrate", -1)
            if (up > 0 && down > 0) {
                LynxCpuSchedulerControlRepository.setSchedulerHysteresis(up, down, null)
            }
            val allKeys = prefs.all
            allKeys.forEach { (k, v) ->
                if (k != "apply_on_boot" && k != "sched_upmigrate" && k != "sched_downmigrate") {
                    val lVal = when (v) {
                        is Long -> v
                        is Int -> v.toLong()
                        is Boolean -> if (v) 1L else 0L
                        else -> null
                    }
                    if (lVal != null) {
                        LynxCpuSchedulerControlRepository.setSchedulerTunable(k, lVal, null)
                    }
                }
            }
            true
        } catch (_: Exception) { false }
    }

    suspend fun applySchedulerPreset(preset: String, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val script = when (preset.lowercase()) {
                "extreme" -> """
                    # CFS Latency & Preemption (Extreme Anti-Stutter & Frame Pacing)
                    [ -f /proc/sys/kernel/sched_latency_ns ] && echo 10000000 > /proc/sys/kernel/sched_latency_ns 2>/dev/null
                    [ -f /proc/sys/kernel/sched_min_granularity_ns ] && echo 3000000 > /proc/sys/kernel/sched_min_granularity_ns 2>/dev/null
                    [ -f /proc/sys/kernel/sched_wakeup_granularity_ns ] && echo 2000000 > /proc/sys/kernel/sched_wakeup_granularity_ns 2>/dev/null
                    [ -f /proc/sys/kernel/sched_migration_cost_ns ] && echo 500000 > /proc/sys/kernel/sched_migration_cost_ns 2>/dev/null
                    [ -f /proc/sys/kernel/sched_nr_migrate ] && echo 32 > /proc/sys/kernel/sched_nr_migrate 2>/dev/null
                    [ -f /proc/sys/kernel/sched_child_runs_first ] && echo 1 > /proc/sys/kernel/sched_child_runs_first 2>/dev/null
                    [ -f /proc/sys/kernel/sched_cstate_aware ] && echo 0 > /proc/sys/kernel/sched_cstate_aware 2>/dev/null
                    [ -f /proc/sys/kernel/sched_schedstats ] && echo 0 > /proc/sys/kernel/sched_schedstats 2>/dev/null
                    [ -f /sys/module/workqueue/parameters/power_efficient ] && echo N > /sys/module/workqueue/parameters/power_efficient 2>/dev/null
                    # Unrestricted Real-Time Throttling (Never throttle render/game threads)
                    [ -f /proc/sys/kernel/sched_rt_runtime_us ] && echo -1 > /proc/sys/kernel/sched_rt_runtime_us 2>/dev/null
                    [ -f /proc/sys/kernel/sched_rt_period_us ] && echo 1000000 > /proc/sys/kernel/sched_rt_period_us 2>/dev/null
                    # Schedutil / WALT / Sugov_ext Clock Dynamics (Asymmetric: Little 0µs/10ms, Big 0µs/2ms)
                    for p in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/policy*/walt /sys/devices/system/cpu/cpufreq/policy*/sugov_ext; do
                        [ -d "${'$'}p" ] || continue
                        pol_id=${'$'}(echo "${'$'}p" | tr -dc '0-9')
                        if [ "${'$'}pol_id" = "0" ]; then
                            echo 0 > "${'$'}p/up_rate_limit_us" 2>/dev/null
                            echo 10000 > "${'$'}p/down_rate_limit_us" 2>/dev/null
                        else
                            echo 0 > "${'$'}p/up_rate_limit_us" 2>/dev/null
                            echo 2000 > "${'$'}p/down_rate_limit_us" 2>/dev/null
                        fi
                        [ -f "${'$'}p/rate_limit_us" ] && echo 0 > "${'$'}p/rate_limit_us" 2>/dev/null
                    done
                    # EAS / Schedtune Boost & Uclamp Floor 820 (80%)
                    [ -f /proc/sys/kernel/sched_boost ] && echo 2 > /proc/sys/kernel/sched_boost 2>/dev/null
                    [ -f /proc/sys/kernel/sched_uclamp_util_min ] && echo 820 > /proc/sys/kernel/sched_uclamp_util_min 2>/dev/null
                    [ -f /proc/sys/kernel/sched_util_clamp_min ] && echo 820 > /proc/sys/kernel/sched_util_clamp_min 2>/dev/null
                    [ -f /proc/sys/kernel/sched_uclamp_util_max ] && echo 1024 > /proc/sys/kernel/sched_uclamp_util_max 2>/dev/null
                    [ -f /proc/sys/kernel/sched_util_clamp_max ] && echo 1024 > /proc/sys/kernel/sched_util_clamp_max 2>/dev/null
                    [ -f /dev/cpuctl/top-app/cpu.uclamp.min ] && (echo 820 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null || echo 80 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null)
                    [ -f /dev/cpuctl/top-app/cpu.uclamp.max ] && (echo 1024 > /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null || echo max > /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null)
                    [ -f /dev/cpuctl/top-app/cpu.uclamp.latency_sensitive ] && echo 1 > /dev/cpuctl/top-app/cpu.uclamp.latency_sensitive 2>/dev/null
                    [ -f /dev/cpuctl/foreground/cpu.uclamp.min ] && (echo 256 > /dev/cpuctl/foreground/cpu.uclamp.min 2>/dev/null || echo 25 > /dev/cpuctl/foreground/cpu.uclamp.min 2>/dev/null)
                    [ -d /dev/stune/top-app ] && echo 80 > /dev/stune/top-app/schedtune.boost 2>/dev/null && echo 1 > /dev/stune/top-app/schedtune.prefer_idle 2>/dev/null && [ -f /dev/stune/top-app/schedtune.prefer_high_cap ] && echo 1 > /dev/stune/top-app/schedtune.prefer_high_cap 2>/dev/null
                    [ -d /dev/stune/foreground ] && echo 25 > /dev/stune/foreground/schedtune.boost 2>/dev/null && echo 1 > /dev/stune/foreground/schedtune.prefer_idle 2>/dev/null
                    [ -d /proc/perfmgr/boost_ctrl/eas_ctrl ] && echo 80 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost 2>/dev/null && echo 25 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost 2>/dev/null && echo 1 > /proc/perfmgr/boost_ctrl/eas_ctrl/sched_big_task_rotation 2>/dev/null && echo 200000 > /proc/perfmgr/boost_ctrl/eas_ctrl/m_sched_migrate_cost_n 2>/dev/null && echo 40000 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_schedplus_down_throttle 2>/dev/null && echo 500 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_schedplus_up_throttle 2>/dev/null
                    # HMP / WALT (Immediate big core dispatch & aggressive spillover)
                    [ -f /proc/sys/kernel/sched_upmigrate ] && echo 50 > /proc/sys/kernel/sched_upmigrate 2>/dev/null
                    [ -f /proc/sys/kernel/sched_downmigrate ] && echo 30 > /proc/sys/kernel/sched_downmigrate 2>/dev/null
                    [ -f /proc/sys/kernel/sched_init_task_load ] && echo 85 > /proc/sys/kernel/sched_init_task_load 2>/dev/null
                    [ -f /proc/sys/kernel/sched_spill_nr_run ] && echo 2 > /proc/sys/kernel/sched_spill_nr_run 2>/dev/null
                    [ -f /proc/sys/kernel/sched_spill_load ] && echo 75 > /proc/sys/kernel/sched_spill_load 2>/dev/null
                    echo ok
                """.trimIndent()
                "gaming" -> """
                    # CFS (Anti-Stutter & Frame Pacing)
                    [ -f /proc/sys/kernel/sched_latency_ns ] && echo 10000000 > /proc/sys/kernel/sched_latency_ns 2>/dev/null
                    [ -f /proc/sys/kernel/sched_min_granularity_ns ] && echo 3000000 > /proc/sys/kernel/sched_min_granularity_ns 2>/dev/null
                    [ -f /proc/sys/kernel/sched_wakeup_granularity_ns ] && echo 2000000 > /proc/sys/kernel/sched_wakeup_granularity_ns 2>/dev/null
                    [ -f /proc/sys/kernel/sched_migration_cost_ns ] && echo 500000 > /proc/sys/kernel/sched_migration_cost_ns 2>/dev/null
                    [ -f /proc/sys/kernel/sched_nr_migrate ] && echo 32 > /proc/sys/kernel/sched_nr_migrate 2>/dev/null
                    [ -f /proc/sys/kernel/sched_child_runs_first ] && echo 1 > /proc/sys/kernel/sched_child_runs_first 2>/dev/null
                    [ -f /proc/sys/kernel/sched_cstate_aware ] && echo 0 > /proc/sys/kernel/sched_cstate_aware 2>/dev/null
                    [ -f /proc/sys/kernel/sched_schedstats ] && echo 0 > /proc/sys/kernel/sched_schedstats 2>/dev/null
                    [ -f /proc/sys/kernel/sched_rt_runtime_us ] && echo 980000 > /proc/sys/kernel/sched_rt_runtime_us 2>/dev/null
                    [ -f /proc/sys/kernel/sched_rt_period_us ] && echo 1000000 > /proc/sys/kernel/sched_rt_period_us 2>/dev/null
                    [ -f /sys/module/workqueue/parameters/power_efficient ] && echo N > /sys/module/workqueue/parameters/power_efficient 2>/dev/null
                    # Schedutil / WALT / Sugov_ext Clock Dynamics (Asymmetric: Little 0µs/10ms, Big 0µs/5ms)
                    for p in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/policy*/walt /sys/devices/system/cpu/cpufreq/policy*/sugov_ext; do
                        [ -d "${'$'}p" ] || continue
                        pol_id=${'$'}(echo "${'$'}p" | tr -dc '0-9')
                        if [ "${'$'}pol_id" = "0" ]; then
                            echo 0 > "${'$'}p/up_rate_limit_us" 2>/dev/null
                            echo 10000 > "${'$'}p/down_rate_limit_us" 2>/dev/null
                        else
                            echo 0 > "${'$'}p/up_rate_limit_us" 2>/dev/null
                            echo 5000 > "${'$'}p/down_rate_limit_us" 2>/dev/null
                        fi
                    done
                    # EAS
                    [ -f /proc/sys/kernel/sched_boost ] && echo 1 > /proc/sys/kernel/sched_boost 2>/dev/null
                    [ -f /proc/sys/kernel/sched_uclamp_util_min ] && echo 512 > /proc/sys/kernel/sched_uclamp_util_min 2>/dev/null
                    [ -f /proc/sys/kernel/sched_util_clamp_min ] && echo 512 > /proc/sys/kernel/sched_util_clamp_min 2>/dev/null
                    [ -f /proc/sys/kernel/sched_uclamp_util_max ] && echo 1024 > /proc/sys/kernel/sched_uclamp_util_max 2>/dev/null
                    [ -f /proc/sys/kernel/sched_util_clamp_max ] && echo 1024 > /proc/sys/kernel/sched_util_clamp_max 2>/dev/null
                    [ -f /dev/cpuctl/top-app/cpu.uclamp.min ] && (echo 512 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null || echo 50 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null)
                    [ -f /dev/cpuctl/top-app/cpu.uclamp.max ] && (echo 1024 > /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null || echo max > /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null)
                    [ -f /dev/cpuctl/top-app/cpu.uclamp.latency_sensitive ] && echo 1 > /dev/cpuctl/top-app/cpu.uclamp.latency_sensitive 2>/dev/null
                    [ -f /dev/cpuctl/foreground/cpu.uclamp.min ] && (echo 154 > /dev/cpuctl/foreground/cpu.uclamp.min 2>/dev/null || echo 15 > /dev/cpuctl/foreground/cpu.uclamp.min 2>/dev/null)
                    [ -d /dev/stune/top-app ] && echo 50 > /dev/stune/top-app/schedtune.boost 2>/dev/null && echo 1 > /dev/stune/top-app/schedtune.prefer_idle 2>/dev/null && [ -f /dev/stune/top-app/schedtune.prefer_high_cap ] && echo 1 > /dev/stune/top-app/schedtune.prefer_high_cap 2>/dev/null
                    [ -d /dev/stune/foreground ] && echo 15 > /dev/stune/foreground/schedtune.boost 2>/dev/null && echo 0 > /dev/stune/foreground/schedtune.prefer_idle 2>/dev/null
                    [ -d /proc/perfmgr/boost_ctrl/eas_ctrl ] && echo 50 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost 2>/dev/null && echo 15 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost 2>/dev/null && echo 1 > /proc/perfmgr/boost_ctrl/eas_ctrl/sched_big_task_rotation 2>/dev/null && echo 200000 > /proc/perfmgr/boost_ctrl/eas_ctrl/m_sched_migrate_cost_n 2>/dev/null && echo 40000 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_schedplus_down_throttle 2>/dev/null && echo 500 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_schedplus_up_throttle 2>/dev/null
                    # HMP / WALT
                    [ -f /proc/sys/kernel/sched_upmigrate ] && echo 60 > /proc/sys/kernel/sched_upmigrate 2>/dev/null
                    [ -f /proc/sys/kernel/sched_downmigrate ] && echo 40 > /proc/sys/kernel/sched_downmigrate 2>/dev/null
                    [ -f /proc/sys/kernel/sched_init_task_load ] && echo 70 > /proc/sys/kernel/sched_init_task_load 2>/dev/null
                    [ -f /proc/sys/kernel/sched_spill_nr_run ] && echo 2 > /proc/sys/kernel/sched_spill_nr_run 2>/dev/null
                    [ -f /proc/sys/kernel/sched_spill_load ] && echo 75 > /proc/sys/kernel/sched_spill_load 2>/dev/null
                    echo ok
                """.trimIndent()
                "battery" -> """
                    # CFS
                    [ -f /proc/sys/kernel/sched_latency_ns ] && echo 20000000 > /proc/sys/kernel/sched_latency_ns 2>/dev/null
                    [ -f /proc/sys/kernel/sched_min_granularity_ns ] && echo 4000000 > /proc/sys/kernel/sched_min_granularity_ns 2>/dev/null
                    [ -f /proc/sys/kernel/sched_wakeup_granularity_ns ] && echo 4000000 > /proc/sys/kernel/sched_wakeup_granularity_ns 2>/dev/null
                    [ -f /proc/sys/kernel/sched_migration_cost_ns ] && echo 1000000 > /proc/sys/kernel/sched_migration_cost_ns 2>/dev/null
                    [ -f /proc/sys/kernel/sched_nr_migrate ] && echo 16 > /proc/sys/kernel/sched_nr_migrate 2>/dev/null
                    [ -f /proc/sys/kernel/sched_child_runs_first ] && echo 0 > /proc/sys/kernel/sched_child_runs_first 2>/dev/null
                    [ -f /proc/sys/kernel/sched_cstate_aware ] && echo 1 > /proc/sys/kernel/sched_cstate_aware 2>/dev/null
                    [ -f /proc/sys/kernel/sched_rt_runtime_us ] && echo 950000 > /proc/sys/kernel/sched_rt_runtime_us 2>/dev/null
                    [ -f /sys/module/workqueue/parameters/power_efficient ] && echo Y > /sys/module/workqueue/parameters/power_efficient 2>/dev/null
                    # Schedutil / WALT / Sugov_ext Clock Dynamics (Asymmetric: Little 10ms/1ms, Big 20ms/0.5ms)
                    for p in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/policy*/walt /sys/devices/system/cpu/cpufreq/policy*/sugov_ext; do
                        [ -d "${'$'}p" ] || continue
                        pol_id=${'$'}(echo "${'$'}p" | tr -dc '0-9')
                        if [ "${'$'}pol_id" = "0" ]; then
                            echo 10000 > "${'$'}p/up_rate_limit_us" 2>/dev/null
                            echo 1000 > "${'$'}p/down_rate_limit_us" 2>/dev/null
                        else
                            echo 20000 > "${'$'}p/up_rate_limit_us" 2>/dev/null
                            echo 500 > "${'$'}p/down_rate_limit_us" 2>/dev/null
                        fi
                    done
                    # EAS
                    [ -f /proc/sys/kernel/sched_boost ] && echo 0 > /proc/sys/kernel/sched_boost 2>/dev/null
                    [ -f /proc/sys/kernel/sched_energy_aware ] && echo 1 > /proc/sys/kernel/sched_energy_aware 2>/dev/null
                    [ -f /proc/sys/kernel/sched_uclamp_util_min ] && echo 0 > /proc/sys/kernel/sched_uclamp_util_min 2>/dev/null
                    [ -f /proc/sys/kernel/sched_util_clamp_min ] && echo 0 > /proc/sys/kernel/sched_util_clamp_min 2>/dev/null
                    [ -f /proc/sys/kernel/sched_uclamp_util_max ] && echo 640 > /proc/sys/kernel/sched_uclamp_util_max 2>/dev/null
                    [ -f /proc/sys/kernel/sched_util_clamp_max ] && echo 640 > /proc/sys/kernel/sched_util_clamp_max 2>/dev/null
                    [ -f /dev/cpuctl/top-app/cpu.uclamp.min ] && echo 0 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null
                    [ -f /dev/cpuctl/top-app/cpu.uclamp.max ] && (echo 640 > /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null || echo 62 > /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null)
                    [ -f /dev/cpuctl/top-app/cpu.uclamp.latency_sensitive ] && echo 0 > /dev/cpuctl/top-app/cpu.uclamp.latency_sensitive 2>/dev/null
                    [ -f /dev/cpuctl/foreground/cpu.uclamp.min ] && echo 0 > /dev/cpuctl/foreground/cpu.uclamp.min 2>/dev/null
                    [ -d /dev/stune/top-app ] && echo 0 > /dev/stune/top-app/schedtune.boost 2>/dev/null && echo 0 > /dev/stune/top-app/schedtune.prefer_idle 2>/dev/null && [ -f /dev/stune/top-app/schedtune.prefer_high_cap ] && echo 0 > /dev/stune/top-app/schedtune.prefer_high_cap 2>/dev/null
                    [ -d /dev/stune/foreground ] && echo 0 > /dev/stune/foreground/schedtune.boost 2>/dev/null && echo 0 > /dev/stune/foreground/schedtune.prefer_idle 2>/dev/null
                    [ -d /proc/perfmgr/boost_ctrl/eas_ctrl ] && echo 0 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost 2>/dev/null && echo 0 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost 2>/dev/null && echo 0 > /proc/perfmgr/boost_ctrl/eas_ctrl/sched_big_task_rotation 2>/dev/null
                    # HMP / WALT
                    [ -f /proc/sys/kernel/sched_upmigrate ] && echo 95 > /proc/sys/kernel/sched_upmigrate 2>/dev/null
                    [ -f /proc/sys/kernel/sched_downmigrate ] && echo 80 > /proc/sys/kernel/sched_downmigrate 2>/dev/null
                    [ -f /proc/sys/kernel/sched_init_task_load ] && echo 20 > /proc/sys/kernel/sched_init_task_load 2>/dev/null
                    [ -f /proc/sys/kernel/sched_spill_nr_run ] && echo 5 > /proc/sys/kernel/sched_spill_nr_run 2>/dev/null
                    [ -f /proc/sys/kernel/sched_spill_load ] && echo 98 > /proc/sys/kernel/sched_spill_load 2>/dev/null
                    echo ok
                """.trimIndent()
                else -> """
                    # Balanced
                    [ -f /proc/sys/kernel/sched_latency_ns ] && echo 10000000 > /proc/sys/kernel/sched_latency_ns 2>/dev/null
                    [ -f /proc/sys/kernel/sched_min_granularity_ns ] && echo 3000000 > /proc/sys/kernel/sched_min_granularity_ns 2>/dev/null
                    [ -f /proc/sys/kernel/sched_wakeup_granularity_ns ] && echo 2000000 > /proc/sys/kernel/sched_wakeup_granularity_ns 2>/dev/null
                    [ -f /proc/sys/kernel/sched_migration_cost_ns ] && echo 500000 > /proc/sys/kernel/sched_migration_cost_ns 2>/dev/null
                    [ -f /proc/sys/kernel/sched_nr_migrate ] && echo 32 > /proc/sys/kernel/sched_nr_migrate 2>/dev/null
                    [ -f /proc/sys/kernel/sched_child_runs_first ] && echo 0 > /proc/sys/kernel/sched_child_runs_first 2>/dev/null
                    [ -f /proc/sys/kernel/sched_cstate_aware ] && echo 1 > /proc/sys/kernel/sched_cstate_aware 2>/dev/null
                    [ -f /proc/sys/kernel/sched_rt_runtime_us ] && echo 950000 > /proc/sys/kernel/sched_rt_runtime_us 2>/dev/null
                    [ -f /sys/module/workqueue/parameters/power_efficient ] && echo Y > /sys/module/workqueue/parameters/power_efficient 2>/dev/null
                    # Schedutil / WALT / Sugov_ext Clock Dynamics (Asymmetric: Little 1ms/20ms, Big 0µs/10ms)
                    for p in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/policy*/walt /sys/devices/system/cpu/cpufreq/policy*/sugov_ext; do
                        [ -d "${'$'}p" ] || continue
                        pol_id=${'$'}(echo "${'$'}p" | tr -dc '0-9')
                        if [ "${'$'}pol_id" = "0" ]; then
                            echo 1000 > "${'$'}p/up_rate_limit_us" 2>/dev/null
                            echo 20000 > "${'$'}p/down_rate_limit_us" 2>/dev/null
                        else
                            echo 0 > "${'$'}p/up_rate_limit_us" 2>/dev/null
                            echo 10000 > "${'$'}p/down_rate_limit_us" 2>/dev/null
                        fi
                    done
                    # EAS
                    [ -f /proc/sys/kernel/sched_boost ] && echo 0 > /proc/sys/kernel/sched_boost 2>/dev/null
                    [ -f /proc/sys/kernel/sched_energy_aware ] && echo 1 > /proc/sys/kernel/sched_energy_aware 2>/dev/null
                    [ -f /proc/sys/kernel/sched_uclamp_util_min ] && echo 205 > /proc/sys/kernel/sched_uclamp_util_min 2>/dev/null
                    [ -f /proc/sys/kernel/sched_util_clamp_min ] && echo 205 > /proc/sys/kernel/sched_util_clamp_min 2>/dev/null
                    [ -f /proc/sys/kernel/sched_uclamp_util_max ] && echo 1024 > /proc/sys/kernel/sched_uclamp_util_max 2>/dev/null
                    [ -f /proc/sys/kernel/sched_util_clamp_max ] && echo 1024 > /proc/sys/kernel/sched_util_clamp_max 2>/dev/null
                    [ -f /dev/cpuctl/top-app/cpu.uclamp.min ] && (echo 205 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null || echo 20 > /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null)
                    [ -f /dev/cpuctl/top-app/cpu.uclamp.max ] && (echo 1024 > /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null || echo max > /dev/cpuctl/top-app/cpu.uclamp.max 2>/dev/null)
                    [ -f /dev/cpuctl/top-app/cpu.uclamp.latency_sensitive ] && echo 0 > /dev/cpuctl/top-app/cpu.uclamp.latency_sensitive 2>/dev/null
                    [ -f /dev/cpuctl/foreground/cpu.uclamp.min ] && (echo 51 > /dev/cpuctl/foreground/cpu.uclamp.min 2>/dev/null || echo 5 > /dev/cpuctl/foreground/cpu.uclamp.min 2>/dev/null)
                    [ -d /dev/stune/top-app ] && echo 20 > /dev/stune/top-app/schedtune.boost 2>/dev/null && echo 1 > /dev/stune/top-app/schedtune.prefer_idle 2>/dev/null && [ -f /dev/stune/top-app/schedtune.prefer_high_cap ] && echo 0 > /dev/stune/top-app/schedtune.prefer_high_cap 2>/dev/null
                    [ -d /dev/stune/foreground ] && echo 5 > /dev/stune/foreground/schedtune.boost 2>/dev/null && echo 0 > /dev/stune/foreground/schedtune.prefer_idle 2>/dev/null
                    [ -d /proc/perfmgr/boost_ctrl/eas_ctrl ] && echo 20 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost 2>/dev/null && echo 5 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost 2>/dev/null && echo 1 > /proc/perfmgr/boost_ctrl/eas_ctrl/sched_big_task_rotation 2>/dev/null && echo 50000 > /proc/perfmgr/boost_ctrl/eas_ctrl/m_sched_migrate_cost_n 2>/dev/null && echo -1 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_schedplus_down_throttle 2>/dev/null && echo -1 > /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_schedplus_up_throttle 2>/dev/null
                    # HMP / WALT
                    [ -f /proc/sys/kernel/sched_upmigrate ] && echo 85 > /proc/sys/kernel/sched_upmigrate 2>/dev/null
                    [ -f /proc/sys/kernel/sched_downmigrate ] && echo 65 > /proc/sys/kernel/sched_downmigrate 2>/dev/null
                    [ -f /proc/sys/kernel/sched_init_task_load ] && echo 35 > /proc/sys/kernel/sched_init_task_load 2>/dev/null
                    [ -f /proc/sys/kernel/sched_spill_nr_run ] && echo 3 > /proc/sys/kernel/sched_spill_nr_run 2>/dev/null
                    [ -f /proc/sys/kernel/sched_spill_load ] && echo 90 > /proc/sys/kernel/sched_spill_load 2>/dev/null
                    echo ok
                """.trimIndent()
            }

            val prefArch = context?.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                ?.getString("preferred_architecture", null)

            val archPreserveScript = when (prefArch) {
                "hybrid" -> """
                    if [ -f /sys/devices/system/cpu/eas/enable ]; then
                        chmod 644 /sys/devices/system/cpu/eas/enable 2>/dev/null
                        echo 2 > /sys/devices/system/cpu/eas/enable 2>/dev/null
                    fi
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
                """.trimIndent()
                "hmp" -> """
                    if [ -f /sys/devices/system/cpu/eas/enable ]; then
                        chmod 644 /sys/devices/system/cpu/eas/enable 2>/dev/null
                        echo 0 > /sys/devices/system/cpu/eas/enable 2>/dev/null
                    fi
                    if [ -f /proc/sys/kernel/sched_energy_aware ]; then
                        chmod 644 /proc/sys/kernel/sched_energy_aware 2>/dev/null
                        echo 0 > /proc/sys/kernel/sched_energy_aware 2>/dev/null
                    fi
                """.trimIndent()
                else -> """
                    if [ -f /data/adb/lynx/preferred_architecture ]; then
                        pref_arch=${'$'}(cat /data/adb/lynx/preferred_architecture 2>/dev/null | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')
                        if [ "${'$'}pref_arch" = "hybrid" ] && [ -f /sys/devices/system/cpu/eas/enable ]; then
                            echo 2 > /sys/devices/system/cpu/eas/enable 2>/dev/null
                        elif [ "${'$'}pref_arch" = "eas" ] && [ -f /sys/devices/system/cpu/eas/enable ]; then
                            echo 1 > /sys/devices/system/cpu/eas/enable 2>/dev/null
                        elif [ "${'$'}pref_arch" = "hmp" ] && [ -f /sys/devices/system/cpu/eas/enable ]; then
                            echo 0 > /sys/devices/system/cpu/eas/enable 2>/dev/null
                        fi
                    elif [ -f /sys/devices/system/cpu/eas/enable ]; then
                        cur_eas=${'$'}(cat /sys/devices/system/cpu/eas/enable 2>/dev/null | tr '[:upper:]' '[:lower:]')
                        if [[ "${'$'}cur_eas" == *"hybrid"* ]] || [ "${'$'}cur_eas" = "2" ]; then
                            echo 2 > /sys/devices/system/cpu/eas/enable 2>/dev/null
                        fi
                    fi
                """.trimIndent()
            }

            val finalScript = "$script\n$archPreserveScript\necho ok"
            val ok = Shell.cmd(finalScript).exec().out.any { it.trim() == "ok" }
            if (ok && context != null) {
                context.getSharedPreferences("lynx_scheduler_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("active_preset", preset.lowercase())
                    .apply()
            }
            ok
        } catch (e: Exception) {
            false
        }
    }

    // ============================================================
    //  CPU SETS & TASK AFFINITY ISOLATION (CGROUPS TASK SHIELD)
    // ============================================================

    private data class CpuSetPresetDefinition(
        val topApp: String,
        val foreground: String,
        val background: String,
        val systemBackground: String,
        val restricted: String
    )
}
