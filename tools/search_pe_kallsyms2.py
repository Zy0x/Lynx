import subprocess

target = subprocess.check_output(['powershell', '-ExecutionPolicy', 'Bypass', '-File', '.\\tools\\connect_device.ps1', '-Quiet']).decode().strip()
cmd = "grep -i 'pump_express' /proc/kallsyms"
out = subprocess.check_output(['adb', '-s', target, 'shell', 'su', '-c', cmd])
print(out.decode(errors='ignore'))
