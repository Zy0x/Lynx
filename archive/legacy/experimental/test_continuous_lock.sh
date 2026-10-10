#!/system/bin/sh
mkdir -p /storage/emulated/0/Debug 2>/dev/null
exec > /storage/emulated/0/Debug/continuous_lock_30s.txt 2>&1

echo "=== STARTING CONTINUOUS HIGH-POWER CHARGING LOCK (30 SECONDS) ==="

# 1. Pause aggressive userspace thermal derater
for tpid in $(pgrep -f "thermalloadalgod" 2>/dev/null); do kill -STOP "$tpid" 2>/dev/null; done

apply_lock() {
    # MediaTek ABCCT PID Throttling Disarm
    echo 0 > /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
    echo "0 70000 1000 200000 5 6000 0" > /proc/driver/thermal/clabcct 2>/dev/null

    # Cooling Device 56 & 57 Zeroing
    chmod 666 /sys/class/thermal/cooling_device56/cur_state /sys/class/thermal/cooling_device57/cur_state 2>/dev/null
    echo 0 > /sys/class/thermal/cooling_device56/cur_state 2>/dev/null
    echo 0 > /sys/class/thermal/cooling_device57/cur_state 2>/dev/null
    chmod 444 /sys/class/thermal/cooling_device56/cur_state /sys/class/thermal/cooling_device57/cur_state 2>/dev/null

    # Transsion PCB Thermal Limit Debug Override (Max 90C / 100C, 6000mA allowance)
    chmod 666 /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug 2>/dev/null
    echo "[90,6000,100,6000,6000]" > /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug 2>/dev/null
    chmod 444 /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug 2>/dev/null

    # Transsion Charger Platform Nodes
    chmod 666 /sys/devices/platform/charger/BN_TestMode \
              /sys/devices/platform/charger/BatteryNotify \
              /sys/devices/platform/charger/tran_charger_full \
              /sys/devices/platform/charger/sw_jeita \
              /sys/devices/platform/charger/pdc_max_watt \
              /sys/devices/platform/charger/input_current \
              /sys/devices/platform/charger/chg1_current \
              /sys/devices/platform/charger/chg2_current \
              /sys/devices/platform/charger/sc_ibat_limit \
              /sys/devices/platform/charger/pe40 \
              /sys/devices/platform/charger/pe20 \
              /sys/devices/platform/charger/Pump_Express \
              /sys/devices/platform/charger/enable_sc 2>/dev/null

    echo 1 > /sys/devices/platform/charger/BN_TestMode 2>/dev/null
    echo 0 > /sys/devices/platform/charger/BatteryNotify 2>/dev/null
    chmod 444 /sys/devices/platform/charger/BatteryNotify 2>/dev/null
    echo 0 > /sys/devices/platform/charger/tran_charger_full 2>/dev/null
    echo 0 > /sys/devices/platform/charger/sw_jeita 2>/dev/null
    echo 120 > /sys/devices/platform/charger/pdc_max_watt 2>/dev/null
    echo 24576 > /sys/devices/platform/charger/input_current 2>/dev/null
    echo 24576 > /sys/devices/platform/charger/chg1_current 2>/dev/null
    echo 24576 > /sys/devices/platform/charger/chg2_current 2>/dev/null
    echo 8000 > /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
    echo 1 > /sys/devices/platform/charger/pe40 2>/dev/null
    echo 1 > /sys/devices/platform/charger/pe20 2>/dev/null
    echo 2 > /sys/devices/platform/charger/Pump_Express 2>/dev/null
    echo 1 > /sys/devices/platform/charger/enable_sc 2>/dev/null

    # Linux Power Supply Battery Class
    BATT="/sys/class/power_supply/battery"
    chmod 666 "$BATT/constant_charge_current" "$BATT/current_max" "$BATT/charge_control_limit" 2>/dev/null
    echo 6000000 > "$BATT/constant_charge_current" 2>/dev/null
    echo 6000000 > "$BATT/current_max" 2>/dev/null
    echo 0 > "$BATT/charge_control_limit" 2>/dev/null
    chmod 444 "$BATT/constant_charge_current" "$BATT/current_max" "$BATT/charge_control_limit" 2>/dev/null
}

apply_lock

echo "=== SAMPLING TELEMETRY CONTINUOUSLY FOR 30 SECONDS ==="
for sec in $(seq 1 15); do
    sleep 2
    # Re-apply lock every 2 seconds to guarantee hardware never backs down
    apply_lock
    cur_mA=$(cat /sys/class/power_supply/battery/current_now 2>/dev/null)
    pe_I=$(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger 2>/dev/null)
    v_mV=$(cat /sys/devices/platform/charger/ADC_Charger_Voltage 2>/dev/null)
    t_raw=$(cat /sys/class/power_supply/battery/temp 2>/dev/null)
    echo "Tick $sec (Sec $((sec * 2))): I_bat=${cur_mA} uA, PE_I=${pe_I}, V_chg=${v_mV} mV, Temp=${t_raw}"
done

echo "=== 30S LOCK TEST COMPLETE ==="
