import subprocess

target = "192.168.1.13:5555"

for step in [50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, 120]:
    val = f"[{step},6000,{step+5},6000,6000000]"
    script = f'''
    echo "{val}" > /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug
    cat /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug
    '''
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=script + '\nexit\n', capture_output=True, text=True)
    out = [l.strip() for l in res.stdout.splitlines() if "Tpcb_step" in l or "Tmin_curr" in l]
    print(f"step={step} -> {' '.join(out)}")
