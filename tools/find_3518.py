with open('tools/apatch_base.apk', 'rb') as f:
    pass

import zipfile
with zipfile.ZipFile('tools/apatch_base.apk') as z:
    dex = z.read('classes.dex')

target = b'\x18\x35'
pos = 0
found = []
while True:
    idx = dex.find(target, pos)
    if idx == -1: break
    found.append(idx)
    pos = idx + 1

print(f"Found {len(found)} occurrences of 0x3518:")
for idx in found[:10]:
    print(f"0x{idx:x}: {dex[max(0, idx-4):idx+6]}")
