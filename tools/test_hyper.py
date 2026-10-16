import subprocess, time

target = "192.168.1.13:5555"
script = r'''
# Raise trip points in thermal_zone1
echo 95000 > /sys/class/thermal/thermal_zone1/trip_point_4_temp
echo 95000 > /sys/class/thermal/thermal_zone1/trip_point_3_temp

# Unlock and clear cooling devices
chmod 666 /sys/class/thermal/cooling_device56/cur_state /sys/class/thermal/cooling_device57/cur_state 2>/dev/null
echo 0 > /sys/class/thermal/cooling_device56/cur_state
echo 0 > /sys/class/thermal/cooling_device57/cur_state
chmod 444 /sys/class/thermal/cooling_device56/cur_state /sys/class/thermal/cooling_device57/cur_state 2>/dev/null

# Disable ABCCT PID
chmod 666 /proc/driver/thermal/clabcct /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
echo 0 > /proc/driver/thermal/clabcct
echo 0 > /proc/driver/thermal/clabcct_lcmoff
chmod 444 /proc/driver/thermal/clabcct /proc/driver/thermal/clabcct_lcmoff 2>/dev/null

# Uncap Transsion PCB and DV2 thermal debug
chmod 666 /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null
echo "[95,6000,100,6000,6000]" > /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug
echo "[101,85,88,90,6000,6000,6000]" > /sys/devices/platform/pca_dv2_algo/dv2_debug
echo "[102,85,88,90,6000,6000,6000]" > /sys/devices/platform/pca_dv2_algo/dv2_debug
chmod 444 /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null

# Mock Battery_Temperature to 28C
chmod 666 /sys/devices/platform/battery/Battery_Temperature 2>/dev/null
echo 28 > /sys/devices/platform/battery/Battery_Temperature
chmod 444 /sys/devices/platform/battery/Battery_Temperature 2>/dev/null

# Charging call state = 0
chmod 666 /sys/devices/platform/odm/odm:tran_battery/Charging_CallState 2>/dev/null
echo 0 > /sys/devices/platform/odm/odm:tran_battery/Charging_CallState
chmod 444 /sys/devices/platform/odm/odm:tran_battery/Charging_CallState 2>/dev/null

# Direct pump and fast charge
chmod 666 /sys/devices/platform/charger/enable_sc /sys/devices/platform/charger/input_current /sys/devices/platform/charger/sc_ibat_limit /sys/devices/platform/charger/chg1_current /sys/devices/platform/charger/chg2_current 2>/dev/null
echo 1 > /sys/devices/platform/charger/enable_sc
echo 12000 > /sys/devices/platform/charger/sc_ibat_limit
echo 33170 > /sys/devices/platform/charger/input_current
echo 33170 > /sys/devices/platform/charger/chg1_current
echo 33170 > /sys/devices/platform/charger/chg2_current
chmod 444 /sys/devices/platform/charger/enable_sc /sys/devices/platform/charger/sc_ibat_limit /sys/devices/platform/charger/input_current /sys/devices/platform/charger/chg1_current /sys/devices/platform/charger/chg2_current 2>/dev/null

sleep 1

echo -n "cdev56: "; cat /sys/class/thermal/cooling_device56/cur_state 2>/dev/null
echo -n "current_now: "; cat /sys/class/power_supply/battery/current_now 2>/dev/null
echo -n "VCharger: "; cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger 2>/dev/null
echo -n "ICharger: "; cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger 2>/dev/null
'''

p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
