import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"
LAYER = "SurfaceView[com.miHoYo.GenshinImpact/com.miHoYo.GetMobileInfo.MainActivity](BLAST)#9681"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

res = subprocess.run(['adb', '-s', TARGET, 'shell', f"dumpsys SurfaceFlinger --latency '{LAYER}'"], capture_output=True, text=True, timeout=8)
lines = [l for l in res.stdout.strip().split('\n') if l]
if len(lines) > 2:
    frames = []
    for l in lines[1:]:
        parts = l.split()
        if len(parts) == 3:
            t0, t1, t2 = int(parts[0]), int(parts[1]), int(parts[2])
            if t1 > 0 and t2 > 0 and t1 != 0x7fffffffffffffff:
                frames.append((t0, t1, t2))
    if len(frames) >= 15:
        recent = frames[-60:]
        intervals = [(recent[i][1] - recent[i-1][1]) / 1e6 for i in range(1, len(recent))]
        valid = [ft for ft in intervals if 10 <= ft <= 120]
        if valid:
            avg_ft = sum(valid) / len(valid)
            fps = 1000.0 / avg_ft if avg_ft > 0 else 0
            max_ft = max(valid)
            min_ft = min(valid)
            janks = [ft for ft in valid if ft > (avg_ft * 1.35)]
            jank_pct = (len(janks) / len(valid)) * 100.0
            print(f"Current Status:")
            print(f"  FPS       : {fps:.1f} FPS")
            print(f"  FrameTime : {avg_ft:.2f} ms (Min: {min_ft:.2f} ms, Max: {max_ft:.2f} ms)")
            print(f"  Jank Rate : {jank_pct:.1f}% ({len(janks)}/{len(valid)})")
            print(f"  GPU Freq  : {adb_su('cat /sys/kernel/ged/hal/current_freqency')}")
            print(f"  GPU Load  : {adb_su('cat /sys/kernel/ged/hal/gpu_utilization')}")
            print(f"  CPU Temps : CPU {int(adb_su('cat /sys/class/thermal/thermal_zone3/temp'))/1000:.1f}°C | Bat {int(adb_su('cat /sys/class/thermal/thermal_zone0/temp'))/1000:.1f}°C")
