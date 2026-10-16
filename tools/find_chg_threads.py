import subprocess

res = subprocess.run(['adb', 'shell', 'su', '-c', 'ps -ef'], capture_output=True, text=True)
for l in res.stdout.splitlines():
    if any(k in l.lower() for k in ['dv2', 'charge', 'algo', 'rt9759', 'rt9471']):
        print(l)
