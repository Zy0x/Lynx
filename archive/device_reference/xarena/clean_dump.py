import subprocess
import json

print("Running aapt dump strings...")
proc = subprocess.run([r'D:\temp\android-sdk\build-tools\34.0.0\aapt.exe', 'dump', 'strings', r'archive\device_reference\xarena\XArena.apk'], capture_output=True, text=True, encoding='utf-8', errors='ignore')

all_strings = []
for line in proc.stdout.splitlines():
    if line.startswith('String #'):
        idx = line.find(': ')
        if idx != -1:
            all_strings.append(line[idx+2:].strip())

print(f"Total strings: {len(all_strings)}")

# Filter English and Indonesian strings
en_id_strings = []
for s in all_strings:
    # ignore file paths
    if s.startswith('res/') or s.startswith('assets/') or s.startswith('lib/') or s.startswith('META-INF'):
        continue
    # ignore short garbage
    if len(s) < 3:
        continue
    # check if mostly ascii
    ascii_count = sum(1 for c in s if ord(c) < 128)
    if ascii_count / len(s) > 0.85:
        en_id_strings.append(s)

print(f"Clean ASCII/Latin strings: {len(en_id_strings)}")

# Search for features
with open('archive/device_reference/xarena/all_clean_strings.txt', 'w', encoding='utf-8') as f:
    for s in en_id_strings:
        f.write(s + '\n')

print("Wrote all_clean_strings.txt")
