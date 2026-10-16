import subprocess
import sys
import time

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def get_active_layer():
    res = subprocess.run(['adb', '-s', TARGET, 'shell', "dumpsys SurfaceFlinger --list | grep 'SurfaceView.*BLAST'"], capture_output=True, text=True)
    lines = [l.strip() for l in res.stdout.strip().split('\n') if l.strip()]
    return lines[-1] if lines else None

layer = get_active_layer()

print(f"Sampling 3 consecutive windows on active layer: {layer}")
for step in range(3):
    time.sleep(3)
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
                print(f"  Window {step+1}: {fps:.1f} FPS | FT: {avg_ft:.2f} ms (Min: {min_ft:.1f}, Max: {max_ft:.1f} ms) | Janks: {len(janks)}/{len(valid)} ({len(janks)/len(valid)*100:.1f}%)")

cpu_t = subprocess.run(['adb', '-s', TARGET, 'shell', "su -c 'cat /sys/class/thermal/thermal_zone3/temp'"], capture_output=True, text=True).stdout.strip()
bat_t = subprocess.run(['adb', '-s', TARGET, 'shell', "su -c 'cat /sys/class/thermal/thermal_zone0/temp'"], capture_output=True, text=True).stdout.strip()
print(f"\nThermals: CPU {int(cpu_t)/1000:.1f}°C | Battery {int(bat_t)/1000:.1f}°C")
