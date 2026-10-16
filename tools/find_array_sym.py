import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
# We can read memory of that global array using kallsyms or see which symbol is at 0xffffff80dd927e98
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'echo 0 > /proc/sys/kernel/kptr_restrict; grep -C 3 \"ffffff80dd927\" /proc/kallsyms'"]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
