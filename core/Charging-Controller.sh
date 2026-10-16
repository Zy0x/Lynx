#!/system/bin/sh
# Lynx Universal - Charging Controller Facade (Delegates 100% to lynxd with sysfs fallbacks)
MODPATH="/data/adb/modules/Lynx"
[ -d "$MODPATH" ] || MODPATH="${0%/*/*}"
LYNXD="$MODPATH/system/bin/lynxd"
[ -x "$LYNXD" ] || LYNXD="/system/bin/lynxd"

# If lynxd binary is available and executable, delegate all actions to it
if [ -x "$LYNXD" ]; then
    case "$1" in
        apply)
            exec "$LYNXD" charging apply 2>/dev/null
            ;;
        status)
            exec "$LYNXD" charging status 2>/dev/null
            ;;
        standard)
            exec "$LYNXD" charging mode standard 2>/dev/null
            ;;
        cool)
            exec "$LYNXD" charging mode cool 1000 2>/dev/null
            ;;
        hyper|extreme)
            exec "$LYNXD" charging mode hyper 2>/dev/null
            ;;
        custom)
            exec "$LYNXD" charging mode custom "$2" 2>/dev/null
            ;;
        disconnect)
            exec "$LYNXD" charging disconnect 2>/dev/null
            ;;
        *)
            exec "$LYNXD" charging tick 2>/dev/null
            ;;
    esac
fi

# ==============================================================================
# Standalone Shell Fallback (Active when lynxd binary is not yet compiled or present)
# ==============================================================================

MTK_DIR="/sys/devices/platform/charger"
TRAN_ODM="/sys/devices/platform/odm/odm:tran_battery"
TRAN_DIR="/sys/devices/platform/tran_battery"
BATT_DIR="/sys/class/power_supply/battery"
BMS_DIR="/sys/class/power_supply/bms"
QC_DIR="/sys/class/qcom-battery"
USB_DIR="/sys/class/power_supply/usb"
MAIN_DIR="/sys/class/power_supply/main"
PCA_DIR="/sys/devices/platform/pca_dv2_algo"

write_node_lock() {
    local node="$1"
    local val="$2"
    if [ -e "$node" ]; then
        chmod 666 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
        chmod 444 "$node" 2>/dev/null
    fi
}

unlock_node() {
    local node="$1"
    [ -e "$node" ] && chmod 666 "$node" 2>/dev/null
}

