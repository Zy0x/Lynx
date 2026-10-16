import subprocess

def run_su(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su'], input=cmd + '\nexit\n', capture_output=True, text=True)
    return p.stdout.strip()

print(run_su("""
cat /proc/kallsyms | grep -iE 'charger_enable|charging_enable|charge_enable|enable_charging|enable_charge' | head -n 40
"""))
