import zipfile
import re

with zipfile.ZipFile('tools/apatch_base.apk', 'r') as z:
    jar_data = z.read('assets/main.jar')
    with open('tools/main.jar', 'wb') as f:
        f.write(jar_data)
    print("main.jar size:", len(jar_data))
    
    kpatch_so = z.read('lib/arm64-v8a/libkpatch.so')
    with open('tools/libkpatch.so', 'wb') as f:
        f.write(kpatch_so)
    print("libkpatch.so size:", len(kpatch_so))
