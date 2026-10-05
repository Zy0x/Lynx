#!/system/bin/sh
# Lynx Universal - Power Supply, Hardware Bypass & Charging Regulator
# True Hardware Bypass, Extreme Ultra-Fast Charging, & Thermal Safety
# Fully POSIX compliant for /system/bin/sh (Android Toybox/ash)

MODPATH="/data/adb/modules/Lynx"
[ -d "$MODPATH" ] || MODPATH="${0%/*/*}"

CONFIG_FILE="$MODPATH/config.json"
[ -f "$CONFIG_FILE" ] || CONFIG_FILE="/data/adb/lynx/config.json"
[ -f "$CONFIG_FILE" ] || CONFIG_FILE="/data/user/0/com.noir.lynx/files/config.json"
[ -f "$CONFIG_FILE" ] || CONFIG_FILE="/data/user/0/com.noir.lynx.debug/files/config.json"

PS_DIR="/sys/class/power_supply"
BATT_DIR="$PS_DIR/battery"
USB_DIR="$PS_DIR/usb"
MAIN_DIR="$PS_DIR/main"
EXT_DIR="$PS_DIR/battery_ext"
QC_DIR="/sys/class/qcom-battery"
MTK_DIR="/sys/devices/platform/charger"

read_node() {
    [ -r "$1" ] && cat "$1" 2>/dev/null
}

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        echo "$val" > "$node" 2>/dev/null && return 0
        chmod 666 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

write_node_lock() {
    local val="$1"
    local node="$2"
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

unlock_extreme_nodes() {
    for node in \
        "$BATT_DIR/constant_charge_current_max" \
        "$BATT_DIR/constant_charge_current" \
        "$BATT_DIR/current_max" \
        "$BATT_DIR/input_current_limit" \
        "$MAIN_DIR/constant_charge_current_max" \
        "$MAIN_DIR/current_max" \
        "$USB_DIR/current_max" \
        "$USB_DIR/hw_current_max" \
        "$BATT_DIR/charge_control_limit_max" \
        "$BATT_DIR/charge_control_limit" \
        "$BATT_DIR/input_suspend" \
        "$BATT_DIR/fastcharge_mode" \
        "$BATT_DIR/fast_charge" \
        "$BATT_DIR/charging_enabled" \
        "$MTK_DIR/BN_TestMode" \
        "$MTK_DIR/BatteryNotify" \
        "$MTK_DIR/sw_jeita" \
        "$MTK_DIR/tran_charger_full" \
        "$MTK_DIR/bypass_charger" \
        "$MTK_DIR/tran_game_mode" \
        "$MTK_DIR/input_current" \
        "$MTK_DIR/chg1_current" \
        "$MTK_DIR/chg2_current" \
        "$MTK_DIR/sc_ibat_limit" \
        "$MTK_DIR/pe40" \
        "$MTK_DIR/pe20" \
        "$MTK_DIR/pdc_max_watt" \
        "$MTK_DIR/enable_sc" \
        "$QC_DIR/direct_charging" \
        "$QC_DIR/restricted_charging" \
        "$QC_DIR/restrict_cur" \
        "$BATT_DIR/system_temp_level" \
        "$BATT_DIR/temp_state" \
        "$BATT_DIR/thermal_input_current_limit" \
        "$BATT_DIR/input_current_settled" \
        "$BATT_DIR/boost_current" \
        "$BATT_DIR/step_charging_enabled" \
        "$BATT_DIR/quick_charge_type" \
        "$BATT_DIR/siop_level" \
        "$BATT_DIR/store_mode" \
        "$BATT_DIR/batt_slate_mode" \
        "$BATT_DIR/wc_control" \
        "$BATT_DIR/afc_result" \
        "$BATT_DIR/direct_charger_mode" \
        "$BATT_DIR/hv_charger_status" \
        "$BATT_DIR/cool_mode" \
        "$BATT_DIR/call_mode" \
        "$BATT_DIR/vooc_charging" \
        "$BATT_DIR/fast_charge_user_type" \
        "$BATT_DIR/authenticate" \
        "$BATT_DIR/charge_stop_level" \
        "/sys/devices/platform/google,battery/charge_stop_level" \
        "/sys/devices/platform/google,charger/charge_stop_level" \
        "$BATT_DIR/bd_trickle_dry_run" \
        "$BATT_DIR/device/smart_charging" \
        "$BATT_DIR/charging_limit_mode" \
        "$BATT_DIR/mmi_charging_enable" \
        "$BATT_DIR/factory_mode" \
        /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug \
        /sys/devices/platform/tran_battery/pcb_thermal_debug; do
        [ -e "$node" ] && chmod 666 "$node" 2>/dev/null
    done

    for tz in /sys/class/thermal/thermal_zone*; do
        [ -d "$tz" ] || continue
        tz_type=$(cat "$tz/type" 2>/dev/null | tr '[:upper:]' '[:lower:]')
        case "$tz_type" in
            *battery*|*bms*|*chg*|*charger*|*mtktsap*|*tsbuck*|*skin*|*pcb*|*sub_batt*|*quiet*|*xo_therm*|*pmic*)
                if [ -e "$tz/mode" ]; then
                    chmod 666 "$tz/mode" 2>/dev/null
                    echo enabled > "$tz/mode" 2>/dev/null
                fi
                ;;
        esac
    done

    killall -CONT com.xiaomi.joyose 2>/dev/null
    cmd thermalservice reset 2>/dev/null
}

