#!/system/bin/sh
# Lynx Universal - Generic Linux Performance Profile
# For non-Qualcomm & non-MediaTek SoCs (Exynos, Tensor, Unisoc)

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# 1. Elevate CPU Minimum Frequencies (Instant Touch & Game Response)
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    [ -z "$max_freq" ] && continue

    write_node "$max_freq" "$policy/scaling_max_freq"
    
    elevated_min=$((max_freq * 60 / 100))
    write_node "$elevated_min" "$policy/scaling_min_freq"

    if [ -d "$policy/schedutil" ]; then
        write_node "0" "$policy/schedutil/up_rate_limit_us"
        write_node "5000" "$policy/schedutil/down_rate_limit_us"
        write_node "$max_freq" "$policy/schedutil/hispeed_freq"
        write_node "80" "$policy/schedutil/hispeed_load"
    fi
done

# 2. Kernel Workqueues & Schedulers
write_node "N" "/sys/module/workqueue/parameters/power_efficient"
write_node "0" "/proc/sys/kernel/sched_tunable_scaling"
write_node "1" "/proc/sys/kernel/sched_autogroup_enabled"

# 3. Virtual Memory Optimization for Gaming
write_node "30" "/proc/sys/vm/swappiness"
write_node "80" "/proc/sys/vm/vfs_cache_pressure"
write_node "10" "/proc/sys/vm/dirty_ratio"
write_node "5" "/proc/sys/vm/dirty_background_ratio"
