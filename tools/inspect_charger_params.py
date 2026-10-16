import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
echo "=== BATTERY PARAMS ==="
cat /sys/devices/platform/battery/Battery_Temperature
cat /sys/class/power_supply/battery/temp
echo "=== CHARGER PARAMS ==="
cat /sys/devices/platform/charger/sw_jeita
cat /sys/devices/platform/charger/BN_TestMode
cat /sys/devices/platform/charger/BatteryNotify
cat /sys/devices/platform/charger/enable_sc
cat /sys/devices/platform/charger/sc_ibat_limit
cat /sys/devices/platform/charger/pe20
cat /sys/devices/platform/charger/pe40
cat /sys/devices/platform/charger/Charger_Type
echo "=== CONSTANT CHARGE CURRENT ==="
cat /sys/class/power_supply/battery/constant_charge_current_max
cat /sys/class/power_supply/battery/constant_charge_current
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
