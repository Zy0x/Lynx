import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    print("Device target:", target)
    
    cmd = """
chmod 666 /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger 2>/dev/null
chmod 666 /sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger 2>/dev/null
vchg=$(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger 2>/dev/null)
ichg=$(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger 2>/dev/null)
vbat=$(cat /sys/class/power_supply/battery/voltage_now 2>/dev/null)
ibat=$(cat /sys/class/power_supply/battery/current_now 2>/dev/null)
echo "vchg: $vchg"
echo "ichg: $ichg"
echo "vbat: $vbat"
echo "ibat: $ibat"
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)
    print("STDERR:\n", res.stderr)

if __name__ == "__main__":
    run()
