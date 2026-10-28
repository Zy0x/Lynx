import os
import shutil
import gzip
import struct

BASE = r"e:\Data\GitHub\Lynx"
SRC_FOX_RAMDISK = os.path.join(BASE, r"Emergency_Recovery\OrangeFox_Base\ramdisk")
SRC_TWRP_RAMDISK = os.path.join(BASE, r"twrp_x698_enhanced\ramdisk")
FINAL_RAMDISK = os.path.join(BASE, r"Emergency_Recovery\OrangeFox_Port\ramdisk_final")
OUT_DIR = os.path.join(BASE, r"Emergency_Recovery\OrangeFox_Port\output")
os.makedirs(OUT_DIR, exist_ok=True)

def prepare_ramdisk():
    if os.path.exists(FINAL_RAMDISK):
        shutil.rmtree(FINAL_RAMDISK)
    os.makedirs(FINAL_RAMDISK, exist_ok=True)

    print("[1/5] Copying base OrangeFox ramdisk (excluding Microtrust TEE)...")
    for root, dirs, files in os.walk(SRC_FOX_RAMDISK):
        rel = os.path.relpath(root, SRC_FOX_RAMDISK)
        if 'vendor\\thh' in rel or 'vendor/thh' in rel:
            continue
        target_dir = os.path.join(FINAL_RAMDISK, rel)
        os.makedirs(target_dir, exist_ok=True)
        for f in files:
            src = os.path.join(root, f)
            dst = os.path.join(target_dir, f)
            shutil.copy2(src, dst)

    print("[2/5] Optimizing heavy fonts and removing unused debug/extra libs...")
    # Remove unused debug sanitizer and unreferenced libraries
    for unused_lib in [
        'system/lib64/libclang_rt.ubsan_standalone-aarch64-android.so',
        'system/lib64/libssh.so'
    ]:
        fp = os.path.join(FINAL_RAMDISK, unused_lib.replace('/', os.sep))
        if os.path.exists(fp):
            os.remove(fp)

    # Replace heavy DroidSansFallback with GoogleSans to save 3.8 MB
    droid = os.path.join(FINAL_RAMDISK, 'twres', 'fonts', 'DroidSansFallback.ttf')
    gsans = os.path.join(FINAL_RAMDISK, 'twres', 'fonts', 'GoogleSans-Regular.ttf')
    if os.path.exists(gsans):
        shutil.copy2(gsans, droid)

    # Remove extra decorative/non-essential fonts
    for extra_font in [
        'Chococooky.ttf', 'EuclidFlex-Medium.ttf', 'EuclidFlex-Regular.ttf',
        'Exo2-Medium.ttf', 'Exo2-Regular.ttf', 'MILanPro-Medium.ttf',
        'MILanPro-Regular.ttf', 'NotoSansCJKjp-Regular.ttf', 'ae_Cortoba.ttf',
        'Amatic.ttf', 'FiraCode-Medium.ttf', 'FiraCode-Regular.ttf',
        'Roboto-Spanish.ttf', 'RobotoSlab.ttf'
    ]:
        fp = os.path.join(FINAL_RAMDISK, 'twres', 'fonts', extra_font)
        if os.path.exists(fp):
            os.remove(fp)

    # Symlink first_stage_ramdisk e2fsck to /system/bin/e2fsck to save 0.79 MB
    fs_e2fsck = os.path.join(FINAL_RAMDISK, 'first_stage_ramdisk', 'system', 'bin', 'e2fsck')
    if os.path.exists(fs_e2fsck):
        os.remove(fs_e2fsck)
        with open(fs_e2fsck + '.symlink', 'w', encoding='utf-8') as sf:
            sf.write('/system/bin/e2fsck')

    print("[3/5] Injecting Infinix X698 Trustonic TEE stack and rescue tools...")
    # Inject Trustonic TEE and tools
    shutil.copy2(os.path.join(SRC_TWRP_RAMDISK, 'tee.rc'), os.path.join(FINAL_RAMDISK, 'tee.rc'))
    shutil.copy2(os.path.join(SRC_TWRP_RAMDISK, 'trustonic.rc'), os.path.join(FINAL_RAMDISK, 'trustonic.rc'))
    shutil.copy2(os.path.join(SRC_TWRP_RAMDISK, 'init.recovery.mt6781.rc'), os.path.join(FINAL_RAMDISK, 'init.recovery.mt6781.rc'))
    
    os.makedirs(os.path.join(FINAL_RAMDISK, 'vendor', 'lib64'), exist_ok=True)
    shutil.copy2(os.path.join(SRC_TWRP_RAMDISK, 'vendor', 'lib64', 'libMcClient.so'), os.path.join(FINAL_RAMDISK, 'vendor', 'lib64', 'libMcClient.so'))
    shutil.copy2(os.path.join(SRC_TWRP_RAMDISK, 'system', 'bin', 'mcDriverDaemon'), os.path.join(FINAL_RAMDISK, 'system', 'bin', 'mcDriverDaemon'))
    shutil.copy2(os.path.join(SRC_TWRP_RAMDISK, 'system', 'bin', 'android.hardware.keymaster@4.0-service.trustonic'), os.path.join(FINAL_RAMDISK, 'system', 'bin', 'android.hardware.keymaster@4.0-service.trustonic'))
    shutil.copy2(os.path.join(SRC_TWRP_RAMDISK, 'system', 'bin', 'vendor.trustonic.tee@1.1-service'), os.path.join(FINAL_RAMDISK, 'system', 'bin', 'vendor.trustonic.tee@1.1-service'))
    
    shutil.copy2(os.path.join(SRC_TWRP_RAMDISK, 'system', 'bin', 'lynx-rescue'), os.path.join(FINAL_RAMDISK, 'system', 'bin', 'lynx-rescue'))
    shutil.copy2(os.path.join(SRC_TWRP_RAMDISK, 'system', 'bin', 'kptools'), os.path.join(FINAL_RAMDISK, 'system', 'bin', 'kptools'))

    # Update prop.default for Infinix Note 11s X698
    prop_path = os.path.join(FINAL_RAMDISK, 'prop.default')
    with open(prop_path, 'r', encoding='utf-8', errors='ignore') as f:
        props = f.read()
    props = props.replace('fleur', 'X698')
    props = props.replace('Redmi Note 11S', 'Infinix Note 11s')
    props = props.replace('Redmi', 'Infinix')
    props = props.replace('xiaomi', 'infinix')
    with open(prop_path, 'w', encoding='utf-8') as f:
        f.write(props)

    # Remove Microtrust RC if present
    micro_rc = os.path.join(FINAL_RAMDISK, 'init.recovery.microtrust.rc')
    if os.path.exists(micro_rc):
        os.remove(micro_rc)

