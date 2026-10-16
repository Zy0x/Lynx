import subprocess

def run(cmd):
    res = subprocess.run(['adb', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return res.stdout.strip() + "\n" + res.stderr.strip()

print(run("ls -la /data/adb/kpatch; /data/adb/kpatch --help"))
print(run("/data/adb/kpatch hello"))
print(run("/data/adb/kpatch kpver"))
print(run("/data/adb/kpatch kpm list"))
