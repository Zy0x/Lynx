import subprocess

def run():
    target = subprocess.check_output(["powershell", "-ExecutionPolicy", "Bypass", "-File", "tools/connect_device.ps1", "-Quiet"]).decode().strip()
    cmd = """
parse_ibus() {
    raw="$1"
    if [ "${#raw}" -ge 10 ] || [ "$raw" -le 0 ] 2>/dev/null; then
        echo 0
        return
    fi
    if [ "$raw" -gt 500000 ] 2>/dev/null; then
        echo $(( raw / 1000 ))
    elif [ "$raw" -gt 50000 ] 2>/dev/null; then
        echo $(( raw / 100 ))
    else
        echo "$raw"
    fi
}

echo "209264 -> $(parse_ibus 209264) mA"
echo "1500000 -> $(parse_ibus 1500000) mA"
echo "2500 -> $(parse_ibus 2500) mA"
echo "4294967249 -> $(parse_ibus 4294967249) mA"
"""
    res = subprocess.run(["adb", "-s", target, "shell", "su"], input=cmd, capture_output=True, text=True)
    print("STDOUT:\n", res.stdout)

if __name__ == "__main__":
    run()
