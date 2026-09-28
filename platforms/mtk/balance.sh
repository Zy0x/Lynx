#!/system/bin/sh
# Lynx Universal - MediaTek Dimensity & Helio Balanced Profile
# Preserves Chimera's "Smooth GPU + Cool & Balanced CPU" Architecture
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# ── 1. CPU Subsystem: Responsive & Efficient (power_mode 0 + Schedutil) ─────
write_node "0" "/proc/cpufreq/cpufreq_power_mode"
write_node "0" "/proc/cpufreq/cpufreq_cci_mode"
write_node "0" "/proc/cpufreq/cpufreq_imax_enable"
write_node "0" "/proc/cpufreq/cpufreq_debug"
write_node "0" "/proc/cpufreq/cpufreq_sched_disable"
write_node "Y" "/sys/module/workqueue/parameters/power_efficient"

# Enable EAS
write_node "1" "/sys/devices/system/cpu/eas/enable"
write_node "1" "/sys/devices/system/cpu/perf/enable"

# Restore CPU Cluster Frequencies & Schedutil Tuning (Zero Latency Ramp)
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    min_freq=$(cat "$policy/cpuinfo_min_freq" 2>/dev/null)
    max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    [ -n "$min_freq" ] && write_node "$min_freq" "$policy/scaling_min_freq"
    [ -n "$max_freq" ] && write_node "$max_freq" "$policy/scaling_max_freq"

    avail_govs=$(cat "$policy/scaling_available_governors" 2>/dev/null)
    curr_gov=$(cat "$policy/scaling_governor" 2>/dev/null)
    case "$curr_gov" in
        *schedutil*|*pixel*|*electro*|*blu*) ;;
        *)
            if echo "$avail_govs" | grep -q "schedutil"; then
                write_node "schedutil" "$policy/scaling_governor"
            fi
            ;;
    esac

    for s_dir in "$policy/schedutil" "$policy/$curr_gov"; do
        if [ -d "$s_dir" ]; then
            write_node "0" "$s_dir/up_rate_limit_us"
            write_node "15000" "$s_dir/down_rate_limit_us"
            write_node "80" "$s_dir/hispeed_load"
            write_node "1" "$s_dir/pl"
            write_node "1" "$s_dir/iowait_boost_enable"
            if [ -n "$max_freq" ] && [ "$max_freq" -gt 0 ] 2>/dev/null; then
                hi_f=$(( max_freq * 75 / 100 ))
                write_node "$hi_f" "$s_dir/hispeed_freq"
            fi
        fi
    done
done

# MediaTek EAS perfmgr Responsive Balanced
write_node "15" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost"
write_node "10" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost"
write_node "10" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_uclamp_min"
write_node "5" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_uclamp_min"
write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_uclamp_min"
write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_prefer_idle"
write_node "1" "/proc/perfmgr/boost_ctrl/eas_ctrl/sched_big_task_rotation"
write_node "1" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ext_launch_mon"

# PPM Driver Configuration (Balanced & Unthrottled System)
write_node "1" "/proc/ppm/enabled"
write_node "0 0" "/proc/ppm/policy_status"
write_node "1 1" "/proc/ppm/policy_status"
write_node "2 0" "/proc/ppm/policy_status"
write_node "3 0" "/proc/ppm/policy_status"
write_node "4 0" "/proc/ppm/policy_status"
write_node "5 0" "/proc/ppm/policy_status"
write_node "6 1" "/proc/ppm/policy_status"
write_node "7 1" "/proc/ppm/policy_status"
write_node "8 0" "/proc/ppm/policy_status"
write_node "9 1" "/proc/ppm/policy_status"
write_node "0" "/proc/ppm/cpi/cpi_enabled"

# Dynamic DVFS Cluster Bound Restoration
for c in 0 1 2; do
    dvfs_table="/proc/ppm/dump_cluster_${c}_dvfs_table"
    if [ -f "$dvfs_table" ]; then
        c_max=$(awk '{print $1}' "$dvfs_table" 2>/dev/null | head -n 1)
        c_min=$(tail -n 1 "$dvfs_table" 2>/dev/null | awk '{print $NF}')
        [ -n "$c_max" ] && write_node "$c $c_max" "/proc/ppm/policy/hard_userlimit_max_cpu_freq"
        [ -n "$c_min" ] && write_node "$c $c_min" "/proc/ppm/policy/hard_userlimit_min_cpu_freq"
    fi
done

# CPU Idle Mode & Core Ctl
write_node "1" "/proc/cpuidle/control/armpll_mode"
write_node "0" "/proc/cpuidle/control/buck_mode"
for cpu in 0 1 2 3 4 5 6 7; do
    write_node "0 0 0 0" "/sys/devices/system/cpu/cpu${cpu}/core_ctl/not_preferred"
    write_node "1" "/sys/devices/system/cpu/cpu${cpu}/sched_load_boost"
done

