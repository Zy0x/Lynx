import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
ls -la /sys/class/thermal/thermal_zone1/
cat /sys/class/thermal/thermal_zone1/available_policies
echo "mode: $(cat /sys/class/thermal/thermal_zone1/mode)"
for f in /sys/class/thermal/thermal_zone1/trip_point_*; do
    echo "$f: $(cat "$f" 2>/dev/null)"
done
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
