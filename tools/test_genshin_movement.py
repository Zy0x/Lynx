import subprocess
import time
import json
import sys
import numpy as np

TARGET = "192.168.1.13:5555"
subprocess.run(['adb', 'connect', TARGET], capture_output=True)

def adb(cmd):
    return subprocess.run(['adb', '-s', TARGET, 'shell'] + cmd, capture_output=True, text=True, timeout=20).stdout

def adb_su(cmd):
    p = subprocess.Popen(['adb', '-s', TARGET, 'shell', 'su'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    out, _ = p.communicate(cmd + "\n")
    return out.strip()

def get_blast_layer():
    layers = adb_su("dumpsys SurfaceFlinger --list").splitlines()
    for l in layers:
        if "com.miHoYo.GenshinImpact" in l and "(BLAST)" in l:
            return l.strip()
    return None

def sample_latency(layer_name):
    presents = set()
    raw = adb_su(f'dumpsys SurfaceFlinger --latency "{layer_name}"')
    for line in raw.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 3:
            try:
                p = int(parts[1])
                if 0 < p < 0x7ffffffffffffff0:
                    presents.add(p)
            except ValueError:
                pass
    return presents

def run_test_trial(label, duration_sec=20):
    print(f"\n==========================================")
    print(f"  STARTING TRIAL: {label}")
    print(f"==========================================")
    
    layer_name = get_blast_layer()
    print(f"Target BLAST Layer: {layer_name}")
    
    # Pre-trial hardware telemetry
    t_start = time.time()
    cpu_big_before = adb_su("cat /sys/devices/system/cpu/cpufreq/policy6/scaling_cur_freq")
    gpu_before = adb_su("cat /sys/kernel/ged/hal/current_freqency")
    temp_before = adb_su("cat /sys/class/power_supply/battery/temp 2>/dev/null || cat /sys/devices/platform/charger/Battery_Temperature")
    print(f"Pre-trial: CPU Big={int(cpu_big_before)//1000}MHz, GPU={gpu_before}, Temp={int(temp_before)/10.0}C")
    
    # 2. Movement loop with periodic latency sampling
    screenshot_taken = False
    samples_gpu = []
    samples_cpu = []
    all_presents = set()
    fpsgo_samples = []
    
    loop_count = 0
    while time.time() - t_start < duration_sec:
        loop_count += 1
        # Move forward (swipe joystick)
        adb(["input", "swipe", "370", "750", "370", "480", "1200"])
        
        # Attack tap x2
        adb(["input", "tap", "2020", "760"])
        time.sleep(0.1)
        adb(["input", "tap", "2020", "760"])
        
        # Camera rotation (pan around)
        adb(["input", "swipe", "1800", "550", "1200", "550", "600"])
        
        # Jump or dash
        adb(["input", "tap", "2210", "650"])
        
        # Sample ring buffer every loop
        if layer_name:
            all_presents.update(sample_latency(layer_name))
            
        # Sample hardware
        try:
            g = adb_su("cat /sys/kernel/ged/hal/current_freqency").split()
            c = adb_su("cat /sys/devices/system/cpu/cpufreq/policy6/scaling_cur_freq")
            if len(g) >= 2:
                samples_gpu.append(int(g[1]) // 1000)
            samples_cpu.append(int(c) // 1000)
            
            fpsgo_raw = adb_su("cat /sys/kernel/fpsgo/fstb/fpsgo_status 2>/dev/null")
            for line in fpsgo_raw.splitlines():
                if "com.miHoYo" in line or "Genshin" in line or "Unity" in line:
                    cols = line.split()
                    if len(cols) >= 5:
                        fpsgo_samples.append(float(cols[3]))
        except Exception:
            pass
            
        # Take screenshot midway
        if not screenshot_taken and (time.time() - t_start > 8):
            res = subprocess.run(['adb', '-s', TARGET, 'exec-out', 'screencap', '-p'], capture_output=True)
            with open(f"tools/trial_{label.lower().replace(' ', '_')}.png", "wb") as f:
                f.write(res.stdout)
            screenshot_taken = True
            print(f"Captured movement screenshot: tools/trial_{label.lower().replace(' ', '_')}.png")
            
    # Final sample
    if layer_name:
        all_presents.update(sample_latency(layer_name))
        
    # Sort and compute frametimes
    sorted_presents = sorted(list(all_presents))
    frametimes_ms = []
    for i in range(1, len(sorted_presents)):
        diff_ms = (sorted_presents[i] - sorted_presents[i-1]) / 1_000_000.0
        if 1.0 < diff_ms < 500.0:
            frametimes_ms.append(diff_ms)

    temp_after = adb_su("cat /sys/class/power_supply/battery/temp 2>/dev/null || cat /sys/devices/platform/charger/Battery_Temperature")
    
    results = {
        "label": label,
        "duration_sec": duration_sec,
        "total_frames_sampled": len(frametimes_ms),
        "fpsgo_fps_avg": round(float(np.mean(fpsgo_samples)), 1) if fpsgo_samples else 0,
        "sf_avg_fps": round(1000.0 / float(np.mean(frametimes_ms)), 2) if frametimes_ms else 0,
        "frametime_avg_ms": round(float(np.mean(frametimes_ms)), 2) if frametimes_ms else 0,
        "frametime_p50_ms": round(float(np.percentile(frametimes_ms, 50)), 2) if frametimes_ms else 0,
        "frametime_p90_ms": round(float(np.percentile(frametimes_ms, 90)), 2) if frametimes_ms else 0,
        "frametime_p95_ms": round(float(np.percentile(frametimes_ms, 95)), 2) if frametimes_ms else 0,
        "frametime_p99_ms": round(float(np.percentile(frametimes_ms, 99)), 2) if frametimes_ms else 0,
        "jank_frames_count": sum(1 for f in frametimes_ms if f > 33.3),
        "jank_percentage": round(sum(1 for f in frametimes_ms if f > 33.3) / len(frametimes_ms) * 100, 2) if frametimes_ms else 0,
        "gpu_mhz_avg": round(float(np.mean(samples_gpu)), 1) if samples_gpu else 0,
        "cpu_big_mhz_avg": round(float(np.mean(samples_cpu)), 1) if samples_cpu else 0,
        "temp_start_c": int(temp_before) / 10.0,
        "temp_end_c": int(temp_after) / 10.0,
    }
    
    print("\n--- RESULTS ---")
    print(json.dumps(results, indent=2))
    return results

if __name__ == '__main__':
    label = sys.argv[1] if len(sys.argv) > 1 else "trial_1"
    run_test_trial(label)
