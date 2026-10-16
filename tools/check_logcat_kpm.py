import subprocess

res = subprocess.run(['adb', 'shell', 'su', '-c', 'logcat -d | grep -iE "kpm|apatch|kptools|kernelpatch" | tail -n 80'], capture_output=True, text=True)
print(res.stdout)
