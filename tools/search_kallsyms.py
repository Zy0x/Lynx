import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)

def search(term):
    cmd = f"grep {term} /proc/kallsyms"
    res = subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", "su", "-c", cmd], capture_output=True, text=True)
    return res.stdout.splitlines()

for term in ["thermal_cooling_device", "cdev", "cooling_device", "thermal_zone"]:
    print(f"=== SEARCH: {term} ===")
    lines = search(term)
    print(f"Count: {len(lines)}")
    for l in lines[:20]:
        print(" ", l)
