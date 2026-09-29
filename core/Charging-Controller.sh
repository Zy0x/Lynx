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

# Detect OEM Hardware Bypass nodes
detect_hw_bypass() {
    # Infinix / Transsion
    if [ -e "/sys/devices/platform/charger/bypass_charger" ]; then
        echo "/sys/devices/platform/charger/bypass_charger"
    # ASUS ROG
    elif [ -e "/sys/class/power_supply/battery/device/smart_charging" ]; then
        echo "/sys/class/power_supply/battery/device/smart_charging"
    # Sony Xperia
    elif [ -e "/sys/class/power_supply/battery/smart_charging_activation" ]; then
        echo "/sys/class/power_supply/battery/smart_charging_activation"
    # Xiaomi / Qualcomm
    elif [ -e "/sys/class/qcom-battery/direct_charging" ]; then
        echo "/sys/class/qcom-battery/direct_charging"
    # Samsung
    elif [ -e "/sys/class/power_supply/battery/store_mode" ]; then
        echo "/sys/class/power_supply/battery/store_mode"
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
    # CRITICAL: DO NOT SUSPEND INPUT! System must draw 100% operating power from charger!
    write_node "0" "$BATT_DIR/input_suspend"

    # Unlock charger input current so motherboard never draws from battery
    write_node "4294967295" "$MTK_DIR/input_current"
    write_node "4500000" "$USB_DIR/current_max"
    write_node "4500000" "$USB_DIR/hw_current_max"
    write_node "4500000" "$MAIN_DIR/current_max"

    # 1. Hardware OEM bypass switch if available
    [ -n "$HW_BYPASS_NODE" ] && write_node "1" "$HW_BYPASS_NODE"

    # 2. Universal Linux Kernel charge control limit (Stop charging at current level)
    write_node "1" "$BATT_DIR/charge_control_limit_max"
    write_node "0" "$BATT_DIR/charge_control_limit"

    # 3. MediaTek Smart Charging lock to current capacity (zero battery current)
    local cur_cap
    cur_cap=$(read_node "$BATT_DIR/capacity")
    [ -z "$cur_cap" ] && cur_cap=80
    write_node "1" "$MTK_DIR/enable_sc"
    write_node "$cur_cap" "$MTK_DIR/sc_tuisoc"
    write_node "0" "$MTK_DIR/sc_ibat_limit"

    # 4. Zero battery charge current while keeping Vsys alive
    write_node "0" "$MTK_DIR/chg1_current"
    write_node "0" "$MTK_DIR/chg2_current"
    write_node "0" "$BATT_DIR/constant_charge_current"
    write_node "0" "$BATT_DIR/constant_charge_current_max"
    write_node "0" "$QC_DIR/restrict_cur"

    # 5. OEM specific bypass / slate modes
    write_node "1" "$BATT_DIR/device/smart_charging"
    write_node "1" "$BATT_DIR/smart_charging_activation"
    write_node "1" "$QC_DIR/direct_charging"
    write_node "1" "$BATT_DIR/store_mode"
    write_node "1" "$BATT_DIR/batt_slate_mode"

    # Soft charging disable only if input_suspend is safe
    write_node "0" "$BATT_DIR/charging_enabled"
}

