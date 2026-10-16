import subprocess
import time

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=15)
    return res.stdout.strip()

print("==================================================")
print("  APPLYING EXTREME ANTI-STUTTER & FAST-LOAD V2    ")
print("==================================================")

# 1. STORAGE: MicroSD & UFS Queue Optimization
print("[1/5] Configuring Storage Queues (MicroSD & UFS)...")
sd_commands = """
# Set Deadline Scheduler on MicroSD (mmcblk0)
echo deadline > /sys/block/mmcblk0/queue/scheduler 2>/dev/null
echo 1024 > /sys/block/mmcblk0/queue/read_ahead_kb 2>/dev/null
echo 512 > /sys/block/mmcblk0/queue/nr_requests 2>/dev/null
echo 0 > /sys/block/mmcblk0/queue/add_random 2>/dev/null
echo 2 > /sys/block/mmcblk0/queue/rq_affinity 2>/dev/null
echo 0 > /sys/block/mmcblk0/queue/nomerges 2>/dev/null

# Tune Deadline Scheduler for zero read latency
echo 100 > /sys/block/mmcblk0/queue/iosched/read_expire 2>/dev/null
echo 4000 > /sys/block/mmcblk0/queue/iosched/write_expire 2>/dev/null
echo 4 > /sys/block/mmcblk0/queue/iosched/writes_starved 2>/dev/null
echo 32 > /sys/block/mmcblk0/queue/iosched/fifo_batch 2>/dev/null
echo 1 > /sys/block/mmcblk0/queue/iosched/front_merges 2>/dev/null

# Also optimize UFS Internal storage
for d in sda sdb sdc; do
    echo deadline > /sys/block/$d/queue/scheduler 2>/dev/null
    echo 1024 > /sys/block/$d/queue/read_ahead_kb 2>/dev/null
    echo 512 > /sys/block/$d/queue/nr_requests 2>/dev/null
    echo 2 > /sys/block/$d/queue/rq_affinity 2>/dev/null
done
"""
adb_su(sd_commands)
print("  -> MicroSD Scheduler:", adb_su("cat /sys/block/mmcblk0/queue/scheduler"))
print("  -> MicroSD Read-Ahead:", adb_su("cat /sys/block/mmcblk0/queue/read_ahead_kb"), "KB")
print("  -> MicroSD read_expire:", adb_su("cat /sys/block/mmcblk0/queue/iosched/read_expire 2>/dev/null"), "ms")

# 2. VM: Anti-Stutter Dirty Writeback & Pagecache Retention
print("\n[2/5] Tuning VM Pagecache & Writeback Pacing...")
vm_commands = """
# Prevent periodic micro-stutters from frequent disk flushes
echo 1500 > /proc/sys/vm/dirty_writeback_centisecs
echo 3000 > /proc/sys/vm/dirty_expire_centisecs
echo 10 > /proc/sys/vm/dirty_background_ratio
echo 20 > /proc/sys/vm/dirty_ratio
echo 0 > /proc/sys/vm/dirty_background_bytes
echo 0 > /proc/sys/vm/dirty_bytes

# Retain game assets in RAM (low cache pressure)
echo 15 > /proc/sys/vm/vfs_cache_pressure
echo 20 > /proc/sys/vm/swappiness
echo 32768 > /proc/sys/vm/extra_free_kbytes
"""
adb_su(vm_commands)
print("  -> dirty_writeback_centisecs:", adb_su("cat /proc/sys/vm/dirty_writeback_centisecs"))
print("  -> vfs_cache_pressure:", adb_su("cat /proc/sys/vm/vfs_cache_pressure"))
print("  -> swappiness:", adb_su("cat /proc/sys/vm/swappiness"))

# 3. CPU, BUS, CCI & GED GPU
print("\n[3/5] Tuning CPU, CCI Interconnect & GPU...")
hw_commands = """
# Lock CCI interconnect to Performance (reduces inter-core latency)
echo 1 > /proc/cpufreq/cpufreq_cci_mode

# Lock MTK CPU Power Mode to Sports/Performance
echo 3 > /proc/cpufreq/cpufreq_power_mode

# Set performance governor on all 8 cores
for i in 0 1 2 3 4 5 6 7; do
    echo performance > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor 2>/dev/null
done

# Lock Mali GPU to Peak OPP 0 (920 MHz) & Boost Level 2
echo 0 > /sys/kernel/ged/hal/custom_boost_gpu_freq
echo 0 > /sys/kernel/ged/hal/custom_upbound_gpu_freq
echo 2 > /sys/kernel/ged/hal/gpu_boost_level
echo 25 > /sys/kernel/ged/hal/dvfs_margin_value

# Boost scheduler & cpuset
echo 0-7 > /dev/cpuset/top-app/cpus
echo 100 > /dev/stune/top-app/schedtune.boost 2>/dev/null
echo 1 > /dev/stune/top-app/schedtune.sched_boost 2>/dev/null
"""
adb_su(hw_commands)
print("  -> CCI Mode:", adb_su("cat /proc/cpufreq/cpufreq_cci_mode"))
print("  -> GPU Freq:", adb_su("cat /sys/kernel/ged/hal/current_freqency"))
print("  -> GPU Boost Level:", adb_su("cat /sys/kernel/ged/hal/gpu_boost_level"))

