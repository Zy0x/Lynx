import subprocess
import time

print("Waiting for device in recovery...")
for i in range(30):
    res = subprocess.run(["adb", "devices"], capture_output=True, text=True)
    lines = res.stdout.strip().splitlines()[1:]
    for line in lines:
        if "\trecovery" in line or "\tdevice" in line:
            print(f"Device detected: {line}")
            exit(0)
    time.sleep(2)

print("Timeout waiting for adb.")