# -----------------------------------------------------------------------------
# Mode 2: Extreme Fast Charging (Ultra-High Current & Speed)
# Unlocks maximum hardware charging current, Pump Express / Fast Charge,
# and disables JEITA thermal charging current clamp so battery never drops!
# -----------------------------------------------------------------------------
# Mode 2: Extreme Fast Charging (Ultra-High Current & Speed)
# Unlocks maximum hardware charging current, Pump Express / Fast Charge,
# and disables JEITA thermal charging current clamp so battery never drops!
# -----------------------------------------------------------------------------
apply_extreme_charging() {
    local target_soc="$1"
    local allow_lockout_bypass="$2"
    [ -z "$target_soc" ] && target_soc=90
    [ -z "$allow_lockout_bypass" ] && allow_lockout_bypass="true"

    # 1. Disable JEITA thermal current clamp on MediaTek
    write_node "0" "$MTK_DIR/sw_jeita"

    # 2. Unlock MediaTek fast charging protocols (Pump Express 2.0 / 4.0 & max watt)
    write_node "2" "$MTK_DIR/Pump_Express"
    write_node "1" "$MTK_DIR/pe20"
    write_node "1" "$MTK_DIR/pe40"
    write_node "68" "$MTK_DIR/pdc_max_watt"

    # 3. Unlock maximum input and battery current on MediaTek
    write_node "4294967295" "$MTK_DIR/input_current"
    write_node "4294967295" "$MTK_DIR/chg1_current"
    write_node "4294967295" "$MTK_DIR/chg2_current"
    write_node "7000" "$MTK_DIR/sc_ibat_limit"
    write_node "$target_soc" "$MTK_DIR/sc_tuisoc"
    write_node "1" "$MTK_DIR/enable_sc"

    # 4. Bypass MTK Super Charge DV2_TBAT thermal lockout
    if [ "$allow_lockout_bypass" = "true" ]; then
        write_node "28" "/sys/devices/platform/battery/Battery_Temperature"
        write_node "0" "/sys/class/thermal/cooling_device56/cur_state"
    fi

    # 5. Universal & Qualcomm Maximum Current (6000mA = 6A max headroom)
    write_node "6000000" "$BATT_DIR/constant_charge_current_max"
    write_node "6000000" "$BATT_DIR/constant_charge_current"
    write_node "6000000" "$BATT_DIR/current_max"
    write_node "6000000" "$MAIN_DIR/constant_charge_current_max"
    write_node "6000000" "$MAIN_DIR/current_max"
    write_node "6000000" "$USB_DIR/current_max"
    write_node "6000000" "$USB_DIR/hw_current_max"

    # Qualcomm Fastcharge mode & unrestricted
    write_node "1" "$BATT_DIR/fastcharge_mode"
    write_node "1" "$BATT_DIR/fast_charge"
    write_node "0" "$QC_DIR/restricted_charging"
    write_node "6000000" "$QC_DIR/restrict_cur"

    # Reset OEM bypass locks
    [ -n "$HW_BYPASS_NODE" ] && write_node "0" "$HW_BYPASS_NODE"
    write_node "0" "$BATT_DIR/device/smart_charging"
    write_node "0" "$BATT_DIR/smart_charging_activation"
    write_node "0" "$QC_DIR/direct_charging"
    write_node "0" "$BATT_DIR/store_mode"
    write_node "0" "$BATT_DIR/batt_slate_mode"

    # Ensure charging is fully enabled and input is NOT suspended
    write_node "0" "$BATT_DIR/input_suspend"
    write_node "1" "$BATT_DIR/charging_enabled"
    write_node "0" "$BATT_DIR/charge_control_limit_max"
}

