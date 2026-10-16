import subprocess

target = "192.168.1.13:5555"
script = '''
grep -rn "tran_game_mode" /sys/firmware/devicetree/ 2>/dev/null
for f in $(find /sys/firmware/devicetree -name "*dv2*" -o -name "*charger*" -o -name "*battery*"); do
    echo "$f"
done
'''
p = subprocess.run(['adb', '-s', target, 'shell', 'su'], input=script + '\nexit\n', capture_output=True, text=True)
print(p.stdout)
