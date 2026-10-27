import subprocess
import os

DEV = "H812X69801234567"
BACKUP_DIR = r"e:\Data\GitHub\Lynx\Emergency_Recovery\Infinix_X698_Full_Backup"
ESSENTIALS = ["nvram.img", "nvdata.img", "persist.img", "proinfo.img", "protect1.img", "protect2.img", "vbmeta_a.img", "dtbo_a.img", "lk_a.img"]

for f in ESSENTIALS:
    src = os.path.join(BACKUP_DIR, f)
    dst = f"/sdcard/TWRP_BACKUPS/ORIGINAL_CRITICAL/{f}"
    print(f"Pushing {f} to phone...")
    subprocess.run(["adb", "-s", DEV, "push", src, dst])

print("Done pushing essentials to phone!")
