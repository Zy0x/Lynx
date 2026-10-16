import zipfile
import re

with zipfile.ZipFile('tools/apatch_base.apk', 'r') as z:
    apjni = z.read('lib/arm64-v8a/libapjni.so')

# Search for strings near nativeLoadKernelPatchModule
idx = apjni.find(b'nativeLoadKernelPatchModule')
print("nativeLoadKernelPatchModule index:", idx)
if idx != -1:
    print("Surrounding bytes:", apjni[max(0, idx-100):idx+200])
