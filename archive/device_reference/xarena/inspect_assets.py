import zipfile
import json

apk_path = 'archive/device_reference/xarena/XArena.apk'
with zipfile.ZipFile(apk_path, 'r') as z:
    for name in ['assets/flash_funk.json', 'assets/X6871.json', 'assets/guide_moba_1.json']:
        try:
            raw = z.read(name).decode('utf-8', errors='ignore')
            print(f"=== {name} ===")
            print(raw[:500])
        except Exception as e:
            print(f"Error {name}: {e}")
