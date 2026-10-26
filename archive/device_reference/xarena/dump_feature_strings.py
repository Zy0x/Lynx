import subprocess
import re

print("Dumping strings from aapt...")
proc = subprocess.run([r'D:\temp\android-sdk\build-tools\34.0.0\aapt.exe', 'dump', 'strings', r'archive\device_reference\xarena\XArena.apk'], capture_output=True, text=True, encoding='utf-8', errors='ignore')

lines = proc.stdout.splitlines()
print(f"Total lines: {len(lines)}")

# Save all strings to a clean list
out_strings = []
for line in lines:
    m = re.match(r'String #\d+: (.*)', line)
    if m:
        out_strings.append(m.group(1))

print(f"Extracted {len(out_strings)} strings.")

# Filter for game features
game_features = []
keywords = ['kombo', 'combo', 'xboost', 'taktis', 'bahu', 'shoulder', 'heavymode', 'suara', 'magic', 'fps', 'grafis', 'gamepad', 'kontrol', 'pemetaan', 'mapping', 'ketuk', 'sentuh', 'rekam', 'kecepatan', 'jaringan', 'bypass']

for s in out_strings:
    for kw in keywords:
        if kw in s.lower():
            game_features.append(s)
            break

print(f"Found {len(game_features)} feature-related strings.")
with open('archive/device_reference/xarena/game_features.txt', 'w', encoding='utf-8') as f:
    for gf in sorted(list(set(game_features))):
        f.write(gf + '\n')
print("Wrote archive/device_reference/xarena/game_features.txt")
