import subprocess

target = "192.168.1.13:5555"
script = '''
grep -r "dv2_algo_task" /vendor/lib/modules/ 2>/dev/null
grep -r "dv2_algo_task" /system/ 2>/dev/null
'''
p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
