import subprocess

p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', 'dmesg | tail -n 60'], capture_output=True, text=True)
for line in p.stdout.strip().split('\n'):
    if any(k in line.lower() for k in ['chg', 'charger', 'type', 'rt9759', 'pe', 'pd', 'pump', 'pca', 'dv2', 'usb']):
        print(line)
