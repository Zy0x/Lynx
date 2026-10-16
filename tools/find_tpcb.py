import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c \"find /sys -name '*tpcb*' 2>/dev/null; find /sys/devices/platform/charger -type f 2>/dev/null; find /sys/class/thermal -name 'thermal_zone*' -exec grep -H 'pcb' {}/type \\; 2>/dev/null\""]
res = subprocess.run(cmd, capture_output=True, text=True)
print(res.stdout)