# -----------------------------------------------------------------------------
# Mode 3: Normal Regulated Charging
# Uses user-configured current limit (limit_current_ma) with JEITA active.
# -----------------------------------------------------------------------------
apply_regulated_charging() {
    local target_ma="$1"
    [ -z "$target_ma" ] && target_ma=1500
    local target_ua=$(( target_ma * 1000 ))

    # Release bypass switches
    [ -n "$HW_BYPASS_NODE" ] && write_node "0" "$HW_BYPASS_NODE"
    write_node "0" "$BATT_DIR/device/smart_charging"
    write_node "0" "$BATT_DIR/smart_charging_activation"
    write_node "0" "$QC_DIR/direct_charging"
    write_node "0" "$BATT_DIR/store_mode"
    write_node "0" "$BATT_DIR/batt_slate_mode"
    write_node "0" "$MTK_DIR/enable_sc"

    # Restore JEITA on MediaTek
    write_node "1" "$MTK_DIR/sw_jeita"

    # Restore genuine battery temperature reporting
    write_node "65535" "/sys/devices/platform/battery/Battery_Temperature"

    # Ensure charging is enabled and input not suspended
    write_node "0" "$BATT_DIR/input_suspend"
    write_node "1" "$BATT_DIR/charging_enabled"
    write_node "0" "$BATT_DIR/charge_control_limit_max"

    # Apply target currents
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
    write_node "4294967295" "$MTK_DIR/input_current"

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
    [ -z "$adpv" ] && adpv=0
    [ "$adpv" -gt 100000 ] && adpv=$(( adpv / 1000 ))

    # Adapter Current (mA)
    ibus_val=$(cat /sys/bus/i2c/drivers/rt9759/*/Ibus 2>/dev/null | head -n 1)
    [ -z "$ibus_val" ] && ibus_val=$(read_node "$USB_DIR/current_now")
    [ -z "$ibus_val" ] && ibus_val=0
    [ "$ibus_val" -gt 100000 ] && ibus_val=$(( ibus_val / 1000 ))

    # Charger Type / Protocol
    chgtyp=$(read_node "$MTK_DIR/Charger_Type")
    [ -z "$chgtyp" ] && chgtyp=$(read_node "$USB_DIR/type")

    rfc_status=$(cat /sys/bus/i2c/drivers/rt9759/*/rfc_dcp_ta 2>/dev/null | head -n 1)
    [ -z "$rfc_status" ] && rfc_status=0

    # Real Physical Thermal Zone Temp
    real_temp=$(read_node "/sys/class/thermal/thermal_zone1/temp")
    [ -z "$real_temp" ] && real_temp=$(read_node "/sys/class/thermal/thermal_zone0/temp")
    [ -z "$real_temp" ] && real_temp="$temp"
    [ "$real_temp" -gt 1000 ] && real_temp=$(( real_temp / 100 ))

    # Active IC Determination
    local active_ic="Unknown / Standby"
    local protocol="Battery Power"

    if [ "$rfc_status" = "1" ] || [ "$chgtyp" = "9" ] || { [ "$adpv" -gt 7000 ] && [ "$ibus_val" -gt 500 ]; }; then
        active_ic="Direct Charge Pump (RT9759 2:1)"
        protocol="Transsion Super Charge (RFC 33W)"
    elif [ "$adpv" -gt 8000 ] || [ "$chgtyp" = "4" ]; then
        active_ic="Switching Buck Converter (RT9471)"
        protocol="USB-PD / PE2.0 Fast Charge (18W)"
    elif [ "$adpv" -gt 4200 ]; then
        active_ic="Switching Buck Converter (RT9471)"
        protocol="Standard USB Fast Charge (5V)"
    fi

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

    local guard_state="false"
    [ -f "/dev/lynx_charging_guard" ] && guard_state="true"

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
  "emergency_guard_active": ${guard_state}
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
        if [ -f "$CONFIG_FILE" ]; then
            c_limit=$(awk -F': ' '/"limit_current_ma"/ {gsub(/[^0-9]/,"",$2); print $2}' "$CONFIG_FILE" 2>/dev/null)
            c_max=$(awk -F': ' '/"max_battery_percent"/ {gsub(/[^0-9]/,"",$2); print $2}' "$CONFIG_FILE" 2>/dev/null)
            c_target=$(awk -F': ' '/"high_current_target_percent"/ {gsub(/[^0-9]/,"",$2); print $2}' "$CONFIG_FILE" 2>/dev/null)
            c_byp=$(awk -F': ' '/"bypass_enabled"/ {print $2}' "$CONFIG_FILE" 2>/dev/null | grep -q "true" && echo "true" || echo "false")
            c_ext=$(awk -F': ' '/"extreme_charging_enabled"/ {print $2}' "$CONFIG_FILE" 2>/dev/null | grep -q "true" && echo "true" || echo "false")
            c_lock=$(awk -F': ' '/"thermal_lockout_bypass_enabled"/ {print $2}' "$CONFIG_FILE" 2>/dev/null | grep -q "false" && echo "false" || echo "true")
            [ -z "$c_target" ] && c_target=90
        fi
        if [ "$c_byp" = "true" ]; then
            apply_bypass_charging
        elif [ "$c_ext" = "true" ]; then
            apply_extreme_charging "$c_target" "$c_lock"
        else
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

    # Read real physical thermal zone temp
    real_temp_raw=$(read_node "/sys/class/thermal/thermal_zone1/temp")
    [ -z "$real_temp_raw" ] && real_temp_raw=$(read_node "/sys/class/thermal/thermal_zone0/temp")
    [ -z "$real_temp_raw" ] && real_temp_raw="$temp"
    if [ "$real_temp_raw" -gt 1000 ]; then
        real_dC=$(( real_temp_raw / 100 ))
    else
        real_dC=$(( real_temp_raw * 10 ))
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

    if [ -f "$CONFIG_FILE" ]; then
        c_temp=$(awk -F': ' '/"temp_cutoff_c"/ {gsub(/[^0-9]/,"",$2); print $2}' "$CONFIG_FILE" 2>/dev/null)
        c_limit=$(awk -F': ' '/"limit_current_ma"/ {gsub(/[^0-9]/,"",$2); print $2}' "$CONFIG_FILE" 2>/dev/null)
        c_max=$(awk -F': ' '/"max_battery_percent"/ {gsub(/[^0-9]/,"",$2); print $2}' "$CONFIG_FILE" 2>/dev/null)
        c_target=$(awk -F': ' '/"high_current_target_percent"/ {gsub(/[^0-9]/,"",$2); print $2}' "$CONFIG_FILE" 2>/dev/null)
        c_byp=$(awk -F': ' '/"bypass_enabled"/ {print $2}' "$CONFIG_FILE" 2>/dev/null | grep -q "true" && echo "true" || echo "false")
        c_ext=$(awk -F': ' '/"extreme_charging_enabled"/ {print $2}' "$CONFIG_FILE" 2>/dev/null | grep -q "true" && echo "true" || echo "false")
        c_guard=$(awk -F': ' '/"emergency_temp_guard_enabled"/ {print $2}' "$CONFIG_FILE" 2>/dev/null | grep -q "false" && echo "false" || echo "true")
        c_lock=$(awk -F': ' '/"thermal_lockout_bypass_enabled"/ {print $2}' "$CONFIG_FILE" 2>/dev/null | grep -q "false" && echo "false" || echo "true")

        [ -n "$c_temp" ] && cutoff_c="$c_temp"
        [ -n "$c_limit" ] && limit_ma="$c_limit"
        [ -n "$c_max" ] && max_pct="$c_max"
        [ -n "$c_target" ] && high_target_pct="$c_target"
        [ -n "$c_byp" ] && bypass_on="$c_byp"
        [ -n "$c_ext" ] && extreme_charging_on="$c_ext"
        [ -n "$c_guard" ] && temp_guard_on="$c_guard"
        [ -n "$c_lock" ] && lockout_byp_on="$c_lock"
    fi

    # 1. Emergency Thermal Guard (>46.0°C real physical temperature)
    if [ "$temp_guard_on" = "true" ] && [ "$real_dC" -ge 460 ]; then
        # Critical Protection: Revoke spoofing, restore OEM thermal control, clamp to safe current
        echo "guard_active=true temp=${real_dC}" > /dev/lynx_charging_guard
        write_node "65535" "/sys/devices/platform/battery/Battery_Temperature"
        apply_regulated_charging 1500
        sleep 8
        continue
    else
        [ "$real_dC" -le 410 ] && rm -f /dev/lynx_charging_guard
    fi

    # Convert Celsius to decicelsius (e.g. 45 -> 450)
    cutoff_dC=$(( cutoff_c * 10 ))
    emergency_dC=$(( cutoff_dC + 35 )) # ~3.5C buffer before critical protection

    # 2. Hardware Thermal Protection from battery temp node
    if [ "$temp" -ge "$emergency_dC" ]; then
        # Critical Cutoff: suspend charging to prevent hardware damage, keep system running
        write_node "0" "$BATT_DIR/charging_enabled"
        write_node "1000000" "$BATT_DIR/constant_charge_current_max"
        sleep 8
        continue
    elif [ "$temp" -ge "$cutoff_dC" ]; then
        # Soft Thermal Throttling: clamp to 1000mA if not in bypass mode
        if [ "$bypass_on" != "true" ]; then
            apply_regulated_charging 1000
            sleep 6
            continue
        fi
    fi

    # 3. Bypass Charging vs Extreme Charging vs Normal Charging
    if [ "$bypass_on" = "true" ]; then
        # Check capacity vs max_pct with 3% hysteresis latch
        if [ "$capacity" -ge "$max_pct" ]; then
            in_bypass_latch=true
        elif [ "$capacity" -le $(( max_pct - 3 )) ]; then
            in_bypass_latch=false
        fi

        if [ "$in_bypass_latch" = "true" ]; then
            # Target battery percentage reached: Engage true bypass
            apply_bypass_charging
        else
            # Battery below target threshold: charge up to max_pct
            if [ "$extreme_charging_on" = "true" ] || [ "$cur_prof" = "extreme" ] || [ "$limit_ma" -ge 3000 ]; then
                apply_extreme_charging "$high_target_pct" "$lockout_byp_on"
            else
                apply_regulated_charging "$limit_ma"
            fi
        fi

    elif [ "$extreme_charging_on" = "true" ] || [ "$cur_prof" = "extreme" ] || [ "$limit_ma" -ge 3000 ]; then
        in_bypass_latch=false

        # High-Current Target Check: If target is reached and not 100%, transition to trickle charge
        if [ "$capacity" -ge "$high_target_pct" ] && [ "$high_target_pct" -lt 100 ]; then
            # Target capacity reached: transition to safe trickle charge
            apply_regulated_charging 1500
        else
            # Sustain full high-current Super Charge
            apply_extreme_charging "$high_target_pct" "$lockout_byp_on"
        fi

    else
        # Standard Regulated Charging
        in_bypass_latch=false
        apply_regulated_charging "$limit_ma"
    fi

    sleep 6
done
