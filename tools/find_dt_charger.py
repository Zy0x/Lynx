import subprocess

def run(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip()

print(run('find /sys/firmware/devicetree/base -name "*pca*" -o -name "*charger*" -o -name "*tran_battery*" 2>/dev/null'))
