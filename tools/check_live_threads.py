import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== LIVE POST-TWEAK THREADS & CPU USAGE ===")
res = adb_su("top -H -b -n 1 -p 13825")
lines = res.split('\n')
for line in lines[:25]:
    print(line)
