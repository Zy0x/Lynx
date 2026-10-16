import subprocess

def run(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip()

print("Symbols around store_BN_TestMode:")
print(run('grep -B 20 -A 20 "store_BN_TestMode" /proc/kallsyms'))
