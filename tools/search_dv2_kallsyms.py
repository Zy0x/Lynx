import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c \"grep -i 'dv2_debug' /proc/kallsyms; grep -i 'tpcb' /proc/kallsyms\""]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
