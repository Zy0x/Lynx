import subprocess, time

target = "192.168.1.13:5555"
script = r'''
echo "Turning screen OFF..."
input keyevent KEYCODE_POWER
sleep 3
dmesg -c > /dev/null
sleep 2
echo "=== SCREEN OFF DMESG ==="
dmesg | grep -iE "dv2|ita_lmt" | tail -n 10
echo -n "IBat: "; cat /sys/class/power_supply/battery/current_now 2>/dev/null
echo -n "ICharger: "; cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger 2>/dev/null
echo "Turning screen ON..."
input keyevent KEYCODE_POWER
'''
p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
