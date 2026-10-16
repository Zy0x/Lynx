import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("Touch drivers:")
print(adb_su("ls -d /sys/devices/platform/*touch* /sys/class/input/input* 2>/dev/null"))
print("\nTouch boost props:")
print(adb_su("getprop | grep -iE 'touch|refresh|rate|smooth' | head -n 15"))
