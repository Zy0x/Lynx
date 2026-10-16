import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'cat /sys/class/thermal/cooling_device56/max_state; echo 0 > /sys/class/thermal/cooling_device56/cur_state; cat /sys/class/thermal/cooling_device56/cur_state'"]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
