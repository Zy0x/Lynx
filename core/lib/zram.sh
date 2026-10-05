#!/system/bin/sh
# Lynx Universal - Dynamic ZRAM Configuration
# Hardened POSIX compliance for Android /system/bin/sh (Toybox/ash)

# Help function for ZRAM commands
help_zram() {
    # Get available ZRAM algorithms dynamically
    if [ -f /sys/block/zram0/comp_algorithm ]; then
        supported_algorithms=$(cat /sys/block/zram0/comp_algorithm 2>/dev/null | tr -d '[]')
    else
        supported_algorithms="Unknown (ZRAM not initialized)"
    fi

    # Get current ZRAM size dynamically
    if [ -f /sys/block/zram0/disksize ]; then
        current_size=$(cat /sys/block/zram0/disksize 2>/dev/null)
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
    local size=""
    local algo=""
    local size_bytes=""

    # Parse arguments
    for arg in "$@"; do
        case "$arg" in
            size=*) size="${arg#size=}" ;;
            algo=*) algo="${arg#algo=}" ;;
        esac
    done

    # Handle size input (GB, MB, or bytes)
    if [ -n "$size" ]; then
        case "$size" in
            *[0-9][Gg]|[0-9][Gg][Bb])
                size_num=$(echo "$size" | sed 's/[Gg][Bb]//g; s/[Gg]//g')
                size_bytes=$(awk -v num="$size_num" 'BEGIN {print int(num * 1073741824)}')
                log_msg "Converted size: $size_num GB to $size_bytes bytes"
                ;;
            *[0-9][Mm]|[0-9][Mm][Bb])
                size_num=$(echo "$size" | sed 's/[Mm][Bb]//g; s/[Mm]//g')
                size_bytes=$(awk -v num="$size_num" 'BEGIN {print int(num * 1048576)}')
                log_msg "Converted size: $size_num MB to $size_bytes bytes"
                ;;
            *[0-9]|[0-9][Bb])
                size_bytes=$(echo "$size" | sed 's/[Bb]//g')
                log_msg "Size: $size_bytes bytes"
                ;;
            *)
                log_msg "ERROR: Invalid ZRAM size '$size'. Use GB (e.g., 2G), MB (e.g., 512M), or bytes (e.g., 1024B)."
                help_zram
                return 1
                ;;
        esac

        # Validate size (ensure it doesn't exceed 50% of total RAM)
        total_ram=$(awk '/MemTotal/ {print $2 * 1024}' /proc/meminfo 2>/dev/null)
        max_size=$((total_ram / 2))
        if [ "$size_bytes" -gt "$max_size" ]; then
            log_msg "ERROR: Requested ZRAM size ($size_bytes bytes) exceeds 50% of total RAM ($total_ram bytes)."
            return 1
        fi
    fi

    # Configure ZRAM size (only if size is provided)
    if [ -n "$size_bytes" ]; then
        for req in "/sys/class/zram-control/hot_add" "/sys/block/zram0/disksize" "/sys/block/zram0/reset" "/dev/block/zram0"; do
            if [ ! -e "$req" ]; then
                log_msg "ERROR: Required file/directory not found: $req"
                echo "Please ensure that ZRAM is supported and properly initialized on your system."
                return 1
            fi
        done

        cat /sys/class/zram-control/hot_add >/dev/null 2>&1
        echo "3" > /proc/sys/vm/drop_caches 2>/dev/null
        swapoff /dev/block/zram0 2>/dev/null
        echo "1" > /sys/block/zram0/reset 2>/dev/null
        echo "$size_bytes" > /sys/block/zram0/disksize 2>/dev/null
        mkswap /dev/block/zram0 >/dev/null 2>&1
        swapon /dev/block/zram0 -p 5 >/dev/null 2>&1
        if [ $? -eq 0 ]; then
            log_msg "ZRAM successfully enabled with size: $size_bytes bytes."
            su -lp 2000 -c "cmd notification post -S bigtext -t 'Lynx - Deity' 'Lynx' 'ZRAM Enabled'" >/dev/null 2>&1
        else
            log_msg "ERROR: Failed to enable ZRAM."
            return 1
        fi
    fi

    # Configure ZRAM algorithm (only if algo is provided)
    if [ -n "$algo" ]; then
        output=$(cat /sys/block/zram0/comp_algorithm 2>/dev/null)
        case "$output" in
            *"$algo"*)
                echo "$algo" > /sys/block/zram0/comp_algorithm 2>/dev/null
                log_msg "ZRAM compression algorithm set to '$algo'."
                ;;
            *)
                log_msg "ERROR: Requested algorithm '$algo' is not supported."
                return 1
                ;;
        esac
    fi
}

# Disable ZRAM
main_disablezram() {
    if [ ! -f /sys/class/zram-control/hot_remove ] || ! ls /dev/block/zram* > /dev/null 2>&1; then
        log_msg "ERROR: Required ZRAM files or devices not found. Please ensure ZRAM is installed and enabled."
        return 1
    fi
    log_msg "Disabling all ZRAM..."
    echo "3" > /proc/sys/vm/drop_caches 2>/dev/null
    for zram_device in /dev/block/zram*; do
        [ -e "$zram_device" ] || continue
        log_msg "Processing device: $zram_device"
        swapoff "$zram_device" 2>/dev/null
        echo "0" > /sys/class/zram-control/hot_remove 2>/dev/null
        log_msg "Successfully disabled ZRAM for $zram_device."
    done
    su -lp 2000 -c "cmd notification post -S bigtext -t 'Lynx - Deity' 'Lynx' 'ZRAM Disabled'" >/dev/null 2>&1
}

# Main entry point for Lxcore dispatcher
main_zram() {
    shift # drop -zram
    case "$1" in
        set)
            shift
            main_setzram "$@"
            ;;
        disable)
            main_disablezram
            ;;
        help|*)
            help_zram
            ;;
    esac
}