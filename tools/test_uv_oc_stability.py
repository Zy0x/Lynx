import subprocess
import sys
import time

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"
LAYER = "SurfaceView[com.miHoYo.GenshinImpact/com.miHoYo.GetMobileInfo.MainActivity](BLAST)#9681"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("==================================================")
print("  TESTING 950 MHz OVERCLOCK + 825 mV UNDERVOLT    ")
print("==================================================")

# Apply 950 MHz + 82500 uV (0.825V)
adb_su("echo '950000 82500' > /proc/gpufreq/gpufreq_fixed_freq_volt")
time.sleep(1)

var_dump = adb_su("cat /proc/gpufreq/gpufreq_var_dump | head -n 3")
print("GPU State:\n", var_dump)

print("\nMonitoring SurfaceFlinger latency for 10 seconds...")
frames_data = []
for i in range(5):
    time.sleep(2)
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
            recent = frames[-40:]
            intervals = [(recent[j] - recent[j-1]) / 1e6 for j in range(1, len(recent))]
            valid = [ft for ft in intervals if 10 <= ft <= 120]
            if valid:
                avg_ft = sum(valid) / len(valid)
                fps = 1000.0 / avg_ft
                max_ft = max(valid)
                janks = [ft for ft in valid if ft > (avg_ft * 1.35)]
                print(f"  Step {i+1}: {fps:.1f} FPS | Avg FT: {avg_ft:.2f} ms | Max FT: {max_ft:.2f} ms | Janks: {len(janks)}/{len(valid)}")

# Check for GPU errors in dmesg
gpu_err = adb_su("dmesg | grep -iE 'mali.*fault|gpu.*reset|gpufreq.*fail' | tail -n 5")
print("\nGPU Error Check:", gpu_err if gpu_err else "CLEAN - No GPU faults detected!")
