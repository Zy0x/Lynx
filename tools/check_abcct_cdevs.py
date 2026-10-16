import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'for c in /sys/class/thermal/cooling_device*; do type=$(cat $c/type 2>/dev/null); if [[ \"$type\" == *\"abcct\"* || \"$type\" == *\"bcct\"* ]]; then echo \"$c - $type: cur=$(cat $c/cur_state 2>/dev/null), max=$(cat $c/max_state 2>/dev/null)\"; fi; done'"]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
