import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
echo "=== BATTERY TEMP ==="
cat /sys/class/power_supply/battery/temp 2>/dev/null
cat /sys/devices/platform/battery/Battery_Temperature 2>/dev/null
echo "=== THERMAL THROTTLE NODES ==="
cat /proc/driver/thermal/clabcct 2>/dev/null
cat /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
echo "=== COOLING DEVICES ==="
for c in /sys/class/thermal/cooling_device*; do
    type=$(cat "$c/type" 2>/dev/null)
    state=$(cat "$c/cur_state" 2>/dev/null)
    max=$(cat "$c/max_state" 2>/dev/null)
    if [ "$state" != "0" ] && [ -n "$state" ]; then
        echo "$c ($type): cur=$state / max=$max"
    fi
done
echo "=== PCB THERMAL DEBUG ==="
cat /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug 2>/dev/null
cat /sys/devices/platform/odm/odm:pca_rfc_ta/dv2_debug 2>/dev/null
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
