#!/system/bin/sh
# Lynx Kernel Manager (LKM) - AnyKernel3 Flasher & Partition Backup/Restore
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)

BACKUP_DIR="/sdcard/Lynx/backups"
TMP_DIR="/dev/lynx_flasher_tmp"

# Helper: Find Block Device for Partition
find_block_dev() {
    part_name="$1"
    slot=$(getprop ro.boot.slot_suffix 2>/dev/null)
    [ -z "$slot" ] && slot=$(getprop ro.build.ab_update 2>/dev/null)
    
    # Try with slot suffix first if A/B
    target_part="$part_name"
    [ -n "$slot" ] && [ "$slot" != "false" ] && target_part="${part_name}${slot}"
    
    # 1. Search in /dev/block/by-name
    if [ -e "/dev/block/by-name/$target_part" ]; then
        echo "/dev/block/by-name/$target_part"
        return 0
    elif [ -e "/dev/block/by-name/$part_name" ]; then
        echo "/dev/block/by-name/$part_name"
        return 0
    fi
    
    # 2. Search in /dev/block/bootdevice/by-name
    if [ -e "/dev/block/bootdevice/by-name/$target_part" ]; then
        echo "/dev/block/bootdevice/by-name/$target_part"
        return 0
    elif [ -e "/dev/block/bootdevice/by-name/$part_name" ]; then
        echo "/dev/block/bootdevice/by-name/$part_name"
        return 0
    fi

    # 3. Search in /dev/block/platform/*/by-name
    for d in /dev/block/platform/*/by-name /dev/block/platform/*/*/by-name; do
        if [ -e "$d/$target_part" ]; then
            echo "$d/$target_part"
            return 0
        elif [ -e "$d/$part_name" ]; then
            echo "$d/$part_name"
            return 0
        fi
    done

    # 4. Search in /dev/block/mapper
    if [ -e "/dev/block/mapper/$target_part" ]; then
        echo "/dev/block/mapper/$target_part"
        return 0
    fi

    return 1
}

# 1. Backup Partition
backup_boot() {
    mkdir -p "$BACKUP_DIR" 2>/dev/null
    timestamp=$(date +%Y%m%d_%H%M%S 2>/dev/null || echo "latest")
    
    boot_dev=$(find_block_dev "boot")
    if [ -z "$boot_dev" ]; then
        echo "Error: Partisi 'boot' tidak ditemukan."
        return 1
    fi
    
    boot_out="$BACKUP_DIR/boot_${timestamp}.img"
    echo "Mencadangkan boot partition dari $boot_dev ..."
    dd if="$boot_dev" of="$boot_out" bs=4096 2>/dev/null
    
    if [ ! -s "$boot_out" ]; then
        echo "Error: Gagal mencadangkan boot image."
        rm -f "$boot_out"
        return 1
    fi
    echo "[OK] Berhasil mencadangkan boot: $boot_out"

    # Also check init_boot if present (Android 13+)
    init_boot_dev=$(find_block_dev "init_boot")
    if [ -n "$init_boot_dev" ]; then
        init_boot_out="$BACKUP_DIR/init_boot_${timestamp}.img"
        echo "Mencadangkan init_boot partition dari $init_boot_dev ..."
        dd if="$init_boot_dev" of="$init_boot_out" bs=4096 2>/dev/null
        if [ -s "$init_boot_out" ]; then
            echo "[OK] Berhasil mencadangkan init_boot: $init_boot_out"
        else
            rm -f "$init_boot_out"
        fi
    fi
    
    echo "Pencadangan partisi kernel selesai."
    return 0
}

