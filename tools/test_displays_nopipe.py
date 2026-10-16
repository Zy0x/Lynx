import subprocess
import time

t0 = time.time()
cmd = "su -c 'dumpsys window displays'"
res = subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", cmd], capture_output=True, text=True)
dt = (time.time() - t0) * 1000

for line in res.stdout.splitlines():
    if "mCurrentFocus=" in line:
        print("FOUND:", line.strip())
        break

print(f"Total time over ADB: {dt:.1f}ms")
print("STDERR len:", len(res.stderr.strip()))
