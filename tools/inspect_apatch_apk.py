import subprocess
import zipfile
import re
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

apk_path = subprocess.run(['adb', 'shell', 'pm', 'path', 'me.bmax.apatch'], capture_output=True, text=True).stdout.strip().replace('package:', '')
print("APK path on device:", apk_path)

# Pull base.apk to temp
subprocess.run(['adb', 'pull', apk_path, 'tools/apatch_base.apk'])

with zipfile.ZipFile('tools/apatch_base.apk', 'r') as z:
    for name in z.namelist():
        if name.endswith('.dex'):
            data = z.read(name)
            # Search for loadKpm or load_kpm or kpm
            matches = re.findall(rb'[a-zA-Z0-9_]{3,30}[kK][pP][mM][a-zA-Z0-9_]{0,30}', data)
            unique = set(m.decode('ascii', errors='ignore') for m in matches)
            print(f"Matches in {name}:", list(unique)[:20])
