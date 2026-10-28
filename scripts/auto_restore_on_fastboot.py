import subprocess
import time
import os
import sys

IMG_TWRP = r"e:\Data\GitHub\Lynx\twrp_x698_enhanced\output\twrp_enhanced_X698.img"
IMG_STOCK = r"e:\Data\GitHub\Lynx\Emergency_Recovery\Infinix_X698_Full_Backup\boot_a.img"

print("Waiting for Infinix X698 in FASTBOOT mode (listening for 10 minutes)...", flush=True)

found = False
for i in range(300): # wait up to 10 minutes
    res = subprocess.run(["fastboot", "devices"], capture_output=True, text=True)
    out = res.stdout.strip()
    if out:
        print(f"\n[DETECTED] Device found in Fastboot: {out}", flush=True)
        found = True
        break
    time.sleep(2)

if found:
    print("\n[ACTION] Restoring verified working TWRP to boot_a...", flush=True)
    p1 = subprocess.run(["fastboot", "flash", "boot_a", IMG_TWRP], capture_output=True, text=True)
    print(p1.stdout, flush=True)
    print(p1.stderr, flush=True)
    
    print("\n[ACTION] Rebooting recovery...", flush=True)
    subprocess.run(["fastboot", "reboot", "recovery"])
    print("[SUCCESS] Phone restored and rebooted to verified recovery!", flush=True)
else:
    print("\nTimeout waiting for fastboot.", flush=True)
