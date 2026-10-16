import subprocess
import time

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("Setting cci_mode = 1...")
adb_su("echo 1 > /proc/cpufreq/cpufreq_cci_mode")
print("Immediate:", adb_su("cat /proc/cpufreq/cpufreq_cci_mode"))

for i in range(5):
    time.sleep(1)
    val = adb_su("cat /proc/cpufreq/cpufreq_cci_mode")
    print(f"After {i+1}s: {val}")