# ── 2. GPU & GED Subsystem: Responsive Render Engine (Chimera Core) ───
for mali in /sys/devices/platform/*mali*; do
    [ -d "$mali" ] || continue
    write_node "always_on" "$mali/power_policy"
done
write_node "1" "/proc/mali/always_on"
write_node "1" "/proc/mali/dvfs_enable"
write_node "0" "/proc/mali/debug_log"

# MediaTek GED & FPSGO Tuning (Stutter-Free Display & Frame Pipeline)
write_node "1" "/sys/kernel/fpsgo/common/gpu_block_boost"
write_node "1" "/sys/kernel/fpsgo/common/force_onoff"
write_node "1" "/sys/kernel/fpsgo/fbt/boost_ta"
write_node "1" "/sys/kernel/fpsgo/fbt/thrm_limit_cpu"
write_node "1" "/sys/module/ged/parameters/boost_amp"
write_node "0" "/sys/module/ged/parameters/boost_extra"
write_node "1" "/sys/module/ged/parameters/boost_gpu_enable"
write_node "100" "/sys/module/ged/parameters/cpu_boost_policy"
write_node "0" "/sys/module/ged/parameters/deboost_reduce"
write_node "1" "/sys/module/ged/parameters/enable_game_self_frc_detect"
write_node "1" "/sys/module/ged/parameters/enable_cpu_boost"
write_node "1" "/sys/module/ged/parameters/enable_gpu_boost"
write_node "60" "/sys/module/ged/parameters/g_fb_dvfs_threshold"
write_node "0" "/sys/module/ged/parameters/ged_boost_enable"
write_node "0" "/sys/module/ged/parameters/ged_force_mdp_enable"
write_node "1" "/sys/module/ged/parameters/ged_smart_boost"
write_node "1" "/sys/module/ged/parameters/gpu_idle"
write_node "0" "/sys/module/ged/parameters/gx_boost_on"
write_node "0" "/sys/module/ged/parameters/gx_force_cpu_boost"
write_node "0" "/sys/module/ged/parameters/gx_game_mode"
write_node "36" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
write_node "0" "/sys/kernel/ged/hal/gpu_boost_level"
write_node "10" "/sys/kernel/ged/hal/dvfs_margin_value"
write_node "0" "/proc/gpufreq/gpufreq_opp_freq"
write_node "0 0" "/proc/gpufreq/gpufreq_fixed_freq_volt"
write_node "1" "/sys/kernel/fpsgo/fbt/switch_idleprefer"
write_node "1" "/sys/kernel/fpsgo/fbt/enable_switch_down_throttle"

cur_peak_rr=$(settings get system peak_refresh_rate 2>/dev/null)
case "$cur_peak_rr" in
    144*|144|120*|120|90*|90)
        write_node "8333333" "/sys/module/ged/parameters/target_t_cpu_remained"
        ;;
    *)
        write_node "16666666" "/sys/module/ged/parameters/target_t_cpu_remained"
        ;;
esac

write_node "1" "/proc/gpufreq/gpufreq_aging_enable"
write_node "1" "/sys/module/ged/parameters/gpu_dvfs_enable"

# ── 3. Memory, SchedTune, CPUSet, & Kernel Tuning ────────────────────
# CPUSet Distribution
write_node "0-7" "/dev/cpuset/foreground/cpus"
write_node "0-2" "/dev/cpuset/background/cpus"
write_node "0-5" "/dev/cpuset/system-background/cpus"
write_node "0-7" "/dev/cpuset/top-app/cpus"
write_node "0" "/dev/cpuset/restricted/cpus"
write_node "0-3" "/dev/cpuset/camera-daemon/cpus"
write_node "0-3" "/dev/cpuset/audio-app/cpus"

# UCLAMP
for u_node in "/dev/cpuset/top-app/cpu.uclamp.min" "/proc/sys/kernel/sched_util_clamp_min"; do
    if [ -e "$u_node" ]; then
        max_sc=100
        [ -e "/dev/cpuset/top-app/cpu.uclamp.max" ] && max_sc=$(cat "/dev/cpuset/top-app/cpu.uclamp.max" 2>/dev/null)
        if [ "$max_sc" -gt 100 ] 2>/dev/null; then
            write_node "100" "$u_node"
        else
            write_node "10" "$u_node"
        fi
    fi
done
write_node "1" "/dev/cpuset/top-app/cpu.uclamp.latency_sensitive"
write_node "1" "/dev/cpuset/foreground/boost/cpu.uclamp.latency_sensitive"

# SchedTune / Stune
write_node "1" "/dev/stune/schedtune.sched_boost_enabled"
write_node "5" "/dev/stune/schedtune.boost"
write_node "0" "/dev/stune/schedtune.prefer_idle"
write_node "10" "/dev/stune/foreground/schedtune.boost"
write_node "1" "/dev/stune/foreground/schedtune.prefer_idle"
write_node "15" "/dev/stune/top-app/schedtune.boost"
write_node "1" "/dev/stune/top-app/schedtune.prefer_idle"
write_node "1" "/proc/sys/kernel/sched_big_task_rotation"
write_node "1" "/proc/sys/kernel/sched_sync_hint_enable"

# Virtual Memory Latency & Page Cache
write_node "60" "/proc/sys/vm/vfs_cache_pressure"
write_node "1" "/proc/sys/vm/stat_interval"
write_node "16" "/proc/sys/vm/watermark_scale_factor"
write_node "1500" "/proc/sys/vm/watermark_boost_factor"
write_node "20" "/proc/sys/vm/dirty_ratio"
write_node "5" "/proc/sys/vm/dirty_background_ratio"
write_node "0" "/proc/sys/vm/oom_dump_tasks"

# Scheduler Latency & Preemption
write_node "5000000" "/proc/sys/kernel/sched_latency_ns"
write_node "1000000" "/proc/sys/kernel/sched_min_granularity_ns"
write_node "800000" "/proc/sys/kernel/sched_wakeup_granularity_ns"
write_node "200000" "/proc/sys/kernel/sched_migration_cost_ns"
write_node "32" "/proc/sys/kernel/sched_nr_migrate"
write_node "0" "/proc/sys/kernel/sched_schedstats"
write_node "0" "/proc/sys/kernel/sched_child_runs_first"
write_node "1" "/proc/sys/kernel/sched_cstate_aware"
write_node "25" "/proc/sys/kernel/perf_cpu_time_max_percent"
write_node "1" "/proc/sys/kernel/sched_boost"
write_node "50" "/proc/sys/fs/lease-break-time"
write_node "64" "/proc/sys/kernel/random/read_wakeup_threshold"
write_node "512" "/proc/sys/kernel/random/write_wakeup_threshold"

# UFS & Storage Throughput
for queue in /sys/block/sd[a-z]/queue /sys/block/mmcblk[0-9]/queue; do
    [ -d "$queue" ] || continue
    write_node "1" "$queue/rq_affinity"
done
for q in /sys/block/sd[a-z]/queue/scheduler /sys/block/mmcblk[0-9]/queue/scheduler; do
    [ -e "$q" ] && echo deadline > "$q" 2>/dev/null
done
for ra in /sys/block/sd[a-z]/queue/read_ahead_kb /sys/block/mmcblk[0-9]/queue/read_ahead_kb; do
    write_node "512" "$ra"
done
for ufs in /sys/devices/platform/soc/*ufshc*; do
    [ -d "$ufs" ] || continue
    write_node "100" "$ufs/clkgate_delay_ms_perf"
    write_node "5" "$ufs/clkgate_delay_ms_pwr_save"
done

# EARA Thermal Balance
write_node "1" "/sys/kernel/eara_thermal/enable"
write_node "0" "/sys/kernel/eara_thermal/fake_throttle"
write_node "100 0" "/proc/driver/thermal/clatm_gpu_threshold"

# SurfaceFlinger Low-Latency Frame Latching for butter-smooth scrolling
setprop debug.sf.latch_unsignaled 1 2>/dev/null
setprop vendor.perf.gestureFlingBoost.enable 1 2>/dev/null
setprop vendor.perf.gestureflingboost.enable true 2>/dev/null

if which resetprop >/dev/null 2>&1; then
    for p in debug.sf.enable_gl_backpressure \
             debug.sf.disable_backpressure \
             debug.renderengine.backend \
             debug.hwui.renderer \
             debug.hwui.use_buffer_age \
             debug.hwui.fps_divisor \
             debug.sf.early_phase_offset_ns \
             debug.sf.early_app_phase_offset_ns \
             debug.sf.early_gl_phase_offset_ns \
             debug.sf.high_fps_early_phase_offset_ns \
             debug.sf.high_fps_early_gl_phase_offset_ns \
             debug.sf.high_fps_late_app_phase_offset_ns \
             debug.composition.type \
             persist.sys.composition.type \
             ro.hwui.render_dirty_regions; do
        resetprop -p --delete "$p" 2>/dev/null
    done
else
    setprop debug.sf.enable_gl_backpressure "" 2>/dev/null
    setprop debug.sf.disable_backpressure "" 2>/dev/null
fi

# Revert Unity Trick if applied
for cpu in 0 1 2 3 4 5 6 7; do
    path="/sys/devices/system/cpu/cpu${cpu}"
    [ -e "$path/cpufreq/cpuinfo_max_freq" ] && chmod 444 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
    [ -e "$path/cpu_capacity" ] && chmod 444 "$path/cpu_capacity" 2>/dev/null
done

# Restore Display Refresh Rates
if [ -f "/dev/lynx_orig_min_rr" ]; then
    orig_min=$(cat "/dev/lynx_orig_min_rr" 2>/dev/null)
    [ -n "$orig_min" ] && settings put system min_refresh_rate "$orig_min" 2>/dev/null
    rm -f "/dev/lynx_orig_min_rr" 2>/dev/null
fi
if [ -f "/dev/lynx_orig_peak_rr" ]; then
    orig_peak=$(cat "/dev/lynx_orig_peak_rr" 2>/dev/null)
    [ -n "$orig_peak" ] && settings put system peak_refresh_rate "$orig_peak" 2>/dev/null
    rm -f "/dev/lynx_orig_peak_rr" 2>/dev/null
fi
