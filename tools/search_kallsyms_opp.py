import subprocess

cmd = """
echo 0 > /proc/sys/kernel/kptr_restrict
grep -i 'mt_cpufreq' /proc/kallsyms | head -n 40
"""
p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su'], input=cmd + '\nexit\n', capture_output=True, text=True)
print(p.stdout.strip())
