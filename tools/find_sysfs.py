import subprocess

target = "192.168.1.13:5555"
script = r'''
find /sys/devices -name "*game*" 2>/dev/null
find /sys/devices -name "*tpcb*" 2>/dev/null
find /sys/devices -name "*lcd_on*" 2>/dev/null
find /sys/devices -name "*fb_on*" 2>/dev/null
'''
p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
