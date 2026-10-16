import subprocess
import time

target = "192.168.1.13:5555"

tests = [
    "[70000,6000,80000,6000,6000]",
    "[95000,6000,100000,6000,6000]",
    "[0,6000,0,6000,6000]",
]

for val in tests:
    print(f"\n--- Testing pcb_thermal_debug = {val} ---")
    script = f'''
    chmod 666 /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug
    echo "{val}" > /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug
    cat /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug
    '''
    subprocess.run(["adb", "-s", target, "shell", "su"], input=script + '\nexit\n', capture_output=True, text=True)
    time.sleep(1.5)
    
    dmesg = subprocess.run(["adb", "-s", target, "shell", "su"], input='dmesg | grep "__dv2_get_ita_lmt ita" | tail -n 1\nexit\n', capture_output=True, text=True)
    print(dmesg.stdout.strip())
