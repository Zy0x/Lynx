import subprocess

def run(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip()

print("lsof for thermal (1904):")
print(run('lsof -p 1904'))
print("\nlsof for thermalloadalgod (1906):")
print(run('lsof -p 1906'))
