import subprocess
import time

target = "192.168.1.13:5555"

print("1. Testing STANDARD MODE:")
subprocess.run(["adb", "-s", target, "shell", "su"], input='/data/adb/modules/Lynx/system/bin/lynxd charging mode standard\nexit\n', capture_output=True, text=True)
time.sleep(1)

# Check that Battery_Temperature is unlocked and reports real temp
res_std = subprocess.run(["adb", "-s", target, "shell", "su"], input='/data/adb/modules/Lynx/system/bin/lynxd charging status\nexit\n', capture_output=True, text=True)
print("Standard mode status:")
print(res_std.stdout)

print("2. Re-applying HYPER MODE:")
subprocess.run(["adb", "-s", target, "shell", "su"], input='/data/adb/modules/Lynx/system/bin/lynxd charging mode hyper\nexit\n', capture_output=True, text=True)
time.sleep(1)

res_hyp = subprocess.run(["adb", "-s", target, "shell", "su"], input='/data/adb/modules/Lynx/system/bin/lynxd charging status\nexit\n', capture_output=True, text=True)
print("Hyper mode status:")
print(res_hyp.stdout)
