import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

dvfsrc = "/sys/devices/platform/10012000.dvfsrc/helio-dvfsrc"

print("1. Testing dvfsrc_force_vcore_dvfs_opp...")
adb_su(f"echo 0 > {dvfsrc}/dvfsrc_force_vcore_dvfs_opp")
print("dvfsrc dump:\n", adb_su(f"cat {dvfsrc}/dvfsrc_dump | head -n 4"))

print("\n2. Testing PPM hard_userlimit...")
# In MTK PPM, format is: echo <cluster> <freq_khz> or echo <cluster> <min_idx> <max_idx>
adb_su("echo '0 0 0' > /proc/ppm/policy/hard_userlimit_cpu_freq 2>/dev/null")
adb_su("echo '1 0 0' > /proc/ppm/policy/hard_userlimit_cpu_freq 2>/dev/null")
print("hard_userlimit:", adb_su("cat /proc/ppm/policy/hard_userlimit_cpu_freq"))

print("\n3. Testing scaling_min_freq...")
adb_su("echo 2000000 > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq")
adb_su("echo 2050000 > /sys/devices/system/cpu/cpufreq/policy6/scaling_min_freq")
print("policy0 min:", adb_su("cat /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq"))
print("policy6 min:", adb_su("cat /sys/devices/system/cpu/cpufreq/policy6/scaling_min_freq"))
