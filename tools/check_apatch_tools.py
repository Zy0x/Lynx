import subprocess

def run(cmd):
    res = subprocess.run(['adb', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return res.stdout.strip() + "\n" + res.stderr.strip()

print("--- AP BIN ---")
print(run("ls -la /data/adb/ap/bin/"))
print("\n--- KPTOOLS HELP ---")
print(run("/data/adb/ap/bin/kptools -h"))
print("\n--- APD HELP ---")
print(run("/data/adb/apd -h"))
