import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("CPU Frequencies:")
for i in range(8):
    cur = adb_su(f"cat /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_cur_freq")
    gov = adb_su(f"cat /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_governor")
    min_f = adb_su(f"cat /sys/devices/system/cpu/cpu{i}/cpufreq/scaling_min_freq")
    print(f"  cpu{i}: cur={cur} kHz | min={min_f} kHz | gov={gov}")

print("\nGPU Status:")
print("  Freq:", adb_su("cat /sys/kernel/ged/hal/current_freqency"))
print("  Load:", adb_su("cat /sys/kernel/ged/hal/gpu_utilization"))
print("  Boost Level:", adb_su("cat /sys/kernel/ged/hal/gpu_boost_level"))

print("\nDVFSRC Status:")
print("  Dump:\n", adb_su("cat /sys/devices/platform/10012000.dvfsrc/helio-dvfsrc/dvfsrc_dump | head -n 4"))
