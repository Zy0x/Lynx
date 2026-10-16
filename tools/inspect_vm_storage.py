import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== VM DIRTY SETTINGS ===")
print("dirty_ratio:", adb_su("cat /proc/sys/vm/dirty_ratio"))
print("dirty_background_ratio:", adb_su("cat /proc/sys/vm/dirty_background_ratio"))
print("dirty_bytes:", adb_su("cat /proc/sys/vm/dirty_bytes"))
print("dirty_background_bytes:", adb_su("cat /proc/sys/vm/dirty_background_bytes"))
print("dirty_expire_centisecs:", adb_su("cat /proc/sys/vm/dirty_expire_centisecs"))
print("dirty_writeback_centisecs:", adb_su("cat /proc/sys/vm/dirty_writeback_centisecs"))

print("\n=== MOUNT OPTIONS FOR SDCARD & DATA ===")
print(adb_su("mount | grep -E 'mmcblk|fuse|sdcard|emulated'"))

print("\n=== MMCBLK0 TUNABLES ===")
print("scheduler:", adb_su("cat /sys/block/mmcblk0/queue/scheduler"))
print("read_ahead_kb:", adb_su("cat /sys/block/mmcblk0/queue/read_ahead_kb"))
print("nomerges:", adb_su("cat /sys/block/mmcblk0/queue/nomerges"))
print("rq_affinity:", adb_su("cat /sys/block/mmcblk0/queue/rq_affinity"))
print("add_random:", adb_su("cat /sys/block/mmcblk0/queue/add_random"))
print("iostats:", adb_su("cat /sys/block/mmcblk0/queue/iostats"))

print("\n=== UFS / INTERNAL STORAGE (mmcblk0 or sda?) ===")
print(adb_su("ls -d /sys/block/*"))
