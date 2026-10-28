import subprocess
import time
import sys

DEV = "H812X69801234567"
EXPECTED_MD5 = "1536cf1ce9bab0d871c167699167ed7a"
IMG_PATH = "/tmp/OrangeFox_X698_Enhanced.img"

def run_adb(cmd):
    res = subprocess.run(["adb", "-s", DEV] + cmd, capture_output=True, text=True, errors="replace")
    return res.stdout.strip(), res.stderr.strip(), res.returncode

def main():
    print("=== STEP 1: VERIFYING SOURCE IMAGE ON PHONE ===")
    out, err, code = run_adb(["shell", f"md5sum {IMG_PATH}"])
    print(f"MD5 on phone: {out}")
    if EXPECTED_MD5 not in out:
        print("[ERROR] MD5 on phone does not match expected! Aborting.")
        sys.exit(1)

    print("\n=== STEP 2: FLASHING PORTED ORANGEFOX TO boot_a ===")
    cmd_flash = f"dd if={IMG_PATH} of=/dev/block/by-name/boot_a bs=4M && sync"
    out, err, code = run_adb(["shell", cmd_flash])
    print(f"Flash output:\n{out}\n{err}")

    print("\n=== STEP 3: VERIFYING WRITTEN MD5 ON boot_a (BIT-FOR-BIT) ===")
    cmd_verify = "head -c 33554432 /dev/block/by-name/boot_a | md5sum"
    out, err, code = run_adb(["shell", cmd_verify])
    print(f"Written block MD5: {out}")
    if EXPECTED_MD5 not in out:
        print("[CRITICAL ERROR] Block MD5 does not match! Restoring previous TWRP immediately...")
        run_adb(["shell", "dd if=/data/media/twrp_enhanced_X698.img of=/dev/block/by-name/boot_a bs=4M && sync"])
        sys.exit(1)

    print("\n[SUCCESS] OrangeFox successfully written and verified bit-for-bit!")
    print("\n=== STEP 4: REBOOTING TO ORANGEFOX RECOVERY ===")
    run_adb(["reboot", "recovery"])
    print("Reboot signal sent. Waiting for device in recovery mode...")

if __name__ == "__main__":
    main()
