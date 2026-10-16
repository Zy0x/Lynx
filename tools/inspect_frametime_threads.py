import subprocess
import time

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c \"{cmd}\""], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== 1. FPS & FRAMETIME FROM SURFACEFLINGER ===")
res = subprocess.run(['adb', '-s', TARGET, 'shell', 'dumpsys SurfaceFlinger --latency com.miHoYo.GenshinImpact/com.miHoYo.GetMobileInfo.MainActivity'], capture_output=True, text=True)
lines = [l for l in res.stdout.strip().split('\n') if l]
if len(lines) > 2:
    refresh_period = int(lines[0])
    print(f"Refresh period: {refresh_period} ns ({1e9/refresh_period:.1f} Hz)")
    frames = []
    for l in lines[1:]:
        parts = l.split()
        if len(parts) == 3:
            t0, t1, t2 = int(parts[0]), int(parts[1]), int(parts[2])
            if t1 > 0 and t2 > 0:
                frames.append((t0, t1, t2))
    print(f"Recorded {len(frames)} valid frames")
    if len(frames) >= 10:
        recent = frames[-30:]
        intervals = [(recent[i][1] - recent[i-1][1]) / 1e6 for i in range(1, len(recent))]
        avg_ft = sum(intervals) / len(intervals)
        fps = 1000.0 / avg_ft if avg_ft > 0 else 0
        max_ft = max(intervals)
        min_ft = min(intervals)
        print(f"Recent 30 frames: Avg FT = {avg_ft:.2f} ms ({fps:.1f} FPS), Min FT = {min_ft:.2f} ms, Max FT (Jank) = {max_ft:.2f} ms")
        janks = [ft for ft in intervals if ft > (avg_ft * 1.5)]
        print(f"Jank count (>1.5x avg): {len(janks)} out of {len(intervals)}")

print("\n=== 2. MALI GPU DETAILED STATS ===")
print("Mali power status:", adb_su("cat /sys/class/misc/mali0/device/power_policy 2>/dev/null"))
print("Mali dvfs:", adb_su("cat /sys/class/misc/mali0/device/dvfs 2>/dev/null"))
print("GED fps:", adb_su("cat /sys/kernel/ged/hal/fps 2>/dev/null || cat /proc/ged 2>/dev/null"))
print("GED vsync:", adb_su("cat /sys/kernel/ged/hal/vsync_head 2>/dev/null"))

print("\n=== 3. TOP CPU THREADS OF GENSHIN ===")
pid = adb_su("pidof com.miHoYo.GenshinImpact").split()[0]
top_res = adb_su(f"top -H -b -n 1 -p {pid} | head -n 25")
print(top_res)
