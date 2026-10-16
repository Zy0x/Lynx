import subprocess

def run(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip()

for line in run('ps -A').splitlines():
    if any(k in line.lower() for k in ["charg", "batt", "therm", "tran", "power"]):
        print(line)
