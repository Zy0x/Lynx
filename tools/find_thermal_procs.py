import subprocess

target = "192.168.1.13:5555"
script = r'''
ps -A | grep -iE "thermal|algo|charge"
'''
p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
