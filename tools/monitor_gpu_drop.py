import subprocess
import time

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("Current values:")
print("  custom_boost_gpu_freq:", adb_su("cat /sys/kernel/ged/hal/custom_boost_gpu_freq"))
print("  custom_upbound_gpu_freq:", adb_su("cat /sys/kernel/ged/hal/custom_upbound_gpu_freq"))
print("  current_freqency:", adb_su("cat /sys/kernel/ged/hal/current_freqency"))

print("\nMonitoring for 10 seconds to see if it changes automatically...")
for i in range(10):
    time.sleep(1)
    b = adb_su("cat /sys/kernel/ged/hal/custom_boost_gpu_freq")
    u = adb_su("cat /sys/kernel/ged/hal/custom_upbound_gpu_freq")
    f = adb_su("cat /sys/kernel/ged/hal/current_freqency")
    print(f"  [{i+1}s] boost={b}, upbound={u}, freq={f}")
