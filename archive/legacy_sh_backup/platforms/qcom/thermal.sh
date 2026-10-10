#!/system/bin/sh
# ==============================================================================
# Lynx Universal - Qualcomm Snapdragon Thermal Engine Controller v3.0
# Hardware Safety Dominant & Thermal Stabil Architecture
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)
# ==============================================================================

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

is_battery_zone() {
    local tz="$1"
    local type=""
    [ -f "$tz/type" ] && type=$(cat "$tz/type" 2>/dev/null | tr '[:upper:]' '[:lower:]')
    case "$type" in
        *batt*|*chg*|*charger*|*bms*|*battery*) return 0 ;;
        *) return 1 ;;
    esac
}

ACTION="$1"

case "$ACTION" in
    # ── 1. HARDWARE SAFETY DOMINANT MODE ──────────────────────────────────
    disable|hardware_safety_dominant|0)
        # Stop user-space vendor thermal daemons
        stop thermal-engine mi_thermald thermal_factory thermald \
             vendor.thermal-engine vendor.thermal-hal-1-0 2>/dev/null
        cmd thermalservice override-status 0 2>/dev/null

        # KGSL Thermal Overrides
        write_node "0" "/sys/class/kgsl/kgsl-3d0/throttling"
        write_node "0" "/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel"

        # Elevate writable SoC trip points to 75°C (Skip battery zones)
        for tz in /sys/class/thermal/thermal_zone*; do
            [ -d "$tz" ] || continue
            if is_battery_zone "$tz"; then
                continue
            fi
            if [ -w "$tz/trip_point_0_temp" ]; then
                write_node "75000" "$tz/trip_point_0_temp"
            fi
            if [ -w "$tz/mode" ]; then
                write_node "disabled" "$tz/mode"
            fi
        done

        # Reset cooling devices to 0
        for cooling in /sys/class/thermal/cooling_device*; do
            [ -d "$cooling" ] || continue
            write_node "0" "$cooling/cur_state"
        done
        ;;

    # ── 2. THERMAL STABIL (SUSTAINED PERFORMANCE CURVE) ────────────────────
    stable)
        cmd thermalservice override-status 0 2>/dev/null

        # KGSL sustained power level
        write_node "0" "/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel"

        # Schedutil rate limits
        for pol in /sys/devices/system/cpu/cpufreq/policy*; do
            [ -d "$pol" ] || continue
            write_node "500" "$pol/schedutil/up_rate_limit_us"
            write_node "10000" "$pol/schedutil/down_rate_limit_us"
        done

        # Calibrated 52-55°C trip points on writable SoC zones
        for tz in /sys/class/thermal/thermal_zone*; do
            [ -d "$tz" ] || continue
            if is_battery_zone "$tz"; then
                continue
            fi
            if [ -w "$tz/trip_point_0_temp" ]; then
                write_node "55000" "$tz/trip_point_0_temp"
            fi
            if [ -w "$tz/mode" ]; then
                write_node "enabled" "$tz/mode"
            fi
        done
        ;;

    # ── 3. DEFAULT OEM / BASELINE RESTORE ─────────────────────────────────
    enable|default_oem|1|*)
        start thermal-engine mi_thermald thermal_factory thermald 2>/dev/null
        cmd thermalservice reset 2>/dev/null

        # KGSL Throttling Restore
        write_node "1" "/sys/class/kgsl/kgsl-3d0/throttling"
        write_node "1" "/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel"

        # Enable thermal zones
        for tz in /sys/class/thermal/thermal_zone*; do
            [ -d "$tz" ] || continue
            if is_battery_zone "$tz"; then
                continue
            fi
            write_node "enabled" "$tz/mode"
        done

        # Reset cooling devices
        for cooling in /sys/class/thermal/cooling_device*; do
            [ -d "$cooling" ] || continue
            write_node "0" "$cooling/cur_state"
        done
        ;;
esac
