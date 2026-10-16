import subprocess

def run(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip()

print("Searching for thermal conf / tp:")
print(run('find /vendor -name "*thermal*" -o -name "*charger*" -o -name "*tp*" 2>/dev/null'))
