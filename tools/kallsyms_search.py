import subprocess

def run_su(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su'], input=cmd + '\nexit\n', capture_output=True, text=True)
    return p.stdout.strip()

print(run_su("""
cat /proc/kallsyms | grep -iE 'bypass|charging_enable|charge_enable|stop_charge|suspend_charge|tran_charge|charger_full' | head -n 40
"""))
