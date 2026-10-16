import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c \"dmesg | grep -iE '\\[PCA\\]|dv2_|rt9759_enable|rfc_ta' | tail -n 60\""]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
