import subprocess

sh_script = """
for f in /sys/devices/platform/charger/* /sys/devices/platform/odm/odm:tran_battery/* /sys/class/power_supply/usb/* /sys/class/power_supply/charger/* /sys/class/power_supply/battery/*; do
  if [ -f "$f" ]; then
    v=$(cat "$f" 2>/dev/null | head -c 100)
    if [ -n "$v" ] && [ "$v" != "0" ]; then
      echo "$f = $v"
    fi
  fi
done
"""

res = subprocess.run(["adb", "shell", "su", "-c", sh_script], capture_output=True, text=True)
print(res.stdout)
