import subprocess
import time

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=20)
    return res.stdout.strip()

target_file = "/data/sdext2/MountX/Android/data/com.miHoYo.GenshinImpact/files/ctable.dat"

def test_config(name, sched, ra_kb, setup_cmds=""):
    print(f"\n--- Testing {name}: sched={sched}, ra={ra_kb} ---")
    adb_su(f"echo {sched} > /sys/block/mmcblk0/queue/scheduler")
    adb_su(f"echo {ra_kb} > /sys/block/mmcblk0/queue/read_ahead_kb")
    if setup_cmds:
        adb_su(setup_cmds)
    
    times = []
    for i in range(3):
        # Drop pagecache for true cold physical read
        adb_su("sync; echo 3 > /proc/sys/vm/drop_caches")
        time.sleep(0.5)
        # Time the read
        out = adb_su(f"time dd if={target_file} of=/dev/null bs=1M 2>&1")
        # Extract real time
        for line in out.split('\n'):
            if 'real' in line or 'records' in line or 'bytes' in line:
                print(f"  Run {i+1}: {line}")
            if '0m' in line:
                # time format 0m2.84s
                try:
                    s_part = line.split('0m')[1].replace('s', '').strip()
                    times.append(float(s_part))
                except:
                    pass
    if times:
        avg = sum(times) / len(times)
        print(f"  => AVERAGE TIME: {avg:.3f} s")
        return avg
    return None

test_config("Config 1 (Previous: noop + 256KB)", "noop", 256)
test_config("Config 2 (noop + 1024KB)", "noop", 1024)
deadline_setup = "echo 150 > /sys/block/mmcblk0/queue/iosched/read_expire; echo 4 > /sys/block/mmcblk0/queue/iosched/writes_starved; echo 32 > /sys/block/mmcblk0/queue/iosched/fifo_batch"
test_config("Config 3 (deadline tuned + 1024KB)", "deadline", 1024, deadline_setup)
test_config("Config 4 (deadline tuned + 2048KB)", "deadline", 2048, deadline_setup)
