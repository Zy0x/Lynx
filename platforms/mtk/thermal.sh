#!/system/bin/sh
# Lynx Universal - MediaTek Thermal Policy Controller
# Integrates Chimera disable_thermal & enable_thermal engines
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
    # 1. MediaTek CPU & Charging Thermal Protection
    write_node "0" "/proc/cpufreq/cpufreq_imax_thermal_protect"
    write_node "0" "/sys/devices/platform/charger/sw_jeita"

    # 2. Disable PPM Thermal Policies
    write_node "3 0" "/proc/ppm/policy_status"
    write_node "4 0" "/proc/ppm/policy_status"
    write_node "5 0" "/proc/ppm/policy_status"
    write_node "0" "/proc/ppm/cpi/cpi_enabled"

    # 3. Android Framework Thermal Override
    cmd thermalservice override-status 0 2>/dev/null

    # 4. Disable Thermal Zones & Elevate Trip Points
    for tz in /sys/class/thermal/thermal_zone*; do
        [ -d "$tz" ] || continue
        write_node "disabled" "$tz/mode"
        write_node "1" "$tz/passive"
        write_node "user_space" "$tz/policy"
        write_node "150000" "$tz/trip_point_0_temp"
        write_node "1" "$tz/sustainable_power"
    done

    # 5. Reset Cooling Devices to 0 (Unrestricted state)
    for cooling in /sys/class/thermal/cooling_device*; do
        [ -d "$cooling" ] || continue
        write_node "0" "$cooling/cur_state"
    done

    # 6. Stop Vendor Thermal Daemons
    stop android.thermal-hal debug_pid.sec-thermal-1-0 mi_thermald thermal thermal-engine \
         thermal_mnt_hal_service thermal-hal thermald thermalloadalgod thermalservice \
         sec-thermal-1-0 vendor.thermal-hal-1-0 vendor.semc.hardware.thermal-1-0 \
         vendor-thermal-1-0 vendor.thermal-engine vendor.thermal-manager \
         vendor.thermal-hal-1-0 vendor.thermal-hal-2-0 vendor.thermal-symlinks 2>/dev/null

elif [ "$ACTION" = "enable" ] || [ "$ACTION" = "1" ]; then
    # 1. Restore MediaTek CPU & Charging Thermal Protection
    write_node "1" "/proc/cpufreq/cpufreq_imax_thermal_protect"
    write_node "1" "/sys/devices/platform/charger/sw_jeita"

    # 2. Restore PPM Thermal Policies
    write_node "3 1" "/proc/ppm/policy_status"
    write_node "4 1" "/proc/ppm/policy_status"
    write_node "5 1" "/proc/ppm/policy_status"
    write_node "1" "/proc/ppm/cpi/cpi_enabled"

    # 3. Reset Android Framework Thermal Status
    cmd thermalservice reset 2>/dev/null

    # 4. Enable Thermal Zones & Reset Trip Points
    for tz in /sys/class/thermal/thermal_zone*; do
        [ -d "$tz" ] || continue
        write_node "enabled" "$tz/mode"
        write_node "0" "$tz/passive"
        write_node "80000" "$tz/trip_point_0_temp"
    done

    # 5. Reset Cooling Devices
    for cooling in /sys/class/thermal/cooling_device*; do
        [ -d "$cooling" ] || continue
        write_node "0" "$cooling/cur_state"
    done

    # 6. Restart Vendor Thermal Services
    start android.thermal-hal mi_thermald thermal thermal-engine thermald \
          vendor.thermal-hal-1-0 vendor.thermal-engine vendor.thermal-manager 2>/dev/null
fi