fallback_hyper() {
    mkdir -p /dev 2>/dev/null
    echo "hyper-12000" > /dev/lynx_extreme_charging 2>/dev/null
    echo "state=hyper cap=50" > /dev/lynx_charging_state 2>/dev/null
    rm -f /dev/lynx_bypass_active /dev/lynx_standard_charging 2>/dev/null

    # 1. Native Battery & Linux Charge Rails Uncap
    local qcom_cur="12000000"
    if [ ! -d /sys/class/power_supply/smb1390 ] && [ ! -d /sys/class/power_supply/charge_pump ] && { [ -d "$QC_DIR" ] || [ -d /sys/class/power_supply/bms ]; }; then
        qcom_cur="4500000"
    fi

    write_node_lock "$BATT_DIR/input_suspend" "0"
    write_node_lock "$BATT_DIR/charging_enabled" "1"
    write_node_lock "$BATT_DIR/fastcharge_mode" "1"
    write_node_lock "$BATT_DIR/fast_charge" "1"
    write_node_lock "$BATT_DIR/boost_current" "1"
    write_node_lock "$BATT_DIR/constant_charge_current_max" "$qcom_cur"
    write_node_lock "$BATT_DIR/constant_charge_current" "$qcom_cur"
    write_node_lock "$BATT_DIR/input_current_limit" "$qcom_cur"
    write_node_lock "$USB_DIR/current_max" "5000000"
    write_node_lock "$MAIN_DIR/current_max" "$qcom_cur"

    # 2. R1 Screen-On Charging Throttle Bypass & CallState Lock
    write_node_lock "$MTK_DIR/BatteryNotify" "0"
    write_node_lock "$MTK_DIR/BN_TestMode" "1"
    write_node_lock "$MTK_DIR/tran_charger_full" "0"
    write_node_lock "$MTK_DIR/tran_game_mode" "0"
    write_node_lock "$MTK_DIR/bypass_charger" "0"
    write_node_lock "$MTK_DIR/sw_jeita" "0"
    write_node_lock "$MTK_DIR/sc_tuisoc" "100"
    write_node_lock "$TRAN_ODM/Charging_CallState" "0"
    write_node_lock "$TRAN_DIR/Charging_CallState" "0"

    # 3. Qualcomm & Universal Charge Controls
    write_node_lock "$QC_DIR/restricted_charging" "0"
    write_node_lock "$QC_DIR/restrict_cur" "0"
    write_node_lock "$QC_DIR/direct_charging" "1"
    write_node_lock "$QC_DIR/system_temp_level" "0"
    write_node_lock "$BATT_DIR/system_temp_level" "0"
    write_node_lock "$BATT_DIR/temp_state" "0"
    write_node_lock "$BATT_DIR/thermal_input_current_limit" "-1"
    write_node_lock "$BATT_DIR/step_charging_enabled" "0"
    write_node_lock "$BATT_DIR/current_max" "12000000"
    write_node_lock "$BATT_DIR/charge_control_limit_max" "0"

    # 4. Thermal Uncapped (Peak 6000mA at all temperatures)
    write_node_lock "$TRAN_ODM/pcb_thermal_debug" "[95,6000,100,6000,6000000]"
    write_node_lock "$TRAN_DIR/pcb_thermal_debug" "[95,6000,100,6000,6000000]"
    write_node_lock "$PCA_DIR/dv2_debug" "[101,85,88,90,6000,6000,6000]"
    write_node_lock "$PCA_DIR/dv2_debug" "[102,85,88,90,6000,6000,6000]"
    write_node_lock "/sys/devices/platform/battery/Battery_Temperature" "25"
    write_node_lock "/proc/driver/thermal/clabcct" "70000 1000 200000 6000 6000 0 1 1 0"
    write_node_lock "/proc/driver/thermal/clabcct_lcmoff" "1 70000 1000 200000 6000 6000 0 6000 0"

    # Dynamic Cooling Device Detection: only touch battery charge cooling devices (bcct / abcct)
    for cd in /sys/class/thermal/cooling_device*; do
        if [ -f "$cd/type" ]; then
            cd_t=$(cat "$cd/type" 2>/dev/null | tr '[:upper:]' '[:lower:]')
            case "$cd_t" in
                *bcct*|*abcct*) write_node_lock "$cd/cur_state" "0" ;;
            esac
        fi
    done

    # Dynamic Thermal Zone Detection: only disable battery thermal zones, never touch CPU/GPU
    for tz in /sys/class/thermal/thermal_zone*; do
        if [ -f "$tz/type" ]; then
            tz_t=$(cat "$tz/type" 2>/dev/null | tr '[:upper:]' '[:lower:]')
            case "$tz_t" in
                battery|mtktsbattery|mtktsap)
                    write_node_lock "$tz/mode" "disabled"
                    for trip in "$tz"/trip_point_*_temp; do
                        [ -f "$trip" ] && write_node_lock "$trip" "99000"
                    done
                    ;;
            esac
        fi
    done

    for daemon in thermalloadalgod com.xiaomi.joyose mi_thermald thermal-engine thermal-engine-v2; do
        killall -STOP "$daemon" 2>/dev/null
    done

    # 5. Continuous Maximum Pump Current Enforcement (>6A Battery Current)
    write_node_lock "$MTK_DIR/pe20" "1"
    write_node_lock "$MTK_DIR/pe40" "1"

    # Voltage-Gated Pump: Only enable switched capacitor pump when adapter has stepped up to >= 6.5V
    local adpv=$(cat "$MTK_DIR/ADC_Charger_Voltage" 2>/dev/null || cat "$USB_DIR/voltage_now" 2>/dev/null || echo 0)
    [ "$adpv" -gt 100000 ] 2>/dev/null && adpv=$((adpv / 1000))
    if [ "$adpv" -ge 6500 ] 2>/dev/null; then
        write_node_lock "$MTK_DIR/enable_sc" "1"
    else
        write_node_lock "$MTK_DIR/enable_sc" "0"
    fi
    write_node_lock "$MTK_DIR/input_current" "33170"
    write_node_lock "$MTK_DIR/sc_ibat_limit" "12000"
    write_node_lock "$MTK_DIR/chg1_current" "33170"
    write_node_lock "$MTK_DIR/chg2_current" "33170"
    if [ -e "$MTK_DIR/pdc_max_watt" ] && [ ! -f /dev/lynx_orig_pdc_watt ]; then
        cat "$MTK_DIR/pdc_max_watt" 2>/dev/null > /dev/lynx_orig_pdc_watt
    fi
    write_node_lock "$MTK_DIR/pdc_max_watt" "120"
}

