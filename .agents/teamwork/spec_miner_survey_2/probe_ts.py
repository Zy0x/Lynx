import subprocess

p = subprocess.run(
    ['adb', '-s', '192.168.1.13:5555', 'shell', 'su'],
    input='''
cmd thermalservice -h 2>&1
dumpsys thermalservice 2>&1 | head -n 30
exit
''',
    capture_output=True,
    text=True
)
print(p.stdout)
