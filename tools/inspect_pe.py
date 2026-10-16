import subprocess

target = subprocess.check_output(['powershell', '-ExecutionPolicy', 'Bypass', '-File', '.\\tools\\connect_device.ps1', '-Quiet']).decode().strip()
cmd = "dmesg | grep -iE 'pump_express|pe20|pe40|rt9759' | tail -n 25"
out = subprocess.check_output(['adb', '-s', target, 'shell', 'su', '-c', cmd])
print(out.decode(errors='ignore'))
