import subprocess

files = [
    "/sys/devices/platform/charger/Charger_Type",
    "/sys/devices/platform/charger/Pump_Express",
    "/sys/class/power_supply/usb/type",
    "/sys/class/power_supply/usb/real_type",
    "/sys/class/power_supply/usb/quick_charge_type",
    "/sys/class/qcom-battery/qc_type",
    "/sys/class/power_supply/usb/pd_active",
    "/sys/class/typec/pd_active",
    "/sys/class/power_supply/battery/vooc_charging",
    "/sys/class/power_supply/battery/fastcharge_mode",
    "/sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger",
    "/sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger",
]
for f in files:
    res = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', f'cat {f} 2>/dev/null'], capture_output=True, text=True).stdout.strip()
    print(f"{f} = '{res}'")
