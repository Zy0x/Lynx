#!/system/bin/sh
# Lynx Universal - MediaTek Dimensity & Helio Performance / Extreme Profile
# Full Chimera High-Performance Engine + Lynx Tiered Dynamic Stability
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)

MODE="${1:-perf}"
MODDIR="/data/adb/modules/Lynx"

write_node() {
    [ -e "$2" ] || return 0
    echo "$1" > "$2" 2>/dev/null && return 0
    chmod 644 "$2" 2>/dev/null
    echo "$1" > "$2" 2>/dev/null
}

# ── 1. CPU Power Mode & Interconnect (Sport Mode) ────────────────────
write_node "3" "/proc/cpufreq/cpufreq_power_mode"
write_node "1" "/proc/cpufreq/cpufreq_cci_mode"
write_node "1" "/proc/cpufreq/cpufreq_imax_enable"
write_node "0" "/proc/cpufreq/cpufreq_debug"
write_node "1" "/proc/cpufreq/cpufreq_sched_disable"
write_node "N" "/sys/module/workqueue/parameters/power_efficient"

# HMP Mode Override for Gaming
write_node "0" "/sys/devices/system/cpu/eas/enable"
write_node "1" "/sys/devices/system/cpu/perf/enable"

# MediaTek EAS perfmgr Kernel Turbo
write_node "100" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost"
write_node "100" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost"
write_node "100" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_uclamp_min"
write_node "100" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_uclamp_min"
write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_prefer_idle"
write_node "1" "/proc/perfmgr/boost_ctrl/eas_ctrl/sched_big_task_rotation"
write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_schedplus_down_throttle"
write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_schedplus_up_throttle"

# ── 2. Disable PPM Throttling & Policy Configuration ─────────────────
write_node "0" "/proc/ppm/enabled"
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

# Dynamic DVFS Cluster Clamping
lock_cluster_freq() {
    local cluster="$1"
    local table="/proc/ppm/dump_cluster_${cluster}_dvfs_table"
    if [ -f "$table" ]; then
        local max_freq
        max_freq=$(awk '{print $1}' "$table" 2>/dev/null | head -n 1)
        if [ -n "$max_freq" ]; then
            write_node "$cluster $max_freq" "/proc/ppm/policy/hard_userlimit_max_cpu_freq"
            if [ "$MODE" = "extreme" ]; then
                write_node "$cluster $max_freq" "/proc/ppm/policy/hard_userlimit_min_cpu_freq"
            else
                local target_floor=$(( max_freq * 85 / 100 ))
                write_node "$cluster $target_floor" "/proc/ppm/policy/hard_userlimit_min_cpu_freq"
            fi
        fi
    fi
}
lock_cluster_freq 0
lock_cluster_freq 1
lock_cluster_freq 2

# CPU Scaling Policy Bounds
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    [ -z "$max_freq" ] && continue

    write_node "$max_freq" "$policy/scaling_max_freq"
    if [ "$MODE" = "extreme" ]; then
        write_node "$max_freq" "$policy/scaling_min_freq"
    else
        target_floor=$(( max_freq * 85 / 100 ))
        write_node "$target_floor" "$policy/scaling_min_freq"
    fi

    # Prefer performance governor or schedutil with instant ramp-up
    avail_govs=$(cat "$policy/scaling_available_governors" 2>/dev/null)
    if echo "$avail_govs" | grep -q "performance"; then
        write_node "performance" "$policy/scaling_governor"
    fi

    for s_dir in "$policy/schedutil" "$policy/scaling_governor"; do
        if [ -d "$s_dir" ]; then
            write_node "0" "$s_dir/up_rate_limit_us"
            write_node "5000" "$s_dir/down_rate_limit_us"
            write_node "$max_freq" "$s_dir/hispeed_freq"
            write_node "85" "$s_dir/hispeed_load"
            write_node "1" "$s_dir/pl"
        fi
    done
done

