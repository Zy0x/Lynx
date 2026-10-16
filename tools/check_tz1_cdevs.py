import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'for f in /sys/class/thermal/thermal_zone1/cdev*; do echo $(basename $f): $(cat $f/type 2>/dev/null || cat $f); done'"]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
