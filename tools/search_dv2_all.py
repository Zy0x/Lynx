import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c \"grep -i 'dv2_' /proc/kallsyms | tail -n 50\""]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
