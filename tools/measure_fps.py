import sys
import subprocess
import re

def main():
    target = "192.168.1.13:5555"
    if len(sys.argv) > 1:
        target = sys.argv[1]

    # Dynamically find the active Genshin BLAST layer
    try:
        layers = subprocess.check_output(["adb", "-s", target, "shell", "dumpsys SurfaceFlinger --list"]).decode("utf-8", errors="ignore").splitlines()
        target_layer = None
        for l in layers:
            if "SurfaceView[com.miHoYo.GenshinImpact" in l and "(BLAST)" in l:
                target_layer = l.strip()
                break
        if not target_layer:
            for l in layers:
                if "com.miHoYo.GenshinImpact" in l and "SurfaceView" in l:
                    target_layer = l.strip()
                    break
        if not target_layer:
            target_layer = "SurfaceView[com.miHoYo.GenshinImpact/com.miHoYo.GetMobileInfo.MainActivity](BLAST)#604"
    except Exception as e:
        target_layer = "SurfaceView[com.miHoYo.GenshinImpact/com.miHoYo.GetMobileInfo.MainActivity](BLAST)#604"

    cmd = ["adb", "-s", target, "shell", f"su -c 'dumpsys SurfaceFlinger --latency \"{target_layer}\"'"]
    try:
        raw = subprocess.check_output(cmd, timeout=5).decode("utf-8", errors="ignore").strip().splitlines()
    except Exception as e:
        print(f"Error fetching latency: {e}")
        return

    if len(raw) < 2:
        print("Not enough latency data.")
        return

    nanos = []
    for line in raw[1:]:
        parts = line.split()
        if len(parts) == 3 and parts[1].isdigit():
            val = int(parts[1])
            if val > 0:
                nanos.append(val)

    if len(nanos) < 5:
        print(f"Only {len(nanos)} valid frames.")
        return

    diffs = [(nanos[i] - nanos[i-1]) / 1e6 for i in range(1, len(nanos))]
    active_diffs = [d for d in diffs if 0 < d < 120]
    if not active_diffs:
        print("No active frames in range.")
        return

    avg_ft = sum(active_diffs) / len(active_diffs)
    fps = 1000.0 / avg_ft
    janks = sum(1 for d in active_diffs if d > 25.0)
    jank_rate = (janks / len(active_diffs)) * 100

    # Also check thermal & battery
    try:
        temp_raw = subprocess.check_output(["adb", "-s", target, "shell", "su -c 'cat /sys/devices/virtual/thermal/thermal_zone0/temp'"]).decode().strip()
        temp_c = int(temp_raw) / 1000.0 if temp_raw.isdigit() else 0.0
    except:
        temp_c = 0.0

    try:
        gpu_stat = subprocess.check_output(["adb", "-s", target, "shell", "su -c 'cat /proc/gpufreq/gpufreq_opp_freq'"]).decode().strip()
    except:
        gpu_stat = "unknown"

    print(f"=== METRICS ({len(active_diffs)} frames) ===")
    print(f"FPS: {fps:.2f} | Avg FrameTime: {avg_ft:.2f} ms")
    print(f"Jank Rate: {jank_rate:.1f}% ({janks}/{len(active_diffs)} frames > 25ms)")
    print(f"Thermal Zone 0: {temp_c:.1f}°C")
    print(f"GPU State: {gpu_stat}")

if __name__ == "__main__":
    main()
