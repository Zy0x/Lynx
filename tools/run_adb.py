import subprocess
import sys

def run_su(cmd):
    full_cmd = f"su -c '{cmd}'"
    res = subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", "su", "-c", cmd], capture_output=True, text=True)
    print("STDOUT:\n" + res.stdout)
    if res.stderr:
        print("STDERR:\n" + res.stderr)
    return res.returncode

if __name__ == "__main__":
    if len(sys.argv) > 1:
        cmd = " ".join(sys.argv[1:])
        sys.exit(run_su(cmd))
