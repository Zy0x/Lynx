import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c \"grep -i 'battery_cmd' /proc/kallsyms; grep -i 'current_cmd' /proc/kallsyms\""]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
