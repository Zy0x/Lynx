import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'strings /data/adb/ap/ori.img | grep -C 5 \"current_cmd\"'"]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
