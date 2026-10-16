import subprocess

target = "192.168.1.13:5555"
script = r'''
strings /proc/kcore 2>/dev/null | grep "tran_game_mode" | head -n 5
'''
p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
