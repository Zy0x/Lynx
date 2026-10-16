import subprocess

res = subprocess.run(['adb', 'shell', 'su', '-c', 'logcat -d -t 250'], capture_output=True, text=True)
for line in res.stdout.splitlines():
    if any(k in line.lower() for k in ['kpm', 'apatch', 'fail', 'error', 'exception', 'download', 'lynx', '-1']):
        print(line)
