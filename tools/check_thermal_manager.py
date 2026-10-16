import subprocess

def run_su(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip(), p.stderr.strip()

out, err = run_su("strings /vendor/bin/thermal_manager")
for line in out.splitlines():
    if any(k in line.lower() for k in ["conf", "usage", ".tp", "reset", "load"]):
        print(line)
