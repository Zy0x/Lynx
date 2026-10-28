import subprocess
import time
import os
import sys

IMG_TWRP = r"e:\Data\GitHub\Lynx\twrp_x698_enhanced\output\twrp_enhanced_X698.img"
IMG_STOCK = r"e:\Data\GitHub\Lynx\Emergency_Recovery\Infinix_X698_Full_Backup\boot_a.img"

print("Listening for Infinix X698 (Fastboot, ADB, or Preloader)...", flush=True)

while True:
    # 1. Check Fastboot
    res_fb = subprocess.run(["fastboot", "devices"], capture_output=True, text=True)
    if res_fb.stdout.strip():
        dev_id = res_fb.stdout.strip()
        print(f"\n[FASTBOOT DETECTED] {dev_id}", flush=True)
        print("Restoring verified working TWRP to boot_a...", flush=True)
        p = subprocess.run(["fastboot", "flash", "boot_a", IMG_TWRP], capture_output=True, text=True)
        print(p.stdout, flush=True)
        print(p.stderr, flush=True)
        print("Rebooting to recovery...", flush=True)
        subprocess.run(["fastboot", "reboot", "recovery"])
        print("[RESTORE COMPLETED]", flush=True)
        break

    # 2. Check ADB
    res_adb = subprocess.run(["adb", "devices"], capture_output=True, text=True)
    lines = [line for line in res_adb.stdout.strip().splitlines() if "\t" in line]
    if lines:
        print(f"\n[ADB DETECTED] {lines}", flush=True)
        break

    time.sleep(2)
