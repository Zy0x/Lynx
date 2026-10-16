import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== PPM USER LIMITS DETAILS ===")
print("hard_userlimit:\n", adb_su("cat /proc/ppm/policy/hard_userlimit_cpu_freq 2>/dev/null"))
print("userlimit:\n", adb_su("cat /proc/ppm/policy/userlimit_cpu_freq 2>/dev/null"))

print("\n=== DVFSRC / VCORE PLATFORM NODES ===")
print(adb_su("find /sys/devices/platform -name '*dvfsrc*' -o -name '*vcore*' 2>/dev/null"))

print("\n=== THERMAL ZONES LIST ===")
print(adb_su("for z in /sys/class/thermal/thermal_zone*; do echo \"$(basename $z): type=$(cat $z/type 2>/dev/null), mode=$(cat $z/mode 2>/dev/null), temp=$(cat $z/temp 2>/dev/null)\"; done | head -n 16"))
