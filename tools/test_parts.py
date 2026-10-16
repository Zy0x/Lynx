import subprocess

cmd = "echo -n 'status: '; cat /sys/class/power_supply/battery/status; echo -n 'Charger_Type: '; cat /sys/devices/platform/charger/Charger_Type 2>/dev/null; echo -n 'Vbus: '; cat /sys/devices/platform/charger/ADC_Charger_Voltage 2>/dev/null; echo -n 'lynxd state: '; cat /dev/lynx_charging_state 2>/dev/null"
p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
print(p.stdout)
