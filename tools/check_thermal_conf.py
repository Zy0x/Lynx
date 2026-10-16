import subprocess

p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', 'cat /vendor/etc/.tp/thermal.conf'], capture_output=True, text=True)
lines = p.stdout.splitlines()
for line in lines:
    if any(k in line.lower() for k in ['bcct', 'chg', 'battery', 'current', 'charge', 'cool']):
        print(line)
