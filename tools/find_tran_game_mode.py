import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'grep -i tran_game_mode /proc/kallsyms; getprop | grep -i game; find /sys -name \"*game*\" 2>/dev/null | grep -i tran'"]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
