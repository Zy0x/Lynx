import zipfile
import re

with zipfile.ZipFile('tools/apatch_base.apk', 'r') as z:
    dex = z.read('classes.dex')

classes = re.findall(rb'Lme/bmax/apatch/[a-zA-Z0-9_/$]+;', dex)
print(f"Total APatch classes: {len(classes)}")
for c in sorted(set(classes)):
    t = c.decode('ascii', errors='ignore')
    if any(k in t.lower() for k in ['kpm', 'patch', 'kernel', 'native', 'load', 'viewmodel', 'ui']):
        print(t)
