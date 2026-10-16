import subprocess
import time

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=20)
    return res.stdout.strip()

test_file = "/storage/emulated/0/Android/data/com.miHoYo.GenshinImpact/files/ctable.dat"

for ra in [256, 512, 1024, 2048, 4096]:
    adb_su(f"echo {ra} > /sys/block/mmcblk0/queue/read_ahead_kb")
    adb_su("echo 3 > /proc/sys/vm/drop_caches")
    time.sleep(0.5)
    t0 = time.time()
    out = adb_su(f"dd if={test_file} of=/dev/null bs=1M 2>&1")
    dt = time.time() - t0
    # 28 MB file
    speed = 28.16 / dt if dt > 0 else 0
    print(f"read_ahead_kb = {ra:4d} KB -> time: {dt:.3f}s (Throughput: {speed:.1f} MB/s)")
