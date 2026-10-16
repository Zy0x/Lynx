import subprocess

subprocess.run(['adb', 'push', 'tools/libkpatch.so', '/data/local/tmp/kpatch'])
subprocess.run(['adb', 'shell', 'su', '-c', 'cp /data/local/tmp/kpatch /data/adb/ap/bin/kpatch && chmod 755 /data/adb/ap/bin/kpatch'])

res = subprocess.run(['adb', 'shell', 'su', '-c', '/data/adb/ap/bin/kpatch hello; /data/adb/ap/bin/kpatch kpver'], capture_output=True, text=True)
print(res.stdout)
print(res.stderr)
