import subprocess

target = '192.168.1.13:5555'
res = subprocess.run(['adb', '-s', target, 'shell', 'dumpsys', 'SurfaceFlinger', '--list'], capture_output=True, text=True)
layers = [l.strip() for l in res.stdout.splitlines() if 'mihoyo' in l.lower() or 'genshin' in l.lower()]
print("Matching layers:")
for l in layers:
    print(" -", l)
    lat = subprocess.run(['adb', '-s', target, 'shell', 'dumpsys', 'SurfaceFlinger', '--latency', f"'{l}'"], capture_output=True, text=True)
    lines = [x for x in lat.stdout.splitlines() if x.strip()]
    print(f"   Lines count: {len(lines)}")
    if len(lines) > 1:
        print(f"   Sample: {lines[1]}")
