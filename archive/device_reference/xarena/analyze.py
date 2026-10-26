import zipfile
import re
import os

apk_path = 'archive/device_reference/xarena/XArena.apk'
print(f"Opening {apk_path} ({os.path.getsize(apk_path)} bytes)...")

with zipfile.ZipFile(apk_path, 'r') as z:
    names = z.namelist()
    print(f"Total entries: {len(names)}")
    
    # 1. Assets
    assets = [n for n in names if n.startswith('assets/')]
    print(f"Assets count: {len(assets)}")
    for a in assets[:30]:
        print("  Asset:", a)
        
    # 2. Res/layout
    layouts = [n for n in names if n.startswith('res/layout') or n.startswith('res/xml')]
    print(f"Layouts/xml count: {len(layouts)}")
    for l in layouts:
        if any(k in l.lower() for k in ['game', 'boost', 'combo', 'xboost', 'macro', 'touch', 'shoulder', 'magic', 'overlay', 'panel']):
            print("  Interesting layout:", l)

    # 3. Search strings in all DEX files
    dex_names = [n for n in names if n.endswith('.dex')]
    for dex_name in dex_names:
        print(f"\nScanning {dex_name}...")
        data = z.read(dex_name)
        for kw in ['xboost', 'combo', 'kombo', 'macro', 'shoulder', 'magicvoice', 'touchshare', 'heavymode', 'gamemaster']:
            pattern = re.compile(rb'[a-zA-Z0-9_\-\.]{0,30}' + kw.encode() + rb'[a-zA-Z0-9_\-\.]{0,30}', re.IGNORECASE)
            matches = set(pattern.findall(data))
            if matches:
                sample = [m.decode('utf-8', errors='ignore') for m in list(matches)[:12]]
                print(f"  [{kw}] {len(matches)} matches: {sample}")
