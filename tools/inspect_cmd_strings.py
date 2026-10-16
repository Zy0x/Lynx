import subprocess

def run(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip()

print("Strings search for current_cmd:")
# Let's search inside /boot or kernel memory or dmesg
print(run('dmesg | grep -i current_cmd'))
print(run('dmesg | grep -i en_power_path'))
