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

    pol_id=$(basename "$policy" | tr -dc '0-9')
    if [ -d "$policy/schedutil" ]; then
        if [ "$pol_id" = "0" ]; then
            write_node "1000" "$policy/schedutil/up_rate_limit_us"
            write_node "20000" "$policy/schedutil/down_rate_limit_us"
            write_node "85" "$policy/schedutil/hispeed_load"
        else
            write_node "0" "$policy/schedutil/up_rate_limit_us"
            write_node "10000" "$policy/schedutil/down_rate_limit_us"
            write_node "80" "$policy/schedutil/hispeed_load"
        fi
    fi
done

# 2. Restore Energy Efficiency
write_node "Y" "/sys/module/workqueue/parameters/power_efficient"

# 3. Standard Linux VM Parameters
write_node "60" "/proc/sys/vm/swappiness"
write_node "100" "/proc/sys/vm/vfs_cache_pressure"
write_node "20" "/proc/sys/vm/dirty_ratio"
write_node "10" "/proc/sys/vm/dirty_background_ratio"

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
