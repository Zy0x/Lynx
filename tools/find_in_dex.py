import zipfile
import re

with zipfile.ZipFile('tools/apatch_base.apk', 'r') as z:
    dex = z.read('classes.dex')

idx = 0
while True:
    pos = dex.find(b'nativeLoadKernelPatchModule', idx)
    if pos == -1:
        break
    print(f"Found in dex at {pos}:", dex[max(0, pos-40):pos+80])
    idx = pos + 1
