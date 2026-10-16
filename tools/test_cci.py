import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("Current cci_mode:", adb_su("cat /proc/cpufreq/cpufreq_cci_mode"))
adb_su("echo 1 > /proc/cpufreq/cpufreq_cci_mode")
print("After echo 1:", adb_su("cat /proc/cpufreq/cpufreq_cci_mode"))

print("\n=== DRAM / EMI BUS FREQUENCY ===")
print(adb_su("cat /sys/kernel/helio-dvfsrc/dvfsrc_force_vcore_dvfs 2>/dev/null || cat /sys/devices/platform/10012000.dvfsrc/helio-dvfsrc/dvfsrc_force_vcore_dvfs 2>/dev/null"))
print("dvfsrc dump:", adb_su("cat /sys/kernel/helio-dvfsrc/dvfsrc_dump 2>/dev/null || cat /sys/devices/platform/10012000.dvfsrc/helio-dvfsrc/dvfsrc_dump 2>/dev/null | head -n 15"))

print("\n=== MTK GED / FPSGO ===")
print("ged fpsgo:", adb_su("cat /sys/kernel/ged/hal/fpsgo_enable 2>/dev/null || cat /sys/module/ged/parameters/ged_smart_boost 2>/dev/null"))
print("ged gx_force_cpu_boost:", adb_su("cat /sys/kernel/ged/hal/gx_force_cpu_boost 2>/dev/null"))
print("ged gx_top_app_pid:", adb_su("cat /sys/kernel/ged/hal/gx_top_app_pid 2>/dev/null"))
print("ged gx_game_mode:", adb_su("cat /sys/kernel/ged/hal/gx_game_mode 2>/dev/null"))
