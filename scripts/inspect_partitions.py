import subprocess
import os
import sys

def run_adb(cmd):
    full_cmd = ["adb", "-s", "H812X69801234567"] + cmd
    res = subprocess.run(full_cmd, capture_output=True, text=True, errors="replace")
    return res.stdout.strip(), res.stderr.strip(), res.returncode

def run_su(shell_cmd):
    return run_adb(["shell", f"su -c '{shell_cmd}'"])

out, err, code = run_su("for p in /dev/block/by-name/*; do echo $(basename $p) $(blockdev --getsize64 $p); done")
print(out)
