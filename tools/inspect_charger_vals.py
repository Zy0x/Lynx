import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
for f in /sys/devices/platform/charger/ADC_Charger_Voltage \
         /sys/devices/platform/charger/Charger_Type \
         /sys/devices/platform/charger/input_current \
         /sys/devices/platform/charger/chg1_current \
         /sys/devices/platform/charger/chg2_current \
         /sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger \
         /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger \
         /sys/bus/i2c/drivers/rt9759/*/switching_charger/*; do
    if [ -f "$f" ]; then
        echo "$f: $(cat "$f" 2>/dev/null)"
    fi
done
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
