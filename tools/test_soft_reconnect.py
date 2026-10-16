import subprocess, time

target = subprocess.check_output(['powershell', '-ExecutionPolicy', 'Bypass', '-File', '.\\tools\\connect_device.ps1', '-Quiet']).decode().strip()

def run_cmd(c):
    return subprocess.check_output(['adb', '-s', target, 'shell', 'su', '-c', c]).decode().strip()

print("Initial Voltage:", run_cmd("cat /sys/devices/platform/charger/ADC_Charger_Voltage"))
print("Initial Type:", run_cmd("cat /sys/devices/platform/charger/Charger_Type"))

print("Toggling input_suspend...")
run_cmd("echo 1 > /sys/class/power_supply/battery/input_suspend; sleep 0.3; echo 0 > /sys/class/power_supply/battery/input_suspend")

time.sleep(2)
print("After 2s Voltage:", run_cmd("cat /sys/devices/platform/charger/ADC_Charger_Voltage"))
print("After 2s Type:", run_cmd("cat /sys/devices/platform/charger/Charger_Type"))
print("After 2s Current Now:", run_cmd("cat /sys/class/power_supply/battery/current_now"))
