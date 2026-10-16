import subprocess

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)

# Test if we can read /proc/kcore at specific symbol address
def test_kcore(addr, size=64):
    cmd = f"dd if=/proc/kcore bs=1 skip={addr} count={size} 2>/dev/null | xxd -p"
    res = subprocess.run(["adb", "-s", "192.168.1.13:5555", "shell", "su", "-c", cmd], capture_output=True, text=True)
    return res.stdout.strip()

print("Testing /proc/kcore read:")
out = test_kcore(0xffffff80dc36906c, 32)
print("Hex output:", out)
