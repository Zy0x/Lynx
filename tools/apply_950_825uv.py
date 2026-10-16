import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("Applying Experiment B: 950 MHz Overclock + 825 mV Undervolt...")
adb_su("echo '950000 82500' > /proc/gpufreq/gpufreq_fixed_freq_volt")
print("Status:", adb_su("cat /proc/gpufreq/gpufreq_fixed_freq_volt"))
print("Var dump:\n", adb_su("cat /proc/gpufreq/gpufreq_var_dump | head -n 3"))
