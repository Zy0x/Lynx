import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== THERMAL ZONE 0 (mtktsbattery) TRIP POINTS ===")
for p in range(10):
    t_type = adb_su(f"cat /sys/class/thermal/thermal_zone0/trip_point_{p}_type 2>/dev/null")
    t_temp = adb_su(f"cat /sys/class/thermal/thermal_zone0/trip_point_{p}_temp 2>/dev/null")
    if t_type and not t_type.startswith("ERR"):
        print(f"  Trip {p}: type={t_type}, temp={int(t_temp)/1000 if t_temp.isdigit() else t_temp} °C")

print("\n=== THERMAL ZONE 0 COOLING DEVICES BOUND ===")
print(adb_su("ls -la /sys/class/thermal/thermal_zone0/cdev* 2>/dev/null"))