# Detect OEM Hardware Bypass nodes
detect_hw_bypass() {
    # Infinix / Transsion
    if [ -e "/sys/devices/platform/charger/bypass_charger" ]; then
        echo "/sys/devices/platform/charger/bypass_charger"
    # ASUS ROG
    elif [ -e "/sys/class/power_supply/battery/device/smart_charging" ]; then
        echo "/sys/class/power_supply/battery/device/smart_charging"
    elif [ -e "/sys/class/power_supply/battery/charging_limit_mode" ]; then
        echo "/sys/class/power_supply/battery/charging_limit_mode"
    # Sony Xperia
    elif [ -e "/sys/class/power_supply/battery/smart_charging_activation" ]; then
        echo "/sys/class/power_supply/battery/smart_charging_activation"
    # Xiaomi / Qualcomm
    elif [ -e "/sys/class/qcom-battery/direct_charging" ]; then
        echo "/sys/class/qcom-battery/direct_charging"
    # Samsung One UI
    elif [ -e "/sys/class/power_supply/battery/store_mode" ]; then
        echo "/sys/class/power_supply/battery/store_mode"
    elif [ -e "/sys/class/power_supply/battery/batt_slate_mode" ]; then
        echo "/sys/class/power_supply/battery/batt_slate_mode"
    # Google Tensor
    elif [ -e "/sys/class/power_supply/battery/charge_stop_level" ]; then
        echo "/sys/class/power_supply/battery/charge_stop_level"
    else
        echo ""
    fi
}
HW_BYPASS_NODE=$(detect_hw_bypass)

# -----------------------------------------------------------------------------
# Mode 1: True Bypass Charging (Power direct to Motherboard / Vsys)
# Ensures zero battery drain and zero battery charge (percentage latched).
# -----------------------------------------------------------------------------
apply_bypass_charging() {
    rm -f /dev/lynx_extreme_charging 2>/dev/null
    unlock_extreme_nodes

    # CRITICAL: DO NOT SUSPEND INPUT! System must draw 100% operating power from charger!
    write_node "0" "$BATT_DIR/input_suspend"

    # Unlock charger input current so motherboard never draws from battery
    write_node "6000" "$MTK_DIR/input_current"
    write_node "4500000" "$USB_DIR/current_max"
    write_node "4500000" "$USB_DIR/hw_current_max"
    write_node "4500000" "$MAIN_DIR/current_max"

    # 1. Hardware OEM bypass switch if available
    [ -n "$HW_BYPASS_NODE" ] && write_node "1" "$HW_BYPASS_NODE"

    # 2. Universal Linux Kernel charge control limit (Stop charging at current level)
    write_node "1" "$BATT_DIR/charge_control_limit_max"
    write_node "0" "$BATT_DIR/charge_control_limit"

    local cur_cap
    cur_cap=$(read_node "$BATT_DIR/capacity")
    [ -z "$cur_cap" ] && cur_cap=80

    # 3. MediaTek Smart Charging lock to current capacity (zero battery current)
    write_node "1" "$MTK_DIR/enable_sc"
    write_node "$cur_cap" "$MTK_DIR/sc_tuisoc"
    write_node "0" "$MTK_DIR/sc_ibat_limit"
    write_node "0" "$MTK_DIR/chg1_current"
    write_node "0" "$MTK_DIR/chg2_current"

    # 4. Universal & Qualcomm Zero battery charge current while keeping Vsys alive
    write_node "0" "$BATT_DIR/constant_charge_current"
    write_node "0" "$BATT_DIR/constant_charge_current_max"
    write_node "0" "$QC_DIR/restrict_cur"
    write_node "1" "$QC_DIR/direct_charging"

    # 5. Multi-OEM specific bypass / slate modes
    # ASUS ROG
    write_node "1" "$BATT_DIR/device/smart_charging"
    write_node "1" "$BATT_DIR/charging_limit_mode"
    # Sony
    write_node "1" "$BATT_DIR/smart_charging_activation"
    # Samsung One UI
    write_node "1" "$BATT_DIR/store_mode"
    write_node "1" "$BATT_DIR/batt_slate_mode"
    chmod 666 "$BATT_DIR/siop_level" 2>/dev/null
    write_node "100" "$BATT_DIR/siop_level"
    chmod 444 "$BATT_DIR/siop_level" 2>/dev/null
    # OnePlus / Oppo
    chmod 666 "$BATT_DIR/cool_mode" 2>/dev/null
    write_node "0" "$BATT_DIR/cool_mode"
    chmod 444 "$BATT_DIR/cool_mode" 2>/dev/null
    chmod 666 "$BATT_DIR/call_mode" 2>/dev/null
    write_node "0" "$BATT_DIR/call_mode"
    chmod 444 "$BATT_DIR/call_mode" 2>/dev/null
    # Google Tensor
    write_node "$cur_cap" "$BATT_DIR/charge_stop_level"
    write_node "1" "$BATT_DIR/bd_trickle_dry_run"
    # Transsion
    write_node "1" "/sys/devices/platform/charger/bypass_charger"

    # Soft charging disable only if input_suspend is safe
    write_node "0" "$BATT_DIR/charging_enabled"
}

