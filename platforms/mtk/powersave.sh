#!/system/bin/sh
# Lynx Universal - MediaTek Dimensity & Helio Powersave Profile
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)

MODDIR="/data/adb/modules/Lynx"

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# ── 1. CPU Power Mode: Low Power (1) ─────────────────────────────────
write_node "1" "/proc/cpufreq/cpufreq_power_mode"
write_node "0" "/proc/cpufreq/cpufreq_cci_mode"
write_node "0" "/proc/cpufreq/cpufreq_imax_enable"
write_node "0" "/proc/cpufreq/cpufreq_debug"
write_node "0" "/proc/cpufreq/cpufreq_sched_disable"
write_node "Y" "/sys/module/workqueue/parameters/power_efficient"

# Enable EAS
write_node "1" "/sys/devices/system/cpu/eas/enable"
write_node "0" "/sys/devices/system/cpu/perf/enable"

# Cap CPU Cluster Frequencies to 60% of Max Clock
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    min_freq=$(cat "$policy/cpuinfo_min_freq" 2>/dev/null)
    max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    [ -n "$min_freq" ] && write_node "$min_freq" "$policy/scaling_min_freq"
    
    if [ -n "$max_freq" ]; then
        power_cap=$(( max_freq * 60 / 100 ))
        [ "$power_cap" -gt "$min_freq" ] && write_node "$power_cap" "$policy/scaling_max_freq"
    fi

    # Schedutil sluggish downclocking
    for s_dir in "$policy/schedutil" "$policy/scaling_governor"; do
        if [ -d "$s_dir" ]; then
            write_node "20000" "$s_dir/up_rate_limit_us"
            write_node "500" "$s_dir/down_rate_limit_us"
            write_node "99" "$s_dir/hispeed_load"
            write_node "0" "$s_dir/pl"
        fi
    done
done

# Enable MediaTek PPM Driver
write_node "1" "/proc/ppm/enabled"
write_node "0 0" "/proc/ppm/policy_status"
write_node "1 1" "/proc/ppm/policy_status"
write_node "2 1" "/proc/ppm/policy_status"
write_node "3 1" "/proc/ppm/policy_status"
write_node "4 1" "/proc/ppm/policy_status"
write_node "5 1" "/proc/ppm/policy_status"
write_node "6 1" "/proc/ppm/policy_status"
write_node "7 1" "/proc/ppm/policy_status"
write_node "8 1" "/proc/ppm/policy_status"
write_node "9 1" "/proc/ppm/policy_status"
write_node "1" "/proc/ppm/cpi/cpi_enabled"

# ── 2. GPU & GED Subsystem: Sleep Allowed (Coarse Demand) ─────────────
for mali in /sys/devices/platform/*mali*; do
    [ -d "$mali" ] || continue
    write_node "coarse_demand" "$mali/power_policy"
done
write_node "0" "/proc/mali/always_on"
write_node "1" "/proc/mali/dvfs_enable"

write_node "0" "/sys/kernel/fpsgo/common/gpu_block_boost"
write_node "0" "/sys/kernel/fpsgo/fbt/boost_ta"
write_node "1" "/sys/kernel/fpsgo/fbt/thrm_limit_cpu"
write_node "0" "/sys/module/ged/parameters/boost_amp"
write_node "0" "/sys/module/ged/parameters/boost_extra"
write_node "0" "/sys/module/ged/parameters/boost_gpu_enable"
write_node "0" "/sys/module/ged/parameters/enable_cpu_boost"
write_node "0" "/sys/module/ged/parameters/enable_gpu_boost"
write_node "30" "/sys/module/ged/parameters/g_fb_dvfs_threshold"
write_node "0" "/sys/module/ged/parameters/ged_boost_enable"
write_node "0" "/sys/module/ged/parameters/ged_smart_boost"
write_node "0" "/sys/module/ged/parameters/gx_boost_on"
write_node "0" "/sys/module/ged/parameters/gx_game_mode"

# ── 3. Kernel, Thermal, & VM Power Conservation ──────────────────────
if [ -f "$MODDIR/platforms/mtk/thermal.sh" ]; then
    sh "$MODDIR/platforms/mtk/thermal.sh" enable >/dev/null 2>&1
fi

write_node "1" "/sys/kernel/eara_thermal/enable"
write_node "100 0" "/proc/driver/thermal/clatm_gpu_threshold"

write_node "0" "/dev/stune/schedtune.boost"
write_node "0" "/dev/stune/foreground/schedtune.boost"
write_node "0" "/dev/stune/top-app/schedtune.boost"

# Revert Unity Trick if applied
for cpu in 0 1 2 3 4 5 6 7; do
    path="/sys/devices/system/cpu/cpu${cpu}"
    [ -e "$path/cpufreq/cpuinfo_max_freq" ] && chmod 444 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
    [ -e "$path/cpu_capacity" ] && chmod 444 "$path/cpu_capacity" 2>/dev/null
done
