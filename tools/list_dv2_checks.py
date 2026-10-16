import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)

res = subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", "su", "-c", "grep -E '__dv2_check|dv2_thermal|jeita' /proc/kallsyms"], capture_output=True, text=True)
for line in res.stdout.splitlines():
    print(line)
