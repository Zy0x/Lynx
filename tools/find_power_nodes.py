import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
find /sys/class/power_supply/ -maxdepth 3 -type f 2>/dev/null
echo "=== RT9759 ==="
ls -la /sys/bus/i2c/drivers/rt9759/*/ 2>/dev/null
echo "=== MTK ==="
ls -la /sys/devices/platform/*charger*/ 2>/dev/null
ls -la /sys/devices/platform/*battery*/ 2>/dev/null
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