fallback_disconnect() {
    rm -f /dev/lynx_extreme_charging /dev/lynx_standard_charging /dev/lynx_charging_guard /dev/lynx_bypass_active 2>/dev/null
    echo "state=disconnect" > /dev/lynx_charging_state 2>/dev/null

    # Unlock all locked sysfs nodes (MTK, QCOM, Universal Linux)
    for node in \
        "$QC_DIR/restricted_charging" "$QC_DIR/restrict_cur" "$QC_DIR/direct_charging" \
        "$QC_DIR/system_temp_level" "$BATT_DIR/system_temp_level" "$BATT_DIR/temp_state" \
        "$BATT_DIR/thermal_input_current_limit" "$BATT_DIR/step_charging_enabled" \
        "$BATT_DIR/current_max" "$BATT_DIR/charge_control_limit_max" \
        "$MTK_DIR/input_current" "$MTK_DIR/sc_ibat_limit" "$MTK_DIR/chg1_current" "$MTK_DIR/chg2_current" \
        "$MTK_DIR/enable_sc" "$MTK_DIR/pe20" "$MTK_DIR/pe40" "$MTK_DIR/pdc_max_watt" \
        "$MTK_DIR/BN_TestMode" "$MTK_DIR/BatteryNotify" "$MTK_DIR/tran_charger_full" "$MTK_DIR/tran_game_mode" \
        "$MTK_DIR/bypass_charger" "$MTK_DIR/sw_jeita" \
        "$BATT_DIR/input_suspend" "$BATT_DIR/charging_enabled" "$BATT_DIR/fastcharge_mode" "$BATT_DIR/fast_charge" \
        "$BATT_DIR/boost_current" "$BATT_DIR/constant_charge_current_max" "$BATT_DIR/constant_charge_current" \
        "$BATT_DIR/input_current_limit" "$USB_DIR/current_max" "$MAIN_DIR/current_max" \
        "$TRAN_ODM/pcb_thermal_debug" "$TRAN_DIR/pcb_thermal_debug" "$PCA_DIR/dv2_debug" \
        "$TRAN_ODM/Charging_CallState" "$TRAN_DIR/Charging_CallState" \
        "/sys/devices/platform/battery/Battery_Temperature" \
        "/proc/driver/thermal/clabcct" "/proc/driver/thermal/clabcct_lcmoff"; do
        unlock_node "$node"
    done

    # Unlock BCCT cooling devices
    for cd in /sys/class/thermal/cooling_device*; do
        if [ -f "$cd/type" ]; then
            cd_t=$(cat "$cd/type" 2>/dev/null | tr '[:upper:]' '[:lower:]')
            case "$cd_t" in
                *bcct*|*abcct*) unlock_node "$cd/cur_state" ;;
            esac
        fi
    done

    # Unlock & restore battery thermal zones
    for tz in /sys/class/thermal/thermal_zone*; do
        if [ -f "$tz/type" ]; then
            tz_t=$(cat "$tz/type" 2>/dev/null | tr '[:upper:]' '[:lower:]')
            case "$tz_t" in
                battery|mtktsbattery|mtktsap)
                    unlock_node "$tz/mode"
                    echo "enabled" > "$tz/mode" 2>/dev/null
                    for trip in "$tz"/trip_point_*_temp; do
                        [ -f "$trip" ] && unlock_node "$trip"
                    done
                    ;;
            esac
        fi
    done

    # Revert to safe OEM baselines
    [ -e "$MTK_DIR/enable_sc" ] && echo "0" > "$MTK_DIR/enable_sc" 2>/dev/null
    [ -e "$MTK_DIR/pe20" ] && echo "0" > "$MTK_DIR/pe20" 2>/dev/null
    [ -e "$MTK_DIR/pe40" ] && echo "0" > "$MTK_DIR/pe40" 2>/dev/null
    [ -e "$MTK_DIR/input_current" ] && echo "24576" > "$MTK_DIR/input_current" 2>/dev/null
    [ -e "$MTK_DIR/sc_ibat_limit" ] && echo "2000" > "$MTK_DIR/sc_ibat_limit" 2>/dev/null
    [ -e "$MTK_DIR/chg1_current" ] && echo "24576" > "$MTK_DIR/chg1_current" 2>/dev/null
    [ -e "$MTK_DIR/chg2_current" ] && echo "24576" > "$MTK_DIR/chg2_current" 2>/dev/null
    echo "0" > "$TRAN_ODM/Charging_CallState" 2>/dev/null
    echo "0" > "$TRAN_DIR/Charging_CallState" 2>/dev/null
    if [ -f /dev/lynx_orig_pdc_watt ]; then
        orig_pdc=$(cat /dev/lynx_orig_pdc_watt 2>/dev/null)
        [ -n "$orig_pdc" ] && [ -e "$MTK_DIR/pdc_max_watt" ] && echo "$orig_pdc" > "$MTK_DIR/pdc_max_watt" 2>/dev/null
        rm -f /dev/lynx_orig_pdc_watt 2>/dev/null
    elif [ -e "$MTK_DIR/pdc_max_watt" ]; then
        cur_pdc=$(cat "$MTK_DIR/pdc_max_watt" 2>/dev/null)
        if [ "$cur_pdc" = "120" ] || [ -z "$cur_pdc" ]; then
            echo "33" > "$MTK_DIR/pdc_max_watt" 2>/dev/null
        fi
    fi
    [ -e "$MTK_DIR/BN_TestMode" ] && echo "0" > "$MTK_DIR/BN_TestMode" 2>/dev/null
    [ -e "$MTK_DIR/BatteryNotify" ] && echo "1" > "$MTK_DIR/BatteryNotify" 2>/dev/null
    [ -e "$MTK_DIR/tran_charger_full" ] && echo "0" > "$MTK_DIR/tran_charger_full" 2>/dev/null
    [ -e "$MTK_DIR/tran_game_mode" ] && echo "0" > "$MTK_DIR/tran_game_mode" 2>/dev/null
    [ -e "$MTK_DIR/sw_jeita" ] && echo "1" > "$MTK_DIR/sw_jeita" 2>/dev/null

    # Thermal reversions
    [ -e "$TRAN_ODM/pcb_thermal_debug" ] && echo "[45,3000,50,2000,1000]" > "$TRAN_ODM/pcb_thermal_debug" 2>/dev/null
    if [ -e "/sys/devices/platform/battery/Battery_Temperature" ]; then
        local_temp=35
        if [ -f "/sys/bus/iio/devices/iio:device1/in_voltage3_BAT_TEMP_input" ]; then
            local_adc=$(cat "/sys/bus/iio/devices/iio:device1/in_voltage3_BAT_TEMP_input" 2>/dev/null)
            if [ -n "$local_adc" ] && [ "$local_adc" -gt 200 ] && [ "$local_adc" -lt 1500 ] 2>/dev/null; then
                local_temp=$(( 70 - local_adc / 20 ))
                [ "$local_temp" -lt 20 ] && local_temp=25
                [ "$local_temp" -gt 50 ] && local_temp=45
            fi
        elif [ -f "/sys/class/power_supply/battery/temp" ]; then
            local_raw=$(cat "/sys/class/power_supply/battery/temp" 2>/dev/null)
            [ -n "$local_raw" ] && [ "$local_raw" -gt 100 ] 2>/dev/null && local_temp=$(( local_raw / 10 ))
        fi
        echo "$local_temp" > "/sys/devices/platform/battery/Battery_Temperature" 2>/dev/null
    fi
    [ -e "/proc/driver/thermal/clabcct" ] && echo "1 42000 1000 200000 5 2000 0" > "/proc/driver/thermal/clabcct" 2>/dev/null
    [ -e "/proc/driver/thermal/clabcct_lcmoff" ] && echo "1 43000 1000 200000 5 2000 0" > "/proc/driver/thermal/clabcct_lcmoff" 2>/dev/null

    # Reset Android battery framework state
    dumpsys battery reset 2>/dev/null

    # Resume suspended userspace thermal daemons
    for daemon in thermalloadalgod com.xiaomi.joyose mi_thermald thermal-engine thermal-engine-v2; do
        killall -CONT "$daemon" 2>/dev/null
    done
}

