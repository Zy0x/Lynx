import subprocess, time

target = "192.168.1.13:5555"
script = r'''
# 1. Suspend /vendor/bin/thermal & thermalloadalgod
kill -STOP $(pidof thermal 2>/dev/null) 2>/dev/null
kill -STOP $(pidof thermalloadalgod 2>/dev/null) 2>/dev/null

# 2. Raise trip points on mtktsAP (thermal_zone1)
for i in 0 1 2 3 4; do
    echo 99000 > /sys/class/thermal/thermal_zone1/trip_point_${i}_temp 2>/dev/null
done
echo disabled > /sys/class/thermal/thermal_zone1/mode 2>/dev/null

# 3. Disable and lock ABCCT & BCCT cooling devices
for c in /sys/class/thermal/cooling_device*; do
    type=$(cat "$c/type" 2>/dev/null)
    case "$type" in
        *bcct*|*abcct*)
            chmod 666 "$c/cur_state" 2>/dev/null
            echo 0 > "$c/cur_state" 2>/dev/null
            chmod 444 "$c/cur_state" 2>/dev/null
            ;;
    esac
done

# 4. Disable ABCCT PID completely
chmod 666 /proc/driver/thermal/clabcct /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
echo 0 > /proc/driver/thermal/clabcct 2>/dev/null
echo 0 > /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
chmod 444 /proc/driver/thermal/clabcct /proc/driver/thermal/clabcct_lcmoff 2>/dev/null

# 5. Lock Transsion CallState to 0 (Screen-On Throttle Shield)
chmod 666 /sys/devices/platform/odm/odm:tran_battery/Charging_CallState /sys/devices/platform/tran_battery/Charging_CallState 2>/dev/null
echo 0 > /sys/devices/platform/odm/odm:tran_battery/Charging_CallState 2>/dev/null
echo 0 > /sys/devices/platform/tran_battery/Charging_CallState 2>/dev/null
chmod 444 /sys/devices/platform/odm/odm:tran_battery/Charging_CallState /sys/devices/platform/tran_battery/Charging_CallState 2>/dev/null

# 6. Override Transsion PCB thermal derating
chmod 666 /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug /sys/devices/platform/tran_battery/pcb_thermal_debug 2>/dev/null
echo "[95,6000,100,6000,6000]" > /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug 2>/dev/null
echo "[95,6000,100,6000,6000]" > /sys/devices/platform/tran_battery/pcb_thermal_debug 2>/dev/null
chmod 444 /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug /sys/devices/platform/tran_battery/pcb_thermal_debug 2>/dev/null

# 7. Override Transsion DV2 charge pump thermal tables
chmod 666 /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null
echo "[101,85,88,90,6000,6000,6000]" > /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null
echo "[102,85,88,90,6000,6000,6000]" > /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null
chmod 444 /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null

# 8. Lock Battery_Temperature to 25C mock
chmod 666 /sys/devices/platform/battery/Battery_Temperature 2>/dev/null
echo 25 > /sys/devices/platform/battery/Battery_Temperature 2>/dev/null
chmod 444 /sys/devices/platform/battery/Battery_Temperature 2>/dev/null

# 9. Hardware registers max uncap
for n in enable_sc pe20 pe40 BN_TestMode; do
    chmod 666 "/sys/devices/platform/charger/$n" 2>/dev/null
    echo 1 > "/sys/devices/platform/charger/$n" 2>/dev/null
    chmod 444 "/sys/devices/platform/charger/$n" 2>/dev/null
done
for n in BatteryNotify sw_jeita tran_charger_full tran_game_mode bypass_charger; do
    chmod 666 "/sys/devices/platform/charger/$n" 2>/dev/null
    echo 0 > "/sys/devices/platform/charger/$n" 2>/dev/null
    chmod 444 "/sys/devices/platform/charger/$n" 2>/dev/null
done
chmod 666 /sys/devices/platform/charger/pdc_max_watt 2>/dev/null
echo 120 > /sys/devices/platform/charger/pdc_max_watt 2>/dev/null
chmod 444 /sys/devices/platform/charger/pdc_max_watt 2>/dev/null

chmod 666 /sys/devices/platform/charger/input_current /sys/devices/platform/charger/sc_ibat_limit /sys/devices/platform/charger/chg1_current /sys/devices/platform/charger/chg2_current 2>/dev/null
echo 33170 > /sys/devices/platform/charger/input_current 2>/dev/null
echo 12000 > /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
echo 33170 > /sys/devices/platform/charger/chg1_current 2>/dev/null
echo 33170 > /sys/devices/platform/charger/chg2_current 2>/dev/null
chmod 444 /sys/devices/platform/charger/input_current /sys/devices/platform/charger/sc_ibat_limit /sys/devices/platform/charger/chg1_current /sys/devices/platform/charger/chg2_current 2>/dev/null

# Uncap power_supply class nodes
for f in /sys/class/power_supply/battery/constant_charge_current_max /sys/class/power_supply/battery/constant_charge_current /sys/class/power_supply/main/current_max /sys/class/power_supply/usb/current_max; do
    if [ -e "$f" ]; then
        chmod 666 "$f" 2>/dev/null
        echo 12000000 > "$f" 2>/dev/null
        chmod 444 "$f" 2>/dev/null
    fi
done

sleep 2

echo "=== HASIL TEST HYPER UNCONSTRAINED ==="
echo -n "VBat: "; cat /sys/class/power_supply/battery/voltage_now 2>/dev/null
echo -n "IBat: "; cat /sys/class/power_supply/battery/current_now 2>/dev/null
echo -n "VCharger: "; cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger 2>/dev/null
echo -n "ICharger: "; cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger 2>/dev/null
echo -n "cdev56 (abcct): "; cat /sys/class/thermal/cooling_device56/cur_state 2>/dev/null
'''

p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
