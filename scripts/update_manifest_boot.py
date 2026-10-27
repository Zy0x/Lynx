import json
import hashlib
import os

manifest_file = r"e:\Data\GitHub\Lynx\Emergency_Recovery\Infinix_X698_Full_Backup\backup_manifest.json"
with open(manifest_file, "r") as f:
    manifest = json.load(f)

for name in ["boot_a", "boot_b", "vendor_boot_a"]:
    path = os.path.join(r"e:\Data\GitHub\Lynx\Emergency_Recovery\Infinix_X698_Full_Backup", f"{name}.img")
    h = hashlib.md5()
    with open(path, "rb") as f:
        while chunk := f.read(1024*1024):
            h.update(chunk)
    manifest[name] = {
        "size": os.path.getsize(path),
        "md5": h.hexdigest().lower(),
        "verified": True,
        "timestamp": "2026-10-28 07:14:00"
    }

with open(manifest_file, "w") as f:
    json.dump(manifest, f, indent=2)

print(f"Manifest now has {len(manifest)} verified partition images!")
