import zipfile
import re

with zipfile.ZipFile('tools/apatch_base.apk', 'r') as z:
    dex = z.read('classes.dex')

# Let's search for references to nativeLoadKernelPatchModule
# In dex format, find method_id or string index
str_idx = dex.find(b'nativeLoadKernelPatchModule')
print("String offset:", str_idx)

# Search for any string containing "kpm" or "load" or error toast
strings = re.findall(rb'[\x20-\x7e]{3,60}', dex)
for s in strings:
    t = s.decode('ascii', errors='ignore')
    if any(k in t.lower() for k in ['load kpm', 'loadkpm', 'failed to load', 'load failed', 'load_module']):
        print(t)
