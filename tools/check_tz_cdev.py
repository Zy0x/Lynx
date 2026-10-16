import subprocess

target = "192.168.1.13:5555"
script = r'''
for tz in /sys/class/thermal/thermal_zone*; do
    tz_name=$(basename "$tz")
    tz_type=$(cat "$tz/type" 2>/dev/null)
    for c in "$tz"/cdev*; do
        if [ -d "$c" ]; then
            c_name=$(basename "$c")
            c_type=$(cat "$c/type" 2>/dev/null)
            c_target=$(readlink -f "$c" 2>/dev/null)
            echo "$tz_name ($tz_type) -> $c_name ($c_type) target: $c_target"
        fi
    done
done
'''
p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
