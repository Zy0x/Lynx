import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
f="/sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger"
echo "exists: $([ -f "$f" ] && echo yes || echo no)"
echo "content: '$(cat "$f" 2>&1)'"
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
