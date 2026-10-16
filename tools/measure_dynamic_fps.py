import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def get_active_layer():
    res = subprocess.run(['adb', '-s', TARGET, 'shell', "dumpsys SurfaceFlinger --list | grep 'SurfaceView.*BLAST'"], capture_output=True, text=True)
    lines = [l.strip() for l in res.stdout.strip().split('\n') if l.strip()]
    return lines[-1] if lines else None

layer = get_active_layer()
print(f"Active BLAST Layer: {layer}")

if layer:
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"dumpsys SurfaceFlinger --latency '{layer}'"], capture_output=True, text=True)
    lines = [l for l in res.stdout.strip().split('\n') if l]
    if len(lines) > 2:
        frames = []
        for l in lines[1:]:
            parts = l.split()
            if len(parts) == 3:
                t1 = int(parts[1])
                if t1 > 0 and t1 != 0x7fffffffffffffff:
                    frames.append(t1)
        print(f"Total valid frames: {len(frames)}")
        if len(frames) >= 10:
            recent = frames[-50:]
            intervals = [(recent[i] - recent[i-1]) / 1e6 for i in range(1, len(recent))]
            valid = [ft for ft in intervals if 10 <= ft <= 120]
            if valid:
                avg_ft = sum(valid) / len(valid)
                fps = 1000.0 / avg_ft
                max_ft = max(valid)
                min_ft = min(valid)
                janks = [ft for ft in valid if ft > (avg_ft * 1.35)]
                print(f"\n==========================================")
                print(f"  LIVE BENCHMARK ON 950 MHz OC + 825 mV UV")
                print(f"==========================================")
                print(f"FPS               : {fps:.1f} FPS")
                print(f"FrameTime         : {avg_ft:.2f} ms (Min: {min_ft:.2f} ms, Max: {max_ft:.2f} ms)")
                print(f"Jank Rate         : {len(janks)/len(valid)*100:.1f}% ({len(janks)}/{len(valid)})")
                
# Check GPU State
var_dump = subprocess.run(['adb', '-s', TARGET, 'shell', "su -c 'cat /proc/gpufreq/gpufreq_var_dump | head -n 3'"], capture_output=True, text=True).stdout.strip()
print("\nGPU Var Dump:\n", var_dump)
