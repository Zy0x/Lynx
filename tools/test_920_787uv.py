import subprocess
import sys
import time

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"
LAYER = "SurfaceView[com.miHoYo.GenshinImpact/com.miHoYo.GetMobileInfo.MainActivity](BLAST)#191"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("==========================================================")
print("  TESTING 920 MHz WITH DEEPER UNDERVOLT: 787.5 mV (78750) ")
print("==========================================================")

# Clear dmesg to catch any gpu fault
adb_su("dmesg -c > /dev/null")

# Apply 920 MHz @ 78750 uV (-62.5 mV UV vs Stock 850 mV)
adb_su("echo '920000 78750' > /proc/gpufreq/gpufreq_fixed_freq_volt")
time.sleep(1)

var_dump = adb_su("cat /proc/gpufreq/gpufreq_var_dump | head -n 3")
print("GPU Status:\n", var_dump)

# Check dmesg
dmesg_chk = adb_su("dmesg | grep -iE 'mali.*fault|gpu.*reset|gpufreq.*fail|buck' | tail -n 5")
print("Hardware Voltage/GPU Check:", dmesg_chk if dmesg_chk else "CLEAN - 787.5 mV is rock solid!")

# Measure live latency
print("\nMeasuring SurfaceFlinger Latency...")
res = subprocess.run(['adb', '-s', TARGET, 'shell', f"dumpsys SurfaceFlinger --latency '{LAYER}'"], capture_output=True, text=True, timeout=5)
lines = [l for l in res.stdout.strip().split('\n') if l]
if len(lines) > 2:
    frames = []
    for l in lines[1:]:
        parts = l.split()
        if len(parts) == 3:
            t1 = int(parts[1])
            if t1 > 0 and t1 != 0x7fffffffffffffff:
                frames.append(t1)
    if len(frames) >= 15:
        recent = frames[-45:]
        intervals = [(recent[i] - recent[i-1]) / 1e6 for i in range(1, len(recent))]
        valid = [ft for ft in intervals if 10 <= ft <= 120]
        if valid:
            avg_ft = sum(valid) / len(valid)
            fps = 1000.0 / avg_ft
            max_ft = max(valid)
            min_ft = min(valid)
            janks = [ft for ft in valid if ft > (avg_ft * 1.35)]
            print(f"  FPS       : {fps:.1f} FPS")
            print(f"  FrameTime : {avg_ft:.2f} ms (Min: {min_ft:.1f}, Max: {max_ft:.1f} ms)")
            print(f"  Janks     : {len(janks)}/{len(valid)} ({len(janks)/len(valid)*100:.1f}%)")

cpu_t = adb_su("cat /sys/class/thermal/thermal_zone3/temp 2>/dev/null")
bat_t = adb_su("cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null")
print(f"  Thermals  : CPU {int(cpu_t)/1000:.1f}°C | Battery {int(bat_t)/1000:.1f}°C")
