import zipfile
import re

with zipfile.ZipFile('tools/main.jar') as z:
    dex = z.read('classes.dex')

strings = re.findall(rb'[\x20-\x7e]{3,80}', dex)
for s in strings:
    t = s.decode('ascii', errors='ignore')
    if any(k in t.lower() for k in ['kpm', 'patch', 'load', 'native', 'super']):
        print(t)