# -----------------------------------------------------------------------------
# Mode 2: Extreme Fast Charging (Ultra-High Current & Speed)
# Unlocks maximum hardware charging current, Pump Express / Fast Charge,
# Screen-On Throttling Bypass, Multi-OEM derate removal & Thermal clamp bypass!
# -----------------------------------------------------------------------------
apply_extreme_charging() {
    local target_soc="$1"
    local allow_lockout_bypass="$2"
    [ -z "$target_soc" ] && target_soc=90
    [ -z "$allow_lockout_bypass" ] && allow_lockout_bypass="true"

    touch /dev/lynx_extreme_charging 2>/dev/null

    # 1. Universal Linux & Android Rails (Uncap to 6A / 6000mA max headroom)
    for node in "$BATT_DIR/constant_charge_current_max" \
                "$BATT_DIR/constant_charge_current" \
                "$BATT_DIR/current_max" \
                "$BATT_DIR/input_current_limit" \
                "$MAIN_DIR/constant_charge_current_max" \
                "$MAIN_DIR/current_max" \
                "$USB_DIR/current_max" \
                "$USB_DIR/hw_current_max"; do
        write_node_lock "6000000" "$node"
    done
    for node in "$BATT_DIR/charge_control_limit_max" \
                "$BATT_DIR/charge_control_limit" \
                "$BATT_DIR/input_suspend"; do
        write_node_lock "0" "$node"
    done
    for node in "$BATT_DIR/fastcharge_mode" \
                "$BATT_DIR/fast_charge" \
                "$BATT_DIR/charging_enabled"; do
        write_node_lock "1" "$node"
    done

    # 2. MediaTek (Dimensity & Helio) Architecture & Hardware Bypass
    for node in "$MTK_DIR/BN_TestMode" \
                "$MTK_DIR/pe40" \
                "$MTK_DIR/pe20" \
                "$MTK_DIR/enable_sc"; do
        write_node_lock "1" "$node"
    done
    for node in "$MTK_DIR/BatteryNotify" \
                "$MTK_DIR/sw_jeita" \
                "$MTK_DIR/tran_charger_full" \
                "$MTK_DIR/bypass_charger" \
                "$MTK_DIR/tran_game_mode"; do
        write_node_lock "0" "$node"
    done
    for node in "$MTK_DIR/input_current" \
                "$MTK_DIR/chg1_current" \
                "$MTK_DIR/chg2_current"; do
        write_node_lock "24576" "$node"
    done
    write_node_lock "120" "$MTK_DIR/pdc_max_watt"
    write_node_lock "8000" "$MTK_DIR/sc_ibat_limit"
    write_node "$target_soc" "$MTK_DIR/sc_tuisoc"

    # 3. Qualcomm Snapdragon Architecture
    write_node_lock "1" "$QC_DIR/direct_charging"
    write_node_lock "0" "$QC_DIR/restricted_charging"
    write_node_lock "6000000" "$QC_DIR/restrict_cur"
    write_node_lock "0" "$QC_DIR/system_temp_level"
    write_node_lock "0" "$BATT_DIR/system_temp_level"
    write_node_lock "0" "$BATT_DIR/temp_state"

    # 4. Xiaomi / HyperOS / MIUI Screen-On & Thermal Throttling Bypass
    write_node_lock "6000000" "$BATT_DIR/thermal_input_current_limit"
    write_node_lock "6000000" "$BATT_DIR/input_current_settled"
    write_node_lock "1" "$BATT_DIR/boost_current"
    write_node_lock "0" "$BATT_DIR/step_charging_enabled"
    write_node_lock "2" "$BATT_DIR/quick_charge_type"
    killall -STOP com.xiaomi.joyose 2>/dev/null

    # 5. Samsung One UI (Exynos / Qualcomm) Screen-On Throttling Bypass
    write_node_lock "100" "$BATT_DIR/siop_level"
    write_node_lock "0" "$BATT_DIR/store_mode"
    write_node_lock "0" "$BATT_DIR/batt_slate_mode"
    write_node_lock "0" "$BATT_DIR/wc_control"
    write_node_lock "1" "$BATT_DIR/afc_result"
    write_node_lock "1" "$BATT_DIR/direct_charger_mode"
    write_node_lock "1" "$BATT_DIR/hv_charger_status"

    # 6. OnePlus / OPPO / Realme (ColorOS / OxygenOS) SuperVOOC Screen-On Bypass
    write_node_lock "0" "$BATT_DIR/cool_mode"
    write_node_lock "0" "$BATT_DIR/call_mode"
    write_node_lock "1" "$BATT_DIR/vooc_charging"
    write_node_lock "1" "$BATT_DIR/fast_charge_user_type"
    write_node_lock "1" "$BATT_DIR/authenticate"

    # 7. Google Tensor (Pixel) Fast Charging Unlock
    write_node_lock "100" "$BATT_DIR/charge_stop_level"
    write_node_lock "100" "/sys/devices/platform/google,battery/charge_stop_level"
    write_node_lock "100" "/sys/devices/platform/google,charger/charge_stop_level"
    write_node_lock "0" "$BATT_DIR/bd_trickle_dry_run"

    # 8. ASUS ROG & Motorola Charging Throttling Unlock
    write_node_lock "0" "$BATT_DIR/device/smart_charging"
    write_node_lock "0" "$BATT_DIR/charging_limit_mode"
    write_node_lock "1" "$BATT_DIR/mmi_charging_enable"
    write_node_lock "1" "$BATT_DIR/factory_mode"

    # 9. Transsion (Infinix / Tecno) Screen-On Throttling & PCB Thermal Bypass
    for node in /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug \
                /sys/devices/platform/tran_battery/pcb_thermal_debug; do
        if [ -e "$node" ]; then
            chmod 666 "$node" 2>/dev/null
            echo "[85,6000,90,5000,4500]" > "$node" 2>/dev/null
            chmod 444 "$node" 2>/dev/null
        fi
    done

    # 10. Universal Thermal Zones & Trip Points Bypass
    for tz in /sys/class/thermal/thermal_zone*; do
        [ -d "$tz" ] || continue
        tz_type=$(cat "$tz/type" 2>/dev/null | tr '[:upper:]' '[:lower:]')
        case "$tz_type" in
            *battery*|*bms*|*chg*|*charger*|*mtktsap*|*tsbuck*|*skin*|*pcb*|*sub_batt*|*quiet*|*xo_therm*|*pmic*)
                if [ -e "$tz/mode" ]; then
                    chmod 666 "$tz/mode" 2>/dev/null
                    echo disabled > "$tz/mode" 2>/dev/null
                    chmod 444 "$tz/mode" 2>/dev/null
                fi
                for tp in "$tz"/trip_point_*_temp; do
                    if [ -e "$tp" ]; then
                        chmod 666 "$tp" 2>/dev/null
                        echo 95000 > "$tp" 2>/dev/null
                        chmod 444 "$tp" 2>/dev/null
                    fi
                done
                ;;
        esac
    done

    # 11. Universal Thermal Lockout Bypass (Spoof 28°C & Freeze Cooling Devices)
    if [ "$allow_lockout_bypass" = "true" ]; then
        chmod 644 /sys/devices/platform/battery/Battery_Temperature 2>/dev/null
        write_node "28" "/sys/devices/platform/battery/Battery_Temperature"
        chmod 444 /sys/devices/platform/battery/Battery_Temperature 2>/dev/null
        for c in /sys/class/thermal/cooling_device*; do
            type=$(cat "$c/type" 2>/dev/null | tr '[:upper:]' '[:lower:]')
            case "$type" in
                *bcct*|*chg*|*current*|*abcct*|*battery*|*cdev*|*skin*|*thermal*)
                    chmod 666 "$c/cur_state" 2>/dev/null
                    echo 0 > "$c/cur_state" 2>/dev/null
                    chmod 444 "$c/cur_state" 2>/dev/null
                    ;;
            esac
        done
        cmd thermalservice override-status 0 2>/dev/null
    fi

    # Reset hardware bypass switches
    [ -n "$HW_BYPASS_NODE" ] && write_node "0" "$HW_BYPASS_NODE"
}

