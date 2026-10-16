import subprocess

def run_su(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su'], input=cmd + '\nexit\n', capture_output=True, text=True)
    return p.stdout.strip()

print("--- sysfs under switching_charger ---")
print(run_su("find /sys/class/switching_charger/ /sys/class/power_supply/ /sys/devices/platform/charger/ -type f 2>/dev/null"))
