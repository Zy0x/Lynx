#!/system/bin/sh
echo "=========================================================="
echo "          LYNX REAL HARDWARE DISCHARGE AUDIT             "
echo "=========================================================="

echo "[1] CABLE & POWER SUPPLY STATE"
echo "Battery Status      : $(cat /sys/class/power_supply/battery/status 2>/dev/null)"
echo "Current Now         : $(cat /sys/class/power_supply/battery/current_now 2>/dev/null) uA"
echo "Voltage Now         : $(cat /sys/class/power_supply/battery/voltage_now 2>/dev/null) uV"
echo "Capacity            : $(cat /sys/class/power_supply/battery/capacity 2>/dev/null) %"
echo "Battery Temp        : $(cat /sys/class/power_supply/battery/temp 2>/dev/null) (0.1 C)"
echo "USB Online          : $(cat /sys/class/power_supply/usb/online 2>/dev/null)"

echo ""
echo "[2] CHARGER REGISTERS & VENDOR OVERRIDES"
echo "sw_jeita            : $(cat /sys/devices/platform/charger/sw_jeita 2>/dev/null) (Expected: 1)"
echo "pe20                : $(cat /sys/devices/platform/charger/pe20 2>/dev/null) (Expected: 0)"
echo "pe40                : $(cat /sys/devices/platform/charger/pe40 2>/dev/null) (Expected: 0)"
echo "pdc_max_watt        : $(cat /sys/devices/platform/charger/pdc_max_watt 2>/dev/null) (Expected: 33)"
echo "BN_TestMode         : $(cat /sys/devices/platform/charger/BN_TestMode 2>/dev/null) (Expected: 0)"
echo "BatteryNotify       : $(cat /sys/devices/platform/charger/BatteryNotify 2>/dev/null) (Expected: 1)"
echo "Charging_CallState  : $(cat /sys/devices/platform/odm/odm:tran_battery/Charging_CallState 2>/dev/null) (Expected: 0)"
echo "Pump_Express_IChg   : $(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger 2>/dev/null)"
echo "Pump_Express_VChg   : $(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger 2>/dev/null) mV"
echo "ADC_Charger_Voltage : $(cat /sys/devices/platform/charger/ADC_Charger_Voltage 2>/dev/null) mV"

echo ""
echo "[3] THERMAL COOLING DEVICES (BCCT)"
for cd in /sys/class/thermal/cooling_device*; do
    type=$(cat "$cd/type" 2>/dev/null)
    case "$type" in
        *bcct*|*abcct*)
            cur=$(cat "$cd/cur_state" 2>/dev/null)
            max=$(cat "$cd/max_state" 2>/dev/null)
            perms=$(ls -ld "$cd/cur_state" 2>/dev/null | awk '{print $1}')
            echo "  $(basename "$cd") ($type): cur=$cur max=$max perm=$perms"
            ;;
    esac
done

echo ""
echo "[4] THERMAL SENSORS (TEMPERATURES)"
for tz in /sys/class/thermal/thermal_zone*; do
    type=$(cat "$tz/type" 2>/dev/null)
    temp=$(cat "$tz/temp" 2>/dev/null)
    case "$type" in
        *battery*|*ap*|*AP*|*cpu*|*bts*|*wmt*)
            if [ -n "$temp" ] && [ "$temp" -gt 0 ] 2>/dev/null; then
                c_temp=$((temp / 1000))
                echo "  $(basename "$tz") ($type): ${c_temp} C (raw: $temp)"
            fi
            ;;
    esac
done

echo ""
echo "[5] CPU CLOCK & GOVERNOR SCALING"
echo "CPU0 Governor       : $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null)"
echo "CPU0 Cur / Min / Max: $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null) / $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq 2>/dev/null) / $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq 2>/dev/null) kHz"
echo "CPU4 Cur / Min / Max: $(cat /sys/devices/system/cpu/cpu4/cpufreq/scaling_cur_freq 2>/dev/null) / $(cat /sys/devices/system/cpu/cpu4/cpufreq/scaling_min_freq 2>/dev/null) / $(cat /sys/devices/system/cpu/cpu4/cpufreq/scaling_max_freq 2>/dev/null) kHz"
echo "CPU6 Governor       : $(cat /sys/devices/system/cpu/cpu6/cpufreq/scaling_governor 2>/dev/null)"
echo "CPU6 Cur / Min / Max: $(cat /sys/devices/system/cpu/cpu6/cpufreq/scaling_cur_freq 2>/dev/null) / $(cat /sys/devices/system/cpu/cpu6/cpufreq/scaling_min_freq 2>/dev/null) / $(cat /sys/devices/system/cpu/cpu6/cpufreq/scaling_max_freq 2>/dev/null) kHz"

echo ""
echo "[6] GPU CLOCK & DVFS"
if [ -f /proc/gpufreq/gpufreq_var_dump ]; then
    grep -E "(real|freq:|gpu_loading)" /proc/gpufreq/gpufreq_var_dump 2>/dev/null | head -n 3
fi

echo ""
echo "[7] ACTIVE LOCK FILES IN /dev"
ls -la /dev/lynx* 2>/dev/null || echo "No /dev/lynx files found"

echo "=========================================================="
