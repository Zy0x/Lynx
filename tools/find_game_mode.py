import subprocess

target = "192.168.1.13:5555"
script = '''
for f in $(find /sys -name "*tran_game_mode*" 2>/dev/null); do
    echo "$f = $(cat $f 2>/dev/null)"
done
'''
p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
