import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c \"{cmd}\""], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== MOUNT ENTRY FOR /data/sdext2 ===")
print(adb_su("mount | grep sdext2"))

print("\n=== MMCBLK0 QUEUE TUNABLES ===")
queue_path = "/sys/block/mmcblk0/queue"
for node in ["scheduler", "read_ahead_kb", "nr_requests", "nomerges", "rq_affinity", "add_random", "iostats"]:
    val = adb_su(f"cat {queue_path}/{node} 2>/dev/null")
    print(f"{node}: {val}")

print("\n=== CHECK AVAILABLE SCHEDULERS ON MMCBLK0 ===")
print("Schedulers:", adb_su("cat /sys/block/mmcblk0/queue/scheduler"))

print("\n=== CHECK MMC BLK BDI ===")
print(adb_su("for b in /sys/class/bdi/*; do echo $(basename $b): $(cat $b/read_ahead_kb 2>/dev/null); done | grep -E '179|254|8:'"))

print("\n=== GENSHIN THREADS IN STATE D ===")
pid = adb_su("pidof com.miHoYo.GenshinImpact").split()[0]
print(adb_su(f"for t in /proc/{pid}/task/*; do if [ -f $t/stat ]; then s=$(awk '{{print $3}}' $t/stat); if [ \"$s\" = \"D\" ]; then echo \"TID $(basename $t): $(cat $t/comm) is in STATE D (I/O wait)\"; fi; fi; done"))
