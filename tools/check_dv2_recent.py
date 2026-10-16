import subprocess

res = subprocess.run(['adb', 'shell', 'su', '-c', 'dmesg | tail -n 120'], capture_output=True, text=True, errors='replace')
for l in res.stdout.splitlines():
    if any(k in l.lower() for k in ['dv2', 'rfc', 'ta', 'rt9759', 'lynx', 'charge', 'usb', 'vbus']):
        print(l)