# -----------------------------------------------------------------------------
# Mode 3: Normal Regulated Charging
# Uses user-configured current limit (limit_current_ma) with JEITA active.
# -----------------------------------------------------------------------------
apply_regulated_charging() {
    local target_ma="$1"
    [ -z "$target_ma" ] && target_ma=1500
    local target_ua=$(( target_ma * 1000 ))

    rm -f /dev/lynx_extreme_charging 2>/dev/null
    unlock_extreme_nodes

    # Release bypass switches
    [ -n "$HW_BYPASS_NODE" ] && write_node "0" "$HW_BYPASS_NODE"
    write_node "0" "$BATT_DIR/device/smart_charging"
    write_node "0" "$BATT_DIR/smart_charging_activation"
    write_node "0" "$QC_DIR/direct_charging"
    write_node "0" "$BATT_DIR/store_mode"
    write_node "0" "$BATT_DIR/batt_slate_mode"
    write_node "0" "$MTK_DIR/enable_sc"

    # Restore multi-OEM screen-on throttling nodes
    chmod 644 "$BATT_DIR/siop_level" 2>/dev/null
    write_node "100" "$BATT_DIR/siop_level"
    chmod 644 "$BATT_DIR/cool_mode" 2>/dev/null
    chmod 644 "$BATT_DIR/call_mode" 2>/dev/null
    killall -CONT com.xiaomi.joyose 2>/dev/null
    write_node "0" "$BATT_DIR/boost_current"

    # Restore JEITA on MediaTek
    write_node "1" "$MTK_DIR/sw_jeita"

    # Restore genuine battery temperature reporting
    write_node "65535" "/sys/devices/platform/battery/Battery_Temperature"

    # Restore thermal cooling devices
    for c in /sys/class/thermal/cooling_device*; do
        type=$(cat "$c/type" 2>/dev/null)
        case "$type" in
            *bcct*|*chg*|*current*|*abcct*|*battery*|*cdev*)
                chmod 666 "$c/cur_state" 2>/dev/null
                ;;
        esac
    done

    # Restore Transsion PCB thermal clamp
    for node in /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug /sys/devices/platform/tran_battery/pcb_thermal_debug; do
        if [ -e "$node" ]; then
            chmod 666 "$node" 2>/dev/null
            echo "[45,3000,50,2000,1000]" > "$node" 2>/dev/null
            chmod 644 "$node" 2>/dev/null
        fi
    done
    if [ -e "/sys/class/thermal/thermal_zone1/mode" ]; then
        chmod 666 "/sys/class/thermal/thermal_zone1/mode" 2>/dev/null
        echo enabled > "/sys/class/thermal/thermal_zone1/mode" 2>/dev/null
        chmod 644 "/sys/class/thermal/thermal_zone1/mode" 2>/dev/null
    fi
    cmd thermalservice reset 2>/dev/null

    # Ensure charging is enabled and input not suspended
    write_node "0" "$BATT_DIR/input_suspend"
    write_node "1" "$BATT_DIR/charging_enabled"
    write_node "0" "$BATT_DIR/charge_control_limit_max"

    # Unlock charger input current so motherboard draws operating power from charger
    write_node "6000" "$MTK_DIR/input_current"
    write_node "4500000" "$USB_DIR/current_max"
    write_node "4500000" "$USB_DIR/hw_current_max"
    write_node "4500000" "$MAIN_DIR/current_max"

    # Dynamic Headroom Guard: Ensure net current is not negative
    local cur_now
    cur_now=$(read_node "$BATT_DIR/current_now")
    if [ -n "$cur_now" ]; then
        if [ "$cur_now" -gt 100000 ] || [ "$cur_now" -lt -100000 ]; then
            cur_now=$(( cur_now / 1000 ))
        fi
        if [ "$cur_now" -lt 0 ]; then
            local deficit=$(( 0 - cur_now ))
            target_ma=$(( target_ma + deficit + 300 ))
            [ "$target_ma" -gt 3500 ] && target_ma=3500
            target_ua=$(( target_ma * 1000 ))
        fi
    fi

    # Apply target currents
    chmod 644 "$BATT_DIR/constant_charge_current_max" 2>/dev/null
    write_node "$target_ua" "$BATT_DIR/constant_charge_current"
    write_node "$target_ua" "$BATT_DIR/constant_charge_current_max"
    write_node "$target_ua" "$MAIN_DIR/constant_charge_current_max"
    write_node "$target_ua" "$MAIN_DIR/current_max"
    write_node "$target_ua" "$USB_DIR/current_max"
    write_node "$target_ua" "$USB_DIR/hw_current_max"
    write_node "$target_ua" "$EXT_DIR/max_charge_current"
    write_node "$target_ua" "$EXT_DIR/chg_pwr_fcc"

    # MediaTek nodes
    write_node "$target_ma" "$MTK_DIR/chg1_current"
    write_node "$target_ma" "$MTK_DIR/chg2_current"
    write_node "$target_ma" "$MTK_DIR/sc_ibat_limit"

    # Qualcomm specific nodes
    write_node "$target_ua" "$QC_DIR/restrict_cur"
    write_node "0" "$QC_DIR/restricted_charging"
}

