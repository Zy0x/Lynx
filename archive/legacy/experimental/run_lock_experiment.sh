#!/system/bin/sh
mkdir -p /storage/emulated/0/Debug 2>/dev/null
exec > /storage/emulated/0/Debug/lock_experiment.txt 2>&1

echo "=== INITIAL STATE ==="
echo "current_now: $(cat /sys/class/power_supply/battery/current_now 2>/dev/null)"
echo "Pump_Express_ICharger: $(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger 2>/dev/null)"

# Pause MediaTek thermal algorithm daemons so they don't override charging current
for tpid in $(pgrep -f "thermalloadalgod" 2>/dev/null); do
    kill -STOP "$tpid" 2>/dev/null
done
for tpid in $(pgrep -f "/vendor/bin/thermal" 2>/dev/null); do
    kill -STOP "$tpid" 2>/dev/null
done

# MediaTek ABCCT override
echo 0 > /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
echo "0 70000 1000 200000 5 6000 0" > /proc/driver/thermal/clabcct 2>/dev/null

# Cooling device 56 & 57 lock
chmod 666 /sys/class/thermal/cooling_device56/cur_state /sys/class/thermal/cooling_device57/cur_state 2>/dev/null
echo 0 > /sys/class/thermal/cooling_device56/cur_state 2>/dev/null
echo 0 > /sys/class/thermal/cooling_device57/cur_state 2>/dev/null
chmod 444 /sys/class/thermal/cooling_device56/cur_state /sys/class/thermal/cooling_device57/cur_state 2>/dev/null

# Transsion PCB Thermal Debug override
chmod 666 /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug /sys/devices/platform/tran_battery/pcb_thermal_debug 2>/dev/null
echo "[90,6000,100,6000,6000]" > /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug 2>/dev/null
echo "[90,6000,100,6000,6000]" > /sys/devices/platform/tran_battery/pcb_thermal_debug 2>/dev/null
chmod 444 /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug /sys/devices/platform/tran_battery/pcb_thermal_debug 2>/dev/null

# Transsion Charger Nodes
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

# Linux Battery Class
BATT="/sys/class/power_supply/battery"
chmod 666 "$BATT/constant_charge_current" "$BATT/current_max" "$BATT/charge_control_limit" 2>/dev/null
echo 6000000 > "$BATT/constant_charge_current" 2>/dev/null
echo 6000000 > "$BATT/current_max" 2>/dev/null
echo 0 > "$BATT/charge_control_limit" 2>/dev/null

echo "=== RECORDING OVER 10 SECONDS ==="
for i in 1 2 3 4 5 6 7 8 9 10; do
    sleep 1
    c=$(cat /sys/class/power_supply/battery/current_now 2>/dev/null)
    pe=$(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger 2>/dev/null)
    v=$(cat /sys/devices/platform/charger/ADC_Charger_Voltage 2>/dev/null)
    t=$(cat /sys/class/power_supply/battery/temp 2>/dev/null)
    echo "Sec $i: current=$c mA, PE_I=$pe, V=$v mV, temp=$t"
done

# Resume thermal daemons at test end
for tpid in $(pgrep -f "thermalloadalgod" 2>/dev/null); do kill -CONT "$tpid" 2>/dev/null; done
for tpid in $(pgrep -f "/vendor/bin/thermal" 2>/dev/null); do kill -CONT "$tpid" 2>/dev/null; done