# 2. List Backups (JSON)
list_backups_json() {
    mkdir -p "$BACKUP_DIR" 2>/dev/null
    first=1
    printf '{"backups":['
    for img in "$BACKUP_DIR"/*.img; do
        [ -f "$img" ] || continue
        name=$(basename "$img")
        size=$(stat -c%s "$img" 2>/dev/null || wc -c < "$img" 2>/dev/null || echo 0)
        date_str=$(stat -c%y "$img" 2>/dev/null || echo "")
        
        [ "$first" -eq 0 ] && printf ','
        first=0
        printf '{"name":"%s","path":"%s","size":%d,"date":"%s"}' \
            "$name" "$img" "$size" "$date_str"
    done
    printf ']}\n'
}

# 3. Restore Partition
restore_boot() {
    backup_file="$1"
    if [ ! -f "$backup_file" ]; then
        echo "Error: File backup tidak ditemukan: $backup_file"
        return 1
    fi

    filename=$(basename "$backup_file")
    target_part="boot"
    case "$filename" in
        init_boot*) target_part="init_boot" ;;
        vendor_boot*) target_part="vendor_boot" ;;
        *) target_part="boot" ;;
    esac

    target_dev=$(find_block_dev "$target_part")
    if [ -z "$target_dev" ]; then
        echo "Error: Perangkat partisi $target_part tidak ditemukan."
        return 1
    fi

    echo "Memulihkan $filename ke $target_dev..."
    dd if="$backup_file" of="$target_dev" bs=4096 2>/dev/null
    sync
    echo "[OK] Partisi $target_part berhasil dipulihkan dari $filename."
    return 0
}

# 4. Flash AnyKernel3 ZIP
flash_anykernel() {
    zip_path="$1"
    if [ ! -f "$zip_path" ]; then
        echo "Error: Berkas zip tidak ditemukan: $zip_path"
        return 1
    fi

    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  Lynx Kernel Manager - AnyKernel3 Flasher"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Target ZIP: $(basename "$zip_path")"
    
    # Step A: Auto-backup current boot before flashing
    echo "Mengamankan kernel lama..."
    backup_boot || echo "Peringatan: Auto-backup gagal, melanjutkan proses flash..."

    # Step B: Prepare Temporary Directory
    rm -rf "$TMP_DIR" 2>/dev/null
    mkdir -p "$TMP_DIR"
    
    echo "Mengekstrak AnyKernel3 package..."
    if command -v unzip >/dev/null 2>&1; then
        unzip -o -q "$zip_path" -d "$TMP_DIR"
    elif [ -x "/data/adb/magisk/busybox" ]; then
        /data/adb/magisk/busybox unzip -o -q "$zip_path" -d "$TMP_DIR"
    elif [ -x "/data/adb/ksu/bin/busybox" ]; then
        /data/adb/ksu/bin/busybox unzip -o -q "$zip_path" -d "$TMP_DIR"
    elif [ -x "/data/adb/ap/bin/busybox" ]; then
        /data/adb/ap/bin/busybox unzip -o -q "$zip_path" -d "$TMP_DIR"
    else
        echo "Error: Utilitas 'unzip' tidak ditemukan."
        rm -rf "$TMP_DIR"
        return 1
    fi

    # Step C: Locate and Execute Installer
    updater_bin="$TMP_DIR/META-INF/com/google/android/update-binary"
    anykernel_sh="$TMP_DIR/anykernel.sh"

    exit_code=1
    if [ -f "$updater_bin" ]; then
        chmod 755 "$updater_bin"
        chmod 755 "$TMP_DIR"/*.sh 2>/dev/null
        echo "Menjalankan update-binary..."
        cd "$TMP_DIR"
        sh "$updater_bin" 3 1 "$zip_path"
        exit_code=$?
    elif [ -f "$anykernel_sh" ]; then
        chmod 755 "$anykernel_sh"
        echo "Menjalankan anykernel.sh..."
        cd "$TMP_DIR"
        sh "$anykernel_sh"
        exit_code=$?
    else
        echo "Error: Berkas AnyKernel3 tidak valid (tidak ada update-binary atau anykernel.sh)."
        rm -rf "$TMP_DIR"
        return 1
    fi

    # Step D: Cleanup & Result
    rm -rf "$TMP_DIR" 2>/dev/null
    sync

    if [ "$exit_code" -eq 0 ]; then
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "[OK] Flashing Kernel Berhasil! Silakan reboot perangkat."
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        return 0
    else
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "[ERROR] Flashing Kernel Gagal (Exit code: $exit_code)."
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        return 1
    fi
}

case "$1" in
    backup)
        backup_boot
        ;;
    list_backups)
        list_backups_json
        ;;
    restore)
        restore_boot "$2"
        ;;
    flash)
        flash_anykernel "$2"
        ;;
    *)
        echo "Usage: $0 {backup|list_backups|restore <file>|flash <zip>}"
        exit 1
        ;;
esac
