import subprocess

target = "192.168.1.13:5555"
script = r'''
for f in /proc/driver/thermal/tzbts /proc/driver/thermal/tzbts_param /proc/driver/thermal/tzcharger /proc/driver/thermal/sspm_thermal_throttle; do
    echo "=== $f ==="
    cat "$f" 2>/dev/null
done
'''
p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
