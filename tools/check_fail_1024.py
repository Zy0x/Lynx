import subprocess

def get(cmd):
    res = subprocess.run(['adb', 'shell', 'su', '-c', cmd], capture_output=True, text=True, errors='replace')
    return res.stdout.strip()

print("--- RECENT LOGCAT AROUND KPM ---")
logcat = get("logcat -d -t 150")
for line in logcat.splitlines():
    if any(k in line.lower() for k in ['apatch', 'kpm', 'lynx', 'native', 'error', 'fail', '-1', 'load']):
        print(line)

print("\n--- RECENT DMESG ---")
dmesg = get("dmesg | tail -n 80")
for line in dmesg.splitlines():
    if any(k in line.lower() for k in ['kp', 'kpm', 'lynx', 'error', 'fail', 'symbol', 'reloc', 'load', 'init', 'open']):
        print(line)
