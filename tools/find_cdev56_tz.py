import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
echo "cooling56 type: $(cat /sys/class/thermal/cooling_device56/type)"
for c in /sys/class/thermal/thermal_zone*/cdev*; do
    if [ -e "$c" ]; then
        target_cdev=$(readlink -f "$c" 2>/dev/null)
        if [[ "$target_cdev" == *"cooling_device56"* ]]; then
            tz=$(dirname "$c")
            echo "Bound to $tz:"
            cat "$tz/type" 2>/dev/null
            cat "$tz/policy" 2>/dev/null
            cat "$tz/temp" 2>/dev/null
            cat "$tz/trip_point"* 2>/dev/null
        fi
    fi
done
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
