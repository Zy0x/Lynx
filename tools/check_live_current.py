import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c \"cat /sys/class/power_supply/battery/current_now; cat /sys/devices/platform/charger/ADC_Charger_Voltage; cat /sys/devices/platform/charger/enable_sc; cat /sys/devices/platform/charger/sc_ibat_limit; cat /sys/class/power_supply/battery/Pump_Express_VCharger 2>/dev/null\""]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
