import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("CPU0 Freq:", adb_su("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"))
print("CPU6 Freq:", adb_su("cat /sys/devices/system/cpu/cpu6/cpufreq/scaling_cur_freq"))
print("CPU0 Min:", adb_su("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq"))
print("CPU6 Min:", adb_su("cat /sys/devices/system/cpu/cpu6/cpufreq/scaling_min_freq"))
print("CPU Temp:", int(adb_su("cat /sys/class/thermal/thermal_zone3/temp"))/1000, "°C")
print("Bat Temp:", int(adb_su("cat /sys/class/thermal/thermal_zone0/temp"))/1000, "°C")
print("GPU Load:", adb_su("cat /sys/kernel/ged/hal/gpu_utilization"))
