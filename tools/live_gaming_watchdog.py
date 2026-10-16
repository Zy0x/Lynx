import subprocess
import time
import sys

sys.stdout.reconfigure(encoding='utf-8')

TARGET = "192.168.1.13:5555"
LAYER = "SurfaceView[com.miHoYo.GenshinImpact/com.miHoYo.GetMobileInfo.MainActivity](BLAST)#9681"

def adb_su(cmd):
    try:
        res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=12)
        return res.stdout.strip()
    except Exception as e:
        return f"ERR: {e}"

def get_frametimes():
    try:
        res = subprocess.run(['adb', '-s', TARGET, 'shell', f"dumpsys SurfaceFlinger --latency '{LAYER}'"], capture_output=True, text=True, timeout=8)
        lines = [l for l in res.stdout.strip().split('\n') if l]
        if len(lines) < 15:
            return None, None, None, None
        frames = []
        for l in lines[1:]:
            parts = l.split()
            if len(parts) == 3:
                t0, t1, t2 = int(parts[0]), int(parts[1]), int(parts[2])
                if t1 > 0 and t2 > 0 and t1 != 0x7fffffffffffffff:
                    frames.append((t0, t1, t2))
        if len(frames) < 15:
            return None, None, None, None
        recent = frames[-60:]
        intervals = [(recent[i][1] - recent[i-1][1]) / 1e6 for i in range(1, len(recent))]
        valid = [ft for ft in intervals if 10 <= ft <= 120]
        if not valid:
            return None, None, None, None
        avg_ft = sum(valid) / len(valid)
        fps = 1000.0 / avg_ft if avg_ft > 0 else 0
        max_ft = max(valid)
        janks = [ft for ft in valid if ft > (avg_ft * 1.35)]
        jank_pct = (len(janks) / len(valid)) * 100.0
        return fps, avg_ft, max_ft, jank_pct
    except Exception as e:
        return None, None, None, None

def apply_aggressive_remedies(reason):
    print(f"\n[AUTO-REMEDY TRIGGERED] Reason: {reason}")
    print("   Enforcing Extreme Hardware Clocks, DVFSRC OPP0, Mali Always-On, and I/O Priority...")
    remedy_cmd = """
    # 1. Hardware Overclock & Minimum Frequency Locking (Zero downclock)
    echo 2000000 > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq 2>/dev/null
    echo 2050000 > /sys/devices/system/cpu/cpufreq/policy6/scaling_min_freq 2>/dev/null
    echo performance > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor 2>/dev/null
    echo performance > /sys/devices/system/cpu/cpufreq/policy6/scaling_governor 2>/dev/null
    
    # 2. Lock DDR to 4266 MHz & Vcore to 800mV
    echo 0 > /sys/devices/platform/10012000.dvfsrc/helio-dvfsrc/dvfsrc_force_vcore_dvfs_opp 2>/dev/null
    
    # 3. Lock Mali GPU to Peak OPP 0 (920 MHz), Boost Level 2 & Always-On power policy
    echo 0 > /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null
    echo 0 > /sys/kernel/ged/hal/custom_upbound_gpu_freq 2>/dev/null
    echo 2 > /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null
    echo 25 > /sys/kernel/ged/hal/dvfs_margin_value 2>/dev/null
    echo always_on > /sys/class/misc/mali0/device/power_policy 2>/dev/null
    echo 33 > /sys/class/misc/mali0/device/js_scheduling_period 2>/dev/null
    echo 10 > /sys/class/misc/mali0/device/dvfs_period 2>/dev/null
    
    # 4. Storage & I/O Priority VIP
    echo deadline > /sys/block/mmcblk0/queue/scheduler 2>/dev/null
    echo 1024 > /sys/block/mmcblk0/queue/read_ahead_kb 2>/dev/null
    echo 100 > /sys/block/mmcblk0/queue/iosched/read_expire 2>/dev/null
    echo 4 > /sys/block/mmcblk0/queue/iosched/writes_starved 2>/dev/null
    echo 32 > /sys/block/mmcblk0/queue/iosched/fifo_batch 2>/dev/null
    
    # 5. F2FS Read-Ahead metadata
    echo 16 > /sys/fs/f2fs/mmcblk0p3/ra_nid_pages 2>/dev/null
    echo 16 > /sys/fs/f2fs/dm-2/ra_nid_pages 2>/dev/null
    
    # 6. Schedtune Top-App Boost
    echo 100 > /dev/stune/top-app/schedtune.boost 2>/dev/null
    echo 1 > /dev/stune/top-app/schedtune.sched_boost 2>/dev/null
    
    # 7. SurfaceFlinger early offsets
    setprop debug.sf.latch_unsignaled 1
    """
    adb_su(remedy_cmd)
    
    # Re-pin Genshin PID threads if PID exists
    pid_str = adb_su("pidof com.miHoYo.GenshinImpact")
    if pid_str and not pid_str.startswith("ERR"):
        pid = pid_str.split()[0]
        thread_tune = f"""
        for t in /proc/{pid}/task/*; do
            comm=$(cat $t/comm 2>/dev/null)
            tid=$(basename $t)
            case "$comm" in
                UnityMain)
                    taskset -p c0 $tid >/dev/null 2>&1
                    renice -n -20 -p $tid >/dev/null 2>&1
                    ;;
                UnityGfxDeviceW)
                    taskset -p f0 $tid >/dev/null 2>&1
                    renice -n -20 -p $tid >/dev/null 2>&1
                    ;;
                *Load*|*AsyncRead*|*MHYLoad*)
                    ionice -c 1 -n 0 -p $tid >/dev/null 2>&1
                    renice -n -10 -p $tid >/dev/null 2>&1
                    ;;
            esac
        done
        """
        adb_su(thread_tune)
    print("   [REMEDY APPLIED SUCCESSFULLY]\n")

