import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c \"dmesg | grep -iE 'batterynotify|dv2|rt9759|rt9471|enable_sc|rfc' | tail -n 50\""]
try:
    res = subprocess.run(cmd, capture_output=True, text=True, check=True)
    print(res.stdout)
except Exception as e:
    print("Error:", e)
    if hasattr(e, 'stderr'):
        print(e.stderr)
