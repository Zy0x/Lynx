import subprocess

p = subprocess.run(
    ['adb', '-s', '192.168.1.13:5555', 'shell', 'su'],
    input='''
for c in /sys/class/thermal/cooling_device*; do
    t=$(cat $c/type 2>/dev/null)
    cur=$(cat $c/cur_state 2>/dev/null)
    max=$(cat $c/max_state 2>/dev/null)
    echo "$(basename $c): type=$t cur=$cur max=$max"
done
exit
''',
    capture_output=True,
    text=True
)

for line in p.stdout.splitlines():
    l_lower = line.lower()
    if any(k in l_lower for k in ['bcct', 'chg', 'battery', 'current', 'pump']):
        print(line)
