import subprocess, time

def run_su(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su'], input=cmd + '\nexit\n', capture_output=True, text=True)
    return p.stdout.strip()

print("Current input_current:", run_su("cat /sys/devices/platform/charger/input_current"))
print("Current chg1_current:", run_su("cat /sys/devices/platform/charger/chg1_current"))

# What values are accepted by input_current?
# Usually in MediaTek charger driver: input_current is in uA (e.g. 500000 = 500mA, 100000 = 100mA) or mA!
# Let's see: When we echo "0", what does dmesg say?
run_su("dmesg -c > /dev/null") # clear dmesg buffer
run_su("echo 0 > /sys/devices/platform/charger/input_current")
time.sleep(0.5)
dmesg = run_su("dmesg | grep -iE 'charger|input_current|chg1|rt9471|rt9759' | tail -n 20")
print("dmesg after echo 0 > input_current:")
print(dmesg)
