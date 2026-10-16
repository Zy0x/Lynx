import subprocess
import time
import sys

TARGET = "192.168.1.13:5555"

print("Waiting for device to finish booting up and reconnect...")
for i in range(40):
    time.sleep(2)
    try:
        subprocess.run(['adb', 'connect', TARGET], capture_output=True, text=True, timeout=2)
    except Exception:
        pass
    
    try:
        res = subprocess.run(['adb', 'devices'], capture_output=True, text=True, timeout=2)
        if f"{TARGET}\tdevice" in res.stdout:
            print(f"\nDevice successfully reconnected after {(i+1)*2} seconds!")
            
            # Check boot completion
            for b in range(10):
                boot = subprocess.run(['adb', '-s', TARGET, 'shell', 'getprop', 'sys.boot_completed'], capture_output=True, text=True, timeout=3)
                if boot.stdout.strip() == "1":
                    print("sys.boot_completed = 1 (System fully booted!)")
                    break
                time.sleep(1)
            
            # Unlock screen with password 4636
            print("Waking up display and entering PIN 4636...")
            subprocess.run(['adb', '-s', TARGET, 'shell', 'input', 'keyevent', 'KEYCODE_WAKEUP'], capture_output=True, text=True, timeout=3)
            time.sleep(1)
            subprocess.run(['adb', '-s', TARGET, 'shell', 'input', 'keyevent', '82'], capture_output=True, text=True, timeout=3) # MENU / Swipe
            time.sleep(1)
            subprocess.run(['adb', '-s', TARGET, 'shell', 'input', 'text', '4636'], capture_output=True, text=True, timeout=3)
            time.sleep(0.5)
            subprocess.run(['adb', '-s', TARGET, 'shell', 'input', 'keyevent', 'KEYCODE_ENTER'], capture_output=True, text=True, timeout=3)
            print("Device unlocked successfully!")
            sys.exit(0)
    except Exception:
        pass

    print(f"  Attempt {i+1}/40: Device still booting...", flush=True)

print("Timeout: Device taking longer to boot or IP changed.")
