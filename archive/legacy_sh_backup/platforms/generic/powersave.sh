#!/system/bin/sh
# Lynx Universal - Generic Linux Powersave Profile
# Pure POSIX /system/bin/sh compliance

write_node() {
    [ -e "$2" ] || return 0
    echo "$1" > "$2" 2>/dev/null && return 0
    chmod 666 "$2" 2>/dev/null
    echo "$1" > "$2" 2>/dev/null
}

for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    min_freq=$(cat "$policy/cpuinfo_min_freq" 2>/dev/null)
    max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    [ -n "$min_freq" ] && write_node "$min_freq" "$policy/scaling_min_freq"

    if [ -n "$max_freq" ]; then
        power_cap=$(( max_freq * 55 / 100 ))
        [ "$power_cap" -gt "$min_freq" ] && write_node "$power_cap" "$policy/scaling_max_freq"
    fi

    pol_id=$(basename "$policy" | tr -dc '0-9')
    for s_dir in "$policy/schedutil" "$policy/scaling_governor"; do
        if [ -d "$s_dir" ]; then
            if [ "$pol_id" = "0" ]; then
                write_node "10000" "$s_dir/up_rate_limit_us"
                write_node "1000" "$s_dir/down_rate_limit_us"
            else
                write_node "20000" "$s_dir/up_rate_limit_us"
                write_node "500" "$s_dir/down_rate_limit_us"
            fi
            write_node "99" "$s_dir/hispeed_load"
            write_node "0" "$s_dir/pl"
            write_node "0" "$s_dir/iowait_boost_enable"
        fi
    done
done

write_node "Y" "/sys/module/workqueue/parameters/power_efficient"
write_node "0" "/dev/stune/schedtune.boost"
write_node "0" "/dev/stune/foreground/schedtune.boost"
write_node "0" "/dev/stune/top-app/schedtune.boost"

# Enforce 60Hz Screen Refresh Rate for Power Conservation
cur_min_rr=$(settings get system min_refresh_rate 2>/dev/null)
cur_peak_rr=$(settings get system peak_refresh_rate 2>/dev/null)
if [ ! -f "/dev/lynx_orig_min_rr" ] && [ -n "$cur_min_rr" ] && [ "$cur_min_rr" != "null" ]; then
    echo "$cur_min_rr" > /dev/lynx_orig_min_rr
fi
if [ ! -f "/dev/lynx_orig_peak_rr" ] && [ -n "$cur_peak_rr" ] && [ "$cur_peak_rr" != "null" ]; then
    echo "$cur_peak_rr" > /dev/lynx_orig_peak_rr
fi
settings put system min_refresh_rate 60.0 2>/dev/null
settings put system peak_refresh_rate 60.0 2>/dev/null
