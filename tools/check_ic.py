import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
f="/sys/devices/platform/charger/input_current"
echo "input_current: $(cat $f 2>/dev/null)"
f2="/sys/devices/platform/charger/ADC_Charger_Voltage"
echo "ADC_Charger_Voltage: $(cat $f2 2>/dev/null)"
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
