import subprocess
import sys
import time

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("==========================================================")
print("  APPLYING SILKY SMOOTH & COOL HARMONIZED SWEET SPOT     ")
print("==========================================================")

# 1. GPU: 850 MHz @ 750 mV (-100 mV Deep Undervolt, -38% Heat vs 950MHz)
print("[1/4] Locking GPU to 850 MHz @ 750 mV (Deep Undervolt)...")
adb_su("echo '850000 75000' > /proc/gpufreq/gpufreq_fixed_freq_volt")
print("  GPU State:\n", adb_su("cat /proc/gpufreq/gpufreq_var_dump | head -n 3"))

# 2. CPU Little Cores: Cap to 1500 MHz (Eliminates ~1W of continuous heat)
print("\n[2/4] Calibrating Little Cores to 1375 - 1500 MHz (schedutil)...")
for i in range(6):
    adb_su(f"echo 1375000 > /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_min_freq")
    adb_su(f"echo 1500000 > /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_max_freq")
    adb_su(f"echo schedutil > /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_governor 2>/dev/null")

# 3. CPU Big Cores: 1796 - 2050 MHz (Dynamic burst on demand)
print("\n[3/4] Calibrating Big Cores to 1796 - 2050 MHz (schedutil)...")
for i in [6, 7]:
    adb_su(f"echo 1796000 > /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_min_freq")
    adb_su(f"echo 2050000 > /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_max_freq")
    adb_su(f"echo schedutil > /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_governor 2>/dev/null")

# 4. Storage & RAM: Deadline + 1024KB Read-Ahead + Low Cache Pressure
print("\n[4/4] Verifying Storage & RAM Pacing...")
adb_su("echo deadline > /sys/block/mmcblk0/queue/scheduler 2>/dev/null")
adb_su("echo 1024 > /sys/block/mmcblk0/queue/read_ahead_kb 2>/dev/null")
adb_su("echo 100 > /sys/block/mmcblk0/queue/iosched/read_expire 2>/dev/null")
adb_su("echo 15 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null")
adb_su("echo 1500 > /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null")

print("\n>>> SILKY & COOL SETTINGS APPLIED! <<<")
print("Monitoring cooldown over 10 seconds...")
for s in range(5):
    time.sleep(2)
    cpu_t = adb_su("cat /sys/class/thermal/thermal_zone3/temp 2>/dev/null")
    bat_t = adb_su("cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null")
    c_str = f"{int(cpu_t)/1000:.1f}°C" if cpu_t.isdigit() else "N/A"
    b_str = f"{int(bat_t)/1000:.1f}°C" if bat_t.isdigit() else "N/A"
    print(f"  Step {s+1} ({(s+1)*2}s): CPU Temp = {c_str} | Battery Temp = {b_str}")
