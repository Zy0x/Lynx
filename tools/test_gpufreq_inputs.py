import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=12)
    return res.stdout.strip()

print("Testing echo into gpufreq_opp_freq:")
out1 = adb_su("echo 'help' > /proc/gpufreq/gpufreq_opp_freq 2>&1")
print("  Result:", out1)

print("\nTesting echo into gpufreq_fixed_freq_volt:")
out2 = adb_su("echo 'help' > /proc/gpufreq/gpufreq_fixed_freq_volt 2>&1")
print("  Result:", out2)

print("\nRecent dmesg lines regarding gpufreq:")
print(adb_su("dmesg | grep -iE 'gpufreq|mali.*volt|dvfs' | tail -n 15"))
