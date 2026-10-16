import subprocess

def run(cmd):
    res = subprocess.run(['adb', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return res.stdout.strip()

print("--- STRINGS IN APD FOR KPM ---")
print(run("strings /data/adb/apd | grep -i 'kpm' | head -n 30"))
print("--- STRINGS IN KPTOOLS FOR KPM ---")
print(run("strings /data/adb/ap/bin/kptools | grep -i 'kpm' | head -n 30"))
