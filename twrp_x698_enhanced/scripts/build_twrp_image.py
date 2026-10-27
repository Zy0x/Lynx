import struct
import os

def build_enhanced_twrp():
    src_boot = 'boot_a_backup.img'
    new_ramdisk_gz = r'e:\Data\GitHub\Lynx\twrp_x698_enhanced\ramdisk_patched.gz'
    out_dir = r'e:\Data\GitHub\Lynx\twrp_x698_enhanced\output'
    os.makedirs(out_dir, exist_ok=True)
    out_boot = os.path.join(out_dir, 'twrp_enhanced_X698.img')

    with open(src_boot, 'rb') as f:
        orig_data = f.read()

    hdr = bytearray(orig_data[:2048])
    magic, k_sz, k_addr, orig_r_sz, r_addr, s_sz, s_addr, tags_addr, page_sz, hdr_ver, os_ver = struct.unpack('<8s10I', hdr[:48])
    dtbo_sz, dtbo_off, hdr_sz, dtb_sz, dtb_addr = struct.unpack('<I Q I I Q', hdr[1648:1676])

    print(f"Header: Version {hdr_ver}, Page Size {page_sz}")
    print(f"Kernel Size: {k_sz} bytes")
    print(f"Original Ramdisk Size: {orig_r_sz} bytes")
    print(f"DTBO Size: {dtbo_sz} bytes")

    with open(new_ramdisk_gz, 'rb') as f:
        new_rdata = f.read()
    new_r_sz = len(new_rdata)
    print(f"New Patched Ramdisk Size: {new_r_sz} bytes")

    # Update ramdisk size in header at offset 16 (uint32)
    struct.pack_into('<I', hdr, 16, new_r_sz)

    def pad(sz, page):
        return (sz + page - 1) // page * page

    k_offset = page_sz
    k_padded = pad(k_sz, page_sz)
    kernel_data = orig_data[k_offset : k_offset + k_sz]

    orig_r_offset = k_offset + k_padded
    orig_dtb_offset = orig_r_offset + pad(orig_r_sz, page_sz)

    # In original boot, dtbo starts at orig_dtb_offset
    dtbo_data = orig_data[orig_dtb_offset : orig_dtb_offset + pad(dtbo_sz, page_sz)]
    print(f"Extracted DTBO data: {len(dtbo_data)} bytes")

    # Locate AVB footer in original image
    avb_pos = orig_data.find(b'AVB0')
    if avb_pos != -1:
        avb_footer_data = orig_data[avb_pos:]
        print(f"Extracted AVB0 footer: {len(avb_footer_data)} bytes from offset {avb_pos}")
    else:
        avb_footer_data = b''

    # Assemble new image
    new_image = bytearray()
    
    # 1. Header (padded to page_sz)
    new_image.extend(hdr)
    while len(new_image) % page_sz != 0: new_image.append(0)

    # 2. Kernel (padded to page_sz)
    new_image.extend(kernel_data)
    while len(new_image) % page_sz != 0: new_image.append(0)

    # 3. New Patched Ramdisk (padded to page_sz)
    new_image.extend(new_rdata)
    while len(new_image) % page_sz != 0: new_image.append(0)

    # 4. DTBO (padded to page_sz)
    new_image.extend(dtbo_data)
    while len(new_image) % page_sz != 0: new_image.append(0)

    # Target partition size = 33,554,432 bytes (32 MB)
    target_size = 33554432
    if len(new_image) + len(avb_footer_data) <= target_size:
        # Pad with 0s until target_size - len(avb_footer_data)
        padding_needed = target_size - len(new_image) - len(avb_footer_data)
        new_image.extend(b'\x00' * padding_needed)
        new_image.extend(avb_footer_data)
    else:
        raise ValueError(f"Image too large! Total {len(new_image) + len(avb_footer_data)} exceeds {target_size}")

    assert len(new_image) == target_size, f"Size mismatch: {len(new_image)} != {target_size}"

    with open(out_boot, 'wb') as f:
        f.write(new_image)

    print(f"\n[SUCCESS] Enhanced TWRP Image created successfully!")
    print(f"Output File: {out_boot}")
    print(f"Total Size : {len(new_image)} bytes (Exact 32 MB partition match)")

if __name__ == '__main__':
    build_enhanced_twrp()
