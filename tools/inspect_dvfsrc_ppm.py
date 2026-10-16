import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

dvfsrc = "/sys/devices/platform/10012000.dvfsrc/helio-dvfsrc"
print("=== DVFSRC OPP TABLE ===")
print("num_opps:", adb_su(f"cat {dvfsrc}/dvfsrc_num_opps 2>/dev/null"))
print("opp_table:\n", adb_su(f"cat {dvfsrc}/dvfsrc_opp_table 2>/dev/null"))
print("buck_vcore_voltage:", adb_su("cat /sys/devices/platform/1000d000.pwrap/1000d000.pwrap:mt6358-pmic/mt-pmic/buck_vcore_voltage 2>/dev/null"))

print("\n=== LOCK PPM TO INDEX 0 (ABSOLUTE OC/PEAK FREQ) ===")
print("Before:", adb_su("cat /proc/ppm/policy/hard_userlimit_cpu_freq"))
adb_su("echo '0 0' > /proc/ppm/policy/hard_userlimit_cpu_freq")
adb_su("echo '1 0' > /proc/ppm/policy/hard_userlimit_cpu_freq")
print("After:", adb_su("cat /proc/ppm/policy/hard_userlimit_cpu_freq"))
