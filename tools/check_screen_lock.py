import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== SCREEN & TOP ACTIVITY ===")
screen = adb_su("dumpsys power | grep -E 'mHoldingDisplaySuspendBlocker|mWakefulness'")
print("Screen:", screen)
top = adb_su("dumpsys activity activities | grep -m1 topResumedActivity")
print("Top Activity:", top)
locked = adb_su("dumpsys window | grep -E 'mDreamingLockscreen|mShowingDream|isStatusBarKeyguard'")
print("Lockscreen:", locked)
