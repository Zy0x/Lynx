import subprocess

def run(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip()

print("Checking dmesg for pump express avc denials:")
print(run('dmesg | grep -i avc | grep -i pump | tail -n 10'))
