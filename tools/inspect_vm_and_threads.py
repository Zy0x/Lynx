import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c \"{cmd}\""], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== VM RECLAIM & MEMORY TUNABLES ===")
print("min_free_kbytes:", adb_su("cat /proc/sys/vm/min_free_kbytes"))
print("extra_free_kbytes:", adb_su("cat /proc/sys/vm/extra_free_kbytes 2>/dev/null"))
print("vfs_cache_pressure:", adb_su("cat /proc/sys/vm/vfs_cache_pressure"))
print("swappiness:", adb_su("cat /proc/sys/vm/swappiness"))
print("dirty_ratio:", adb_su("cat /proc/sys/vm/dirty_ratio"))
print("dirty_background_ratio:", adb_su("cat /proc/sys/vm/dirty_background_ratio"))
print("dirty_writeback_centisecs:", adb_su("cat /proc/sys/vm/dirty_writeback_centisecs"))
print("dirty_expire_centisecs:", adb_su("cat /proc/sys/vm/dirty_expire_centisecs"))

print("\n=== DEADLINE SCHEDULER TUNABLES ON MMCBLK0 ===")
print("scheduler:", adb_su("cat /sys/block/mmcblk0/queue/scheduler"))
print("iosched dir:", adb_su("ls /sys/block/mmcblk0/queue/iosched 2>/dev/null"))

print("\n=== GENSHIN THREAD CPUS CURRENTLY ASSIGNED ===")
pid = adb_su("pidof com.miHoYo.GenshinImpact").split()[0]
print(adb_su(f"ps -o TID,PSR,COMM -T -p {pid} | head -n 30"))
