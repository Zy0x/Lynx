#!/system/bin/sh
# Lynx Universal - Generic Linux Thermal Controller Fallback
# Pure POSIX /system/bin/sh compliance

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

ACTION="$1"

if [ "$ACTION" = "disable" ] || [ "$ACTION" = "0" ]; then
    cmd thermalservice override-status 0 2>/dev/null
    for tz in /sys/class/thermal/thermal_zone*; do
        [ -d "$tz" ] || continue
        write_node "disabled" "$tz/mode"
    done
elif [ "$ACTION" = "enable" ] || [ "$ACTION" = "1" ]; then
    cmd thermalservice reset 2>/dev/null
    for tz in /sys/class/thermal/thermal_zone*; do
        [ -d "$tz" ] || continue
        write_node "enabled" "$tz/mode"
    done
fi