fallback_cool() {
    mkdir -p /dev 2>/dev/null
    echo "regulated-1000" > /dev/lynx_standard_charging 2>/dev/null
    echo "state=cool cap=50" > /dev/lynx_charging_state 2>/dev/null
    rm -f /dev/lynx_extreme_charging /dev/lynx_bypass_active 2>/dev/null

    local adpv=$(cat "$MTK_DIR/ADC_Charger_Voltage" 2>/dev/null || cat "$USB_DIR/voltage_now" 2>/dev/null || echo 0)
    [ "$adpv" -gt 100000 ] 2>/dev/null && adpv=$((adpv / 1000))
    if [ "$adpv" -ge 6000 ] 2>/dev/null; then
        chmod 666 "$BATT_DIR/input_suspend" 2>/dev/null
        echo "1" > "$BATT_DIR/input_suspend" 2>/dev/null
        sleep 0.15 2>/dev/null || usleep 150000 2>/dev/null
        echo "0" > "$BATT_DIR/input_suspend" 2>/dev/null
        in_cur=$(( 1000 * 4200 / adpv ))
        [ "$in_cur" -lt 350 ] && in_cur=350
        [ "$in_cur" -gt 1000 ] && in_cur=1000
    else
        in_cur=1000
    fi

    write_node_lock "$MTK_DIR/pe20" "0"
    write_node_lock "$MTK_DIR/pe40" "0"
    write_node_lock "$MTK_DIR/enable_sc" "0"
    write_node_lock "$MTK_DIR/input_current" "$in_cur"
    write_node_lock "$MTK_DIR/sc_ibat_limit" "1000"
    write_node_lock "$MTK_DIR/chg1_current" "$in_cur"
    write_node_lock "$MTK_DIR/chg2_current" "$in_cur"
    write_node_lock "$MTK_DIR/pdc_max_watt" "5"
    write_node_lock "$MTK_DIR/BN_TestMode" "0"
    write_node_lock "$MTK_DIR/BatteryNotify" "1"
    write_node_lock "$MTK_DIR/sw_jeita" "1"
    write_node_lock "$QC_DIR/restricted_charging" "1"
    write_node_lock "$QC_DIR/restrict_cur" "1000000"
    write_node_lock "$BATT_DIR/current_max" "1000000"
    write_node_lock "$BATT_DIR/constant_charge_current_max" "1000000"
    write_node_lock "$BATT_DIR/constant_charge_current" "1000000"
    write_node_lock "$USB_DIR/current_max" "$((in_cur * 1000))"
}

