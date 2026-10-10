#!/system/bin/sh
# Lynx Universal - Generic Linux Fallback Sysfs Tweaks
# Pure POSIX /system/bin/sh compliance

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

write_node "Y" "/sys/module/workqueue/parameters/power_efficient"
write_node "1" "/proc/sys/kernel/sched_autogroup_enabled"
