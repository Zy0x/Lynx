import subprocess

target = "192.168.1.13:5555"
script = r'''
chmod 666 /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
echo "1 70000 1000 200000 6000 6000 0 6000 0" > /proc/driver/thermal/clabcct_lcmoff
head -n 12 /proc/driver/thermal/clabcct_lcmoff
'''
p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
