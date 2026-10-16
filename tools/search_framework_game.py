import subprocess

target = "192.168.1.13:5555"

# Search in /system/framework, /vendor/etc, /system_ext for tran_game_mode
script = '''
grep -rn "tran_game_mode" /vendor/etc/ 2>/dev/null
grep -rn "tran_game_mode" /system/etc/ 2>/dev/null
'''
p = subprocess.run(['adb', '-s', target, 'shell', 'su'], input=script + '\nexit\n', capture_output=True, text=True)
print(p.stdout)
