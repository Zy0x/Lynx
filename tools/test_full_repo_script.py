import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
adpv=$(cat /sys/devices/platform/charger/ADC_Charger_Voltage 2>/dev/null)
[ -z "$adpv" ] || [ "$adpv" -le 0 ] 2>/dev/null && adpv=$(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger 2>/dev/null)
[ -z "$adpv" ] || [ "$adpv" -le 0 ] 2>/dev/null && adpv=$(cat /sys/class/power_supply/usb/voltage_now 2>/dev/null)
[ -z "$adpv" ] || [ "$adpv" -le 0 ] 2>/dev/null && adpv=$(cat /sys/class/power_supply/battery/charger_voltage 2>/dev/null)
[ -z "$adpv" ] && adpv=0

raw_ibus=$(cat /sys/devices/platform/charger/input_current 2>/dev/null)
[ -z "$raw_ibus" ] || [ "$raw_ibus" -le 0 ] 2>/dev/null && raw_ibus=$(cat /sys/bus/i2c/drivers/rt9759/*/Ibus 2>/dev/null | head -n 1)
[ -z "$raw_ibus" ] || [ "$raw_ibus" -le 0 ] 2>/dev/null && raw_ibus=$(cat /sys/class/power_supply/usb/current_now 2>/dev/null)
[ -z "$raw_ibus" ] || [ "$raw_ibus" -le 0 ] 2>/dev/null && raw_ibus=$(cat /sys/class/power_supply/usb/input_current_now 2>/dev/null)
[ -z "$raw_ibus" ] || [ "$raw_ibus" -le 0 ] 2>/dev/null && raw_ibus=$(cat /sys/class/power_supply/main/current_now 2>/dev/null)

if [ "${#raw_ibus}" -ge 10 ] || [ "$raw_ibus" -le 0 ] 2>/dev/null; then
    ibus=0
elif [ "$raw_ibus" -gt 500000 ] 2>/dev/null; then
    ibus=$(( raw_ibus / 1000 ))
elif [ "$raw_ibus" -gt 50000 ] 2>/dev/null; then
    ibus=$(( raw_ibus / 100 ))
else
    ibus="$raw_ibus"
fi
[ -n "$ibus" ] && [ "$ibus" -gt 15000 ] 2>/dev/null && ibus=0

echo "adpv: $adpv mV"
echo "ibus: $ibus mA"
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
