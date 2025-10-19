#!/bin/bash

# Help function for ZRAM commands
help_zram() {
    # Get available ZRAM algorithms dynamically
    if [ -f /sys/block/zram0/comp_algorithm ]; then
        supported_algorithms=$(cat /sys/block/zram0/comp_algorithm | sed 's/\[//g; s/\]//g')
    else
        supported_algorithms="Unknown (ZRAM not initialized)"
    fi

    # Get current ZRAM size dynamically
    if [ -f /sys/block/zram0/disksize ]; then
        current_size=$(cat /sys/block/zram0/disksize)
        # Convert size to human-readable format using awk
        current_size_human=$(awk -v size="$current_size" '
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
                printf "%.1f%sB\n", size, substr(suffixes, i, 1)
            }')
    else
        current_size="N/A"
        current_size_human="N/A"
    fi

    echo "Usage: Lxcore -zram [set|disable|help]"
    echo ""
    echo "Options:"
    echo "  set size=<SIZE> [algo=<ALGORITHM>]  Set the size of the ZRAM device (in GB, MB, or bytes)."
    echo "                                      Use 'G'/'GB', 'M'/'MB', or 'B' suffix (e.g., 2G, 512M, 1024B)."
    echo "  set algo=<ALGORITHM>                Set the ZRAM compression algorithm."
    echo "  disable                            Disable ZRAM."
    echo "  help                               Show this help message."
    echo ""
    echo "Examples:"
    echo "  Lxcore -zram set size=2G                     # Set ZRAM size to 2 GB"
    echo "  Lxcore -zram set size=512M                   # Set ZRAM size to 512 MB"
    echo "  Lxcore -zram set size=1024B                  # Set ZRAM size to 1024 bytes"
    echo "  Lxcore -zram set size=2G algo=zstd           # Set ZRAM size to 2 GB and algorithm to zstd"
    echo "  Lxcore -zram set algo=lz4                    # Set ZRAM algorithm to lz4"
    echo "  Lxcore -zram disable                        # Disable ZRAM"
    echo ""
    echo "Additional Information:"
    echo "  Supported ZRAM Algorithms: $supported_algorithms"
    echo "  Current ZRAM Size: $current_size_human ($current_size bytes)"
}

