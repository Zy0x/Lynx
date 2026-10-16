import subprocess

def run(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip()

print(run('ls -la /sys/kernel/debug/regmap/ 2>/dev/null | head -n 30'))
print(run('find /sys/kernel/debug -name "*rt9759*" -o -name "*5-0066*" 2>/dev/null'))
