import subprocess

def run_su(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su'], input=cmd + '\nexit\n', capture_output=True, text=True, timeout=5)
    return p.stdout.strip()

print("Files in pca_dv2_algo:")
print(run_su("ls -la /sys/devices/platform/pca_dv2_algo"))

print("Reading dv2_debug:")
print(run_su("cat /sys/devices/platform/pca_dv2_algo/dv2_debug"))
