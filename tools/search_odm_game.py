import subprocess

target = "192.168.1.13:5555"
script = '''
grep -rn "tran_game_mode" /odm/ 2>/dev/null
'''
p = subprocess.run(['adb', '-s', target, 'shell', 'su'], input=script + '\nexit\n', capture_output=True, text=True)
print(p.stdout)
