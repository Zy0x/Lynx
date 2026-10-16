import subprocess
import sys
import time

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    p = subprocess.Popen(['adb', '-s', TARGET, 'shell', 'su'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    out, _ = p.communicate(cmd + "\n")
    return out.strip()

subprocess.run(['adb', 'connect', TARGET], capture_output=True)

print("==========================================================")
print("  LYNX UNIVERSAL - PURE EXTREME GAMING MONITOR           ")
print("==========================================================")

# Wait for Genshin
print("Waiting for Genshin Impact to be active...")
genshin_layer = None
for _ in range(30):
    top = adb_su("dumpsys activity activities | grep -m1 topResumedActivity")
    if "GenshinImpact" in top:
        print(" [OK] Genshin Impact detected in foreground!")
        break
    time.sleep(1)

# Find SurfaceFlinger layer
layers = adb_su("dumpsys SurfaceFlinger --list | grep -i genshin").splitlines()
for l in layers:
    if "(BLAST)" in l:
        genshin_layer = l.strip()
        break
if not genshin_layer:
    for l in layers:
        if "SurfaceView[" in l and not "Background" in l:
            genshin_layer = l.strip()
            break
if not genshin_layer and layers:
    genshin_layer = layers[0].strip()

print(f"Target Render Layer: {genshin_layer if genshin_layer else 'Default SurfaceView'}")

print("\n--- Starting Sustained Telemetry Sampling (10 cycles) ---")
fps_list = []
frametime_list = []
jank_counts = 0
total_frames = 0

for step in range(10):
    time.sleep(2)
    # 1. Thermals
    batt_temp = adb_su("cat /sys/class/power_supply/battery/temp 2>/dev/null")
    try:
        batt_c = float(batt_temp) / 10.0 if float(batt_temp) > 100 else float(batt_temp)
    except:
        batt_c = 0.0

    # 2. Clocks
    gpu_opp = adb_su("cat /proc/gpufreq/gpufreq_opp_freq 2>/dev/null | grep -E '^\\[[0-9]+\\]'")
    c0 = adb_su("cat /sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq 2>/dev/null")
    c6 = adb_su("cat /sys/devices/system/cpu/cpufreq/policy6/scaling_cur_freq 2>/dev/null")
    gpu_load = adb_su("cat /sys/kernel/ged/hal/gpu_utilization 2>/dev/null")

    # 3. SurfaceFlinger Frametimes
    step_fps = 0.0
    step_ft = 0.0
    step_janks = 0
    if genshin_layer:
        res = adb_su(f"dumpsys SurfaceFlinger --latency '{genshin_layer}'")
        lines = [l for l in res.splitlines() if l]
        if len(lines) > 2:
            frames = []
            for l in lines[1:]:
                parts = l.split()
                if len(parts) == 3:
                    try:
                        t1 = int(parts[1])
                        if t1 > 0 and t1 != 0x7fffffffffffffff:
                            frames.append(t1)
                    except:
                        pass
            if len(frames) >= 15:
                recent = frames[-35:]
                intervals = [(recent[j] - recent[j-1]) / 1e6 for j in range(1, len(recent))]
                valid = [ft for ft in intervals if 8.0 <= ft <= 120.0]
                if valid:
                    step_ft = sum(valid) / len(valid)
                    step_fps = 1000.0 / step_ft
                    janks = [ft for ft in valid if ft > (step_ft * 1.35)]
                    step_janks = len(janks)
                    fps_list.append(step_fps)
                    frametime_list.append(step_ft)
                    jank_counts += step_janks
                    total_frames += len(valid)

    c0_mhz = int(c0)//1000 if c0.isdigit() else 0
    c6_mhz = int(c6)//1000 if c6.isdigit() else 0
    gpu_str = gpu_opp.split(',')[0].strip() if gpu_opp else "Dynamic"

    print(f"[{step+1:02d}/10] FPS: {step_fps:5.1f} | FT: {step_ft:5.2f} ms | Temp: {batt_c:.1f}°C | CPU: {c0_mhz}/{c6_mhz} MHz | GPU: {gpu_str} ({gpu_load}%)")

# Final summary
print("\n==========================================================")
print("  PURE LYNX EXTREME BENCHMARK RESULTS                     ")
print("==========================================================")
if fps_list:
    avg_fps = sum(fps_list) / len(fps_list)
    avg_ft = sum(frametime_list) / len(frametime_list)
    jank_rate = (jank_counts / total_frames * 100.0) if total_frames > 0 else 0.0
    print(f" • Average FPS        : {avg_fps:.2f} FPS")
    print(f" • Average Frametime  : {avg_ft:.2f} ms")
    print(f" • Jank Frame Ratio   : {jank_rate:.1f}% ({jank_counts}/{total_frames} frames)")
else:
    print(" • No render frames captured yet (game may still be loading).")

dmesg_err = adb_su("dmesg | grep -iE 'mali.*fault|gpu.*reset|kernel panic' | tail -n 5")
print(f" • Hardware Stability : {'Clean (0 Kernel / GPU Faults)' if not dmesg_err else 'Fault Detected: ' + dmesg_err}")
print("==========================================================")
