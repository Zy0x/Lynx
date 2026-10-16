import subprocess

cmd = """su -c 'time sh -c "dumpsys window displays | awk \\"/mCurrentFocus=/ { print }\\""'"""
res = subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", cmd], capture_output=True, text=True)
print("STDOUT:", res.stdout)
print("STDERR:", res.stderr)
