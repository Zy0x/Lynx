import subprocess

TARGET = "192.168.1.13:5555"
subprocess.run(['adb', 'connect', TARGET], capture_output=True)

script = """
echo "=== CURRENT & VOLTAGE ==="
cat /sys/class/power_supply/battery/current_now
cat /sys/class/power_supply/battery/voltage_now
cat /sys/devices/platform/charger/ADC_Charger_Voltage
echo "=== CHARGER LIMITS ==="
cat /sys/devices/platform/charger/sc_ibat_limit
cat /sys/devices/platform/charger/input_current
echo "=== TEMPERATURE ==="
cat /sys/devices/platform/battery/Battery_Temperature
"""

p = subprocess.Popen(['adb', '-s', TARGET, 'shell', 'su'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
out, err = p.communicate(script)
print(out)
