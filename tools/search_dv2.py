import subprocess

def run_su(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su'], input=cmd + '\nexit\n', capture_output=True, text=True)
    return p.stdout.strip()

print("--- SYSFS NODES CONTAINING dv ---")
print(run_su("find /sys/ -name '*dv2*' -o -name '*dvchg*' 2>/dev/null"))

print("--- KALLSYMS CONTAINING dv2 or dvchg ---")
print(run_su("cat /proc/kallsyms | grep -iE 'dv2|dvchg' | head -n 40"))
