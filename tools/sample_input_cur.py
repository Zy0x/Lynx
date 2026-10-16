import subprocess, time

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
for i in 1 2 3; do
    echo "--- sample $i ---"
    cat /sys/devices/platform/charger/ADC_Charger_Voltage 2>/dev/null
    cat /sys/devices/platform/charger/input_current 2>/dev/null
    cat /sys/devices/platform/charger/chg1_current 2>/dev/null
    cat /sys/devices/platform/charger/chg2_current 2>/dev/null
    cat /sys/class/power_supply/battery/current_now 2>/dev/null
    cat /sys/class/power_supply/battery/voltage_now 2>/dev/null
    sleep 1
done
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
