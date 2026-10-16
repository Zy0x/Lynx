import subprocess

p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', '/vendor/bin/thermal_manager /vendor/etc/.tp/thermal.off.conf'], capture_output=True, text=True)
print("STDOUT:", p.stdout)
print("STDERR:", p.stderr)
