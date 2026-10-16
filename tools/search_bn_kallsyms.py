import subprocess

def run(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip()

print(run('grep -i BatteryNotify /proc/kallsyms'))
print(run('grep -i BN_TestMode /proc/kallsyms'))
