import subprocess

script = """
for r in /sys/class/regulator/regulator.*; do
    name=$(cat $r/name 2>/dev/null)
    uv=$(cat $r/microvolts 2>/dev/null)
    echo "$r: $name = ${uv}uV"
done
"""
res = subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", "su", "-c", script], capture_output=True, text=True)
print("REGULATORS:")
for line in res.stdout.splitlines():
    if any(k in line.lower() for k in ["vproc", "vgpu", "vcore", "vdram", "cpu", "gpu"]):
        print(line)
