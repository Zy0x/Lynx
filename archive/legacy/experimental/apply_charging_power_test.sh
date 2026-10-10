#!/system/bin/sh
mkdir -p /storage/emulated/0/Debug 2>/dev/null
exec > /storage/emulated/0/Debug/charging_test_output.txt 2>&1

echo "=== BEFORE APPLY ==="
echo "current_now: $(cat /sys/class/power_supply/battery/current_now 2>/dev/null)"
echo "Pump_Express_ICharger: $(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger 2>/dev/null)"

# 1. Disable MTK Adaptive Battery Charging Current Throttling (ABCCT)
echo 0 > /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
echo "0 70000 1000 200000 5 6000 0" > /proc/driver/thermal/clabcct 2>/dev/null

# 2. Reset Cooling Device 56 (abcct) to 0 and lock
if [ -e "/sys/class/thermal/cooling_device56/cur_state" ]; then
    chmod 666 /sys/class/thermal/cooling_device56/cur_state 2>/dev/null
    echo 0 > /sys/class/thermal/cooling_device56/cur_state 2>/dev/null
fi

# 3. Transsion Screen-On & Derating Bypass
echo 1 > /sys/devices/platform/charger/BN_TestMode 2>/dev/null
echo 0 > /sys/devices/platform/charger/BatteryNotify 2>/dev/null
chmod 444 /sys/devices/platform/charger/BatteryNotify 2>/dev/null
echo 0 > /sys/devices/platform/charger/tran_charger_full 2>/dev/null
echo 0 > /sys/devices/platform/charger/sw_jeita 2>/dev/null

# 4. Transsion PCB Thermal Limit Debug Override (Set steps to 90C and 100C, 6000mA allowance)
echo "[90,6000,100,6000,6000]" > /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug 2>/dev/null
echo "[90,6000,100,6000,6000]" > /sys/devices/platform/tran_battery/pcb_thermal_debug 2>/dev/null

# 5. MediaTek Pump Express & Fast Charge Current Rails
echo 120 > /sys/devices/platform/charger/pdc_max_watt 2>/dev/null
echo 24576 > /sys/devices/platform/charger/input_current 2>/dev/null
echo 24576 > /sys/devices/platform/charger/chg1_current 2>/dev/null
echo 24576 > /sys/devices/platform/charger/chg2_current 2>/dev/null
echo 8000 > /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
echo 1 > /sys/devices/platform/charger/pe40 2>/dev/null
echo 1 > /sys/devices/platform/charger/pe20 2>/dev/null
echo 2 > /sys/devices/platform/charger/Pump_Express 2>/dev/null
echo 1 > /sys/devices/platform/charger/enable_sc 2>/dev/null

# 6. Override Android Thermal Framework Status to NONE (0)
cmd thermalservice override-status 0 2>/dev/null

# 7. Linux Power Supply Class
BATT="/sys/class/power_supply/battery"
if [ -d "$BATT" ]; then
    chmod 666 "$BATT/constant_charge_current" "$BATT/current_max" 2>/dev/null
    echo 6000000 > "$BATT/constant_charge_current" 2>/dev/null
    echo 6000000 > "$BATT/current_max" 2>/dev/null
    echo 0 > "$BATT/charge_control_limit" 2>/dev/null
fi

sleep 2

echo "=== AFTER APPLY ==="
echo "clabcct: $(head -n 2 /proc/driver/thermal/clabcct 2>/dev/null)"
echo "clabcct_lcmoff: $(head -n 2 /proc/driver/thermal/clabcct_lcmoff 2>/dev/null)"
echo "cooling_device56: $(cat /sys/class/thermal/cooling_device56/cur_state 2>/dev/null)"
echo "current_now: $(cat /sys/class/power_supply/battery/current_now 2>/dev/null)"
echo "Pump_Express_ICharger: $(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger 2>/dev/null)"
echo "ADC_Charger_Voltage: $(cat /sys/devices/platform/charger/ADC_Charger_Voltage 2>/dev/null)"

echo "=== RECENT KERNEL DV2 LOGS ==="
dmesg | tail -n 25
