import subprocess
import hashlib
import os

def test_safe_pull():
    dev = "H812X69801234567"
    # Get device MD5 directly from block device
    res = subprocess.run(["adb", "-s", dev, "shell", "su -c 'md5sum /dev/block/by-name/dtbo_a'"], capture_output=True, text=True)
    dev_md5 = res.stdout.strip().split()[0].lower()
    print("Device MD5: ", dev_md5)

    # dd to /sdcard/Download/_tmp_test.img
    subprocess.run(["adb", "-s", dev, "shell", "su -c 'dd if=/dev/block/by-name/dtbo_a of=/sdcard/Download/_tmp_test.img bs=1M && chmod 666 /sdcard/Download/_tmp_test.img'"], capture_output=True, text=True)

    # Pull to PC
    pc_file = "e:\\Data\\GitHub\\Lynx\\Emergency_Recovery\\test_dtbo_a.img"
    os.makedirs(os.path.dirname(pc_file), exist_ok=True)
    subprocess.run(["adb", "-s", dev, "pull", "/sdcard/Download/_tmp_test.img", pc_file], capture_output=True, text=True)

    # Delete tmp on device
    subprocess.run(["adb", "-s", dev, "shell", "rm -f /sdcard/Download/_tmp_test.img"])

    # Verify PC MD5
    h = hashlib.md5()
    with open(pc_file, "rb") as f:
        while chunk := f.read(1024*1024):
            h.update(chunk)
    pc_md5 = h.hexdigest().lower()
    print("PC MD5:     ", pc_md5)
    print("Match:      ", dev_md5 == pc_md5, "Size:", os.path.getsize(pc_file))
    if os.path.exists(pc_file):
        os.remove(pc_file)

test_safe_pull()
