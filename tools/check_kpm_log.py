import subprocess

res = subprocess.run(['adb', 'shell', 'su', '-c', 'dmesg | tail -n 100'], capture_output=True, text=True)
for line in res.stdout.splitlines():
    if any(k in line.lower() for k in ['kp', 'kpm', 'lynx', 'patch', 'module', 'load', 'error', 'dv2', 'fail', 'symbol', 'reloc']):
        print(line)
