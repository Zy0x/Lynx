import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("Testing perf lock acquire via power_native_test_v_1_0...")
# command 8: perf lock acquire (duration_ms, cmd, param)
# 8 60000 0x00410000 1 (60 seconds, CCI freq, param 1)
out = adb_su("/vendor/bin/hw/power_native_test_v_1_0 8 60000 0x00410000 1")
print("Output:", out)
print("CCI Mode:", adb_su("cat /proc/cpufreq/cpufreq_cci_mode"))
