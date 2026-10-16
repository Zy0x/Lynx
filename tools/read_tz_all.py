import subprocess

target = "192.168.1.13:5555"
script = '''
for tz in /sys/class/thermal/thermal_zone*; do
    if [ -f "$tz/type" ]; then
        echo "$(cat $tz/type): $(cat $tz/temp) mC ($(cat $tz/mode 2>/dev/null)) [$(basename $tz)]"
    fi
done
'''
p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
