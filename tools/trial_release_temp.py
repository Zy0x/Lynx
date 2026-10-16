import subprocess, time

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
echo "=== SEBELUM DIBEBASKAN ==="
echo -n "VBat: "; cat /sys/class/power_supply/battery/voltage_now 2>/dev/null
echo -n "IBat: "; cat /sys/class/power_supply/battery/current_now 2>/dev/null
echo -n "cooling56: "; cat /sys/class/thermal/cooling_device56/cur_state 2>/dev/null

echo "=== MELEPAS PEMBATAS SUHU (TRIAL) ==="
# 1. Reset cooling device state
chmod 666 /sys/class/thermal/cooling_device56/cur_state 2>/dev/null
echo 0 > /sys/class/thermal/cooling_device56/cur_state 2>/dev/null

# 2. Reset clabcct policy
chmod 666 /proc/driver/thermal/clabcct 2>/dev/null
echo 0 > /proc/driver/thermal/clabcct 2>/dev/null
chmod 666 /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
echo 0 > /proc/driver/thermal/clabcct_lcmoff 2>/dev/null

# 3. PCB thermal debug scale up step
chmod 666 /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug 2>/dev/null
echo "[55,5000,58,4000,2500]" > /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug 2>/dev/null

# 4. Set Battery_Temperature mock to 35C for thermal governor
chmod 666 /sys/devices/platform/battery/Battery_Temperature 2>/dev/null
echo 35 > /sys/devices/platform/battery/Battery_Temperature 2>/dev/null

# 5. Fast charge current restoration
echo 12000000 > /sys/class/power_supply/battery/constant_charge_current_max 2>/dev/null
echo 12000000 > /sys/class/power_supply/battery/constant_charge_current 2>/dev/null
echo 33170 > /sys/devices/platform/charger/chg1_current 2>/dev/null
echo 33170 > /sys/devices/platform/charger/chg2_current 2>/dev/null
echo 12000 > /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null

sleep 2

echo "=== SETELAH DIBEBASKAN ==="
echo -n "VBat: "; cat /sys/class/power_supply/battery/voltage_now 2>/dev/null
echo -n "IBat: "; cat /sys/class/power_supply/battery/current_now 2>/dev/null
echo -n "VCharger: "; cat /sys/devices/platform/charger/ADC_Charger_Voltage 2>/dev/null
echo -n "ICharger: "; cat /sys/devices/platform/charger/input_current 2>/dev/null
echo -n "cooling56: "; cat /sys/class/thermal/cooling_device56/cur_state 2>/dev/null
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
