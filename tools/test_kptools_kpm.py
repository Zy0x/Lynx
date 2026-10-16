import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

def run(cmd):
    res = subprocess.run(['adb', 'shell', 'su', '-c', cmd], capture_output=True, encoding='utf-8', errors='replace')
    return (res.stdout or "") + "\n" + (res.stderr or "")

out = run("/data/adb/ap/bin/kptools -l -M /sdcard/Download/lynx_thermal.kpm")
print(out)
