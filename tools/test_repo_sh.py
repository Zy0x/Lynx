import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
is_plugged=1
adpv=$(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger 2>/dev/null)
[ -z "$adpv" ] || [ "$adpv" -le 0 ] 2>/dev/null && adpv=$(cat /sys/devices/platform/charger/ADC_Charger_Voltage 2>/dev/null)
[ -z "$adpv" ] || [ "$adpv" -le 0 ] 2>/dev/null && adpv=$(cat /sys/class/power_supply/usb/voltage_now 2>/dev/null)
[ -z "$adpv" ] && adpv=0

chgtyp=$(cat /sys/devices/platform/charger/Charger_Type 2>/dev/null || cat /sys/class/power_supply/usb/type 2>/dev/null || echo "")

raw_ibus=$(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger 2>/dev/null)
if [ -n "$raw_ibus" ] && [ "$raw_ibus" -gt 4000000000 ] 2>/dev/null; then
    raw_ibus=0
fi
pe_ibus=0
if [ -n "$raw_ibus" ] && [ "$raw_ibus" -gt 0 ] 2>/dev/null; then pe_ibus=1; fi
[ "$pe_ibus" = "1" ] || raw_ibus=$(cat /sys/bus/i2c/drivers/rt9759/*/Ibus 2>/dev/null | head -n 1)
[ -z "$raw_ibus" ] || [ "$raw_ibus" -le 0 ] 2>/dev/null && raw_ibus=$(cat /sys/class/power_supply/usb/current_now 2>/dev/null)
[ -z "$raw_ibus" ] || [ "$raw_ibus" -le 0 ] 2>/dev/null && raw_ibus=$(cat /sys/class/power_supply/usb/input_current_now 2>/dev/null)
[ -z "$raw_ibus" ] || [ "$raw_ibus" -le 0 ] 2>/dev/null && raw_ibus=$(cat /sys/class/power_supply/main/current_now 2>/dev/null)
[ -z "$raw_ibus" ] && raw_ibus=0

if [ -n "$raw_ibus" ] && [ "$raw_ibus" -gt 100000 ] 2>/dev/null; then
    ibus=$(( raw_ibus / 1000 ))
elif [ "$pe_ibus" = "1" ] && [ "$raw_ibus" -gt 10 ] && [ "$raw_ibus" -lt 1000 ] 2>/dev/null; then
    ibus=$(( raw_ibus * 10 ))
else
    ibus="$raw_ibus"
fi
if [ -n "$ibus" ] && [ "$ibus" -gt 15000 ] 2>/dev/null; then
    ibus=0
fi

echo "RESULT:"
echo "adpv=$adpv"
echo "raw_ibus=$raw_ibus"
echo "pe_ibus=$pe_ibus"
echo "ibus=$ibus"
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
