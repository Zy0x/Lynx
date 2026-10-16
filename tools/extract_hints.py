import subprocess
import re

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

xml = adb_su("cat /vendor/etc/powerscntbl.xml")
hints = set(re.findall(r'powerhint=["\']([^"\']+)["\']', xml))
print("Hints:", sorted(list(hints)))
