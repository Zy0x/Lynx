import subprocess
import time

DEVICE = "192.168.1.5:5555"

def main():
    subprocess.run(["adb", "connect", DEVICE])
    time.sleep(1.0)
    
    # 1. Check package status
    res = subprocess.run(["adb", "-s", DEVICE, "shell", "su", "-c", "pm list packages -f com.noir.lynx"], capture_output=True, text=True)
    print("PM LIST:", res.stdout.strip())
    
    # 2. Check clear logcat
    subprocess.run(["adb", "-s", DEVICE, "shell", "su", "-c", "logcat -c"])
    
    # 3. Try starting activity
    start_res = subprocess.run(["adb", "-s", DEVICE, "shell", "am", "start", "-n", "com.noir.lynx/.ui.MainActivity"], capture_output=True, text=True)
    print("START RES:", start_res.stdout, start_res.stderr)
    
    # 4. Fetch logcat
    log_res = subprocess.run(["adb", "-s", DEVICE, "shell", "su", "-c", "logcat -d -t 300"], capture_output=True, text=True)
    for line in log_res.stdout.splitlines():
        if any(k in line.lower() for k in ["noir", "lynx", "mainactivity", "not found", "classnotfound", "activitytaskmanager"]):
            print("LOG:", line)

if __name__ == "__main__":
    main()
