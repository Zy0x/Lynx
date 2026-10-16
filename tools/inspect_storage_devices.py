import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c \"{cmd}\""], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== MOUNT POINTS ===")
print(adb_su("df -h"))

print("\n=== UFS STORAGE QUEUE (sda) ===")
print("scheduler sda:", adb_su("cat /sys/block/sda/queue/scheduler 2>/dev/null"))
print("read_ahead_kb sda:", adb_su("cat /sys/block/sda/queue/read_ahead_kb 2>/dev/null"))
print("nr_requests sda:", adb_su("cat /sys/block/sda/queue/nr_requests 2>/dev/null"))
print("iostats sda:", adb_su("cat /sys/block/sda/queue/iostats 2>/dev/null"))

print("\n=== UFS STORAGE QUEUE (sdb, sdc) ===")
print("read_ahead_kb sdb:", adb_su("cat /sys/block/sdb/queue/read_ahead_kb 2>/dev/null"))
print("read_ahead_kb sdc:", adb_su("cat /sys/block/sdc/queue/read_ahead_kb 2>/dev/null"))

print("\n=== MICROSD QUEUE (mmcblk0) ===")
print("scheduler mmcblk0:", adb_su("cat /sys/block/mmcblk0/queue/scheduler 2>/dev/null"))
print("read_ahead_kb mmcblk0:", adb_su("cat /sys/block/mmcblk0/queue/read_ahead_kb 2>/dev/null"))
print("nr_requests mmcblk0:", adb_su("cat /sys/block/mmcblk0/queue/nr_requests 2>/dev/null"))
print("iostats mmcblk0:", adb_su("cat /sys/block/mmcblk0/queue/iostats 2>/dev/null"))
