import subprocess

cmd = 'echo "50,6000,55,5000,1000" > /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug'
res = subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", "su", "-c", cmd], capture_output=True, text=True)
print("STDOUT:", res.stdout)
print("STDERR:", res.stderr)

res2 = subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", "su", "-c", "cat /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug"], capture_output=True, text=True)
print("READBACK:\n" + res2.stdout)

res3 = subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", "su", "-c", "dmesg | grep -i pcb | tail -n 10"], capture_output=True, text=True)
print("DMESG:\n" + res3.stdout)
