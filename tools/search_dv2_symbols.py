import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c \"grep -E ' (t|T) [a-zA-Z0-9_]*dv2' /proc/kallsyms | head -n 40\""]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
