import subprocess

p = subprocess.run(
    ['adb', '-s', '192.168.1.13:5555', 'shell', 'su'],
    input='''
ls -l /proc/1903/fd/ 2>/dev/null
ls -l /proc/1901/fd/ 2>/dev/null
exit
''',
    capture_output=True,
    text=True
)
print(p.stdout)
