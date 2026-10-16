import subprocess

subprocess.run(['adb', 'push', 'tools/hello.kpm', '/data/local/tmp/hello.kpm'])
res = subprocess.run(['adb', 'shell', 'su', '-c', '/data/adb/ap/bin/kptools -l -M /data/local/tmp/hello.kpm'], capture_output=True, text=True)
print(res.stdout)
