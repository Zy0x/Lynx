import subprocess

def run_py_on_device(code):
    cmd = f"python3 -c \"{code}\""
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout, p.stderr

# Check if python3 exists on device or test with sh
p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', 'echo 0 > /sys/devices/platform/charger/Pump_Express'], capture_output=True, text=True)
print("Return code:", p.returncode)
print("Stdout:", p.stdout)
print("Stderr:", p.stderr)
