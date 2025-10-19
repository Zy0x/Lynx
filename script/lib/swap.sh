#!/system/bin/sh

# List detected swap files
list_swap_files() {
    echo "Detected SWAP files on device:"
    found=0
    for f in $(find /data /cache /mnt /sdcard /storage -type f \( -iname "*swap*" -o -iname "swapfile*" \) 2>/dev/null); do
        size_bytes=$(du -b "$f" 2>/dev/null | cut -f1)

        # Only process files larger than 100 MB (104857600 bytes)
        if [ "$size_bytes" -gt 104857600 ]; then
            # Convert size to human-readable format using awk
            human_size=$(awk -v size="$size_bytes" '
                BEGIN {
                    suffixes = "KMGTPEZY"
                    scale = 1024
                    if (size == 0) {
                        print "0B"
                        exit
                    }
                    for (i = 0; size >= scale && i < length(suffixes); i++) {
                        size /= scale
                    }
                    printf "%.1f%sB\n", size, substr(suffixes, i+1, 1)
                }')

            if grep -q "$f" /proc/swaps; then
                echo "  ✔️ $f [ACTIVE] - $human_size ($size_bytes bytes)"
            else
                echo "  ⚠️ $f [INACTIVE] - $human_size ($size_bytes bytes)"
            fi
            found=1
        fi
    done
    if [ $found -eq 0 ]; then
        echo "  No large SWAP files (over 100MB) detected."
    fi
}

# Help function for SWAP commands
help_swap() {
    echo "Usage: Lxcore -swap [set|enable|disable|remove|help]"
    echo ""
    echo "Options:"
    echo "  set size=<SIZE>         Create and enable a SWAP file with the specified size (in GB, MB, or bytes)."
    echo "                          Use 'G'/'GB', 'M'/'MB', or 'B' suffix (e.g., 2G, 512M, 1024B)."
    echo "  enable                  Enable the existing SWAP file without changing its size."
    echo "  disable                 Disable the SWAP file(s)."
    echo "  remove                  Disable and delete all SWAP file(s) found."
    echo "  help                    Show this help message."
    echo ""
    echo "Examples:"
    echo "  Lxcore -swap set size=2G         # Create and enable a 2 GB SWAP file"
    echo "  Lxcore -swap set size=512M       # Create and enable a 512 MB SWAP file"
    echo "  Lxcore -swap set size=1048576B   # Create and enable a 1 MB SWAP file (in bytes)"
    echo "  Lxcore -swap enable             # Enable the existing SWAP file"
    echo "  Lxcore -swap disable            # Disable the SWAP file"
    echo "  Lxcore -swap remove             # Disable and delete all detected SWAP files"
    echo ""
    list_swap_files
    echo ""
    echo "Notes:"
    echo "  - If the SWAP file does not exist, use 'set' to create it."
    echo "  - Ensure you have sufficient storage space before creating a large SWAP file."
}

# Disable all active swap files (excluding ZRAM devices)
disable_all_swaps() {
    awk 'NR>1 {print $1}' /proc/swaps | grep -v '/dev/block/zram' | while read swapfile; do
        log_msg "Disabling swap file: $swapfile"
        su -c swapoff "$swapfile" >/dev/null 2>&1
        if [ $? -eq 0 ]; then
            log_msg "Successfully disabled swap file: $swapfile"
        else
            log_msg "ERROR: Failed to disable swap file: $swapfile"
        fi
    done
}

# Enable the SWAP file
main_enableswap() {
    log_msg "Enabling SWAP..."
    local found_swap=$(awk 'NR>1 {print $1}' /proc/swaps | grep -v '/dev/block/zram')

    if [ -n "$found_swap" ]; then
        log_msg "SWAP is already active: $found_swap"
        return 0
    elif [ -f "/data/swap" ]; then
        su -c swapon /data/swap -p 3 >/dev/null 2>&1
        if [ $? -eq 0 ]; then
            log_msg "SWAP successfully enabled."
            su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʯ' 'Lʏɴx' '☣️ 𝙎𝙬𝙖𝙥 𝙀𝙣𝙖𝙗𝙡𝙚𝙙'" >/dev/null 2>&1
        else
            log_msg "ERROR: Failed to enable SWAP."
            su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʯ' 'Lʏɴx' '❌ 𝙎𝙬𝙖𝙥 𝙁𝙖𝙞𝙡𝙚𝙙 𝙩𝙤 𝙀𝙣𝙖𝙗𝙡𝙚'" >/dev/null 2>&1
            return 1
        fi
    else
        log_msg "ERROR: No SWAP file found. Please run 'set' first."
        su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʯ' 'Lʏɴx' '❌ 𝙉𝙤 𝙎𝙬𝙖𝙥 𝙁𝙞𝙡𝙚 𝙁𝙤𝙪𝙣𝙙'" >/dev/null 2>&1
        return 1
    fi
}

# Disable all SWAP files
main_disableswap() {
    log_msg "Disabling all SWAP files..."
    if awk 'NR>1 {print $1}' /proc/swaps | grep -v '/dev/block/zram' | grep -q .; then
        disable_all_swaps
        log_msg "All SWAP files disabled."
        su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʯ' 'Lʏɴx' '⚠️ 𝘼𝙡𝙡 𝙎𝙬𝙖𝙥 𝘿𝙞𝙨𝙖𝙗𝙡𝙚𝙙'" >/dev/null 2>&1
    else
        log_msg "No active SWAP files to disable."
    fi
}

# Set and enable new SWAP file
main_setswap() {
    echo "DEBUG: Raw arguments: [$@]"
    size=""
    size_bytes=""

    # Parse arguments
    for arg in "$@"; do
        # Check if arg starts with size=
        if [ "${arg#size=}" != "$arg" ]; then
            size="${arg#size=}"
        # If no size= prefix, assume the argument is the size (e.g., 2g)
        elif echo "$arg" | grep -E '^[0-9]+(\.[0-9]*)?([Gg][Bb]?|[Mm][Bb]?|[Bb])?$' >/dev/null; then
            size="$arg"
        fi
    done

    echo "DEBUG: Size: $size"

    # Handle size input (GB, MB, or bytes)
    if [ -n "$size" ]; then
        # Remove any leading/trailing whitespace
        size=$(echo "$size" | tr -d '[:space:]')
        echo "DEBUG: Trimmed size: $size"

        # Use case statement for pattern matching
        case "$size" in
            *[0-9][Gg]|[0-9][Gg][Bb])
                # Extract numeric part and convert GB to bytes (1 GB = 1,073,741,824 bytes)
                size_num=$(echo "$size" | sed 's/[Gg][Bb]//g; s/[Gg]//g')
                echo "DEBUG: GB check - size_num: $size_num"
                if ! echo "$size_num" | grep -E '^[0-9]+(\.[0-9]*)?$' >/dev/null; then
                    log_msg "ERROR: Invalid SWAP size '$size'. Size must be a numeric value (e.g., 2G or 2GB)."
                    help_swap
                    return 1
                fi
                size_bytes=$(awk -v num="$size_num" 'BEGIN {print int(num * 1073741824)}')
                log_msg "Converted size: $size_num GB to $size_bytes bytes"
                ;;
            *[0-9][Mm]|[0-9][Mm][Bb])
                # Extract numeric part and convert MB to bytes (1 MB = 1,048,576 bytes)
                size_num=$(echo "$size" | sed 's/[Mm][Bb]//g; s/[Mm]//g')
                echo "DEBUG: MB check - size_num: $size_num"
                if ! echo "$size_num" | grep -E '^[0-9]+(\.[0-9]*)?$' >/dev/null; then
                    log_msg "ERROR: Invalid SWAP size '$size'. Size must be a numeric value (e.g., 512M or 512MB)."
                    help_swap
                    return 1
                fi
                size_bytes=$(awk -v num="$size_num" 'BEGIN {print int(num * 1048576)}')
                log_msg "Converted size: $size_num MB to $size_bytes bytes"
                ;;
            *[0-9]|[0-9][Bb])
                # Extract numeric part
                size_num=$(echo "$size" | sed 's/[Bb]//g')
                echo "DEBUG: Bytes check - size_num: $size_num"
                if ! echo "$size_num" | grep -E '^[0-9]+$' >/dev/null; then
                    log_msg "ERROR: Invalid SWAP size '$size'. Size must be a numeric value (e.g., 1048576B)."
                    help_swap
                    return 1
                fi
                size_bytes="$size_num"
                log_msg "Size: $size_bytes bytes"
                ;;
            *)
                log_msg "ERROR: Invalid SWAP size '$size'. Use GB (e.g., 2G or 2GB), MB (e.g., 512M or 512MB), or bytes (e.g., 1048576B)."
                help_swap
                return 1
                ;;
        esac

        # Warn if size exceeds 50% of total RAM
        total_ram=$(awk '/MemTotal/ {print $2 * 1024}' /proc/meminfo) # Convert KB to bytes
        max_size=$((total_ram / 2))
        if [ "$size_bytes" -gt "$max_size" ]; then
            log_msg "WARNING: Requested SWAP size ($size_bytes bytes) exceeds 50% of total RAM ($total_ram bytes). Proceeding as requested."
        fi

        # Disable all existing swap files (excluding ZRAM)
        log_msg "Disabling existing SWAP file(s)..."
        disable_all_swaps

        # Remove any detected swap file (especially /data/swap)
        if [ -f "/data/swap" ]; then
            log_msg "Removing existing /data/swap..."
            rm -f /data/swap
        fi

        # Create new SWAP file
        log_msg "Creating new SWAP file: $size_bytes bytes..."
        dd if=/dev/zero of=/data/swap bs=1 count="$size_bytes" >/dev/null 2>&1
        if [ $? -ne 0 ]; then
            log_msg "ERROR: Failed to create SWAP file."
            su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʯ' 'Lʏɴx' '❌ 𝙁𝙖𝙞𝙡𝙚𝙙 𝙩𝙤 𝘾𝙧𝙚𝙖𝙩𝙚 𝙎𝙬𝙖𝙥'" >/dev/null 2>&1
            return 1
        fi

        mkswap /data/swap >/dev/null 2>&1
        if [ $? -ne 0 ]; then
            log_msg "ERROR: Failed to initialize SWAP file."
            su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʯ' 'Lʏɴx' '❌ 𝙁𝙖𝙞𝙡𝙚𝙙 𝙩𝙤 𝙄𝙣𝙞𝙩𝙞𝙖𝙡𝙞𝙯𝙚 𝙎𝙬𝙖𝙥'" >/dev/null 2>&1
            return 1
        fi

        su -c swapon /data/swap -p 3 >/dev/null 2>&1
        if [ $? -eq 0 ]; then
            log_msg "SWAP successfully created and enabled: $size_bytes bytes."
            su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʯ' 'Lʏɴx' '☣️ 𝙎𝙬𝙖𝙥 𝙀𝙣𝙖𝙗𝙡𝙚𝙙'" >/dev/null 2>&1
        else
            log_msg "ERROR: Failed to enable SWAP."
            su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʯ' 'Lʏɴx' '❌ 𝙎𝙬𝙖𝙥 𝙀𝙣𝙖𝙗𝙡𝙞𝙣𝙜 𝙁𝙖𝙞𝙡𝙚𝙙'" >/dev/null 2>&1
            return 1
        fi
    else
        log_msg "ERROR: No size specified for SWAP file."
        help_swap
        return 1
    fi
}

