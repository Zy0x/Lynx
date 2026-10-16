import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("Disabling fixed_freq_volt...")
adb_su("echo '0 0' > /proc/gpufreq/gpufreq_fixed_freq_volt")
print("Status:", adb_su("cat /proc/gpufreq/gpufreq_fixed_freq_volt"))

# Lock GED to OPP 0
adb_su("echo 0 > /sys/kernel/ged/hal/custom_boost_gpu_freq")
adb_su("echo 0 > /sys/kernel/ged/hal/custom_upbound_gpu_freq")
print("GED freq:", adb_su("cat /sys/kernel/ged/hal/current_freqency"))
print("Var dump:", adb_su("cat /proc/gpufreq/gpufreq_var_dump | head -n 3"))
