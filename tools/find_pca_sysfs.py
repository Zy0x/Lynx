import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c \"find /sys -iname '*pe50*' 2>/dev/null; find /sys -iname '*pca*' 2>/dev/null; find /sys -iname '*dv2*' 2>/dev/null\""]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
