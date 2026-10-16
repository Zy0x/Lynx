import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
nodes = [
    "/proc/driver/thermal/clabcct",
    "/proc/driver/thermal/clabcct_lcmoff",
    "/proc/driver/thermal/clbcct",
    "/proc/driver/thermal/tzbts",
    "/proc/driver/thermal/tzbts_param",
    "/proc/mtk_battery_cmd/current_cmd",
]
for n in nodes:
    cmd = ["adb", "-s", "192.168.1.13:5555", "shell", f"su -c 'cat {n} 2>/dev/null'"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    print(f"=== {n} ===")
    print(res.stdout.strip())
