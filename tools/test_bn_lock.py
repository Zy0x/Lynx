import subprocess, time

def run_su(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su'], input=cmd + '\nexit\n', capture_output=True, text=True, timeout=5)
    return p.stdout.strip()

print("Current now before:", run_su("cat /sys/class/power_supply/battery/current_now"))

# Test BN_TestMode + chg1_current=0 + input_current=0
run_su("echo 1 > /sys/devices/platform/charger/BN_TestMode")
run_su("echo 0 > /sys/devices/platform/charger/input_current")
run_su("echo 0 > /sys/devices/platform/charger/chg1_current")
run_su("echo 0 > /sys/devices/platform/charger/chg2_current")
time.sleep(2)

print("input_current:", run_su("cat /sys/devices/platform/charger/input_current"))
print("chg1_current:", run_su("cat /sys/devices/platform/charger/chg1_current"))
print("BN_TestMode:", run_su("cat /sys/devices/platform/charger/BN_TestMode"))
print("Current now after:", run_su("cat /sys/class/power_supply/battery/current_now"))
