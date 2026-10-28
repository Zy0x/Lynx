import subprocess
import time
import sys

DEV = "H812X69801234567"
EXPECTED_MD5 = "8dbfa94f97903eaebe19085546e90779"
IMG_PATH = "/sdcard/Download/twrp_enhanced_X698.img"

def run_adb(cmd):
    res = subprocess.run(["adb", "-s", DEV] + cmd, capture_output=True, text=True, errors="replace")
    return res.stdout.strip(), res.stderr.strip(), res.returncode

def main():
    print("=== STEP 1: VERIFYING SOURCE IMAGE ON PHONE ===")
    out, err, code = run_adb(["shell", f"su -c 'md5sum {IMG_PATH}'"])
    print(f"MD5 on phone: {out}")
    if EXPECTED_MD5 not in out:
        print("[ERROR] MD5 does not match expected! Aborting.")
        sys.exit(1)

    print("\n=== STEP 2: FLASHING ENHANCED TWRP TO boot_a ===")
    cmd_flash = f"dd if={IMG_PATH} of=/dev/block/by-name/boot_a bs=4M && sync"
    out, err, code = run_adb(["shell", f"su -c '{cmd_flash}'"])
    print(f"Flash output:\n{out}\n{err}")

    print("\n=== STEP 3: VERIFYING WRITTEN MD5 ON boot_a ===")
    cmd_verify = "head -c 33554432 /dev/block/by-name/boot_a | md5sum"
    out, err, code = run_adb(["shell", f"su -c '{cmd_verify}'"])
    print(f"Written block MD5: {out}")
    if EXPECTED_MD5 not in out:
        print("[ERROR] Block MD5 does not match! Do not reboot.")
        sys.exit(1)

    print("\n[SUCCESS] Enhanced TWRP successfully written and verified bit-for-bit!")
    print("\n=== STEP 4: REBOOTING TO RECOVERY ===")
    run_adb(["reboot", "recovery"])
    print("Reboot signal sent. Waiting for device in recovery mode...")

if __name__ == "__main__":
    main()
