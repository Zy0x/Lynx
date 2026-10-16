import subprocess
import time
import json
import sys
import re

TARGET = "192.168.1.13:5555"

def adb_cmd(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', cmd], capture_output=True, text=True, timeout=15)
    return res.stdout.strip()

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=15)
    return res.stdout.strip()

def capture_screen(filename):
    res = subprocess.run(['adb', '-s', TARGET, 'exec-out', 'screencap', '-p'], capture_output=True)
    if res.returncode == 0 and len(res.stdout) > 1000:
        with open(filename, 'wb') as f:
            f.write(res.stdout)
        return True
    return False

def get_telemetry():
    t = {}
    # CPU freqs
    try:
        t['cpu_little_mhz'] = int(adb_su("cat /sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq")) // 1000
        t['cpu_big_mhz'] = int(adb_su("cat /sys/devices/system/cpu/cpufreq/policy6/scaling_cur_freq")) // 1000
    except Exception:
        pass
    
    # GPU freq & util
    try:
        gpu_freq_raw = adb_su("cat /sys/kernel/ged/hal/current_freqency")
        parts = gpu_freq_raw.split()
        if len(parts) >= 2:
            t['gpu_idx'] = int(parts[0])
            t['gpu_mhz'] = int(parts[1]) // 1000
    except Exception:
        pass
        
    try:
        gpu_util_raw = adb_su("cat /sys/kernel/ged/hal/gpu_utilization")
        parts = gpu_util_raw.split()
        if len(parts) >= 3:
            t['gpu_util_pct'] = int(parts[0])
    except Exception:
        pass

    # FPSGO status
    try:
        fps_raw = adb_su("cat /sys/kernel/fpsgo/fstb/fpsgo_status")
        for line in fps_raw.splitlines():
            if 'com.miHoYo' in line or 'Genshin' in line or 'Unity' in line:
                cols = line.split()
                if len(cols) >= 5:
                    t['current_fps'] = cols[3]
                    t['target_fps'] = cols[4]
    except Exception:
        pass

    # Battery & Thermal temp
    try:
        t['bat_temp_c'] = int(adb_su("cat /sys/class/power_supply/battery/temp 2>/dev/null || cat /sys/devices/platform/charger/Battery_Temperature")) / 10.0
    except Exception:
        pass

    return t

if __name__ == '__main__':
    print("Testing telemetry reader...")
    print(json.dumps(get_telemetry(), indent=2))
