import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'echo 85000 > /sys/class/thermal/thermal_zone1/trip_point_4_temp; echo 85000 > /sys/class/thermal/thermal_zone1/trip_point_3_temp; cat /sys/class/thermal/thermal_zone1/trip_point_4_temp; cat /sys/class/thermal/cooling_device56/cur_state'"]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
