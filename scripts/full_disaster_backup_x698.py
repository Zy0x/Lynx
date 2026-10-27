import subprocess
import hashlib
import os
import sys
import time
import json

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

DEV_SERIAL = "H812X69801234567"
BACKUP_DIR = "e:\\Data\\GitHub\\Lynx\\Emergency_Recovery\\Infinix_X698_Full_Backup"

PARTITIONS_TO_BACKUP = [
    # 1. Hardware Identity & Radio (IRREPLACEABLE - IMEI, MAC, Calibration)
    "nvram",
    "nvdata",
    "nvcfg",
    "proinfo",
    "protect1",
    "protect2",
    "sec1",
    "seccfg",

    # 2. AVB & dm-verity Security Trees
    "vbmeta_a",
    "vbmeta_b",
    "vbmeta_system_a",
    "vbmeta_system_b",
    "vbmeta_vendor_a",
    "vbmeta_vendor_b",

    # 3. Bootloader, Device Tree, Kernel, & Display
    "dtbo_a",
    "dtbo_b",
    "lk_a",
    "lk_b",
    "logo_a",
    "logo_b",
    "misc",
    "para",
    "boot_para",
    "vendor_boot_b",

    # 4. Keystore, TEE, & Storage Metadata
    "persist",
    "metadata",
    "tee_a",
    "tee_b",
    "scp_a",
    "scp_b",
    "sspm_a",
    "sspm_b",
    "spmfw_a",
    "spmfw_b",
    "gz_a",
    "gz_b"
]

def get_file_md5(filepath):
    h = hashlib.md5()
    with open(filepath, "rb") as f:
        while chunk := f.read(1024*1024):
            h.update(chunk)
    return h.hexdigest().lower()

def main():
    os.makedirs(BACKUP_DIR, exist_ok=True)
    manifest_file = os.path.join(BACKUP_DIR, "backup_manifest.json")
    manifest = {}
    if os.path.exists(manifest_file):
        try:
            with open(manifest_file, "r") as f:
                manifest = json.load(f)
        except Exception:
            manifest = {}

    print(f"=== INITIATING FULL DISASTER RECOVERY BACKUP FOR INFINIX X698 ===")
    print(f"Target PC Folder: {BACKUP_DIR}")
    print(f"Total Partitions to Process: {len(PARTITIONS_TO_BACKUP)}\n")

    success_count = 0
    fail_count = 0

    for idx, part in enumerate(PARTITIONS_TO_BACKUP, 1):
        target_img = os.path.join(BACKUP_DIR, f"{part}.img")
        
        # Check if already backed up and verified
        if os.path.exists(target_img) and part in manifest and manifest[part].get("verified", False):
            print(f"[{idx}/{len(PARTITIONS_TO_BACKUP)}] {part}: Already backed up & verified ({manifest[part]['size']} bytes). Skipping.")
            success_count += 1
            continue

        print(f"[{idx}/{len(PARTITIONS_TO_BACKUP)}] Processing {part}...")
        
        # 1. Query device MD5 and byte size
        cmd_info = f"md5sum /dev/block/by-name/{part} && blockdev --getsize64 /dev/block/by-name/{part}"
        res = subprocess.run(["adb", "-s", DEV_SERIAL, "shell", f"su -c '{cmd_info}'"], capture_output=True, text=True)
        if res.returncode != 0 or not res.stdout.strip():
            print(f"  [ERROR] Error querying device for {part}: {res.stderr}")
            fail_count += 1
            continue
        
        lines = res.stdout.strip().splitlines()
        dev_md5 = lines[0].split()[0].lower()
        dev_size = int(lines[1].strip())
        print(f"  Device: {dev_size:,} bytes | MD5: {dev_md5}")

        # 2. Dump to tmp on phone
        tmp_on_phone = f"/sdcard/Download/_backup_tmp_{part}.img"
        dump_cmd = f"dd if=/dev/block/by-name/{part} of={tmp_on_phone} bs=4M && chmod 666 {tmp_on_phone}"
        res_dd = subprocess.run(["adb", "-s", DEV_SERIAL, "shell", f"su -c '{dump_cmd}'"], capture_output=True, text=True)
        if res_dd.returncode != 0:
            print(f"  [ERROR] Error dumping {part} on phone: {res_dd.stderr}")
            fail_count += 1
            continue

        # 3. Pull to PC
        res_pull = subprocess.run(["adb", "-s", DEV_SERIAL, "pull", tmp_on_phone, target_img], capture_output=True, text=True)
        
        # 4. Delete tmp on phone immediately to conserve free space
        subprocess.run(["adb", "-s", DEV_SERIAL, "shell", f"rm -f {tmp_on_phone}"], capture_output=True, text=True)

        if not os.path.exists(target_img):
            print(f"  [ERROR] Failed to pull {part} to PC!")
            fail_count += 1
            continue

        # 5. Verify PC MD5 & Size
        pc_size = os.path.getsize(target_img)
        pc_md5 = get_file_md5(target_img)

        if pc_size == dev_size and pc_md5 == dev_md5:
            print(f"  [OK] VERIFIED 100% BIT-FOR-BIT! PC Size: {pc_size:,} bytes | MD5: {pc_md5}")
            manifest[part] = {
                "size": pc_size,
                "md5": pc_md5,
                "verified": True,
                "timestamp": time.strftime("%Y-%m-%d %H:%M:%S")
            }
            success_count += 1
            # Save manifest incrementally
            with open(manifest_file, "w") as f:
                json.dump(manifest, f, indent=2)
        else:
            print(f"  [ERROR] MISMATCH! Dev: {dev_size}/{dev_md5} vs PC: {pc_size}/{pc_md5}")
            fail_count += 1

    print(f"\n=== BACKUP SUMMARY ===")
    print(f"Success: {success_count}/{len(PARTITIONS_TO_BACKUP)}")
    print(f"Failed:  {fail_count}/{len(PARTITIONS_TO_BACKUP)}")
    print(f"Manifest written to: {manifest_file}")

if __name__ == "__main__":
    main()
