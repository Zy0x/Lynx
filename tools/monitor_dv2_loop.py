import subprocess
import time

for _ in range(6):
    res = subprocess.run(['adb', 'shell', 'su', '-c', 'dmesg | tail -n 40'], capture_output=True, text=True, errors='replace')
    for l in res.stdout.splitlines():
        if any(k in l for k in ['[LynxKPM]', 'dv2_check', 'dv2_algo', 'tpcb', 'tbat', 'tta', 'rt9759']):
            print(l)
    time.sleep(1)
