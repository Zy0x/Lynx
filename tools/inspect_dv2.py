import subprocess

def run_adb(cmd):
    res = subprocess.run(['adb', 'shell', 'su', '-c', cmd], capture_output=True, text=True, errors='replace')
    return res.stdout.strip() if res.stdout else ""

print("=== RECENT DV2 & PCA DMESG ===")
lines = run_adb('dmesg').splitlines()
relevant = [l for l in lines if 'PCA' in l or 'LynxKPM' in l or 'dv2' in l]
for l in relevant[-35:]:
    print(l)

print("\n=== POWER SUPPLY CURRENT STATUS ===")
print("Battery Current:", run_adb('cat /sys/class/power_supply/battery/current_now'))
print("Battery Voltage:", run_adb('cat /sys/class/power_supply/battery/voltage_now'))
print("Battery Temp:", run_adb('cat /sys/class/power_supply/battery/temp'))
