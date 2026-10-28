import struct

with open(r"e:\Data\GitHub\Lynx\Emergency_Recovery\Infinix_X698_Full_Backup\boot_a.img", "rb") as f:
    hdr = f.read(1024)

magic = hdr[:8]
print("Magic:", magic)
if magic == b"ANDROID!":
    kernel_size, kernel_addr, ramdisk_size, ramdisk_addr = struct.unpack("<IIII", hdr[8:24])
    print(f"Kernel size: {kernel_size:,} | Ramdisk size: {ramdisk_size:,}")
    header_version = struct.unpack("<I", hdr[40:44])[0]
    print(f"Header version: {header_version}")
