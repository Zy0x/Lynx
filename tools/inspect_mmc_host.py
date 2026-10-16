import subprocess

TARGET = "192.168.1.13:5555"

def adb_su(cmd):
    res = subprocess.run(['adb', '-s', TARGET, 'shell', f"su -c '{cmd}'"], capture_output=True, text=True, timeout=10)
    return res.stdout.strip()

print("=== MMC HOSTS ===")
for host in adb_su("ls -d /sys/class/mmc_host/mmc*").split():
    print(f"\nHost: {host}")
    for node in ["clock", "timing", "type", "caps", "caps2", "max_seg_size", "max_req_size"]:
        val = adb_su(f"cat {host}/{node} 2>/dev/null")
        if val:
            print(f"  {node}: {val}")

print("\n=== MMC DEVICES ===")
for dev in adb_su("ls -d /sys/bus/mmc/devices/mmc* 2>/dev/null").split():
    print(f"\nDevice: {dev}")
    for node in ["type", "name", "speed", "enhanced_area_offset", "preferred_erase_size"]:
        val = adb_su(f"cat {dev}/{node} 2>/dev/null")
        if val:
            print(f"  {node}: {val}")
