import subprocess
import os

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)

# Pull first 20MB of ori.img to local PC
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'dd if=/data/adb/ap/ori.img of=/data/local/tmp/boot_head.img bs=1M count=25'"]
subprocess.run(cmd, check=True)

subprocess.run(["adb", "-s", "192.168.1.13:5555", "pull", "/data/local/tmp/boot_head.img", "tools/boot_head.img"], check=True)

print("Downloaded boot_head.img, size:", os.path.getsize("tools/boot_head.img"))
