import subprocess

def run_su(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su'], input=cmd + '\nexit\n', capture_output=True, text=True)
    return p.stdout.strip()

print(run_su("""
echo help > /sys/devices/platform/pca_dv2_algo/dv2_debug
cat /sys/devices/platform/pca_dv2_algo/dv2_debug
dmesg | tail -n 15
"""))
