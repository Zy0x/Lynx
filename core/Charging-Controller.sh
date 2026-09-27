#!/system/bin/sh
# Lynx Universal - Power Supply & Charging Regulator
# Hardware Bypass, Thermal AutoCut, and Software Current Throttling
# Fully POSIX compliant for /system/bin/sh

MODPATH="/data/adb/modules/Lynx"
[ -d "$MODPATH" ] || MODPATH="${0%/*/*}"
CONFIG_FILE="$MODPATH/config.json"

PS_DIR="/sys/class/power_supply"
BATT_DIR="$PS_DIR/battery"
USB_DIR="$PS_DIR/usb"
MAIN_DIR="$PS_DIR/main"
EXT_DIR="$PS_DIR/battery_ext"
QC_DIR="/sys/class/qcom-battery"

read_node() {
    [ -r "$1" ] && cat "$1" 2>/dev/null
}

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

set_charging_current() {
    local current_ua="$1" # In microamps (e.g. 1500000 = 1500mA)

    # Universal Power Supply nodes
    write_node "$current_ua" "$BATT_DIR/constant_charge_current"
    write_node "$current_ua" "$BATT_DIR/constant_charge_current_max"
    write_node "$current_ua" "$MAIN_DIR/constant_charge_current_max"
    write_node "$current_ua" "$MAIN_DIR/current_max"
    write_node "$current_ua" "$USB_DIR/current_max"
    write_node "$current_ua" "$USB_DIR/hw_current_max"
    write_node "$current_ua" "$EXT_DIR/max_charge_current"
    write_node "$current_ua" "$EXT_DIR/chg_pwr_fcc"

    # Qualcomm specific nodes
    write_node "$current_ua" "$QC_DIR/restrict_cur"
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
    else
        echo ""
    fi
}
HW_BYPASS_NODE=$(detect_hw_bypass)

# Daemon Loop for Safety & AutoCut
while true; do
    # Check if charger is connected
    usb_online=$(read_node "$USB_DIR/online")
    [ -z "$usb_online" ] && usb_online=$(read_node "$PS_DIR/ac/online")
    batt_status=$(read_node "$BATT_DIR/status")
    
    if [ "$usb_online" != "1" ] && [ "$batt_status" != "Charging" ]; then
        # Device is on battery power; sleep and avoid unnecessary sysfs writes
        sleep 15
        continue
    fi

    # Read Temperature (in decicelsius, e.g. 450 = 45.0C)
    temp=$(read_node "$BATT_DIR/temp")
    capacity=$(read_node "$BATT_DIR/capacity")
    [ -z "$temp" ] && temp=300
    [ -z "$capacity" ] && capacity=50

    # Read config.json parameters
    cutoff_c=45
    limit_ma=1500
    max_pct=80
    bypass_on="false"

    if [ -f "$CONFIG_FILE" ]; then
        c_temp=$(awk -F': ' '/"temp_cutoff_c"/ {gsub(/[^0-9]/,"",$2); print $2}' "$CONFIG_FILE" 2>/dev/null)
        c_limit=$(awk -F': ' '/"limit_current_ma"/ {gsub(/[^0-9]/,"",$2); print $2}' "$CONFIG_FILE" 2>/dev/null)
        c_max=$(awk -F': ' '/"max_battery_percent"/ {gsub(/[^0-9]/,"",$2); print $2}' "$CONFIG_FILE" 2>/dev/null)
        c_byp=$(awk -F': ' '/"bypass_enabled"/ {print $2}' "$CONFIG_FILE" 2>/dev/null | grep -q "true" && echo "true" || echo "false")
        
        [ -n "$c_temp" ] && cutoff_c="$c_temp"
        [ -n "$c_limit" ] && limit_ma="$c_limit"
        [ -n "$c_max" ] && max_pct="$c_max"
        [ -n "$c_byp" ] && bypass_on="$c_byp"
    fi

    # Convert Celsius to decicelsius (e.g. 45 -> 450)
    cutoff_dC=$(( cutoff_c * 10 ))
    emergency_dC=$(( cutoff_dC + 30 )) # 3C buffer before critical cut

    # 1. Hardware Emergency Thermal Protection
    if [ "$temp" -ge "$emergency_dC" ]; then
        # Critical Cutoff (Hard stop)
        write_node "1" "$BATT_DIR/input_suspend"
        write_node "0" "$BATT_DIR/charging_enabled"
        [ -n "$HW_BYPASS_NODE" ] && write_node "1" "$HW_BYPASS_NODE"
        sleep 10
        continue
    elif [ "$temp" -ge "$cutoff_dC" ]; then
        # Software Current Throttling (Emergency Clamp to 1000mA)
        write_node "0" "$BATT_DIR/input_suspend"
        write_node "1" "$BATT_DIR/charging_enabled"
        set_charging_current 1000000
        sleep 5
        continue
    fi

    # 2. Bypass & Max Limit Logic (with 3% Hysteresis Buffer)
    if [ "$bypass_on" = "true" ]; then
        if [ "$capacity" -ge "$max_pct" ]; then
            in_bypass_latch=true
        elif [ "$capacity" -le $(( max_pct - 3 )) ]; then
            in_bypass_latch=false
        fi
    else
        in_bypass_latch=false
    fi

    if [ "$in_bypass_latch" = "true" ]; then
        # Reached target threshold: Engage OEM Bypass or Safe Software Suspend
        if [ -n "$HW_BYPASS_NODE" ]; then
            write_node "1" "$HW_BYPASS_NODE"
            write_node "0" "$BATT_DIR/charging_enabled"
        elif [ -e "$BATT_DIR/charging_enabled" ]; then
            write_node "0" "$BATT_DIR/charging_enabled"
            write_node "0" "$BATT_DIR/input_suspend"
        else
            write_node "1" "$BATT_DIR/input_suspend"
        fi
    else
        # Normal Regulated Charging
        [ -n "$HW_BYPASS_NODE" ] && write_node "0" "$HW_BYPASS_NODE"
        write_node "0" "$BATT_DIR/input_suspend"
        write_node "1" "$BATT_DIR/charging_enabled"
        
        # Apply target limit current
        target_ua=$(( limit_ma * 1000 ))
        set_charging_current "$target_ua"
    fi

    sleep 5
done
