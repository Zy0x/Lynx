import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'ls -l /dev/block/by-name/boot* 2>/dev/null; ls -la /data/adb/ap/ 2>/dev/null'"]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
