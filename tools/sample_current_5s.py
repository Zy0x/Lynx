import subprocess
import time

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)

for i in range(5):
    cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c \"cat /sys/class/power_supply/battery/current_now; cat /sys/devices/platform/charger/ADC_Charger_Voltage; cat /sys/devices/platform/charger/sc_ibat_limit\""]
    res = subprocess.run(cmd, capture_output=True, text=True)
    lines = [x.strip() for x in res.stdout.strip().split('\n') if x.strip()]
    print(f"Sample {i+1}: Current = {lines[0] if len(lines)>0 else '?'} uA, VCharger = {lines[1] if len(lines)>1 else '?'} mV, sc_ibat_limit = {lines[2] if len(lines)>2 else '?'}")
    time.sleep(1)
