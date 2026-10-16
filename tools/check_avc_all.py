import subprocess

def run(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip()

print("Checking recent avc denials:")
print(run('dmesg | grep -i "avc:  denied" | tail -n 15'))