def build_cpio_gz():
    print("[4/5] Building CPIO archive and compressing with GZIP -9...")
    cpio_path = os.path.join(OUT_DIR, "ramdisk.cpio")
    gz_path = os.path.join(OUT_DIR, "ramdisk.cpio.gz")

    with open(cpio_path, 'wb') as c_out:
        ino = 1
        for root, dirs, files in os.walk(FINAL_RAMDISK):
            for f in files:
                fp = os.path.join(root, f)
                rel = os.path.relpath(fp, FINAL_RAMDISK).replace(os.sep, '/')
                is_symlink = f.endswith('.symlink')
                if is_symlink:
                    rel = rel[:-8]
                    with open(fp, 'r', encoding='utf-8') as sf:
                        data = sf.read().encode('utf-8')
                    mode = 0o120777
                else:
                    with open(fp, 'rb') as in_f:
                        data = in_f.read()
                    mode = 0o100755 if '/bin/' in rel or '/sbin/' in rel or rel.endswith('.sh') else 0o100644

                filesize = len(data)
                namesize = len(rel) + 1
                hdr = f'070701{ino:08x}{mode:08x}{0:08x}{0:08x}{1:08x}{0:08x}{filesize:08x}{0:08x}{0:08x}{0:08x}{0:08x}{namesize:08x}{0:08x}'.encode('ascii')
                c_out.write(hdr)
                c_out.write(rel.encode('utf-8') + b'\x00')
                pad = (4 - ((110 + namesize) % 4)) % 4
                c_out.write(b'\x00' * pad)
                c_out.write(data)
                pad = (4 - (filesize % 4)) % 4
                c_out.write(b'\x00' * pad)
                ino += 1

        trailer = b'TRAILER!!!\x00'
        hdr = f'070701{0:08x}{0:08x}{0:08x}{0:08x}{1:08x}{0:08x}{0:08x}{0:08x}{0:08x}{0:08x}{0:08x}{len(trailer):08x}{0:08x}'.encode('ascii')
        c_out.write(hdr)
        c_out.write(trailer)
        pad = (4 - ((110 + len(trailer)) % 4)) % 4
        c_out.write(b'\x00' * pad)

    with open(cpio_path, 'rb') as f_in, gzip.open(gz_path, 'wb', compresslevel=9) as f_out:
        shutil.copyfileobj(f_in, f_out)

    gz_size = os.path.getsize(gz_path)
    print(f"Compressed Ramdisk: {gz_size / 1024 / 1024:.2f} MB ({gz_size:,} bytes)")
    return gz_path, gz_size

