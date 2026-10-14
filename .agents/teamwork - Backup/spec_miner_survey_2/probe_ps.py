import subprocess

p = subprocess.run(
    ['adb', '-s', '192.168.1.13:5555', 'shell', 'su'],
    input='''
ps -A | grep -iE 'thermal|charger|battery|power'
exit
''',
    capture_output=True,
    text=True
)
print(p.stdout)
