import subprocess
import time

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)

for i in range(3):
    cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'cat /sys/class/thermal/thermal_zone1/temp; cat /sys/class/power_supply/battery/current_now; cat /sys/devices/platform/charger/ADC_Charger_Voltage'"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    lines = [x.strip() for x in res.stdout.strip().split('\n') if x.strip()]
    temp = int(lines[0])/1000.0 if len(lines)>0 and lines[0].isdigit() else lines[0]
    cur = int(lines[1])/1000.0 if len(lines)>1 and lines[1].isdigit() else lines[1]
    vchg = lines[2] if len(lines)>2 else '?'
    print(f"AP Temp: {temp}°C | Bat Current: {cur} mA | VCharger: {vchg} mV")
    time.sleep(1)