# -----------------------------------------------------------------------------
# Hardware Telemetry & Fast Charge Protocol Diagnostic (JSON Output)
# -----------------------------------------------------------------------------
dump_telemetry_json() {
    local cap stat hlth temp volt cur adpv chgtyp rfc_status ibus_val real_temp
    cap=$(read_node "$BATT_DIR/capacity")
    stat=$(read_node "$BATT_DIR/status")
    hlth=$(read_node "$BATT_DIR/health")
    temp=$(read_node "$BATT_DIR/temp")
    volt=$(read_node "$BATT_DIR/voltage_now")
    cur=$(read_node "$BATT_DIR/current_now")

    # Adapter Voltage (mV)
    adpv=$(read_node "$MTK_DIR/ADC_Charger_Voltage")
    [ -z "$adpv" ] && adpv=$(read_node "/sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger")
    [ -z "$adpv" ] && adpv=$(read_node "$USB_DIR/voltage_now")
    [ -z "$adpv" ] && adpv=$(read_node "$BATT_DIR/charger_voltage")
    [ -z "$adpv" ] && adpv=$(read_node "$MAIN_DIR/voltage_now")
    [ -z "$adpv" ] && adpv=0
    [ "$adpv" -gt 100000 ] && adpv=$(( adpv / 1000 ))
    [ "$adpv" -lt 1000 ] 2>/dev/null && adpv=0

    # Adapter Current (mA)
    ibus_val=$(read_node "/sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger")
    if [ -n "$ibus_val" ] && [ "$ibus_val" -lt 1000 ] && [ "$ibus_val" -gt 10 ]; then
        ibus_val=$(( ibus_val * 10 ))
    fi
    [ -z "$ibus_val" ] && ibus_val=$(cat /sys/bus/i2c/drivers/rt9759/*/Ibus 2>/dev/null | head -n 1)
    [ -z "$ibus_val" ] && ibus_val=$(read_node "$USB_DIR/current_now")
    [ -z "$ibus_val" ] && ibus_val=$(read_node "$USB_DIR/input_current_now")
    [ -z "$ibus_val" ] && ibus_val=$(read_node "$MAIN_DIR/current_now")
    [ -z "$ibus_val" ] && ibus_val=0
    [ "$ibus_val" -gt 100000 ] && ibus_val=$(( ibus_val / 1000 ))
    [ "$ibus_val" -lt 0 ] 2>/dev/null && ibus_val=0
    [ "$adpv" -eq 0 ] 2>/dev/null && ibus_val=0

    # Charger Type / Protocol
    chgtyp=$(read_node "$MTK_DIR/Charger_Type")
    [ -z "$chgtyp" ] && chgtyp=$(read_node "$USB_DIR/type")

    rfc_status=$(cat /sys/bus/i2c/drivers/rt9759/*/rfc_dcp_ta 2>/dev/null | head -n 1)
    [ -z "$rfc_status" ] && [ "$chgtyp" = "9" ] && rfc_status=1
    [ -z "$rfc_status" ] && rfc_status=0

    # Multi-vendor signals
    local pmic_sig=""
    if [ -d /sys/class/power_supply/smb1390 ] || [ -d /sys/bus/i2c/drivers/smb1390 ]; then
        pmic_sig="smb1390"
    elif [ -d /sys/class/power_supply/smb1355 ] || [ -d /sys/bus/i2c/drivers/smb1355 ]; then
        pmic_sig="smb1355"
    elif [ -d /sys/class/power_supply/bms ] || [ -d /sys/class/qcom-battery ]; then
        pmic_sig="qcom_pmic"
    elif [ -d /sys/bus/i2c/drivers/rt9759 ]; then
        pmic_sig="rt9759"
    elif [ -e /sys/class/power_supply/sec-direct-charger ]; then
        pmic_sig="sec_direct"
    elif [ -d /sys/bus/i2c/drivers/max77705 ] || [ -d /sys/bus/i2c/drivers/max77854 ] || [ -d /sys/class/power_supply/sec-charger ]; then
        pmic_sig="sec_max"
    elif [ -d /sys/bus/i2c/drivers/ln8000 ] || [ -d /sys/bus/i2c/drivers/sc8551 ] || [ -e /sys/class/power_supply/battery/sub_charger_type ]; then
        pmic_sig="mi_pump"
    elif [ -e /sys/class/power_supply/battery/vooc_charging ] || [ -e /sys/class/power_supply/battery/cool_mode ]; then
        pmic_sig="vooc_pump"
    elif [ -d /sys/bus/i2c/drivers/max77759 ] || [ -d /sys/bus/i2c/drivers/da9121 ]; then
        pmic_sig="pixel_pmic"
    fi

    local sec_sig vooc_sig qc_sig mi_sig pd_sig
    sec_sig=$(cat /sys/class/power_supply/battery/afc_result 2>/dev/null || cat /sys/class/power_supply/battery/charge_mode 2>/dev/null || echo "")
    vooc_sig=$(cat /sys/class/power_supply/battery/vooc_charging 2>/dev/null || cat /sys/class/power_supply/battery/fast_charge_user_type 2>/dev/null || echo "")
    qc_sig=$(cat /sys/class/power_supply/usb/quick_charge_type 2>/dev/null || cat /sys/class/power_supply/usb/real_type 2>/dev/null || echo "")
    mi_sig=$(cat /sys/class/power_supply/battery/fastcharge_mode 2>/dev/null || cat /sys/class/power_supply/battery/boost_current 2>/dev/null || echo "")
    pd_sig=$(cat /sys/class/power_supply/usb/pd_active 2>/dev/null || cat /sys/class/power_supply/usb/pd_allowed 2>/dev/null || echo "")

    # Real Physical Battery Thermal Zone Temp
    real_temp=""
    for tz in /sys/class/thermal/thermal_zone*; do
        [ -d "$tz" ] || continue
        tz_type=$(cat "$tz/type" 2>/dev/null | tr '[:upper:]' '[:lower:]')
        case "$tz_type" in
            *battery*|*mtktsbattery*|*bms*)
                raw_tz=$(cat "$tz/temp" 2>/dev/null)
                if [ -n "$raw_tz" ] && [ "$raw_tz" -gt 0 ] 2>/dev/null; then
                    real_temp="$raw_tz"
                    break
                fi
                ;;
        esac
    done
    [ -z "$real_temp" ] && real_temp="$temp"
    [ "$real_temp" -gt 1000 ] && real_temp=$(( real_temp / 100 ))

    local v_clean=0
    local c_clean=0
    if [ -n "$volt" ]; then
        if [ "$volt" -gt 100000 ] || [ "$volt" -lt -100000 ]; then
            v_clean=$(( volt / 1000 ))
        else
            v_clean="$volt"
        fi
    fi
    if [ -n "$cur" ]; then
        if [ "$cur" -gt 100000 ] || [ "$cur" -lt -100000 ]; then
            c_clean=$(( cur / 1000 ))
        else
            c_clean="$cur"
        fi
    fi

    local is_chg="false"
    if [ "$c_clean" -gt 50 ] || [ "$stat" = "Charging" ]; then
        is_chg="true"
    fi

    # Active IC Determination
    local active_ic="Unknown / Standby"
    local protocol="Battery Power"

    if [ "$is_chg" = "true" ]; then
        if [ "$rfc_status" = "1" ] || [ "$chgtyp" = "9" ] || [ "$pmic_sig" = "rt9759" ] || { [ "$adpv" -gt 7000 ] && [ "$c_clean" -ge 1500 ] && [ -z "$pmic_sig" ]; }; then
            active_ic="Direct Charge Pump (RT9759 2:1)"
        elif [ "$pmic_sig" = "smb1390" ] || { echo "$qc_sig" | grep -qi "QC" && [ "$adpv" -gt 7000 ]; }; then
            active_ic="Qualcomm SMB1390 Dual-Pump"
        elif [ "$pmic_sig" = "smb1355" ]; then
            active_ic="Qualcomm SMB1355 Companion PMIC"
        elif [ "$pmic_sig" = "qcom_pmic" ]; then
            active_ic="Qualcomm PMIC (PM8150/PM6150 Buck)"
        elif [ "$pmic_sig" = "sec_direct" ]; then
            active_ic="Samsung Direct Charger (S2MU/Maxim)"
        elif [ "$pmic_sig" = "sec_max" ]; then
            active_ic="Samsung PMIC (Maxim MAX77x Buck)"
        elif [ "$pmic_sig" = "mi_pump" ]; then
            active_ic="Xiaomi HyperCharge Dual-Pump (LN8000/SC8551)"
        elif [ "$pmic_sig" = "vooc_pump" ] || [ "$vooc_sig" = "1" ]; then
            active_ic="SuperVOOC / Warp Charge Pump"
        elif [ "$pmic_sig" = "pixel_pmic" ]; then
            active_ic="Google Tensor PMIC (MAX77759)"
        else
            active_ic="Switching Buck Converter (RT9471/Universal)"
        fi

        if [ "$rfc_status" = "1" ] || [ "$chgtyp" = "9" ]; then
            protocol="Transsion Super Charge (33W/45W/68W RFC)"
        elif [ "$vooc_sig" = "1" ] || [ "$vooc_sig" = "2" ]; then
            protocol="SuperVOOC / Warp Fast Charge"
        elif [ "$mi_sig" = "1" ] && [ "$adpv" -gt 8000 ]; then
            protocol="Xiaomi HyperCharge / Turbo (67W-120W)"
        elif [ "$sec_sig" = "1" ] || echo "$sec_sig" | grep -qi "AFC"; then
            if [ "$adpv" -gt 8000 ]; then
                protocol="Samsung Super Fast Charging (25W/45W)"
            else
                protocol="Samsung Adaptive Fast Charging (AFC)"
            fi
        elif echo "$qc_sig" | grep -qi "QC" || echo "$qc_sig" | grep -qi "Quick"; then
            protocol="Qualcomm Quick Charge (QC3.0/QC4+/QC5)"
        elif [ "$pd_sig" = "1" ] || [ "$chgtyp" = "4" ] || [ "$adpv" -gt 8000 ]; then
            if [ "$adpv" -gt 8500 ]; then
                protocol="USB Power Delivery / PPS"
            else
                protocol="USB-PD / PE2.0 Fast Charge (18W)"
            fi
        elif [ "$adpv" -gt 4500 ] && [ "$c_clean" -ge 2000 ]; then
            protocol="Fast Charge (High Current 5V)"
        elif [ "$adpv" -gt 4000 ]; then
            protocol="Standard USB Fast Charge (5V)"
        else
            protocol="Standard Charging"
        fi
    fi

    local guard_state="false"
    [ -f "/dev/lynx_charging_guard" ] && guard_state="true"

    local chg_state
    chg_state=$(cat /dev/lynx_charging_state 2>/dev/null)
    local is_overnight_latch="false"
    local is_tapering="false"
    case "$chg_state" in
        *bypass_100*) is_overnight_latch="true" ;;
        *tapering*) is_tapering="true" ;;
    esac
    if [ "${cap:-0}" -ge 100 ] || [ "$stat" = "Full" ]; then
        is_overnight_latch="true"
    fi

    cat <<EOF
{
  "capacity": ${cap:-0},
  "status": "${stat:-Unknown}",
  "health": "${hlth:-Good}",
  "temperature_c": $(awk "BEGIN {print ${temp:-280} / 10}"),
  "real_physical_temp_c": $(awk "BEGIN {print ${real_temp:-300} / 10}"),
  "voltage_mv": ${v_clean},
  "current_ma": ${c_clean},
  "adapter_voltage_mv": ${adpv:-0},
  "adapter_current_ma": ${ibus_val:-0},
  "active_ic": "${active_ic}",
  "fast_charge_protocol": "${protocol}",
  "rfc_authenticated": $( [ "$rfc_status" = "1" ] && echo "true" || echo "false" ),
  "emergency_guard_active": ${guard_state},
  "overnight_bypass_latched": ${is_overnight_latch},
  "smart_tapering_active": ${is_tapering}
}
EOF
}

