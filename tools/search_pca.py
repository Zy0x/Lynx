import subprocess

def run(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip()

print("Kallsyms search for dv2_thermal_throttling:")
print(run('grep -i dv2_thermal_throttling /proc/kallsyms'))
print("\nKallsyms search for pca_:")
print(run('grep -i " pca_" /proc/kallsyms | head -n 30'))
