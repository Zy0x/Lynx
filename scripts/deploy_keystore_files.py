import sqlite3
import subprocess
import os

conn = sqlite3.connect(r"e:\Data\GitHub\Lynx\Emergency_Recovery\persistent.sqlite")
cursor = conn.cursor()

cursor.execute("SELECT blob FROM blobentry WHERE keyentryid = 3272235486342031835;")
blob = cursor.fetchone()[0]

local_tmp = r"e:\Data\GitHub\Lynx\Emergency_Recovery\synthetic_blob.bin"
with open(local_tmp, "wb") as f:
    f.write(blob)

print(f"Wrote {len(blob)} bytes to {local_tmp}")

DEV = "H812X69801234567"
subprocess.run(["adb", "-s", DEV, "push", local_tmp, "/tmp/synthetic_blob.bin"])

# Target filenames to create
filenames = [
    "/data/misc/keystore/1000_USRSKEY_synthetic_password_ac7ac6faa7730978",
    "/data/misc/keystore/103_USRSKEY_synthetic_password_ac7ac6faa7730978",
    "/data/misc/keystore/USRSKEY_synthetic_password_ac7ac6faa7730978",
    "/data/misc/keystore/user_0/1000_USRSKEY_synthetic_password_ac7ac6faa7730978",
    "/data/misc/keystore/user_0/103_USRSKEY_synthetic_password_ac7ac6faa7730978",
    "/data/misc/keystore/user_0/USRSKEY_synthetic_password_ac7ac6faa7730978",
]

cmd = "mkdir -p /data/misc/keystore/user_0; "
for fn in filenames:
    cmd += f"cp /tmp/synthetic_blob.bin {fn}; chmod 600 {fn}; chown keystore:keystore {fn}; "

subprocess.run(["adb", "-s", DEV, "shell", cmd])
print("Successfully deployed synthetic password key files!")
