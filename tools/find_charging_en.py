import subprocess

def run(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip()

print("Search for charging_enabled or input_suspend or bypass:")
print(run('find /sys -name "*charging_enabled*" -o -name "*input_suspend*" -o -name "*battery_charging_enabled*" 2>/dev/null'))
