import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
script = """
for tz in /sys/class/thermal/thermal_zone*; do
    if [ -f "$tz/type" ]; then
        echo "$(basename $tz) - $(cat $tz/type): $(cat $tz/temp)"
    fi
done
"""
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", f"su -c '{script}'"]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
