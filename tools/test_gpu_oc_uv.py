import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== 1. CHECK CURRENT GPU FREQ & VOLT VIA VAR_DUMP ===")
print(adb_su("cat /proc/gpufreq/gpufreq_var_dump | head -n 4"))

print("\n=== 2. TESTING OVERCLOCK TO 950 MHz (950000 85000) ===")
adb_su("echo '950000 85000' > /proc/gpufreq/gpufreq_fixed_freq_volt")
print("Status:", adb_su("cat /proc/gpufreq/gpufreq_fixed_freq_volt"))
print("Var dump:", adb_su("cat /proc/gpufreq/gpufreq_var_dump | head -n 3"))

print("\n=== 3. RESTORING / TESTING 920 MHz with 800 mV (SWEET SPOT UV) ===")
adb_su("echo '920000 80000' > /proc/gpufreq/gpufreq_fixed_freq_volt")
print("Status:", adb_su("cat /proc/gpufreq/gpufreq_fixed_freq_volt"))
print("Var dump:", adb_su("cat /proc/gpufreq/gpufreq_var_dump | head -n 3"))
