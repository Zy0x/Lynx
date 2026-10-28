import subprocess
import time

print("Waiting for normal Android boot...")
for i in range(45):
    res = subprocess.run(["adb", "devices"], capture_output=True, text=True)
    if "H812X69801234567\tdevice" in res.stdout:
        # Check boot_completed
        res_boot = subprocess.run(["adb", "-s", "H812X69801234567", "shell", "getprop sys.boot_completed"], capture_output=True, text=True)
        if res_boot.stdout.strip() == "1":
            print("Normal Android booted successfully!")
            exit(0)
    time.sleep(2)

print("Timeout waiting for Android boot.")
