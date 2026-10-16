import subprocess, time

def run(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip()

print("Current current_now:", run("cat /sys/class/power_supply/battery/current_now"))
print("Current Pump_Express_ICharger:", run("cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger"))
print("Current Pump_Express_VCharger:", run("cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger"))

# Test writing 100 to Pump_Express_ICharger (1.0A or 1000mA?)
run("echo 100 > /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger")
time.sleep(1)
print("After writing 100:", run("cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger"))
print("current_now after 100:", run("cat /sys/class/power_supply/battery/current_now"))

# Test writing 50 to Pump_Express_ICharger
run("echo 50 > /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger")
time.sleep(1)
print("After writing 50:", run("cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger"))
print("current_now after 50:", run("cat /sys/class/power_supply/battery/current_now"))

# Restore
run("echo 194 > /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger")
print("Restored:", run("cat /sys/class/power_supply/battery/current_now"))
