#!/system/bin/sh
# Lynx Universal - Generic Linux Powersave Profile
# Pure POSIX /system/bin/sh compliance

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    min_freq=$(cat "$policy/cpuinfo_min_freq" 2>/dev/null)
    max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    [ -n "$min_freq" ] && write_node "$min_freq" "$policy/scaling_min_freq"

    if [ -n "$max_freq" ]; then
        power_cap=$(( max_freq * 60 / 100 ))
        [ "$power_cap" -gt "$min_freq" ] && write_node "$power_cap" "$policy/scaling_max_freq"
    fi
done

write_node "Y" "/sys/module/workqueue/parameters/power_efficient"
write_node "0" "/dev/stune/schedtune.boost"
write_node "0" "/dev/stune/foreground/schedtune.boost"
write_node "0" "/dev/stune/top-app/schedtune.boost"
