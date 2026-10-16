import subprocess
import time

def run(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip()

print("Applying full Hyper bypass on MediaTek charger nodes...")
cmds = [
    "echo 0 > /sys/devices/platform/charger/sw_jeita",
    "echo 0 > /sys/devices/platform/charger/BN_TestMode",
    "echo 1 > /sys/devices/platform/charger/pe20",
    "echo 1 > /sys/devices/platform/charger/pe40",
    "echo 1 > /sys/devices/platform/charger/enable_sc",
    "echo 120 > /sys/devices/platform/charger/pdc_max_watt",
    "echo 33170 > /sys/devices/platform/charger/input_current",
    "echo 33170 > /sys/devices/platform/charger/chg1_current",
    "echo 33170 > /sys/devices/platform/charger/chg2_current",
    "echo 12000 > /sys/devices/platform/charger/sc_ibat_limit",
    "for c in /sys/class/thermal/cooling_device*; do t=$(cat $c/type 2>/dev/null); if echo $t | grep -qi 'bcct'; then chmod 666 $c/cur_state; echo 0 > $c/cur_state; fi; done",
]
for c in cmds:
    run(c)

print("Nodes applied. Sampling 5 readings (1s interval):")
for i in range(5):
    time.sleep(1)
    cur = run("cat /sys/class/power_supply/battery/current_now")
    volt = run("cat /sys/class/power_supply/battery/voltage_now")
    temp = run("cat /sys/class/power_supply/battery/temp")
    vbus = run("cat /sys/devices/platform/charger/ADC_Charger_Voltage")
    ibus = run("cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger")
    print(f"Sample {i+1}: Ibat={cur} uA, Vbat={volt} uV, Temp={temp}, Vbus={vbus} mV, Ibus={ibus} (0.01A)")
