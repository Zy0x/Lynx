import subprocess

target = "192.168.1.13:5555"
script = r'''
grep -E "__dv2_|pca_algo|tpcb" /proc/kallsyms
'''
p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
