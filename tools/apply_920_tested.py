import subprocess
import sys
import time

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("==========================================================")
print("  RETURNING GPU TO 920 MHz WITH SAFE UNDERVOLT (800 mV)   ")
print("==========================================================")

# Apply 920 MHz @ 800 mV (-50 mV Undervolt vs Stock 850 mV)
adb_su("echo '920000 80000' > /proc/gpufreq/gpufreq_fixed_freq_volt")
time.sleep(1)

var_dump = adb_su("cat /proc/gpufreq/gpufreq_var_dump | head -n 3")
print("GPU Status:\n", var_dump)

# Keep Little cores efficient (1375 - 1618 MHz) and Big Cores full speed (up to 2050 MHz)
for i in range(6):
    adb_su(f"echo 1375000 > /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_min_freq")
    adb_su(f"echo 1618000 > /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_max_freq")
    adb_su(f"echo schedutil > /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_governor 2>/dev/null")

for i in [6, 7]:
    adb_su(f"echo 1796000 > /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_min_freq")
    adb_su(f"echo 2050000 > /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_max_freq")
    adb_su(f"echo performance > /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_governor 2>/dev/null")

print("\nVerifying thermals...")
cpu_t = adb_su("cat /sys/class/thermal/thermal_zone3/temp 2>/dev/null")
bat_t = adb_su("cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null")
c_str = f"{int(cpu_t)/1000:.1f}°C" if cpu_t.isdigit() else "N/A"
b_str = f"{int(bat_t)/1000:.1f}°C" if bat_t.isdigit() else "N/A"
print(f"  CPU Temp: {c_str} | Battery Temp: {b_str}")
