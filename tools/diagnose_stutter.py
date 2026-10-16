import subprocess
import time

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== CPU FREQUENCIES (LIVE) ===")
print("Little (0-5):", adb_su("cat /sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq"), "Gov:", adb_su("cat /sys/devices/system/cpu/cpufreq/policy0/scaling_governor"))
print("Big (6-7):   ", adb_su("cat /sys/devices/system/cpu/cpufreq/policy6/scaling_cur_freq"), "Gov:", adb_su("cat /sys/devices/system/cpu/cpufreq/policy6/scaling_governor"))

print("\n=== CORES ONLINE ===")
print("Online:", adb_su("cat /sys/devices/system/cpu/online"))

print("\n=== GPU LIVE CLOCK & UTILIZATION ===")
print("Freq:", adb_su("cat /sys/kernel/ged/hal/current_freqency"))
print("Load:", adb_su("cat /sys/kernel/ged/hal/gpu_utilization"))

print("\n=== MEMORY & KSWAPD CPU ACTIVITY ===")
print(adb_su("ps -eo pid,tid,comm,%cpu,%mem | grep -E 'kswapd|kcompact|lmkd|miHoYo' | head -n 15"))

print("\n=== GENSHIN CPUSET ASSIGNMENT ===")
genshin_pid = adb_su("pidof com.miHoYo.GenshinImpact")
print(f"Genshin PID: {genshin_pid}")
if genshin_pid:
    first_pid = genshin_pid.split()[0]
    print("cpuset:", adb_su(f"cat /proc/{first_pid}/cpuset"))
    print("oom_score_adj:", adb_su(f"cat /proc/{first_pid}/oom_score_adj"))

print("\n=== THERMAL SENSORS ===")
print(adb_su("cat /sys/class/thermal/thermal_zone*/temp | head -n 6"))
