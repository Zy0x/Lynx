import subprocess

p = subprocess.run(
    ['adb', '-s', '192.168.1.13:5555', 'shell', 'su'],
    input='''
echo "status: $(cat /sys/class/power_supply/battery/status 2>/dev/null)"
echo "capacity: $(cat /sys/class/power_supply/battery/capacity 2>/dev/null)"
echo "current_now: $(cat /sys/class/power_supply/battery/current_now 2>/dev/null)"
echo "voltage_now: $(cat /sys/class/power_supply/battery/voltage_now 2>/dev/null)"
echo "temp: $(cat /sys/class/power_supply/battery/temp 2>/dev/null)"
echo "ADC_Charger_Voltage: $(cat /sys/devices/platform/charger/ADC_Charger_Voltage 2>/dev/null)"
echo "Charger_Type: $(cat /sys/devices/platform/charger/Charger_Type 2>/dev/null)"
echo "Pump_Express_ICharger: $(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger 2>/dev/null)"
echo "Pump_Express_VCharger: $(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger 2>/dev/null)"
echo "input_current: $(cat /sys/devices/platform/charger/input_current 2>/dev/null)"
echo "chg1_current: $(cat /sys/devices/platform/charger/chg1_current 2>/dev/null)"
echo "sc_ibat_limit: $(cat /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null)"
echo "enable_sc: $(cat /sys/devices/platform/charger/enable_sc 2>/dev/null)"
echo "BN_TestMode: $(cat /sys/devices/platform/charger/BN_TestMode 2>/dev/null)"
echo "BatteryNotify: $(cat /sys/devices/platform/charger/BatteryNotify 2>/dev/null)"
echo "sw_jeita: $(cat /sys/devices/platform/charger/sw_jeita 2>/dev/null)"
exit
''',
    capture_output=True,
    text=True
)
print(p.stdout)
