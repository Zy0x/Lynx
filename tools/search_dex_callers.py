import zipfile
import re

with zipfile.ZipFile('tools/apatch_base.apk', 'r') as z:
    dex = z.read('classes.dex')

# In dex, string 'nativeLoadKernelPatchModule' is at string_id index
# Let's find the string_id
str_pos = dex.find(b'nativeLoadKernelPatchModule\x00')
print("str_pos:", str_pos)

# Let's find all occurrences of 'nativeLoad' or KPM in classes.dex strings
strings = re.findall(rb'[\x20-\x7e]{4,80}', dex)
for s in strings:
    t = s.decode('ascii', errors='ignore')
    if any(k in t.lower() for k in ['loadkernelpatchmodule', 'kpmload', 'loadkpm', 'failed to load', 'load kpm']):
        print(t)
