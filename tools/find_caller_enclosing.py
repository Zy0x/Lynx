import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)
cmd = ["adb", "-s", "192.168.1.13:5555", "shell", "su -c 'echo 0 > /proc/sys/kernel/kptr_restrict; cat /proc/kallsyms'"]
res = subprocess.run(cmd, capture_output=True, text=True)

for addr in [0xFFFFFF80DC36387C, 0xFFFFFF80DC365318]:
    closest = None
    for line in res.stdout.splitlines():
        parts = line.split()
        if len(parts) >= 3 and parts[0].startswith("ffffff80"):
            val = int(parts[0], 16)
            if val <= addr:
                closest = (val, parts[1], parts[2])
            else:
                break
    print(f"Address 0x{addr:X} is inside: {closest}")