# -----------------------------------------------------------------------------
# CLI Dispatcher
# -----------------------------------------------------------------------------
case "$1" in
    status)
        dump_telemetry_json
        exit 0
        ;;
    bypass)
        apply_bypass_charging
        exit 0
        ;;
    extreme)
        apply_extreme_charging "$2" "$3"
        exit 0
        ;;
    regulated)
        apply_regulated_charging "$2"
        exit 0
        ;;
    apply)
        # One-shot apply from config.json
        c_limit=4500
        c_max=80
        c_target=90
        c_byp="false"
        c_ext="false"
        c_lock="true"
        c_taper="true"
        if [ -f "$CONFIG_FILE" ]; then
            c_limit=$(awk -F': ' '/"limit_current_ma"/ {gsub(/[^0-9]/,"",$2); print $2}' "$CONFIG_FILE" 2>/dev/null)
            c_max=$(awk -F': ' '/"max_battery_percent"/ {gsub(/[^0-9]/,"",$2); print $2}' "$CONFIG_FILE" 2>/dev/null)
            c_target=$(awk -F': ' '/"high_current_target_percent"/ {gsub(/[^0-9]/,"",$2); print $2}' "$CONFIG_FILE" 2>/dev/null)
            c_byp=$(awk -F': ' '/"bypass_enabled"/ {print $2}' "$CONFIG_FILE" 2>/dev/null | grep -q "true" && echo "true" || echo "false")
            c_ext=$(awk -F': ' '/"extreme_charging_enabled"/ {print $2}' "$CONFIG_FILE" 2>/dev/null | grep -q "true" && echo "true" || echo "false")
            c_lock=$(awk -F': ' '/"thermal_lockout_bypass_enabled"/ {print $2}' "$CONFIG_FILE" 2>/dev/null | grep -q "false" && echo "false" || echo "true")
            c_taper=$(awk -F': ' '/"smart_tapering_enabled"/ {print $2}' "$CONFIG_FILE" 2>/dev/null | grep -q "false" && echo "false" || echo "true")
            [ -z "$c_target" ] && c_target=90
        fi

        cur_cap=$(read_node "$BATT_DIR/capacity")
        [ -z "$cur_cap" ] && cur_cap=50
        cur_stat=$(read_node "$BATT_DIR/status")

        if [ "$cur_cap" -ge 100 ] || [ "$cur_stat" = "Full" ]; then
            echo "state=bypass_100 cap=$cur_cap" > /dev/lynx_charging_state
            apply_bypass_charging
        elif [ "$c_byp" = "true" ] && [ "$cur_cap" -ge "$c_max" ]; then
            echo "state=bypass_custom cap=$cur_cap max=$c_max" > /dev/lynx_charging_state
            apply_bypass_charging
        elif [ "$c_taper" = "true" ] && [ "$cur_cap" -ge 90 ]; then
            if [ "$cur_cap" -ge 95 ]; then
                echo "state=tapering_95 cap=$cur_cap" > /dev/lynx_charging_state
                apply_regulated_charging 750
            else
                echo "state=tapering_90 cap=$cur_cap" > /dev/lynx_charging_state
                apply_regulated_charging 1500
            fi
        elif [ "$c_ext" = "true" ]; then
            echo "state=extreme cap=$cur_cap" > /dev/lynx_charging_state
            apply_extreme_charging "$c_target" "$c_lock"
        else
            echo "state=regulated cap=$cur_cap" > /dev/lynx_charging_state
            apply_regulated_charging "$c_limit"
        fi
        exit 0
        ;;
