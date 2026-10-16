import subprocess
import time

target = "192.168.1.13:5555"

for num5 in [6000, 6000000, 4000, 3000]:
    val = f"[70000,6000,80000,6000,{num5}]"
    script = f'''
    echo "{val}" > /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug
    cat /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug
    '''
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=script + '\nexit\n', capture_output=True, text=True)
    for line in res.stdout.splitlines():
        if "Tmin_curr" in line:
            print(f"num5={num5} -> {line.strip()}")
