import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=12)
    return res.stdout.strip()

print("=== 1. GPUFREQ OPP DUMP ===")
print(adb_su("cat /proc/gpufreq/gpufreq_opp_dump"))

print("\n=== 2. GPUFREQ OPP FREQ ===")
print(adb_su("cat /proc/gpufreq/gpufreq_opp_freq"))

print("\n=== 3. GPUFREQ FIXED FREQ VOLT ===")
print(adb_su("cat /proc/gpufreq/gpufreq_fixed_freq_volt"))

print("\n=== 4. GPUFREQ VAR DUMP ===")
print(adb_su("cat /proc/gpufreq/gpufreq_var_dump"))

print("\n=== 5. GPUFREQ LIMIT TABLE ===")
print(adb_su("cat /proc/gpufreq/gpufreq_limit_table"))
