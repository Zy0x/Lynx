import subprocess

script = """#!/system/bin/sh
for r in /sys/class/regulator/regulator.*; do
    n=$(cat $r/name 2>/dev/null)
    v=$(cat $r/microvolts 2>/dev/null)
    s=$(cat $r/state 2>/dev/null)
    case "$n" in
        vproc*|vgpu*|vsram*|vcore*|vdram*)
            echo "$n: ${v}uV (state: $s)"
            ;;
    esac
done
"""

subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", "echo '" + script + "' > /data/local/tmp/read_rails.sh"])
res = subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", "su", "-c", "sh /data/local/tmp/read_rails.sh"], capture_output=True, text=True)
print(res.stdout)
