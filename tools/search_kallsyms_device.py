import subprocess

res = subprocess.run(['adb', 'shell', 'su', '-c', 'grep -i "dv2" /proc/kallsyms'], capture_output=True, text=True)
print(res.stdout)
