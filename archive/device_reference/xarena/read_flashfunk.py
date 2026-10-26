import zipfile
import json

apk_path = 'archive/device_reference/xarena/XArena.apk'
with zipfile.ZipFile(apk_path, 'r') as z:
    raw = z.read('assets/flash_funk.json').decode('utf-8', errors='ignore')
    with open('archive/device_reference/xarena/flash_funk.txt', 'w', encoding='utf-8') as f:
        f.write(raw)
    print("Wrote flash_funk.txt, size:", len(raw))
    print(raw[:400])
