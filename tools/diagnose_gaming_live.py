import subprocess
import json

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    try:
        res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=15)
        return res.stdout.strip()
    except Exception as e:
        return f"ERR: {e}"

print("=== 1. CPU CLOCKS & GOVERNORS ===")
cmd_cpu = """
for i in 0 1 2 3 4 5 6 7; do
    cur=$(cat /sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq 2>/dev/null)
    gov=$(cat /sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor 2>/dev/null)
    echo -n "cpu$i: ${cur}kHz ($gov) | "
done
echo ""
"""
print(adb_su(cmd_cpu))

print("=== 2. THERMAL SENSORS & THROTTLING ===")
cmd_thermal = """
for t in /sys/class/thermal/thermal_zone*; do
    if [ -f "$t/type" ]; then
        typ=$(cat $t/type)
        temp=$(cat $t/temp 2>/dev/null)
        case "$typ" in
            *cpu*|*soc*|*battery*|*gpu*|*skin*|*ts*)
                echo "$typ: $temp"
                ;;
        esac
    fi
done
"""
print(adb_su(cmd_thermal))

print("=== 3. GPU STATUS & GED ===")
print("GPU Freq:", adb_su("cat /sys/kernel/ged/hal/current_freqency 2>/dev/null"))
print("GPU Load:", adb_su("cat /sys/kernel/ged/hal/gpu_utilization 2>/dev/null"))
print("GED Boost Level:", adb_su("cat /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null"))
print("GED Boost Freq Index:", adb_su("cat /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null"))
print("GED Upbound Freq Index:", adb_su("cat /sys/kernel/ged/hal/custom_upbound_gpu_freq 2>/dev/null"))

print("=== 4. STORAGE / MMC QUEUE ===")
print("Scheduler mmcblk0:", adb_su("cat /sys/block/mmcblk0/queue/scheduler 2>/dev/null"))
print("Read Ahead mmcblk0:", adb_su("cat /sys/block/mmcblk0/queue/read_ahead_kb 2>/dev/null"))
print("NR Requests mmcblk0:", adb_su("cat /sys/block/mmcblk0/queue/nr_requests 2>/dev/null"))
print("Scheduler mmcblk0boot0:", adb_su("cat /sys/block/mmcblk0boot0/queue/scheduler 2>/dev/null"))
print("Read Ahead mmcblk0boot0:", adb_su("cat /sys/block/mmcblk0boot0/queue/read_ahead_kb 2>/dev/null"))
print("VFS Cache Pressure:", adb_su("cat /proc/sys/vm/vfs_cache_pressure"))
print("Dirty Ratio:", adb_su("cat /proc/sys/vm/dirty_ratio"))
print("Dirty Background Ratio:", adb_su("cat /proc/sys/vm/dirty_background_ratio"))

print("=== 5. GENSHIN PID & THREAD ANALYSIS ===")
pid = adb_su("pidof com.miHoYo.GenshinImpact")
print("Genshin PID:", pid)
if pid:
    p = pid.split()[0]
    print(adb_su(f"ps -T -p {p} -o TID,COMM,PRI,NI,CPUS,%CPU | head -n 30"))

print("=== 6. CPUSET & SCHEDTUNE ===")
print("top-app cpus:", adb_su("cat /dev/cpuset/top-app/cpus 2>/dev/null"))
print("foreground cpus:", adb_su("cat /dev/cpuset/foreground/cpus 2>/dev/null"))
print("schedtune top-app boost:", adb_su("cat /dev/stune/top-app/schedtune.boost 2>/dev/null || cat /dev/cpuctl/top-app/cpu.uclamp.min 2>/dev/null"))
