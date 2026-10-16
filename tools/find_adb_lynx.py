import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
ls -la /dev/lynx*
cat /data/adb/modules/Lynx/system.prop 2>/dev/null
find /data/adb/ -name "*config*" 2>/dev/null
find /data/adb/ -name "*lynx*" 2>/dev/null
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