# 4. COMPOSITOR & SURFACEFLINGER
print("\n[4/5] Tuning SurfaceFlinger & Frame Pacing...")
sf_commands = """
setprop debug.sf.latch_unsignaled 1
setprop debug.sf.early_phase_offset_ns 500000
setprop debug.sf.early_app_phase_offset_ns 500000
setprop debug.sf.early_gl_phase_offset_ns 3000000
setprop debug.sf.early_gl_app_phase_offset_ns 15000000
"""
adb_su(sf_commands)
print("  -> latch_unsignaled:", adb_su("getprop debug.sf.latch_unsignaled"))

# 5. GENSHIN SPECIFIC THREAD TUNING & IONICE
print("\n[5/5] Optimizing Genshin Impact Threads & I/O Priority...")
genshin_pid = adb_su("pidof com.miHoYo.GenshinImpact").split()
if genshin_pid:
    pid = genshin_pid[0]
    print(f"  Target PID: {pid}")
    
    # Real-time I/O priority for main process
    adb_su(f"ionice -c 1 -n 0 -p {pid} 2>/dev/null")
    
    # Fast bash thread assignment
    thread_tune_cmd = f"""
    for t in /proc/{pid}/task/*; do
        tid=$(basename $t)
        comm=$(cat $t/comm 2>/dev/null)
        case "$comm" in
            UnityMain)
                # Pin to Big Cores (6,7 = 0xc0) with highest priority
                taskset -p c0 $tid >/dev/null 2>&1
                renice -n -20 -p $tid >/dev/null 2>&1
                echo "  [OPTIMIZED] UnityMain ($tid): Pinned Cores 6,7 (c0), nice -20"
                ;;
            UnityGfxDeviceW)
                # Allow Cores 4,5,6,7 (0xf0) so Gfx never waits on Big cores if busy
                taskset -p f0 $tid >/dev/null 2>&1
                renice -n -20 -p $tid >/dev/null 2>&1
                echo "  [OPTIMIZED] UnityGfxDeviceW ($tid): Pinned Cores 4-7 (f0), nice -20"
                ;;
            UnityMultiRende)
                # Pin to Cores 2,3,4,5 (0x3c)
                taskset -p 3c $tid >/dev/null 2>&1
                renice -n -18 -p $tid >/dev/null 2>&1
                echo "  [OPTIMIZED] UnityMultiRende ($tid): Pinned Cores 2-5 (3c), nice -18"
                ;;
            *Load*|*AsyncRead*|*MHYLoad*)
                # Asset loaders: Pin Cores 0-5 (0x3f), Real-time I/O priority
                taskset -p 3f $tid >/dev/null 2>&1
                renice -n -10 -p $tid >/dev/null 2>&1
                ionice -c 1 -n 0 -p $tid >/dev/null 2>&1
                echo "  [OPTIMIZED] Asset Loader $comm ($tid): RT I/O, nice -10"
                ;;
            *IOThread*|*fmod*|*Audio*)
                # Audio/IO: Pin Cores 0-3 (0x0f), nice -12
                taskset -p 0f $tid >/dev/null 2>&1
                renice -n -12 -p $tid >/dev/null 2>&1
                ionice -c 1 -n 0 -p $tid >/dev/null 2>&1
                echo "  [OPTIMIZED] Audio/IO $comm ($tid): RT I/O, nice -12"
                ;;
            *Worker*)
                # Worker threads: All cores
                taskset -p ff $tid >/dev/null 2>&1
                renice -n -15 -p $tid >/dev/null 2>&1
                ;;
        esac
    done
    """
    res = adb_su(thread_tune_cmd)
    print(res)

print("\n>>> ALL EXTREME TWEAKS SUCCESSFULLY APPLIED! <<<")
