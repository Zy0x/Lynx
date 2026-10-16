import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
check_val() {
    val="$1"
    if [ "${#val}" -ge 10 ] || [ "$val" -lt 0 ] 2>/dev/null; then
        echo "$val -> OVERFLOW (set to 0)"
    else
        echo "$val -> VALID"
    fi
}
check_val "390"
check_val "4294967249"
check_val "209264"
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
