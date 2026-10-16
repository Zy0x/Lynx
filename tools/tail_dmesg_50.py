import subprocess

res = subprocess.run(['adb', 'shell', 'su', '-c', 'dmesg | tail -n 50'], capture_output=True, text=True, errors='replace')
for l in res.stdout.splitlines():
    print(l)
