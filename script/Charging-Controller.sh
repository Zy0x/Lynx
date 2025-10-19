#!/bin/bash
# Powered by AI Charging Controller 4.5

# Define directories
dir="/sys/class"
qc="$dir/qcom-battery"
ps="$dir/power_supply"
battery="$ps/battery"
bms="$ps/bms"
main="$ps/main"
usb="$ps/usb"
dc="$ps/dc"
ext="$ps/battery_ext"

# Reads the required files
read_initial_values() {
    temp_cold=$(<"$bms/temp_cold")
    temp_cool=$(<"$bms/temp_cool")
    temp_hot=$(<"$bms/temp_hot")
    temp_warm=$(<"$bms/temp_warm")
    voltage_max=$(<"$usb/voltage_max")
    voltage_min=$(<"$usb/voltage_min")
    current_max_usb=$(<"$usb/current_max")
    hw_current_max=$(<"$usb/hw_current_max")
    input_current_settled=$(<"$usb/input_current_settled")
    constant_charge_current_max_main=$(<"$main/constant_charge_current_max")
    system_temp_level=$(<"$battery/system_temp_level")
    sw_jeita_enabled=$(<"$battery/sw_jeita_enabled")
    input_current_limited=$(<"$battery/input_current_limited")
    input_suspend=$(<"$battery/input_suspend")
    chg_pwr_fcc=$(<"$ext/chg_pwr_fcc")
    chg_pwr_icl=$(<"$ext/chg_pwr_icl")
    max_charge_current=$(<"$ext/max_charge_current")
    constant_charge_current=$(<"$battery/constant_charge_current")
    constant_charge_current_max=$(<"$battery/constant_charge_current_max")
    boost_current=$(<"$usb/boost_current")
}

# Set permissions
set_permissions() {
    files=(
        "$bms/temp_cool" "$bms/temp_hot" "$bms/temp_warm" "$bms/temp_cold"
        "$usb/voltage_max" "$usb/voltage_min" "$usb/current_max"
        "$main/current_max" "$usb/hw_current_max" "$usb/input_current_settled"
        "$main/constant_charge_current_max" "$battery/system_temp_level"
        "$battery/sw_jeita_enabled" "$battery/input_current_limited"
        "$battery/input_suspend" "$ext/chg_pwr_fcc" "$ext/chg_pwr_icl"
        "$ext/max_charge_current" "$battery/constant_charge_current"
        "$battery/constant_charge_current_max" "$usb/boost_current"
        "/sys/kernel/fast_charge/force_fast_charge" "/sys/class/qcom-battery/restricted_charging" "$qc/restrict_cur" "$main/input_current_settled" "$main/constant_charge_current_max"
    )
    chmod 0667 "${files[@]}"
}

# Control charging parameters
set_charging_limits() {
    echo "$1" > "$ext/max_charge_current"
    echo "$1" > "$battery/constant_charge_current"
    echo "$1" > "$battery/constant_charge_current_max"
    echo "$1" > "$main/current_max"
    echo "$1" > "$main/constant_charge_current_max"
    echo "$1" > "$usb/current_max"
    echo "$1" > "$usb/hw_current_max"
    echo "$1" > "$ext/chg_pwr_fcc"
    echo "$1" > "$ext/chg_pwr_icl"
    echo "$1" > "$usb/input_current_settled"
    echo "$1" > "$qc/restrict_cur"
    echo "$1" > "$dc/current_max"
    echo "$1" > "$main/input_current_settled"
}

# Update temperature settings
set_temperature() {
    echo "$1" > "$bms/temp_cool"
    echo "$2" > "$bms/temp_hot"
    echo "$3" > "$bms/temp_warm"
    echo "$4" > "$bms/temp_cold"
    echo "$5" > "$battery/system_temp_level"
}

# Adjust temperature (mode)
adjust_temperature() {
    case "$1" in
        "balance") set_temperature 120 610 590 0 0 ;;
        "performance") set_temperature 120 650 610 0 0 ;;
        *) set_temperature 120 610 590 0 0 ;;
    esac
}

# Adjust limit (mode)
adjust_limit() {
    case "$1" in
        "balance") mode_current_limit="$limit_bal" ;;
        "performance") mode_current_limit="$limit_perf" ;;
        *) mode_current_limit="$limit_bal" ;;
    esac
}

