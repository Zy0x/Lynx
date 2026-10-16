import subprocess

# In /proc/kcore or via /dev/kmem or gdb
script = '''
python3 -c '
import struct
with open("/proc/kcore", "rb") as f:
    f.seek(0xffffff8f4c96cda8)
    data = f.read(256)
    print("READ", len(data), "bytes")
' 2>/dev/null || echo "kcore python failed"
'''
p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su'], input=script + '\nexit\n', capture_output=True, text=True)
print(p.stdout)
