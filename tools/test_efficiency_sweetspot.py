import subprocess
import sys
import time

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("==========================================================")
print("  APPLYING THERMAL EFFICIENCY SWEET SPOT (COOL & SMOOTH)  ")
print("==========================================================")

# 1. GPU: 900 MHz @ 775 mV (Deep UV: -75mV, -25% GPU Heat)
print("[1/4] Applying GPU 900 MHz @ 775 mV Deep Undervolt...")
adb_su("echo '900000 77500' > /proc/gpufreq/gpufreq_fixed_freq_volt")
print("GPU Status:\n", adb_su("cat /proc/gpufreq/gpufreq_var_dump | head -n 3"))

# 2. CPU Little Cores (0-5): Cap to 1618 MHz (Cuts Little Core Heat by 35%)
print("\n[2/4] Calibrating CPU Little Cores (1375 - 1618 MHz)...")
for i in range(6):
    adb_su(f"echo 1375000 > /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_min_freq")
    adb_su(f"echo 1618000 > /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_max_freq")
    adb_su(f"echo schedutil > /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_governor 2>/dev/null")

# 3. CPU Big Cores (6-7): Full Performance (1796 - 2050 MHz)
print("\n[3/4] Calibrating CPU Big Cores (1796 - 2050 MHz)...")
for i in [6, 7]:
    adb_su(f"echo 1796000 > /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_min_freq")
    adb_su(f"echo 2050000 > /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_max_freq")
    adb_su(f"echo performance > /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_governor 2>/dev/null")

# 4. Storage & Memory: Keep Deadline & 1024KB Read-Ahead
print("\n[4/4] Ensuring Deadline MicroSD Queue & RAM Caching...")
adb_su("echo deadline > /sys/block/mmcblk0/queue/scheduler 2>/dev/null")
adb_su("echo 1024 > /sys/block/mmcblk0/queue/read_ahead_kb 2>/dev/null")
adb_su("echo 100 > /sys/block/mmcblk0/queue/iosched/read_expire 2>/dev/null")
adb_su("echo 4 > /sys/block/mmcblk0/queue/iosched/writes_starved 2>/dev/null")
adb_su("echo 15 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null")
adb_su("echo 1500 > /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null")

print("\n>>> SWEET SPOT APPLIED - MONITORING THERMALS FOR 10 SECONDS <<<")
for s in range(5):
    time.sleep(2)
    cpu_t = adb_su("cat /sys/class/thermal/thermal_zone3/temp 2>/dev/null")
    bat_t = adb_su("cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null")
    c_str = f"{int(cpu_t)/1000:.1f}°C" if cpu_t.isdigit() else "N/A"
    b_str = f"{int(bat_t)/1000:.1f}°C" if bat_t.isdigit() else "N/A"
    print(f"  Step {s+1} ({(s+1)*2}s): CPU Temp = {c_str} | Battery Temp = {b_str}")
