import subprocess
import struct

subprocess.run(["adb", "connect", "192.168.1.13:5555"], capture_output=True)

for name in ["tpcb_level_def", "tpcb_curlmt", "tbat_level_def", "tbat_curlmt", "tdvchg_level_def", "tdvchg_curlmt"]:
    cmd = ["adb", "-s", "192.168.1.13:5555", "shell", f"su -c 'xxd -p /sys/firmware/devicetree/base/odm/mtk_pe50/{name}'"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    hex_str = res.stdout.strip().replace('\n', '').replace(' ', '')
    if hex_str:
        # device tree u32 arrays are big endian
        vals = [int.from_bytes(bytes.fromhex(hex_str[i:i+8]), byteorder='big', signed=True) for i in range(0, len(hex_str), 8)]
        print(f"{name}: {vals}")
