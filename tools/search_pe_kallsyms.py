import subprocess

def run(cmd):
    p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return p.stdout.strip()

print("=== DV2 & RT9759 in kallsyms ===")
print(run('grep -iE "dv2|rt9759" /proc/kallsyms | head -n 30'))
print("=== CHARGER LIMIT in kallsyms ===")
print(run('grep -iE "charging_current|ibat_limit|chg_curr" /proc/kallsyms | head -n 30'))