# Remove all swap files (active and inactive)
main_removeswap() {
    log_msg "Removing all large SWAP files (over 100MB)..."
    for f in $(find /data /cache /mnt /sdcard /storage -type f \( -iname "*swap*" -o -iname "swapfile*" \) 2>/dev/null); do
        size_bytes=$(du -b "$f" 2>/dev/null | cut -f1)

        # Only process files larger than 100 MB (104857600 bytes)
        if [ "$size_bytes" -gt 104857600 ]; then
            if grep -q "$f" /proc/swaps; then
                log_msg "Disabling active SWAP: $f"
                su -c swapoff "$f" >/dev/null 2>&1
                if [ $? -eq 0 ]; then
                    log_msg "SWAP disabled: $f"
                else
                    log_msg "⚠️ Failed to disable SWAP: $f"
                fi
            fi

            log_msg "Deleting SWAP file: $f"
            rm -f "$f"
            if [ $? -eq 0 ]; then
                log_msg "SWAP file deleted: $f"
            else
                log_msg "⚠️ Failed to delete SWAP file: $f"
            fi
        fi
    done

    rm -f "$MODPATH/swapram_installed"

    su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʯ' 'Lʏɴx' '🗑️ 𝘼𝙡𝙡 𝙎𝙬𝙖𝙥 𝙁𝙞𝙡𝙚𝙨 𝙍𝙚𝙢𝙤𝙫𝙚𝙙'" >/dev/null 2>&1
}