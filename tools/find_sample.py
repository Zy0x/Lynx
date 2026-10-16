import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=15)
    return res.stdout.strip()

print("Finding 20MB-50MB Genshin asset...")
sample = adb_su("find /data/sdext2/Android/data/com.miHoYo.GenshinImpact/files -name '*.pck' -o -name '*.dat' -o -name '*.blk' 2>/dev/null | head -n 5")
print("Found samples:\n", sample)