esac

# -----------------------------------------------------------------------------
# Daemon Loop for Safety, AutoCut, Bypass & Extreme Charging
# -----------------------------------------------------------------------------
in_bypass_latch=false
rm -f /dev/lynx_charging_guard

while true; do
    # Check if charger is connected
    usb_online=$(read_node "$USB_DIR/online")
    ac_online=$(read_node "$PS_DIR/ac/online")
    chg_online=$(read_node "$PS_DIR/charger/online")
    batt_status=$(read_node "$BATT_DIR/status")

    is_plugged=false
    if [ "$usb_online" = "1" ] || [ "$ac_online" = "1" ] || [ "$chg_online" = "1" ] || [ "$batt_status" = "Charging" ] || [ "$batt_status" = "Not charging" ]; then
        is_plugged=true
    fi

    if [ "$is_plugged" != "true" ]; then
        # Device is on battery power; sleep and avoid unnecessary sysfs writes
        in_bypass_latch=false
        rm -f /dev/lynx_charging_guard
        sleep 12
        continue
    fi

    # Read Temperature (in decicelsius, e.g. 450 = 45.0C)
    temp=$(read_node "$BATT_DIR/temp")
    capacity=$(read_node "$BATT_DIR/capacity")
    [ -z "$temp" ] && temp=300
    [ -z "$capacity" ] && capacity=50

    # Read real physical battery sensor (never use CPU/SoC thermal_zone0!)
    real_dC=0
    for tz in /sys/class/thermal/thermal_zone*; do
        [ -d "$tz" ] || continue
        tz_type=$(cat "$tz/type" 2>/dev/null | tr '[:upper:]' '[:lower:]')
        case "$tz_type" in
            *battery*|*mtktsbattery*|*bms*)
                raw_tz=$(cat "$tz/temp" 2>/dev/null)
                if [ -n "$raw_tz" ] && [ "$raw_tz" -gt 0 ] 2>/dev/null; then
                    if [ "$raw_tz" -gt 1000 ]; then
                        real_dC=$(( raw_tz / 100 ))
                    else
                        real_dC=$(( raw_tz * 10 ))
                    fi
                    break
                fi
                ;;
        esac
    done

    # Fallback to standard battery thermistor if no thermal zone matched
    if [ "$real_dC" -le 0 ]; then
        if [ "$temp" -gt 1000 ]; then
            real_dC=$(( temp / 100 ))
        elif [ "$temp" -gt 100 ]; then
            real_dC="$temp"
        else
            real_dC=$(( temp * 10 ))
        fi
    fi

    # Active profile check
    cur_prof=$(cat /data/adb/lynx/active_profile 2>/dev/null)
    [ -z "$cur_prof" ] && cur_prof=$(getprop lynx.mode 2>/dev/null)
    [ -z "$cur_prof" ] && cur_prof="balance"

    # Read config.json parameters
    cutoff_c=45
    limit_ma=4500
    max_pct=80
    high_target_pct=90
    bypass_on="false"
    extreme_charging_on="false"
    temp_guard_on="true"
    lockout_byp_on="true"
    smart_taper_on="true"

    if [ -f "$CONFIG_FILE" ]; then
        c_temp=$(awk -F': ' '/"temp_cutoff_c"/ {gsub(/[^0-9]/,"",$2); print $2}' "$CONFIG_FILE" 2>/dev/null)
        c_limit=$(awk -F': ' '/"limit_current_ma"/ {gsub(/[^0-9]/,"",$2); print $2}' "$CONFIG_FILE" 2>/dev/null)
        c_max=$(awk -F': ' '/"max_battery_percent"/ {gsub(/[^0-9]/,"",$2); print $2}' "$CONFIG_FILE" 2>/dev/null)
        c_target=$(awk -F': ' '/"high_current_target_percent"/ {gsub(/[^0-9]/,"",$2); print $2}' "$CONFIG_FILE" 2>/dev/null)
        c_byp=$(awk -F': ' '/"bypass_enabled"/ {print $2}' "$CONFIG_FILE" 2>/dev/null | grep -q "true" && echo "true" || echo "false")
        c_ext=$(awk -F': ' '/"extreme_charging_enabled"/ {print $2}' "$CONFIG_FILE" 2>/dev/null | grep -q "true" && echo "true" || echo "false")
        c_guard=$(awk -F': ' '/"emergency_temp_guard_enabled"/ {print $2}' "$CONFIG_FILE" 2>/dev/null | grep -q "false" && echo "false" || echo "true")
        c_lock=$(awk -F': ' '/"thermal_lockout_bypass_enabled"/ {print $2}' "$CONFIG_FILE" 2>/dev/null | grep -q "false" && echo "false" || echo "true")
        c_taper=$(awk -F': ' '/"smart_tapering_enabled"/ {print $2}' "$CONFIG_FILE" 2>/dev/null | grep -q "false" && echo "false" || echo "true")

        [ -n "$c_temp" ] && cutoff_c="$c_temp"
        [ -n "$c_limit" ] && limit_ma="$c_limit"
        [ -n "$c_max" ] && max_pct="$c_max"
        [ -n "$c_target" ] && high_target_pct="$c_target"
        [ -n "$c_byp" ] && bypass_on="$c_byp"
        [ -n "$c_ext" ] && extreme_charging_on="$c_ext"
        [ -n "$c_guard" ] && temp_guard_on="$c_guard"
        [ -n "$c_lock" ] && lockout_byp_on="$c_lock"
        [ -n "$c_taper" ] && smart_taper_on="$c_taper"
    fi
    [ -f "/dev/lynx_extreme_charging" ] && extreme_charging_on="true"

    # 1. Emergency Thermal Guard: Only trigger on genuine physical battery cell danger (>= 49.0°C)
    if [ "$temp_guard_on" = "true" ] && [ "$real_dC" -ge 490 ]; then
        # Critical Protection: Revoke spoofing, restore OEM thermal control, clamp to safe current
        echo "guard_active=true temp=${real_dC}" > /dev/lynx_charging_guard
        rm -f /dev/lynx_extreme_charging 2>/dev/null
        write_node "65535" "/sys/devices/platform/battery/Battery_Temperature"
        apply_regulated_charging 1500
        sleep 8
        continue
    else
        [ "$real_dC" -le 430 ] && rm -f /dev/lynx_charging_guard
    fi

    # Convert Celsius to decicelsius (e.g. 45 -> 450)
    cutoff_dC=$(( cutoff_c * 10 ))
    emergency_dC=$(( cutoff_dC + 35 )) # ~3.5C buffer before critical protection

    # 2. Hardware Thermal Protection & Bed Insulation Guard
    if [ "$temp_guard_on" = "true" ] && [ "$temp" -ge "$emergency_dC" ]; then
        # Critical Cutoff: suspend charging to prevent hardware damage, keep system running
        write_node "0" "$BATT_DIR/charging_enabled"
        write_node "1000000" "$BATT_DIR/constant_charge_current_max"
        sleep 8
        continue
    elif [ "$extreme_charging_on" != "true" ] && [ "$cur_prof" != "extreme" ] && [ "$limit_ma" -lt 3000 ] && [ "$temp" -ge "$cutoff_dC" ]; then
        # Soft Thermal Throttling / Bed Insulation Guard from user slider:
        # Clamp to 1200mA ONLY if NOT in Extreme Charging mode!
        if [ "$bypass_on" != "true" ] && [ "$capacity" -lt 100 ]; then
            apply_regulated_charging 1200
            sleep 6
            continue
        fi
    fi

    # 3. 100% Full Latch (Overnight Hardware Bypass - Zero Drain & Zero Overcharge)
    # Always engage True Hardware Bypass when 100% or kernel reports "Full",
    # so phone remains cold and stays at 100% without micro-cycling.
    if [ "$capacity" -ge 100 ] || [ "$batt_status" = "Full" ]; then
        echo "state=bypass_100 cap=$capacity" > /dev/lynx_charging_state
        apply_bypass_charging
        sleep 8
        continue
    fi

    # 4. User-Configured Custom Bypass Latch (e.g. Stop-At-% like 80% or 90%)
    if [ "$bypass_on" = "true" ]; then
        if [ "$capacity" -ge "$max_pct" ]; then
            in_bypass_latch=true
        elif [ "$capacity" -le $(( max_pct - 3 )) ]; then
            in_bypass_latch=false
        fi

        if [ "$in_bypass_latch" = "true" ]; then
            echo "state=bypass_custom cap=$capacity max=$max_pct" > /dev/lynx_charging_state
            apply_bypass_charging
            sleep 6
            continue
        fi
    fi

    # 5. Charging In Progress (0% to 99%)
    in_bypass_latch=false

    # Smart Tapering check (90% to 99%)
    if [ "$smart_taper_on" = "true" ] && [ "$capacity" -ge 90 ]; then
        # Smart Tapering Active: Revoke spoofing to restore genuine battery cooling
        write_node "65535" "/sys/devices/platform/battery/Battery_Temperature"

        if [ "$capacity" -ge 95 ]; then
            # Stage 2: Gentle Inflow (750mA)
            echo "state=tapering_95 cap=$capacity" > /dev/lynx_charging_state
            apply_regulated_charging 750
        else
            # Stage 1: Smooth Step-Down (1500mA)
            echo "state=tapering_90 cap=$capacity" > /dev/lynx_charging_state
            apply_regulated_charging 1500
        fi
    elif [ "$extreme_charging_on" = "true" ] || [ "$cur_prof" = "extreme" ] || [ "$limit_ma" -ge 3000 ]; then
        echo "state=extreme cap=$capacity" > /dev/lynx_charging_state
        apply_extreme_charging "$high_target_pct" "$lockout_byp_on"
    else
        echo "state=regulated cap=$capacity" > /dev/lynx_charging_state
        apply_regulated_charging "$limit_ma"
    fi

    sleep 6
done
