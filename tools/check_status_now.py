import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== CHECK TOP APP & GPU UTILIZATION ===")
top_app = subprocess.run(['adb', '-s', TARGET, 'shell', 'dumpsys activity activities | grep topResumedActivity'], capture_output=True, text=True).stdout.strip()
print("Top App:", top_app)

print("GPU Freq:", adb_su("cat /sys/kernel/ged/hal/current_freqency"))
print("GPU Load:", adb_su("cat /sys/kernel/ged/hal/gpu_utilization"))
print("MicroSD Scheduler:", adb_su("cat /sys/block/mmcblk0/queue/scheduler"))
print("MicroSD Read-Ahead:", adb_su("cat /sys/block/mmcblk0/queue/read_ahead_kb"))
print("CCI Mode:", adb_su("cat /proc/cpufreq/cpufreq_cci_mode"))
print("VFS Cache Pressure:", adb_su("cat /proc/sys/vm/vfs_cache_pressure"))
print("Dirty Writeback Centisecs:", adb_su("cat /proc/sys/vm/dirty_writeback_centisecs"))
