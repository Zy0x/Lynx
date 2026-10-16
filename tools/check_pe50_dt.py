import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c \"ls -la /sys/firmware/devicetree/base/odm/mtk_pe50; ls -la /sys/firmware/devicetree/base/odm/\""]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
