import subprocess

def run_su(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout + p.stderr

print("--- PS CHARGER / THERMAL ---")
print(run_su("ps -A | grep -iE 'charger|thermal|battery|tran'"))

print("--- CURRENT NODE VALUES ---")
print(run_su("""
for f in /sys/class/power_supply/battery/* /sys/devices/platform/charger/* /sys/devices/platform/odm/odm:tran_battery/*; do
    if [ -f "$f" ]; then
        v=$(cat "$f" 2>/dev/null)
        [ -n "$v" ] && echo "${f##*/}=$v"
    fi
done
"""))
