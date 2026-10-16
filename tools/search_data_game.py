import subprocess

target = "192.168.1.13:5555"
script = '''
cat /proc/kallsyms | grep -i "game" | grep -v " [tT] "
'''
p = subprocess.run(['adb', '-s', target, 'shell', 'su'], input=script + '\nexit\n', capture_output=True, text=True)
print(p.stdout)
