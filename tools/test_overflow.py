import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
if [ 390 -gt 4000000000 ]; then
    echo "BUG: toybox thinks 390 is greater than 4000000000!"
else
    echo "OK: 390 is not greater"
fi
echo "exit code: $?"
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)
    print("STDERR:\n", res.stderr)

if __name__ == "__main__":
    run()