fallback_standard() {
    rm -f /dev/lynx_extreme_charging /dev/lynx_bypass_active 2>/dev/null
    echo "standard" > /dev/lynx_standard_charging 2>/dev/null
    echo "state=standard cap=50" > /dev/lynx_charging_state 2>/dev/null

    for node in "$MTK_DIR/input_current" "$MTK_DIR/sc_ibat_limit" "$MTK_DIR/chg1_current" "$MTK_DIR/chg2_current" \
        "$MTK_DIR/enable_sc" "$MTK_DIR/pe20" "$MTK_DIR/pe40" "$MTK_DIR/pdc_max_watt" \
        "$QC_DIR/restricted_charging" "$QC_DIR/restrict_cur" "$BATT_DIR/current_max" \
        "$BATT_DIR/constant_charge_current_max" "$BATT_DIR/constant_charge_current" "$USB_DIR/current_max"; do
        unlock_node "$node"
    done

    write_node_lock "$QC_DIR/restricted_charging" "0"
    write_node_lock "$QC_DIR/restrict_cur" "0"
    write_node_lock "$MTK_DIR/BN_TestMode" "0"
    write_node_lock "$MTK_DIR/BatteryNotify" "1"
    write_node_lock "$MTK_DIR/sw_jeita" "1"
    write_node_lock "$MTK_DIR/pe20" "1"
    write_node_lock "$MTK_DIR/pe40" "1"
    [ -e "$MTK_DIR/pdc_max_watt" ] && write_node_lock "$MTK_DIR/pdc_max_watt" "33"
}

