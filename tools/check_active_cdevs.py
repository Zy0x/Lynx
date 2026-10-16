import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("Active Cooling Devices:")
res = adb_su("""
for c in /sys/class/thermal/cooling_device*; do
    type=$(cat $c/type 2>/dev/null)
    cur=$(cat $c/cur_state 2>/dev/null)
    max=$(cat $c/max_state 2>/dev/null)
    if [ "$cur" != "0" ] && [ -n "$cur" ]; then
        echo "$(basename $c): $type | cur=$cur / max=$max"
    fi
done
""")
print(res if res else "No cooling devices are active (cur=0 across all cdevs).")
