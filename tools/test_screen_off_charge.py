import subprocess
import time

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)

print("Turning screen OFF...")
subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", "input keyevent 26"])
time.sleep(4)

for i in range(5):
    cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'cat /sys/class/power_supply/battery/current_now; cat /sys/devices/platform/charger/ADC_Charger_Voltage'"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    lines = [x.strip() for x in res.stdout.strip().split('\n') if x.strip()]
    cur = lines[0] if len(lines) > 0 else '?'
    vchg = lines[1] if len(lines) > 1 else '?'
    print(f"Screen OFF Sample {i+1}: Current = {cur} uA, VCharger = {vchg} mV")
    time.sleep(1)

cmd_dmesg = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'dmesg | grep -iE \"\\[PCA\\]|dv2_\" | tail -n 15'"]
res_dmesg = subprocess.run(cmd_dmesg, capture_output=True, text=True)
print("\nRecent dmesg:")
print(res_dmesg.stdout)

# Turn screen back on
subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", "input keyevent 26"])
