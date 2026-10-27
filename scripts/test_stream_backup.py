import subprocess
import hashlib
import os

def test_stream():
    dev = "H812X69801234567"
    # Get MD5 on device
    res = subprocess.run(["adb", "-s", dev, "shell", "su -c 'md5sum /dev/block/by-name/dtbo_a'"], capture_output=True, text=True)
    dev_md5 = res.stdout.strip().split()[0].lower()
    print("Device MD5:", dev_md5)

    # Stream to PC
    pc_file = "e:\\Data\\GitHub\\Lynx\\Emergency_Recovery\\test_dtbo_a.img"
    os.makedirs(os.path.dirname(pc_file), exist_ok=True)
    with open(pc_file, "wb") as f:
        p = subprocess.Popen(["adb", "-s", dev, "exec-out", "su -c 'cat /dev/block/by-name/dtbo_a'"], stdout=f)
        p.wait()

    # Calculate PC MD5
    h = hashlib.md5()
    with open(pc_file, "rb") as f:
        while chunk := f.read(1024*1024):
            h.update(chunk)
    pc_md5 = h.hexdigest().lower()
    print("PC MD5:    ", pc_md5)
    print("Match:", dev_md5 == pc_md5, "Size:", os.path.getsize(pc_file))
    if os.path.exists(pc_file):
        os.remove(pc_file)

test_stream()
