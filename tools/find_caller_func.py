import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'echo 0 > /proc/sys/kernel/kptr_restrict; grep -C 3 \"ffffff80dc363\" /proc/kallsyms | head -n 20'"]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
