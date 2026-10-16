import subprocess

target = "192.168.1.13:5555"
script = '''
find /sys/ -name "*game*" 2>/dev/null
find /proc/ -name "*game*" 2>/dev/null
'''
p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
