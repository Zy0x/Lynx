import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
echo "write test to trip_point_4_temp:"
chmod 666 /sys/class/thermal/thermal_zone1/trip_point_4_temp 2>/dev/null
echo 65000 > /sys/class/thermal/thermal_zone1/trip_point_4_temp
echo "result: $?"
cat /sys/class/thermal/thermal_zone1/trip_point_4_temp

echo "mode test:"
chmod 666 /sys/class/thermal/thermal_zone1/mode 2>/dev/null
echo disabled > /sys/class/thermal/thermal_zone1/mode
echo "mode result: $?"
cat /sys/class/thermal/thermal_zone1/mode
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)
    print("STDERR:\n", res.stderr)

if __name__ == "__main__":
    run()
