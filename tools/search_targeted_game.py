import subprocess
import os

target = "192.168.1.13:5555"

# Let's search inside /sys/class, /sys/devices/platform, /proc/driver
script = '''
for p in /sys/devices/platform /sys/class/power_supply /proc/driver; do
    find $p -name "*game*" 2>/dev/null
done
'''
p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
