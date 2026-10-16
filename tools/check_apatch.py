import subprocess

def run(cmd):
    res = subprocess.run(['adb', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return res.stdout.strip()

print("--- APATCH DIRS ---")
print(run("ls -la /data/adb/"))
print("\n--- APD / KPTOOLS ---")
print(run("which kptools; which kpm; ls -la /data/adb/ap*"))
