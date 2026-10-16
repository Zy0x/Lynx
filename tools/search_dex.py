import zipfile
import re

with zipfile.ZipFile('tools/apatch_base.apk', 'r') as z:
    dex = z.read('classes.dex')

# Search for strings around "Muat" or "Load" or kpm
for s in [b'kpm', b'load', b'Load', b'loadKpm', b'native', b'nativeLoad', b'load_kpm']:
    matches = [m.start() for m in re.finditer(s, dex)]
    print(f"'{s.decode()}' occurrences: {len(matches)}")

# Let's search strings in dex
strings = re.findall(rb'[\x20-\x7e]{4,80}', dex)
for s in strings:
    text = s.decode('ascii', errors='ignore')
    if any(k in text.lower() for k in ['loadkpm', 'kpmload', 'kpmlist', 'kpatch', 'superkey', 'load_module', 'kpm_load']):
        print(text)
