import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== 1. LIVE CPU FREQUENCIES ===")
for c in range(8):
    freq = adb_su(f"cat /sys/devices/system/cpu/cpu{c}/cpufreq/scaling_cur_freq 2>/dev/null")
    gov = adb_su(f"cat /sys/devices/system/cpu/cpu{c}/cpufreq/scaling_governor 2>/dev/null")
    print(f"Core {c}: {int(freq)//1000 if freq.isdigit() else freq} MHz ({gov})")

print("\n=== 2. LIVE GPU FREQ & GOVERNOR ===")
print("Current GPU Freq:", adb_su("cat /sys/kernel/ged/hal/current_freqency"))
print("GPU Utilization :", adb_su("cat /sys/kernel/ged/hal/gpu_utilization"))
print("DVFS Loading Mode:", adb_su("cat /sys/kernel/ged/hal/dvfs_loading_mode"))

print("\n=== 3. ZRAM & SWAP ACTIVITY ===")
print("Swappiness:", adb_su("cat /proc/sys/vm/swappiness"))
print("ZRAM stat :", adb_su("cat /sys/block/zram0/mm_stat 2>/dev/null"))
print("Free RAM  :", adb_su("cat /proc/meminfo | grep -E 'MemAvailable|MemFree|Cached|SwapFree'"))

print("\n=== 4. THERMAL & THROTTLING STATUS ===")
print("Thermal zones:", adb_su("cat /sys/class/thermal/thermal_zone*/temp | head -n 4"))
print("Thermal mode :", adb_su("cat /sys/class/thermal/thermal_zone*/mode 2>/dev/null | head -n 4"))

print("\n=== 5. RECENT LOGCAT STUTTER / SURFACEFLINGER DROP WARNINGS ===")
print(adb_su("logcat -d -t 30 | grep -E 'SurfaceFlinger|Choreographer|Skipped|dropped|ged|fpsgo' | tail -n 15"))
