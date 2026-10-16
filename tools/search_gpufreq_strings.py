import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=12)
    return res.stdout.strip()

print("Searching strings in kernel or modules for gpufreq_fixed_freq_volt...")
print(adb_su("grep -r 'gpufreq_fixed_freq_volt' /vendor/ /system/ 2>/dev/null"))
print("\nChecking dmesg for fixed_freq_volt:")
print(adb_su("dmesg | grep -iE 'fixed_freq|fixed_opp' 2>/dev/null | tail -n 10"))
