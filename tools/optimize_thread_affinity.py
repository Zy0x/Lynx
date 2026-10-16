import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

genshin_pid = adb_su("pidof com.miHoYo.GenshinImpact").split()[0]
print(f"Genshin PID: {genshin_pid}")

# 1. Apply Storage & Loading Boost
adb_su("echo 1024 > /sys/block/mmcblk0/queue/read_ahead_kb")
adb_su("echo 256 > /sys/block/mmcblk0/queue/nr_requests")
adb_su("echo 20 > /proc/sys/vm/vfs_cache_pressure") # Cache aggressive
adb_su("echo 15 > /proc/sys/vm/swappiness")         # Reduce ZRAM churn
adb_su(f"ionice -c 1 -n 0 -p {genshin_pid} 2>/dev/null")

# 2. Lock GPU to OPP 0 (920 MHz) & Boost Level 2
adb_su("echo 0 > /sys/kernel/ged/hal/custom_boost_gpu_freq")
adb_su("echo 0 > /sys/kernel/ged/hal/custom_upbound_gpu_freq")
adb_su("echo 2 > /sys/kernel/ged/hal/gpu_boost_level")
adb_su("echo 25 > /sys/kernel/ged/hal/dvfs_margin_value")

# 3. CPU Core Panning:
# Separate Unity Main / Gfx onto Big Cores (6,7 = mask 0xc0),
# Audio/Jobs onto all cores (mask 0xff)
res = adb_su(f"""
for t in /proc/{genshin_pid}/task/*; do
    comm=$(cat $t/comm 2>/dev/null)
    tid=$(basename $t)
    case "$comm" in
        UnityMain|*Gfx*|*Render*)
            taskset -p c0 $tid >/dev/null 2>&1
            renice -n -20 -p $tid >/dev/null 2>&1
            echo "PINNED_BIG: $tid ($comm)"
            ;;
        *fmod*|*Worker*|*Job*)
            taskset -p ff $tid >/dev/null 2>&1
            renice -n -15 -p $tid >/dev/null 2>&1
            ;;
    esac
done
""")
print(res)

print("\n=== VERIFICATION ===")
print("GPU Freq:", adb_su("cat /sys/kernel/ged/hal/current_freqency"))
print("GPU Load:", adb_su("cat /sys/kernel/ged/hal/gpu_utilization"))
print("mmcblk0 RA:", adb_su("cat /sys/block/mmcblk0/queue/read_ahead_kb"))
print("VFS Cache Pressure:", adb_su("cat /proc/sys/vm/vfs_cache_pressure"))
print("Swappiness:", adb_su("cat /proc/sys/vm/swappiness"))
