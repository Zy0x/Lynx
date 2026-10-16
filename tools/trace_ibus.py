import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
raw_ibus=$(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger 2>/dev/null)
echo "step 1 raw_ibus: '$raw_ibus'"
if [ -n "$raw_ibus" ] && [ "$raw_ibus" -gt 4000000000 ] 2>/dev/null; then
    raw_ibus=0
fi
echo "step 2 raw_ibus: '$raw_ibus'"
pe_ibus=0
if [ -n "$raw_ibus" ] && [ "$raw_ibus" -gt 0 ] 2>/dev/null; then pe_ibus=1; fi
echo "step 3 pe_ibus: '$pe_ibus'"
[ "$pe_ibus" = "1" ] || raw_ibus=$(cat /sys/bus/i2c/drivers/rt9759/*/Ibus 2>/dev/null | head -n 1)
echo "step 4 raw_ibus: '$raw_ibus'"
[ -z "$raw_ibus" ] || [ "$raw_ibus" -le 0 ] 2>/dev/null && raw_ibus=$(cat /sys/class/power_supply/usb/current_now 2>/dev/null)
echo "step 5 raw_ibus: '$raw_ibus'"
[ -z "$raw_ibus" ] || [ "$raw_ibus" -le 0 ] 2>/dev/null && raw_ibus=$(cat /sys/class/power_supply/usb/input_current_now 2>/dev/null)
echo "step 6 raw_ibus: '$raw_ibus'"
[ -z "$raw_ibus" ] || [ "$raw_ibus" -le 0 ] 2>/dev/null && raw_ibus=$(cat /sys/class/power_supply/main/current_now 2>/dev/null)
echo "step 7 raw_ibus: '$raw_ibus'"
[ -z "$raw_ibus" ] && raw_ibus=0
echo "step 8 raw_ibus: '$raw_ibus'"
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
