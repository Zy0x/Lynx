#!/system/bin/sh
# ==============================================================================
# Lynx Universal - MediaTek Thermal Engine Controller v3.0
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
        # Software Throttling Stripped; Silicon, Battery & PMIC strictly preserved.
        # CRITICAL SAFETY: Keep battery JEITA protection active!
        write_node "1" "/sys/devices/platform/charger/sw_jeita"
        write_node "0" "/proc/cpufreq/cpufreq_imax_thermal_protect"

        # Safe charging limits (no over-current hazard)
        write_node "1" "/sys/devices/platform/charger/enable_sc"

        # 2. Disable Aggressive PPM Throttling Policies
        write_node "3 0" "/proc/ppm/policy_status"
        write_node "4 0" "/proc/ppm/policy_status"
        write_node "5 0" "/proc/ppm/policy_status"
        write_node "0" "/proc/ppm/cpi/cpi_enabled"

        # 3. Android Framework Thermal Override
        cmd thermalservice override-status 0 2>/dev/null

        # 4. Elevate Writable SoC/CPU Trip Points to 75°C (Preserving Hardware Shutdown & Battery)
        for tz in /sys/class/thermal/thermal_zone*; do
            [ -d "$tz" ] || continue
            # NEVER elevate battery/charger thermal zones
            if is_battery_zone "$tz"; then
                continue
            fi
            # Check if trip_point_0_temp is writable
            if [ -w "$tz/trip_point_0_temp" ]; then
                write_node "75000" "$tz/trip_point_0_temp"
            fi
            if [ -w "$tz/mode" ]; then
                write_node "disabled" "$tz/mode"
            fi
        done

        # 5. MediaTek GPU Thermal & PBM Bypass
        if [ -f "/proc/gpufreq/gpufreq_limit_table" ]; then
            for id in 3 4 5 6 7; do
                write_node "$id 0 0" "/proc/gpufreq/gpufreq_limit_table"
            done
        fi
        if [ -f "/proc/gpufreqv2/gpufreq_power_limited" ]; then
            echo "ignore_thermal_protect 1" > /proc/gpufreqv2/gpufreq_power_limited 2>/dev/null
            echo "ignore_pbm_limited 1" > /proc/gpufreqv2/gpufreq_power_limited 2>/dev/null
        fi
        write_node "0" "/sys/module/fbt_cpu/parameters/thrm_limit_cpu"
        write_node "0" "/sys/kernel/fpsgo/fbt/thrm_limit_cpu"
        write_node "0" "/sys/kernel/eara_thermal/enable"

        # 6. Stop User-Space Vendor Thermal Daemons (Xiaomi Joyose, mi_thermald, Transsion, etc.)
        stop mi_thermald thermal thermal-engine \
             thermal_mnt_hal_service thermal-hal thermald thermalloadalgod \
             vendor.thermal-engine vendor.thermal-manager 2>/dev/null
        ;;

    # ── 2. THERMAL STABIL (SUSTAINED PERFORMANCE CURVE) ────────────────────
    stable)
        # Dynamic Load Synchronization & Calibrated 52-55°C Curve
        write_node "1" "/sys/devices/platform/charger/sw_jeita"
        write_node "1" "/proc/cpufreq/cpufreq_imax_thermal_protect"

        # Reset PPM policies to balanced operation
        write_node "3 1" "/proc/ppm/policy_status"
        write_node "4 1" "/proc/ppm/policy_status"
        write_node "5 0" "/proc/ppm/policy_status"

        # Schedutil Rate Limits: Prevent sawtooth frequency oscillations
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

        # EARA & FPSGO smooth throttling margins
        write_node "0" "/sys/kernel/fpsgo/fbt/thrm_limit_cpu"
        write_node "0" "/sys/module/fbt_cpu/parameters/thrm_limit_cpu"
        cmd thermalservice override-status 0 2>/dev/null
        ;;

    # ── 3. DEFAULT OEM / BASELINE RESTORE ─────────────────────────────────
    enable|default_oem|1|*)
        write_node "1" "/proc/cpufreq/cpufreq_imax_thermal_protect"
        write_node "1" "/sys/devices/platform/charger/sw_jeita"

        # Restore PPM Policies
        write_node "3 1" "/proc/ppm/policy_status"
        write_node "4 1" "/proc/ppm/policy_status"
        write_node "5 1" "/proc/ppm/policy_status"
        write_node "1" "/proc/ppm/cpi/cpi_enabled"

        # Restore GPU Thermal Limits
        if [ -f "/proc/gpufreq/gpufreq_limit_table" ]; then
            for id in 3 4 5 6 7; do
                write_node "$id 1 1" "/proc/gpufreq/gpufreq_limit_table"
            done
        fi
        if [ -f "/proc/gpufreqv2/gpufreq_power_limited" ]; then
            echo "ignore_thermal_protect 0" > /proc/gpufreqv2/gpufreq_power_limited 2>/dev/null
            echo "ignore_pbm_limited 0" > /proc/gpufreqv2/gpufreq_power_limited 2>/dev/null
        fi
        write_node "1" "/sys/module/fbt_cpu/parameters/thrm_limit_cpu"
        write_node "1" "/sys/kernel/fpsgo/fbt/thrm_limit_cpu"
        write_node "1" "/sys/kernel/eara_thermal/enable"

        cmd thermalservice reset 2>/dev/null

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

        # Restart vendor thermal services
        start mi_thermald thermal thermal-engine thermald \
              vendor.thermal-engine vendor.thermal-manager 2>/dev/null
        ;;
esac
