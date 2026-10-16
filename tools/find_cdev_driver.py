import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print(adb_su("""
for z in /sys/class/thermal/thermal_zone*; do
    for l in $z/cdev*; do
        if [ -L "$l" ]; then
            dest=$(readlink $l)
            if [ "$dest" = "../cooling_device58" ]; then
                echo "$z -> $dest: $(cat $z/type 2>/dev/null), mode=$(cat $z/mode 2>/dev/null)"
            fi
        fi
    done
done
"""))
