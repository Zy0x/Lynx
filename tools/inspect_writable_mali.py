import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("Writable Mali attributes:")
res = adb_su("for f in /sys/class/misc/mali0/device/*; do if [ -w $f ]; then echo $(basename $f): $(cat $f 2>/dev/null); fi; done")
for l in res.split('\n'):
    if l:
        print(" ", l)
