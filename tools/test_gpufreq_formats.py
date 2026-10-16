import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

# Clear dmesg first
adb_su("dmesg -c > /dev/null")

# Test format 1: echo 920000 85000 95000
adb_su("echo '920000 85000 95000' > /proc/gpufreq/gpufreq_fixed_freq_volt 2>&1")
out1 = adb_su("cat /proc/gpufreq/gpufreq_fixed_freq_volt")
dmesg1 = adb_su("dmesg")
print("Test 1 (920000 85000 95000):")
print("  Status:", out1)
if dmesg1:
    print("  Dmesg:", dmesg1)

# Test format 2: echo 920000 80000 (undervolt to 800mV!)
adb_su("dmesg -c > /dev/null")
adb_su("echo '920000 80000' > /proc/gpufreq/gpufreq_fixed_freq_volt 2>&1")
out2 = adb_su("cat /proc/gpufreq/gpufreq_fixed_freq_volt")
dmesg2 = adb_su("dmesg")
print("\nTest 2 (920000 80000):")
print("  Status:", out2)
if dmesg2:
    print("  Dmesg:", dmesg2)

# Test format 3: echo 0 (OPP 0) to gpufreq_opp_freq
adb_su("dmesg -c > /dev/null")
adb_su("echo '1 0' > /proc/gpufreq/gpufreq_opp_freq 2>&1")
out3 = adb_su("cat /proc/gpufreq/gpufreq_opp_freq")
dmesg3 = adb_su("dmesg")
print("\nTest 3 ('1 0' to gpufreq_opp_freq):")
print("  Status:", out3)
if dmesg3:
    print("  Dmesg:", dmesg3)
