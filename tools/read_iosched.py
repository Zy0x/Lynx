import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

for node in ["fifo_batch", "front_merges", "read_expire", "write_expire", "writes_starved"]:
    val = adb_su(f"cat /sys/block/mmcblk0/queue/iosched/{node}")
    print(f"{node}: {val}")
