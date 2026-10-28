import subprocess

dev = "H812X69801234567"
for fn in ["0000000000000000.handle", "ac7ac6faa7730978.metrics", "ac7ac6faa7730978.pwd", "ac7ac6faa7730978.spblob"]:
    res = subprocess.run(["adb", "-s", dev, "shell", f"xxd -p /data/system_de/0/spblob/{fn}"], capture_output=True, text=True)
    print(f"=== {fn} ===")
    print(res.stdout.strip())
