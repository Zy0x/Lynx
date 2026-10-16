import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
echo "=== RT9759 SYSFS ==="
find /sys/bus/i2c/drivers/rt9759/ -type f 2>/dev/null
echo "=== RFC_TA SYSFS ==="
find /sys/bus/i2c/drivers/*rfc*/ -type f 2>/dev/null
find /sys/devices/platform/odm/odm:pca* -type f 2>/dev/null
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