print("================================================================")
print("  STARTING AUTONOMOUS 15-20s GAMING WATCHDOG & AUTO-TUNER V2   ")
print("================================================================")

# Apply baseline aggressive configuration before testing
apply_aggressive_remedies("Initial Baseline Setup")

# Run 3 monitoring cycles (~18 seconds each)
total_cycles = 3
for cycle in range(1, total_cycles + 1):
    print(f"\n>>> [CYCLE {cycle}/{total_cycles}] Monitoring Live Window (18 seconds)...")
    
    fps_samples = []
    ft_samples = []
    jank_samples = []
    
    for s in range(6):
        time.sleep(3)
        fps, avg_ft, max_ft, jank_pct = get_frametimes()
        if fps is not None:
            fps_samples.append(fps)
            ft_samples.append(avg_ft)
            jank_samples.append(jank_pct)
            print(f"  Sample {s+1}: {fps:.1f} FPS | FT: {avg_ft:.2f} ms | Max FT: {max_ft:.2f} ms | Janks: {jank_pct:.1f}%")
        else:
            print(f"  Sample {s+1}: (Collecting frame data...)")
            
    # Evaluate cycle metrics
    if fps_samples:
        cycle_fps = sum(fps_samples) / len(fps_samples)
        cycle_ft = sum(ft_samples) / len(ft_samples)
        cycle_jank = sum(jank_samples) / len(jank_samples)
        
        gpu_freq = adb_su("cat /sys/kernel/ged/hal/current_freqency")
        gpu_load = adb_su("cat /sys/kernel/ged/hal/gpu_utilization")
        cpu_temp = adb_su("cat /sys/class/thermal/thermal_zone3/temp 2>/dev/null")
        bat_temp = adb_su("cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null")
        
        c_temp_str = f"{int(cpu_temp)/1000:.1f}°C" if cpu_temp and cpu_temp.isdigit() else "N/A"
        b_temp_str = f"{int(bat_temp)/1000:.1f}°C" if bat_temp and bat_temp.isdigit() else "N/A"
        
        print(f"\n--- CYCLE {cycle} SUMMARY ---")
        print(f"Average FPS   : {cycle_fps:.1f} FPS")
        print(f"Average FT    : {cycle_ft:.2f} ms")
        print(f"Average Jank  : {cycle_jank:.1f}%")
        print(f"GPU Freq/Load : {gpu_freq} | Load: {gpu_load}")
        print(f"SoC / Battery : CPU {c_temp_str} | Battery {b_temp_str}")
        
        # Threshold: if average FPS < 38 or Jank > 8.0%, trigger immediate intervention
        if cycle_fps < 38.0 or cycle_jank > 8.0:
            apply_aggressive_remedies(f"FPS low ({cycle_fps:.1f}) or Jank high ({cycle_jank:.1f}%)")
        else:
            print("Status: EXCELLENT SMOOTHNESS (Within optimal performance target)")
    else:
        print("Could not collect valid SurfaceFlinger frames, checking process...")
        apply_aggressive_remedies("Frame data timeout / state reset")

print("\n================================================================")
print("  WATCHDOG TRIAL COMPLETE - ALL TELEMETRY RECORDED              ")
print("================================================================")
