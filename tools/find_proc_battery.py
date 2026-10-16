import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c \"find /proc -name '*battery*' 2>/dev/null; find /proc -name '*cl*' 2>/dev/null; find /proc/mtk_battery_cmd 2>/dev/null; find /proc/driver -name '*thermal*' 2>/dev/null\""]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
