import zipfile
import re

with zipfile.ZipFile('tools/apatch_base.apk', 'r') as z:
    for soname in ['lib/arm64-v8a/libapjni.so', 'lib/arm64-v8a/libkpatch.so']:
        data = z.read(soname)
        print(f"--- EXPORTS/STRINGS IN {soname} ---")
        strings = re.findall(rb'[a-zA-Z0-9_]{4,60}', data)
        for s in set(strings):
            text = s.decode('ascii', errors='ignore')
            if any(k in text.lower() for k in ['kpm', 'load', 'native', 'patch', 'kernel', 'jni']):
                print(text)
