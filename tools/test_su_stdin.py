import subprocess

def run_su(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su'], input=cmd + '\nexit\n', capture_output=True, text=True, timeout=5)
    return p.stdout.strip()

print("Battery current_now:", run_su("cat /sys/class/power_supply/battery/current_now"))
print("Battery capacity:", run_su("cat /sys/class/power_supply/battery/capacity"))
