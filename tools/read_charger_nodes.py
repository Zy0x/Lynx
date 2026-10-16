import subprocess

TARGET = "192.168.1.13:5555"
subprocess.run(['adb', 'connect', TARGET], capture_output=True)

script = """
for f in /sys/devices/platform/charger/*; do
    if [ -f "$f" ]; then
        val=$(cat "$f" 2>/dev/null)
        if [ -n "$val" ] && [ ${#val} -lt 60 ]; then
            echo "$(basename $f) = $val"
        fi
    fi
done
echo "=== TRAN BATTERY ==="
for f in /sys/devices/platform/tran_battery/*; do
    if [ -f "$f" ]; then
        val=$(cat "$f" 2>/dev/null)
        if [ -n "$val" ] && [ ${#val} -lt 60 ]; then
            echo "$(basename $f) = $val"
        fi
    fi
done
"""

p = subprocess.Popen(['adb', '-s', TARGET, 'shell', 'su'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
out, err = p.communicate(script)
print(out)
