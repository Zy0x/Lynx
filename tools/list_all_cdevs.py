import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'for c in /sys/class/thermal/cooling_device*; do echo $(basename $c): $(cat $c/type 2>/dev/null) state=$(cat $c/cur_state 2>/dev/null); done'"]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
