import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
echo -n "enable_sc: "; cat /sys/devices/platform/charger/enable_sc 2>/dev/null
echo -n "pe20: "; cat /sys/devices/platform/charger/pe20 2>/dev/null
echo -n "pe40: "; cat /sys/devices/platform/charger/pe40 2>/dev/null
echo -n "input_current: "; cat /sys/devices/platform/charger/input_current 2>/dev/null
echo -n "sc_ibat_limit: "; cat /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
echo -n "VCharger: "; cat /sys/devices/platform/charger/ADC_Charger_Voltage 2>/dev/null
echo -n "VBat: "; cat /sys/class/power_supply/battery/voltage_now 2>/dev/null
echo -n "IBat: "; cat /sys/class/power_supply/battery/current_now 2>/dev/null
echo -n "extreme_marker: "; cat /dev/lynx_extreme_charging 2>/dev/null
echo ""
echo -n "charging_state: "; cat /dev/lynx_charging_state 2>/dev/null
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
