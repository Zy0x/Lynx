import subprocess

def run(cmd):
    res = subprocess.run(['adb', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
    return res.stdout.strip()

run("echo 0 > /proc/sys/kernel/kptr_restrict")
print(run("grep -i '__dv2_check_thermal_level' /proc/kallsyms"))
