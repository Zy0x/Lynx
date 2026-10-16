import subprocess
import sys

cmd = sys.argv[1] if len(sys.argv) > 1 else "id"
p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su 0 sh'], input=cmd, capture_output=True, text=True, encoding='utf-8', errors='replace')
if p.stdout:
    print(p.stdout)
if p.stderr:
    print("ERR:", p.stderr)
