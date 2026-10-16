import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== CURRENT THERMAL & POWER STATUS ===")
cpu_temp = adb_su("cat /sys/class/thermal/thermal_zone3/temp 2>/dev/null")
bat_temp = adb_su("cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null")
ap_temp = adb_su("cat /sys/class/thermal/thermal_zone1/temp 2>/dev/null")

print(f"CPU Temp: {int(cpu_temp)/1000 if cpu_temp.isdigit() else 'N/A'} °C")
print(f"Bat Temp: {int(bat_temp)/1000 if bat_temp.isdigit() else 'N/A'} °C")
print(f"AP Temp : {int(ap_temp)/1000 if ap_temp.isdigit() else 'N/A'} °C")

print("\n=== CURRENT FREQUENCIES ===")
print("Little Core (cpu0):", adb_su("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"))
print("Big Core (cpu6)   :", adb_su("cat /sys/devices/system/cpu/cpu6/cpufreq/scaling_cur_freq"))
print("GPU Freq & Volt   :\n", adb_su("cat /proc/gpufreq/gpufreq_var_dump | head -n 3"))
print("GPU Load          :", adb_su("cat /sys/kernel/ged/hal/gpu_utilization"))
