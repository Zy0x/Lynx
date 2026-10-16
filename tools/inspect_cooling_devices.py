import subprocess

script = '''
for c in /sys/class/thermal/cooling_device*; do
    t=$(cat "$c/type" 2>/dev/null)
    cur=$(cat "$c/cur_state" 2>/dev/null)
    max=$(cat "$c/max_state" 2>/dev/null)
    echo "$c: $t = $cur / $max"
done
'''

p = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', script], capture_output=True, text=True)
for line in p.stdout.splitlines():
    if any(k in line.lower() for k in ['bcct', 'chg', 'battery', 'cur', 'thermal', 'pwr']):
        print(line)
