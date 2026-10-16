import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== LIVE SPIRAL ABYSS TELEMETRY ===")
print("GPU Freq:", adb_su("cat /sys/kernel/ged/hal/current_freqency"))
print("GPU Load:", adb_su("cat /sys/kernel/ged/hal/gpu_utilization"))

# SurfaceFlinger latency
res = subprocess.run(['adb', '-s', TARGET, 'shell', 'dumpsys SurfaceFlinger --latency com.miHoYo.GenshinImpact/com.miHoYo.GetMobileInfo.MainActivity'], capture_output=True, text=True)
lines = [l for l in res.stdout.strip().split('\n') if l]
if len(lines) > 2:
    refresh_period = int(lines[0])
    frames = []
    for l in lines[1:]:
        parts = l.split()
        if len(parts) == 3:
            t0, t1, t2 = int(parts[0]), int(parts[1]), int(parts[2])
            if t1 > 0 and t2 > 0:
                frames.append((t0, t1, t2))
    if len(frames) >= 15:
        recent = frames[-60:]
        intervals = [(recent[i][1] - recent[i-1][1]) / 1e6 for i in range(1, len(recent))]
        # filter out invalid spikes like alt-tab
        intervals = [ft for ft in intervals if 5 < ft < 100]
        if intervals:
            avg_ft = sum(intervals) / len(intervals)
            fps = 1000.0 / avg_ft if avg_ft > 0 else 0
            max_ft = max(intervals)
            min_ft = min(intervals)
            janks = [ft for ft in intervals if ft > (avg_ft * 1.5)]
            print(f"FPS: {fps:.1f} FPS (Avg FT: {avg_ft:.2f} ms)")
            print(f"Frametime range: {min_ft:.1f} ms - {max_ft:.1f} ms")
            print(f"Janks (>1.5x avg): {len(janks)} out of {len(intervals)} frames ({len(janks)/len(intervals)*100:.1f}%)")
