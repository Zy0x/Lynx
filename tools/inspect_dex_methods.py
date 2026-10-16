import zipfile
import re

with zipfile.ZipFile('tools/apatch_base.apk', 'r') as z:
    dex = z.read('classes.dex')

# Find occurrences of KPModel or Natives in dex
for target in [b'KPModel', b'nativeLoadKernelPatchModule']:
    pos = 0
    while True:
        idx = dex.find(target, pos)
        if idx == -1:
            break
        print(f"Match for {target} at {idx}:")
        chunk = dex[max(0, idx-50):min(len(dex), idx+200)]
        printable = "".join(chr(b) if 32 <= b <= 126 else f"\\x{b:02x}" for b in chunk)
        print("  ", printable)
        pos = idx + len(target)
