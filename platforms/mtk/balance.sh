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

# ── 1. CPU Subsystem: Cool & Efficient (power_mode 0 + Schedutil) ─────
write_node "0" "/proc/cpufreq/cpufreq_power_mode"
write_node "0" "/proc/cpufreq/cpufreq_cci_mode"
write_node "0" "/proc/cpufreq/cpufreq_imax_enable"
write_node "0" "/proc/cpufreq/cpufreq_debug"
write_node "0" "/proc/cpufreq/cpufreq_sched_disable"
write_node "Y" "/sys/module/workqueue/parameters/power_efficient"

# Enable EAS
write_node "1" "/sys/devices/system/cpu/eas/enable"
write_node "1" "/sys/devices/system/cpu/perf/enable"

# Restore CPU Cluster Frequencies & Schedutil Defaults
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
            write_node "500" "$s_dir/up_rate_limit_us"
            write_node "20000" "$s_dir/down_rate_limit_us"
            write_node "99" "$s_dir/hispeed_load"
            write_node "1" "$s_dir/pl"
        fi
    done
done

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
write_node "80" "/sys/module/ged/parameters/ged_smart_boost"
write_node "20" "/sys/module/ged/parameters/gpu_idle"
write_node "1" "/sys/module/ged/parameters/gx_boost_on"
write_node "0" "/sys/module/ged/parameters/gx_force_cpu_boost"
write_node "0" "/sys/module/ged/parameters/gx_game_mode"
write_node "0" "/proc/gpufreq/gpufreq_opp_freq"
write_node "0 0" "/proc/gpufreq/gpufreq_fixed_freq_volt"
write_node "1" "/sys/kernel/fpsgo/fbt/switch_idleprefer"
write_node "1" "/sys/kernel/fpsgo/fbt/enable_switch_down_throttle"
write_node "16000000" "/sys/module/ged/parameters/target_t_cpu_remained"
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

# SchedTune / Stune
write_node "1" "/dev/stune/schedtune.sched_boost_enabled"
write_node "5" "/dev/stune/schedtune.boost"
write_node "1" "/dev/stune/schedtune.prefer_idle"
write_node "5" "/dev/stune/foreground/schedtune.boost"
write_node "5" "/dev/stune/top-app/schedtune.boost"

# Virtual Memory Latency
write_node "40" "/proc/sys/vm/vfs_cache_pressure"
write_node "1" "/proc/sys/vm/stat_interval"
write_node "100" "/proc/sys/vm/watermark_scale_factor"
write_node "1500" "/proc/sys/vm/watermark_boost_factor"
write_node "0" "/proc/sys/vm/oom_dump_tasks"

# Scheduler Latency & Event processing
write_node "32" "/proc/sys/kernel/sched_nr_migrate"
write_node "25" "/proc/sys/kernel/perf_cpu_time_max_percent"
write_node "1" "/proc/sys/kernel/sched_boost"
write_node "50" "/proc/sys/fs/lease-break-time"
write_node "64" "/proc/sys/kernel/random/read_wakeup_threshold"
write_node "512" "/proc/sys/kernel/random/write_wakeup_threshold"

# UFS Clock Gate
for ufs in /sys/devices/platform/soc/*ufshc*; do
    [ -d "$ufs" ] || continue
    write_node "100" "$ufs/clkgate_delay_ms_perf"
    write_node "5" "$ufs/clkgate_delay_ms_pwr_save"
done

# EARA Thermal Balance
write_node "1" "/sys/kernel/eara_thermal/enable"
write_node "0" "/sys/kernel/eara_thermal/fake_throttle"
write_node "100 0" "/proc/driver/thermal/clatm_gpu_threshold"

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
