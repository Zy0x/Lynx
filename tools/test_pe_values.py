import subprocess

def test_val(node, val):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', f'echo "{val}" > {node}'], capture_output=True, text=True)
    print(f"Write {val} to {node} -> code: {p.returncode}, err: {p.stderr.strip()}")

for v in ["0", "1", "2", "3"]:
    test_val("/sys/devices/platform/charger/Pump_Express", v)
