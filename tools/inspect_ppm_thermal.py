import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== MTK PPM POLICIES ===")
print("ppm policy status:\n", adb_su("cat /proc/ppm/policy_status 2>/dev/null"))
print("ppm mode:\n", adb_su("cat /proc/ppm/mode 2>/dev/null"))
print("ppm root_cluster:\n", adb_su("cat /proc/ppm/root_cluster 2>/dev/null"))

print("\n=== MTK CPUFREQ / DVFS ===")
print("cpufreq power mode:", adb_su("cat /proc/cpufreq/cpufreq_power_mode 2>/dev/null"))
print("cpufreq cci mode:", adb_su("cat /proc/cpufreq/cpufreq_cci_mode 2>/dev/null"))
print("cpufreq sched disable:", adb_su("cat /proc/cpufreq/cpufreq_sched_disable 2>/dev/null"))

print("\n=== THERMAL THROTTLING POLICY ===")
print(adb_su("for z in /sys/class/thermal/thermal_zone*; do echo \"$(cat $z/type 2>/dev/null): mode=$(cat $z/mode 2>/dev/null) policy=$(cat $z/policy 2>/dev/null)\"; done | head -n 15"))
