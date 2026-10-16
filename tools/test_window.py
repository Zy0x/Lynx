import subprocess
import time

def test_cmd(desc, cmd):
    t0 = time.time()
    res = subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", f"su -c '{cmd}'"], capture_output=True, text=True)
    dt = (time.time() - t0) * 1000
    print(f"[{desc}] ({dt:.1f}ms):")
    print("  STDOUT:", res.stdout.strip())
    print("  STDERR:", res.stderr.strip())

test_cmd("Method 1: window displays awk", 'dumpsys window displays | awk "/mCurrentFocus=/ { print; exit }"')
test_cmd("Method 2: window displays awk without exit", 'dumpsys window displays | awk "/mCurrentFocus=/ { print }"')
test_cmd("Method 3: activity activities topResumedActivity", 'dumpsys activity activities | awk "/topResumedActivity=/ { print }"')
test_cmd("Method 4: window windows with grep and cat", 'dumpsys window displays | { grep -m1 mCurrentFocus; cat >/dev/null; }')
