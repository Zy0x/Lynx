#!/system/bin/sh
# Lynx Universal - Qualcomm Snapdragon Thermal Engine Controller
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
    # 1. Stop User-Space Thermal Daemons
    stop thermal-engine mi_thermald thermal_factory thermald vendor.thermal-engine vendor.thermal-hal-1-0 2>/dev/null
    cmd thermalservice override-status 0 2>/dev/null

    # 2. KGSL Thermal Overrides
    write_node "0" "/sys/class/kgsl/kgsl-3d0/throttling"
    write_node "0" "/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel"

    # 3. Thermal Zones Throttling Disable
    for tz in /sys/class/thermal/thermal_zone*; do
        [ -d "$tz" ] || continue
        write_node "disabled" "$tz/mode"
        write_node "150000" "$tz/trip_point_0_temp"
    done

    # 4. Disable Cooling Devices
    for cooling in /sys/class/thermal/cooling_device*; do
        [ -d "$cooling" ] || continue
        if [ -f "$cooling/max_state" ]; then
            max_s=$(cat "$cooling/max_state" 2>/dev/null)
            [ -n "$max_s" ] && write_node "$max_s" "$cooling/min_state"
        fi
    done

elif [ "$ACTION" = "enable" ] || [ "$ACTION" = "1" ]; then
    # 1. Start Thermal Daemons
    start thermal-engine mi_thermald thermal_factory thermald 2>/dev/null
    cmd thermalservice reset 2>/dev/null

    # 2. KGSL Throttling Restore
    write_node "1" "/sys/class/kgsl/kgsl-3d0/throttling"

    # 3. Thermal Zones Restore
    for tz in /sys/class/thermal/thermal_zone*; do
        [ -d "$tz" ] || continue
        write_node "enabled" "$tz/mode"
        write_node "80000" "$tz/trip_point_0_temp"
    done

    # 4. Reset Cooling Devices
    for cooling in /sys/class/thermal/cooling_device*; do
        [ -d "$cooling" ] || continue
        write_node "0" "$cooling/min_state"
    done
fi
