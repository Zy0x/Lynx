import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== MALI KBASE PARAMETERS ===")
params = adb_su("ls /sys/module/mali_kbase/parameters/ 2>/dev/null")
for p in params.split():
    val = adb_su(f"cat /sys/module/mali_kbase/parameters/{p} 2>/dev/null")
    print(f"  {p}: {val}")

print("\n=== MALI0 DEVICE ATTRIBUTES ===")
attrs = adb_su("ls /sys/class/misc/mali0/device/ 2>/dev/null")
for a in attrs.split()[:15]:
    val = adb_su(f"cat /sys/class/misc/mali0/device/{a} 2>/dev/null")
    if val and len(val) < 80:
        print(f"  {a}: {val}")
