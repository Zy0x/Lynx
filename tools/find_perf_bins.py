import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print(adb_su("find /vendor/bin /system/bin -name '*perf*' -o -name '*power*' 2>/dev/null"))
