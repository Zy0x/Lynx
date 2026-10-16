import subprocess
import sys

filename = sys.argv[1] if len(sys.argv) > 1 else 'tools/current_screen.png'
target = sys.argv[2] if len(sys.argv) > 2 else '192.168.1.13:5555'

res = subprocess.run(['adb', '-s', target, 'exec-out', 'screencap', '-p'], capture_output=True)
if res.returncode == 0 and len(res.stdout) > 1000:
    with open(filename, 'wb') as f:
        f.write(res.stdout)
    print(f"Captured screen to {filename} ({len(res.stdout)} bytes)")
else:
    print(f"Failed to capture screen: {res.stderr}")
