import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("tz3 trip_0:", adb_su("cat /sys/class/thermal/thermal_zone3/trip_point_0_temp 2>/dev/null"))
print("tz3 trip_1:", adb_su("cat /sys/class/thermal/thermal_zone3/trip_point_1_temp 2>/dev/null"))
print("tz3 cdev0_weight:", adb_su("cat /sys/class/thermal/thermal_zone3/cdev0_weight 2>/dev/null"))
print("tz3 cdev0_trip_point:", adb_su("cat /sys/class/thermal/thermal_zone3/cdev0_trip_point 2>/dev/null"))
