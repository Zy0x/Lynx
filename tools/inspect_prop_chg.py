import subprocess

def run(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip()

print("Listing prop_chgalgo_class:")
print(run('ls -la /sys/class/prop_chgalgo_class/*; ls -la /sys/class/prop_ta/*'))
