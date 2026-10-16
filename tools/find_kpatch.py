import subprocess

def run(cmd):
    res = subprocess.run(['adb', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return res.stdout.strip() + "\n" + res.stderr.strip()

print(run("which kpatch; which kp; ls -la /data/adb/kpatch; ls -la /system/bin/kp*"))
