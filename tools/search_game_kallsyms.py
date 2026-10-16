import subprocess

target = "192.168.1.13:5555"
script = '''
cat /proc/kallsyms | grep -iE "game_mode|tran_game|dv2.*game"
'''
p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
