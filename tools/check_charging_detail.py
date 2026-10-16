import subprocess

TARGET = "192.168.1.13:5555"
subprocess.run(['adb', 'connect', TARGET], capture_output=True)

script = """
echo "=== WAKEFULNESS ==="
dumpsys power | grep -E "mHoldingDisplay|mWakefulness"
echo "=== FOCUS ==="
dumpsys window displays | grep -E "mCurrentFocus"
echo "=== TEMPERATURE ==="
cat /sys/class/power_supply/battery/temp 2>/dev/null
cat /sys/devices/platform/battery/Battery_Temperature 2>/dev/null
echo "=== CHARGING VOLTAGE & CURRENT ==="
cat /sys/class/power_supply/battery/voltage_now 2>/dev/null
cat /sys/class/power_supply/battery/current_now 2>/dev/null
"""

p = subprocess.Popen(['adb', '-s', TARGET, 'shell', 'su'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
out, err = p.communicate(script)
print(out)