def pack_boot_image(gz_path, gz_size):
    print("[5/5] Packaging OrangeFox_X698_Enhanced.img into exact 32 MB partition...")
    src_boot = os.path.join(BASE, r"boot_a_backup.img")
    out_img = os.path.join(OUT_DIR, "OrangeFox_X698_Enhanced.img")

    with open(src_boot, 'rb') as f:
        orig_data = f.read()

    hdr = bytearray(orig_data[:2048])
    magic, k_sz, k_addr, orig_r_sz, r_addr, s_sz, s_addr, tags_addr, page_sz, hdr_ver, os_ver = struct.unpack('<8s10I', hdr[:48])
    dtbo_sz, dtbo_off, hdr_sz, dtb_sz, dtb_addr = struct.unpack('<I Q I I Q', hdr[1648:1676])

    print(f"  Kernel Size: {k_sz:,} bytes")
    print(f"  DTBO Size  : {dtbo_sz:,} bytes")
    print(f"  New Ramdisk: {gz_size:,} bytes")

    # Update ramdisk size in header at offset 16
    struct.pack_into('<I', hdr, 16, gz_size)

    def pad(sz, page):
        return (sz + page - 1) // page * page

    k_offset = page_sz
    k_padded = pad(k_sz, page_sz)
    kernel_data = orig_data[k_offset : k_offset + k_sz]

    orig_r_offset = k_offset + k_padded
    orig_dtb_offset = orig_r_offset + pad(orig_r_sz, page_sz)
    dtbo_data = orig_data[orig_dtb_offset : orig_dtb_offset + pad(dtbo_sz, page_sz)]

    avb_pos = orig_data.find(b'AVB0')
    avb_footer_data = orig_data[avb_pos:] if avb_pos != -1 else b''

    new_img = bytearray()
    # 1. Header
    new_img.extend(hdr)
    while len(new_img) % page_sz != 0: new_img.append(0)

    # 2. Kernel
    new_img.extend(kernel_data)
    while len(new_img) % page_sz != 0: new_img.append(0)

    # 3. Ramdisk
    with open(gz_path, 'rb') as f:
        new_img.extend(f.read())
    while len(new_img) % page_sz != 0: new_img.append(0)

    # 4. DTBO
    new_img.extend(dtbo_data)
    while len(new_img) % page_sz != 0: new_img.append(0)

    target_size = 33554432 # Exact 32 MB partition boundary
    total_needed = len(new_img) + len(avb_footer_data)
    if total_needed > target_size:
        raise ValueError(f"CRITICAL: Image size {total_needed:,} exceeds target partition {target_size:,} by {total_needed - target_size:,} bytes!")

    padding_needed = target_size - len(new_img) - len(avb_footer_data)
    new_img.extend(b'\x00' * padding_needed)
    new_img.extend(avb_footer_data)

    assert len(new_img) == target_size, f"Size mismatch: {len(new_img)} != {target_size}"

    with open(out_img, 'wb') as f:
        f.write(new_img)

    print(f"\n[SUCCESS] Successfully generated OrangeFox_X698_Enhanced.img!")
    print(f"Path : {out_img}")
    print(f"Size : {len(new_img):,} bytes (Exact 32 MB)")

if __name__ == '__main__':
    prepare_ramdisk()
    gz_path, gz_size = build_cpio_gz()
    pack_boot_image(gz_path, gz_size)
