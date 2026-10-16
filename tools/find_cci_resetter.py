import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("Checking grep in /vendor/bin /vendor/etc for cci_mode...")
res = adb_su("grep -rn 'cpufreq_cci_mode' /vendor/bin /vendor/etc /system/vendor /system/bin 2>/dev/null")
print(res)

print("\nChecking chmod 444 on cpufreq_cci_mode...")
adb_su("echo 1 > /proc/cpufreq/cpufreq_cci_mode; chmod 444 /proc/cpufreq/cpufreq_cci_mode")
print("Immediate:", adb_su("cat /proc/cpufreq/cpufreq_cci_mode"))
import time
time.sleep(2)
print("After 2s (with chmod 444):", adb_su("cat /proc/cpufreq/cpufreq_cci_mode"))
