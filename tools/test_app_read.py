import subprocess

uid_out = subprocess.check_output(['adb', '-s', '192.168.1.13:5555', 'shell', 'dumpsys package com.noir.lynx'], text=True)
uid = ""
for line in uid_out.splitlines():
    if "userId=" in line:
        uid = line.split("=")[1].strip()
        break
print("APP UID:", uid)

cmd = f"su {uid} -s /system/bin/sh -c 'cat /sys/devices/platform/charger/Charger_Type 2>&1; cat /sys/class/power_supply/usb/type 2>&1; cat /sys/devices/platform/charger/Pump_Express 2>&1'"
res = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', cmd], capture_output=True, text=True)
print("OUTPUT AS APP UID:")
print(res.stdout)
print("STDERR:")
print(res.stderr)