fallback_custom() {
    local target="${1:-2000}"
    [ "$target" -lt 500 ] && target=500
    [ "$target" -gt 25000 ] && target=25000
    if [ "$target" -ge 5000 ]; then
        fallback_hyper
    else
        mkdir -p /dev 2>/dev/null
        echo "regulated-$target" > /dev/lynx_standard_charging 2>/dev/null
        echo "state=custom_${target} cap=50" > /dev/lynx_charging_state 2>/dev/null
        rm -f /dev/lynx_extreme_charging /dev/lynx_bypass_active 2>/dev/null

        write_node_lock "$MTK_DIR/pe20" "0"
        write_node_lock "$MTK_DIR/pe40" "0"
        write_node_lock "$MTK_DIR/enable_sc" "0"
        write_node_lock "$MTK_DIR/input_current" "$target"
        write_node_lock "$MTK_DIR/sc_ibat_limit" "$target"
        write_node_lock "$MTK_DIR/chg1_current" "$target"
        write_node_lock "$MTK_DIR/chg2_current" "$target"
        write_node_lock "$QC_DIR/restricted_charging" "1"
        write_node_lock "$QC_DIR/restrict_cur" "$((target * 1000))"
        write_node_lock "$BATT_DIR/current_max" "$((target * 1000))"
        write_node_lock "$BATT_DIR/constant_charge_current_max" "$((target * 1000))"
        write_node_lock "$BATT_DIR/constant_charge_current" "$((target * 1000))"
        write_node_lock "$USB_DIR/current_max" "$((target * 1000))"
    fi
}

case "$1" in
    hyper|extreme)
        fallback_hyper
        ;;
    cool)
        fallback_cool
        ;;
    standard)
        fallback_standard
        ;;
    custom)
        fallback_custom "$2"
        ;;
    disconnect|reset)
        fallback_disconnect
        ;;
    *)
        # Default fallback
        [ -e "$MTK_DIR/BN_TestMode" ] && echo "1" > "$MTK_DIR/BN_TestMode" 2>/dev/null
        ;;
esac
