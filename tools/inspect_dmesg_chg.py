import subprocess

target = "192.168.1.13:5555"
script = r'''
dmesg | grep -iE "__dv2_get_ita_lmt" | tail -n 20
'''
p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
