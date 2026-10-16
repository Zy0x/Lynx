import struct
import gzip

with open("tools/boot_head.img", "rb") as f:
    data = f.read()

# Check magic "ANDROID!"
if data[:8] == b"ANDROID!":
    print("Found ANDROID! boot image header")
    # header version
    header_version = struct.unpack("<I", data[40:44])[0]
    kernel_size = struct.unpack("<I", data[8:12])[0]
    print(f"Header version: {header_version}, kernel size: {kernel_size}")
    # page size is usually 2048 or 4096
    page_size = struct.unpack("<I", data[36:40])[0]
    print(f"Page size: {page_size}")
    kernel_offset = page_size
    kernel_data = data[kernel_offset : kernel_offset + kernel_size]
    with open("tools/kernel.bin", "wb") as kf:
        kf.write(kernel_data)
    print(f"Wrote tools/kernel.bin, size: {len(kernel_data)}")
    # Check if gzip or raw
    if kernel_data[:2] == b"\x1f\x8b":
        print("Kernel is GZIP compressed!")
        uncomp = gzip.decompress(kernel_data)
        with open("tools/kernel_uncompressed.bin", "wb") as ukf:
            ukf.write(uncomp)
        print(f"Uncompressed kernel size: {len(uncomp)}")
    elif b"\x1f\x8b" in kernel_data[:1000]:
        idx = kernel_data.find(b"\x1f\x8b")
        print(f"Found gzip stream at {idx}")
        uncomp = gzip.decompress(kernel_data[idx:])
        with open("tools/kernel_uncompressed.bin", "wb") as ukf:
            ukf.write(uncomp)
        print(f"Uncompressed kernel size: {len(uncomp)}")
    else:
        # Check ARM64 header magic at offset 56: 0x644d5241 "ARM\x64"
        magic = kernel_data[56:60]
        print("ARM64 magic:", magic)
        if magic == b"ARM\x64":
            print("Kernel is uncompressed Image!")
            with open("tools/kernel_uncompressed.bin", "wb") as ukf:
                ukf.write(kernel_data)
else:
    print("Not standard ANDROID! header at 0")
