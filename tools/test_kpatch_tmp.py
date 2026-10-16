import subprocess

subprocess.run(['adb', 'push', 'tools/libkpatch.so', '/data/local/tmp/kpatch'])
subprocess.run(['adb', 'shell', 'chmod', '777', '/data/local/tmp/kpatch'])

res = subprocess.run(['adb', 'shell', 'su', '-c', '/data/local/tmp/kpatch hello'], capture_output=True, text=True)
print("hello stdout:", res.stdout)
print("hello stderr:", res.stderr)

res2 = subprocess.run(['adb', 'shell', 'su', '-c', '/data/local/tmp/kpatch kpver'], capture_output=True, text=True)
print("kpver stdout:", res2.stdout)
print("kpver stderr:", res2.stderr)

res3 = subprocess.run(['adb', 'shell', 'su', '-c', '/data/local/tmp/kpatch kpm list'], capture_output=True, text=True)
print("kpm list stdout:", res3.stdout)
print("kpm list stderr:", res3.stderr)