# Configure ZRAM size and/or algorithm
main_setzram() {
    echo "DEBUG: Arguments received: $@"
    local size=""
    local algo=""
    local size_bytes=""

    # Parse arguments
    for arg in "$@"; do
        if [[ "$arg" == size=* ]]; then
            size="${arg#*=}"
        elif [[ "$arg" == algo=* ]]; then
            algo="${arg#*=}"
        fi
    done

    echo "DEBUG: Size: $size"
    echo "DEBUG: Algorithm: $algo"

    # Handle size input (GB, MB, or bytes)
    if [[ -n "$size" ]]; then
        if [[ "$size" =~ ^[0-9]+(\.[0-9]+)?[Gg][Bb]?$ ]]; then
            # Extract numeric part and convert GB to bytes (1 GB = 1,073,741,824 bytes)
            size_num=$(echo "$size" | sed 's/[Gg][Bb]//g')
            if [[ ! "$size_num" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then
                log_msg "ERROR: Invalid ZRAM size '$size'. Size must be a numeric value (e.g., 2G or 2GB)."
                help_zram
                return 1
            fi
            size_bytes=$(awk -v num="$size_num" 'BEGIN {print int(num * 1073741824)}')
            log_msg "Converted size: $size_num GB to $size_bytes bytes"
        elif [[ "$size" =~ ^[0-9]+(\.[0-9]+)?[Mm][Bb]?$ ]]; then
            # Extract numeric part and convert MB to bytes (1 MB = 1,048,576 bytes)
            size_num=$(echo "$size" | sed 's/[Mm][Bb]//g')
            if [[ ! "$size_num" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then
                log_msg "ERROR: Invalid ZRAM size '$size'. Size must be a numeric value (e.g., 512M or 512MB)."
                help_zram
                return 1
            fi
            size_bytes=$(awk -v num="$size_num" 'BEGIN {print int(num * 1048576)}')
            log_msg "Converted size: $size_num MB to $size_bytes bytes"
        elif [[ "$size" =~ ^[0-9]+[Bb]?$ ]]; then
            # Handle bytes (with or without 'B' suffix)
            size_num=$(echo "$size" | sed 's/[Bb]//g')
            if [[ ! "$size_num" =~ ^[0-9]+$ ]]; then
                log_msg "ERROR: Invalid ZRAM size '$size'. Size must be a numeric value (e.g., 1024B)."
                help_zram
                return 1
            fi
            size_bytes="$size_num"
            log_msg "Size: $size_bytes bytes"
        else
            log_msg "ERROR: Invalid ZRAM size '$size'. Use GB (e.g., 2G or 2GB), MB (e.g., 512M or 512MB), or bytes (e.g., 1024B)."
            help_zram
            return 1
        fi

        # Validate size (ensure it doesn't exceed 50% of total RAM)
        total_ram=$(awk '/MemTotal/ {print $2 * 1024}' /proc/meminfo) # Convert KB to bytes
        max_size=$((total_ram / 2))
        if [[ "$size_bytes" -gt "$max_size" ]]; then
            log_msg "ERROR: Requested ZRAM size ($size_bytes bytes) exceeds 50% of total RAM ($total_ram bytes)."
            return 1
        fi
    fi

    # Configure ZRAM size (only if size is provided)
    if [[ -n "$size_bytes" ]]; then
        REQUIRED_FILES=(
            "/sys/class/zram-control/hot_add"
            "/sys/block/zram0/disksize"
            "/sys/block/zram0/reset"
            "/dev/block/zram0"
        )
        for file in "${REQUIRED_FILES[@]}"; do
            if [ ! -e "$file" ]; then
                log_msg "ERROR: Required file/directory not found: $file"
                echo "Please ensure that ZRAM is supported and properly initialized on your system."
                return 1
            fi
        done

        RAM_DEV=$(cat /sys/class/zram-control/hot_add)
        echo "3" > /proc/sys/vm/drop_caches
        swapoff /dev/block/zram0
        echo "1" > /sys/block/zram0/reset
        echo "$size_bytes" > /sys/block/zram0/disksize
        mkswap /dev/block/zram0
        swapon /dev/block/zram0 -p 5
        if [ $? -eq 0 ]; then
            log_msg "ZRAM successfully enabled with size: $size_bytes bytes."
            su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʏ' 'Lʏɴx' '☢️ Zʀᴀᴍ Eɴᴀʙʟᴇᴅ'" >/dev/null 2>&1
        else
            log_msg "ERROR: Failed to enable ZRAM."
            return 1
        fi
    fi

    # Configure ZRAM algorithm (only if algo is provided)
    if [[ -n "$algo" ]]; then
        output=$(cat /sys/block/zram0/comp_algorithm)
        if [[ "$output" == *"$algo"* ]]; then
            echo "$algo" > /sys/block/zram0/comp_algorithm
            log_msg "ZRAM compression algorithm set to '$algo'."
        else
            log_msg "ERROR: Requested algorithm '$algo' is not supported."
            return 1
        fi
    fi
}

# Disable ZRAM
main_disablezram() {
    if [ ! -f /sys/class/zram-control/hot_remove ] || ! ls /dev/block/zram* > /dev/null 2>&1; then
        log_msg "ERROR: Required ZRAM files or devices not found. Please ensure ZRAM is installed and enabled."
        return 1
    fi
    log_msg "Disabling all ZRAM..."
    echo "3" > /proc/sys/vm/drop_caches
    zram_devices=$(ls /dev/block/zram* 2>/dev/null)
    for zram_device in $zram_devices; do
        log_msg "Processing device: $zram_device"
        swapoff "$zram_device"
        if [ $? -ne 0 ]; then
            log_msg "ERROR: Failed to disable swap for $zram_device."
            continue
        fi
        echo "0" > /sys/class/zram-control/hot_remove
        if [ $? -ne 0 ]; then
            log_msg "ERROR: Failed to remove ZRAM module for $zram_device."
            continue
        fi
        log_msg "Successfully disabled ZRAM for $zram_device."
    done
    su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʏ' 'Lʏɴx' '⛔ ZRAM Dɪꜱᴀʙʟᴇᴅ'" >/dev/null 2>&1
}