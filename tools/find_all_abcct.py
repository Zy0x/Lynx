import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
for c in /sys/class/thermal/thermal_zone*/cdev*; do
    target_cdev=$(readlink -f "$c" 2>/dev/null)
    if [[ "$target_cdev" == *"cooling_device56"* ]] || [[ "$target_cdev" == *"abcct"* ]]; then
        echo "$c -> $target_cdev"
        tz=$(dirname "$c")
        echo "   tz: $tz ($(cat $tz/type 2>/dev/null)) temp=$(cat $tz/temp 2>/dev/null)"
    fi
done
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
