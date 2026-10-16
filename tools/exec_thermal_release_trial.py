import subprocess, time

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
# 1. Nonaktifkan thermal zone 1 throttling
echo disabled > /sys/class/thermal/thermal_zone1/mode 2>/dev/null
echo 0 > /sys/class/thermal/cooling_device56/cur_state 2>/dev/null

# 2. Lepaskan pembatas clabcct
echo "6000 0 70000 1000 200000 6000 6000" > /proc/driver/thermal/clabcct 2>/dev/null
echo 0 > /proc/driver/thermal/clabcct_lcmoff 2>/dev/null

# 3. Lepaskan pembatas pcb_thermal_debug ke 58C
echo "[58,5000,60,4000,2500]" > /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug 2>/dev/null

# 4. Set Battery_Temperature ke 32C
echo 32 > /sys/devices/platform/battery/Battery_Temperature 2>/dev/null

# 5. Naikkan limit arus charging
echo 12000000 > /sys/class/power_supply/battery/constant_charge_current_max 2>/dev/null
echo 12000000 > /sys/class/power_supply/battery/constant_charge_current 2>/dev/null
echo 12000 > /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
echo 1 > /sys/devices/platform/charger/enable_sc 2>/dev/null

sleep 3

echo "=== HASIL PENGUJIAN KEMAMPUAN ==="
echo -n "VBat: "; cat /sys/class/power_supply/battery/voltage_now 2>/dev/null
echo -n "IBat: "; cat /sys/class/power_supply/battery/current_now 2>/dev/null
echo -n "Temp Baterai: "; cat /sys/class/power_supply/battery/temp 2>/dev/null
echo -n "VCharger: "; cat /sys/devices/platform/charger/ADC_Charger_Voltage 2>/dev/null
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
