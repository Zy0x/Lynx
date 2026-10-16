import subprocess
import time

target = "192.168.1.13:5555"

for val in ["[95,4000,100,4000,4000]", "[95,6000,100,6000,6000]", "[45,3000,50,2000,1000]", "[95,2500,100,2500,2500]"]:
    print(f"\n--- Testing pcb_thermal_debug = {val} ---")
    script = f'''
    chmod 666 /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug
    echo "{val}" > /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug
    '''
    subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"])
    time.sleep(1.5)
    
    # Check last dmesg line
    dmesg = subprocess.run(["adb", "-s", target, "shell", "su -c 'dmesg | grep \"__dv2_get_ita_lmt ita\" | tail -n 1'"], capture_output=True, text=True)
    print(dmesg.stdout.strip())
