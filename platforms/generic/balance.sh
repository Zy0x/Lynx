#!/system/bin/sh
# Lynx Universal - Generic Linux Balanced Profile

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# 1. Restore Hardware Frequency Bounds
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    min_freq=$(cat "$policy/cpuinfo_min_freq" 2>/dev/null)
    max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    [ -n "$min_freq" ] && write_node "$min_freq" "$policy/scaling_min_freq"
    [ -n "$max_freq" ] && write_node "$max_freq" "$policy/scaling_max_freq"

    if [ -d "$policy/schedutil" ]; then
        write_node "500" "$policy/schedutil/up_rate_limit_us"
        write_node "20000" "$policy/schedutil/down_rate_limit_us"
        write_node "99" "$policy/schedutil/hispeed_load"
    fi
done

# 2. Restore Energy Efficiency
write_node "Y" "/sys/module/workqueue/parameters/power_efficient"

# 3. Standard Linux VM Parameters
write_node "60" "/proc/sys/vm/swappiness"
write_node "100" "/proc/sys/vm/vfs_cache_pressure"
write_node "20" "/proc/sys/vm/dirty_ratio"
write_node "10" "/proc/sys/vm/dirty_background_ratio"
