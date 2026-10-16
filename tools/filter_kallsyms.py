import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'echo 0 > /proc/sys/kernel/kptr_restrict; cat /proc/kallsyms'"]
res = subprocess.run(cmd, capture_output=True, text=True)
for line in res.stdout.splitlines():
    if "tpcb" in line.lower() or "dv2" in line.lower():
        if "check" in line.lower() or "thermal" in line.lower():
            print(line)
