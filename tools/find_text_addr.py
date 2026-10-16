import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'echo 0 > /proc/sys/kernel/kptr_restrict; grep -E \" (T|t) (_text|stext|_stext)\" /proc/kallsyms'"]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