# CPU Idle & Power Supplying
write_node "0" "/proc/cpuidle/control/armpll_mode"
write_node "0" "/proc/cpuidle/control/buck_mode"
for cpu in 0 1 2 3 4 5 6 7; do
    write_node "1 1 1 1" "/sys/devices/system/cpu/cpu${cpu}/core_ctl/not_preferred"
    write_node "1" "/sys/devices/system/cpu/cpu${cpu}/sched_load_boost"
done

# ── 3. GPU Mali & GED Hardcore Boost Engine ───────────────────────────
for mali in /sys/devices/platform/*mali*; do
    [ -d "$mali" ] || continue
    write_node "always_on" "$mali/power_policy"
done
write_node "1" "/proc/mali/always_on"
write_node "0" "/proc/mali/debug_log"

write_node "1" "/proc/mali/dvfs_enable"

# GED Parameters
write_node "1" "/sys/kernel/fpsgo/common/gpu_block_boost"
write_node "1" "/sys/kernel/fpsgo/common/force_onoff"
write_node "1" "/sys/kernel/fpsgo/fbt/boost_ta"
write_node "0" "/sys/kernel/fpsgo/fbt/thrm_limit_cpu"
write_node "0" "/sys/kernel/fpsgo/fbt/switch_idleprefer"
write_node "0" "/sys/kernel/fpsgo/fbt/enable_switch_down_throttle"
write_node "1" "/sys/kernel/fpsgo/fbt/ultra_rescue"
write_node "8333333" "/sys/module/ged/parameters/target_t_cpu_remained"
write_node "1" "/sys/module/ged/parameters/boost_amp"
write_node "1" "/sys/module/ged/parameters/boost_extra"
write_node "1" "/sys/module/ged/parameters/boost_gpu_enable"
write_node "1" "/sys/module/ged/parameters/cpu_boost_policy"
write_node "0" "/sys/module/ged/parameters/deboost_reduce"
write_node "0" "/sys/module/ged/parameters/enable_game_self_frc_detect"
write_node "1" "/sys/module/ged/parameters/enable_cpu_boost"
write_node "1" "/sys/module/ged/parameters/enable_gpu_boost"
write_node "100" "/sys/module/ged/parameters/g_fb_dvfs_threshold"
write_node "1" "/sys/module/ged/parameters/ged_boost_enable"
write_node "1" "/sys/module/ged/parameters/ged_force_mdp_enable"
write_node "1" "/sys/module/ged/parameters/ged_monitor_3D_fence_disable"
write_node "1" "/sys/module/ged/parameters/ged_smart_boost"
write_node "1" "/sys/module/ged/parameters/gpu_idle"
write_node "1" "/sys/module/ged/parameters/gx_boost_on"
write_node "1" "/sys/module/ged/parameters/gx_force_cpu_boost"
write_node "1" "/sys/module/ged/parameters/gx_game_mode"
write_node "0" "/proc/gpufreq/gpufreq_aging_enable"
if grep -q "is enabled" /proc/gpufreq/gpufreq_fixed_freq_volt 2>/dev/null; then
    write_node "0 0" "/proc/gpufreq/gpufreq_fixed_freq_volt"
fi

# Extract Peak GPU Frequency from OPP Dump
if [ -f "/proc/gpufreq/gpufreq_opp_dump" ]; then
    opp_val=$(head -n 1 /proc/gpufreq/gpufreq_opp_dump 2>/dev/null)
    gpu_peak_freq=$(echo "$opp_val" | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
    if [ -n "$gpu_peak_freq" ]; then
        write_node "$gpu_peak_freq" "/sys/module/ged/parameters/gpu_cust_upbound_freq"
        write_node "$gpu_peak_freq" "/sys/module/ged/parameters/gpu_cust_boost_freq"
        write_node "$gpu_peak_freq" "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
        write_node "$gpu_peak_freq" "/sys/module/ged/parameters/gpu_bottom_freq"
        write_node "$gpu_peak_freq" "/proc/gpufreq/gpufreq_opp_freq"
    fi
fi

# GPU Throttling Table Reset
for i in 0 1 2 3 4 5 6 7 8; do
    write_node "$i 0 0" "/proc/gpufreq/gpufreq_limit_table"
done

# Disable MediaTek Syslimiter
write_node "1" "/proc/perfmgr/syslimiter/syslimiter_force_disable"
write_node "0" "/proc/perfmgr/syslimiter/syslimiter_fps_60"
write_node "0" "/proc/perfmgr/syslimiter/syslimiter_fps_90"
write_node "0" "/proc/perfmgr/syslimiter/syslimiter_fps_120"
write_node "0" "/proc/perfmgr/syslimiter/syslimiter_fps_144"

# ── 4. CPUSet, SchedTune, VM, UFS, & Latency Tuning ──────────────────
write_node "0-7" "/dev/cpuset/foreground/cpus"
write_node "0-2" "/dev/cpuset/background/cpus"
write_node "2-7" "/dev/cpuset/system-background/cpus"
write_node "0-7" "/dev/cpuset/top-app/cpus"
write_node "0" "/dev/cpuset/restricted/cpus"
write_node "0-2" "/dev/cpuset/camera-daemon/cpus"
write_node "4-7" "/dev/cpuset/audio-app/cpus"

# SchedTune
write_node "1" "/dev/stune/schedtune.sched_boost_enabled"
write_node "5" "/dev/stune/schedtune.boost"
write_node "0" "/dev/stune/schedtune.prefer_idle"
write_node "5" "/dev/stune/rt/schedtune.boost"
write_node "5" "/dev/stune/background/schedtune.boost"
write_node "5" "/dev/stune/foreground/schedtune.boost"
write_node "5" "/dev/stune/top-app/schedtune.boost"

# VM & Memory Response
sync
write_node "3" "/proc/sys/vm/drop_caches"
write_node "1" "/proc/sys/vm/compact_memory"
write_node "40" "/proc/sys/vm/vfs_cache_pressure"
write_node "10" "/proc/sys/vm/stat_interval"
if [ "$MODE" = "extreme" ]; then
    write_node "200" "/proc/sys/vm/watermark_scale_factor"
    write_node "32" "/proc/sys/kernel/sched_nr_migrate"
else
    write_node "150" "/proc/sys/vm/watermark_scale_factor"
    write_node "16" "/proc/sys/kernel/sched_nr_migrate"
fi
write_node "0" "/proc/sys/vm/watermark_boost_factor"
write_node "0" "/proc/sys/vm/oom_dump_tasks"
write_node "980000" "/proc/sys/kernel/sched_rt_runtime_us"
write_node "1000000" "/proc/sys/kernel/sched_rt_period_us"

# Block I/O Cache Locality
for queue in /sys/block/sd[a-z]/queue /sys/block/mmcblk[0-9]/queue; do
    [ -d "$queue" ] || continue
    write_node "0" "$queue/add_random"
    write_node "0" "$queue/iostats"
    write_node "0" "$queue/nomerges"
    write_node "0" "$queue/rotational"
    write_node "2" "$queue/rq_affinity"
    write_node "512" "$queue/nr_requests"
done

# Scheduler Latency
write_node "40" "/proc/sys/kernel/perf_cpu_time_max_percent"
write_node "1" "/proc/sys/kernel/sched_boost"
write_node "1" "/proc/sys/fs/lease-break-time"
write_node "512" "/proc/sys/kernel/random/read_wakeup_threshold"
write_node "2048" "/proc/sys/kernel/random/write_wakeup_threshold"

# UFS Low Latency Gating (Qualcomm & MediaTek bootdevice)
for ufs in /sys/devices/platform/soc/*ufshc* /sys/devices/platform/bootdevice /sys/devices/platform/*ufshc*; do
    [ -d "$ufs" ] || continue
    write_node "1000" "$ufs/clkgate_delay_ms"
    write_node "0" "$ufs/clkgate_delay_ms_perf"
    write_node "1000" "$ufs/clkgate_delay_ms_pwr_save"
    write_node "0" "$ufs/auto_hibern8_enable"
done

# Network Gaming Stack
sysctl -w net.ipv4.tcp_slow_start_after_idle=0 >/dev/null 2>&1
sysctl -w net.ipv4.tcp_low_latency=1 >/dev/null 2>&1
sysctl -w net.ipv4.tcp_autocorking=0 >/dev/null 2>&1
sysctl -w net.ipv4.tcp_notsent_lowat=16384 >/dev/null 2>&1
sysctl -w net.core.netdev_max_backlog=5000 >/dev/null 2>&1

# EARA Thermal Overrides
write_node "0" "/sys/kernel/eara_thermal/enable"
write_node "0" "/sys/kernel/eara_thermal/fake_throttle"
write_node "100 99" "/proc/driver/thermal/clatm_gpu_threshold"

# ── 5. Mode Specific Integrations (Extreme vs Perf) ───────────────────
if [ "$MODE" = "extreme" ]; then
    # Full Thermal Daemon Stop & Thermal Zone Disabling
    if [ -f "$MODDIR/platforms/mtk/thermal.sh" ]; then
        sh "$MODDIR/platforms/mtk/thermal.sh" disable >/dev/null 2>&1
    fi

    # Universal Scheduler EAS Bypass & Core Activation
    write_node "0" "/proc/sys/kernel/sched_energy_aware"
    write_node "1" "/proc/sys/kernel/sched_sync_hint_enable"
    write_node "1024" "/proc/sys/kernel/sched_uclamp_util_min"
    write_node "1024" "/proc/sys/kernel/sched_util_clamp_min"
    write_node "1024" "/dev/cpuset/top-app/cpu.uclamp.min"
    write_node "60" "/proc/sys/kernel/sched_upmigrate"
    write_node "40" "/proc/sys/kernel/sched_downmigrate"
    write_node "3000000" "/proc/sys/kernel/sched_latency_ns"
    write_node "40" "/proc/sys/vm/vfs_cache_pressure"
    write_node "50" "/proc/sys/vm/swappiness"

    for c in 0 1 2 3 4 5 6 7; do
        write_node "1" "/sys/devices/system/cpu/cpu$c/online"
        write_node "4" "/sys/devices/system/cpu/cpu$c/core_ctl/min_cpus"
    done

    # MediaTek Mali / GED / Display IRQ SMP affinity to big cores
    for irq in $(grep -iE "mali|ged|disp|drm" /proc/interrupts 2>/dev/null | awk '{print $1}' | tr -d ':'); do
        write_node "f0" "/proc/irq/$irq/smp_affinity" 2>/dev/null || write_node "3f" "/proc/irq/$irq/smp_affinity" 2>/dev/null
    done

    # Ensure CPU capabilities and topology are always readable by Game Engines and EAS
    for cpu in 0 1 2 3 4 5 6 7; do
        path="/sys/devices/system/cpu/cpu${cpu}"
        [ -e "$path/cpufreq/cpuinfo_max_freq" ] && chmod 444 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
        [ -e "$path/cpu_capacity" ] && chmod 444 "$path/cpu_capacity" 2>/dev/null
    done
else
    # Re-enable thermal protection in normal Performance mode
    if [ -f "$MODDIR/platforms/mtk/thermal.sh" ]; then
        sh "$MODDIR/platforms/mtk/thermal.sh" enable >/dev/null 2>&1
    fi
fi

# ── 6. Targeted Hardware Sub-Profiles (Anti-Spoofing Protected) ───────
if [ -f "$MODDIR/core/lib/hw_probe.sh" ]; then
    . "$MODDIR/core/lib/hw_probe.sh"
    load_hardware_subprofiles "mtk"
fi

