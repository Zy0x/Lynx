import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== 1. CPU FREQS & POLICIES ===")
for p in ["policy0", "policy6"]:
    avail = adb_su(f"cat /sys/devices/system/cpu/cpufreq/{p}/scaling_available_frequencies 2>/dev/null")
    cur = adb_su(f"cat /sys/devices/system/cpu/cpufreq/{p}/scaling_cur_freq 2>/dev/null")
    max_f = adb_su(f"cat /sys/devices/system/cpu/cpufreq/{p}/scaling_max_freq 2>/dev/null")
    min_f = adb_su(f"cat /sys/devices/system/cpu/cpufreq/{p}/scaling_min_freq 2>/dev/null")
    print(f"{p}: cur={cur}, min={min_f}, max={max_f}\n  avail: {avail}")

print("\n=== 2. MTK PPM LIMITS ===")
print("ppm user_limit:", adb_su("cat /proc/ppm/policy/userlimit_cpu_freq 2>/dev/null || cat /proc/ppm/policy/hard_userlimit_cpu_freq 2>/dev/null"))

print("\n=== 3. GPU OPP & FREQS ===")
print("ged freq levels:", adb_su("cat /sys/kernel/ged/hal/total_gpu_freq_level_count 2>/dev/null"))
print("mali opp table:", adb_su("cat /sys/class/misc/mali0/device/opp_table 2>/dev/null || cat /sys/kernel/ged/hal/gpu_freq_table 2>/dev/null"))
print("mali available freqs:", adb_su("cat /sys/class/misc/mali0/device/devfreq/13040000.mali/available_frequencies 2>/dev/null || cat /sys/class/devfreq/*mali*/available_frequencies 2>/dev/null"))

print("\n=== 4. DVFSRC DRAM & VCORE ===")
print("dvfsrc vcore:", adb_su("cat /sys/kernel/helio-dvfsrc/dvfsrc_force_vcore_dvfs 2>/dev/null"))
print("dvfsrc opp:", adb_su("cat /sys/kernel/helio-dvfsrc/dvfsrc_opp_table 2>/dev/null"))

print("\n=== 5. THERMAL OVERRIDE / EMULATION ===")
print("thermal zones emulation:", adb_su("ls /sys/class/thermal/thermal_zone*/emul_temp 2>/dev/null | head -n 5"))
print("thermal mode:", adb_su("for z in 0 1 2 3 4; do echo -n \"tz$z: $(cat /sys/class/thermal/thermal_zone$z/mode 2>/dev/null) | \"; done"))
