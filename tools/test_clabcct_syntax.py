import subprocess

target = "192.168.1.13:5555"
script = r'''
chmod 666 /proc/driver/thermal/clabcct /proc/driver/thermal/clabcct_lcmoff 2>/dev/null

echo "=== TEST 1: echo 0 ==="
echo 0 > /proc/driver/thermal/clabcct
head -n 12 /proc/driver/thermal/clabcct

echo "=== TEST 2: echo 70000 1000 200000 6000 6000 0 1 1 0 ==="
echo "70000 1000 200000 6000 6000 0 1 1 0" > /proc/driver/thermal/clabcct
head -n 12 /proc/driver/thermal/clabcct
'''
p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
