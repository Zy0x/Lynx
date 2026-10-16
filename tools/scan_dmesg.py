import subprocess

res = subprocess.run(['adb', 'shell', 'su', '-c', 'dmesg'], capture_output=True, text=True, errors='replace')
lines = res.stdout.splitlines()

# Search backwards for KP or kpm or fail
matched = []
for line in lines[-500:]:
    if any(k in line.lower() for k in ['kp', 'kpm', 'lynx', 'fail', 'error', 'symbol', 'reloc', 'load', 'init']):
        matched.append(line)

print(f"Total lines: {len(lines)}, Matched in last 500: {len(matched)}")
for l in matched[-30:]:
    print(l)