# Reset values to initial state
reset_values() {
    echo "$temp_cool" > "$bms/temp_cool"
    echo "$temp_hot" > "$bms/temp_hot"
    echo "$temp_warm" > "$bms/temp_warm"
    echo "$temp_cold" > "$bms/temp_cold"
    echo "$system_temp_level" > "$battery/system_temp_level"
    echo "$sw_jeita_enabled" > "$battery/sw_jeita_enabled"
    echo "$input_current_limited" > "$battery/input_current_limited"
    echo "$input_suspend" > "$battery/input_suspend"
    echo "$boost_current" > "$usb/boost_current"
    echo "$voltage_max" > "$usb/voltage_max"
    echo "$voltage_min" > "$usb/voltage_min"
    echo "$chg_pwr_fcc" > "$ext/chg_pwr_fcc"
    echo "$chg_pwr_icl" > "$ext/chg_pwr_icl"
    echo "$max_charge_current" > "$ext/max_charge_current"
    echo "$constant_charge_current" > "$battery/constant_charge_current"
    echo "$constant_charge_current_max" > "$battery/constant_charge_current_max"
    echo "$constant_charge_current_max_main" > "$main/constant_charge_current_max"
    echo "$current_max_usb" > "$usb/current_max"
    echo "$hw_current_max" > "$usb/hw_current_max"
    echo "$input_current_settled" > "$usb/input_current_settled"
}

# Main script
set_permissions
read_initial_values
discharging_executed=false
sleep_interval=1
echo "1" > /sys/kernel/fast_charge/force_fast_charge
echo "0" > /sys/class/qcom-battery/restricted_charging
echo "1" > /sys/class/power_supply/usb/boost_current
echo "0" > /sys/class/power_supply/battery/restricted_charging
echo "1" > /sys/class/power_supply/usb/pd_allowed
echo "1" > /sys/class/power_supply/allow_hvdcp3
echo "1" > /sys/class/power_supply/battery/subsystem/usb/pd_allowed
echo "0" > /sys/class/power_supply/battery/safety_timer_enabled

while true; do
    cc=$(getprop lynx.cc)
    battery_status=$(awk '{print tolower($0)}' "$battery/status")
    battery_level=$(<"$battery/capacity")
    lynx_fcc=$(getprop lynx.fcc)
    lynx_lcc=$(getprop lynx.lcc)
    lynx_ac=$(getprop lynx.ac)
    lynx_max_ac=$(getprop lynx.max.ac)
    lynx_min_ac=$(getprop lynx.min.ac)
    lynx_mode=$(getprop lynx.mode)
    
    limit_bal=$(awk -v lynx="$lynx_fcc" 'BEGIN {print lynx * 1000000}')
    limit_perf=$(awk -v lynx="$lynx_lcc" 'BEGIN {print lynx * 1000000}')

    adjust_limit "$lynx_mode"

    case "$cc" in
        1)
            case "$battery_status" in
                "full")
                    if [ "$lynx_ac" -eq 1 ]; then
                        if [ "$battery_level" -ge "$lynx_max_ac" ] && ! $discharging_executed; then
                            set_temperature 120 650 610 0 0
                            set_charging_limits 0
                            discharging_executed=true
                        elif [ "$battery_level" -le "$lynx_min_ac" ]; then
                            set_charging_limits "$mode_current_limit"
                            adjust_temperature "$lynx_mode"
                            discharging_executed=false
                        fi
                    elif [ "$lynx_ac" -eq 0 ]; then
                        if [ "$battery_level" -eq 100 ] && ! $discharging_executed; then
                            set_temperature 120 610 590 0 0
                        fi
                    fi
                    ;;
                "not charging")
                    if [ "$lynx_ac" -eq 1 ]; then
                        if [ "$lynx_max_ac" -eq "$lynx_min_ac" ]; then
                            lynx_min_ac=$((lynx_min_ac - 3))
                            setprop lynx.min.ac "$lynx_min_ac"
                        elif [ "$battery_level" -le "$lynx_min_ac" ]; then
                            adjust_temperature "$lynx_mode"
                            set_charging_limits "$mode_current_limit"
                            discharging_executed=false
                        elif [ "$battery_level" -gt "$lynx_min_ac" ] && [ "$battery_level" -lt "$lynx_max_ac" ] && ! $discharging_executed; then
                            set_charging_limits 0
                            discharging_executed=true
                        fi
                    fi
                    ;;
                "charging")
                    if [ "$lynx_ac" -eq "1" ]; then
                        if [ "$battery_level" -ge "$lynx_max_ac" ] && ! $discharging_executed; then
                            set_temperature 120 650 610 0 0
                            set_charging_limits 0
                            discharging_executed=true
                        elif [ "$battery_level" -le "$lynx_min_ac" ]; then
                            set_charging_limits "$mode_current_limit"
                            adjust_temperature "$lynx_mode"
                            discharging_executed=false
                        elif [ "$battery_level" -gt "$lynx_min_ac" ] &&  [ "$battery_level" -lt "$lynx_max_ac" ]; then
                            set_charging_limits "$mode_current_limit"
                            adjust_temperature "$lynx_mode"
                            discharging_executed=false
                        fi
                    else
                        set_charging_limits "$mode_current_limit"
                        adjust_temperature "$lynx_mode"
                        discharging_executed=false
                    fi
                    ;;
                "discharging")
                    if [ "$battery_level" -le 50 ] && ! $discharging_executed; then
                        adjust_temperature "$lynx_mode"
                        discharging_executed=true
                    fi
                    ;;
                *)
                    ;;
            esac
            ;;
        0)
            # Reset values
            reset_values
            ;;
    esac
    sleep "$sleep_interval"
done
