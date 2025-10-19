#!/bin/bash

# Default I/O settings for all block types
apply_default_io() {
    # Apply default settings for all queues
    for queue in /sys/block/*/queue; do
        echo "0" > "$queue/add_random"
        echo "0" > "$queue/iostats"
        echo "1" > "$queue/rq_affinity"
        echo "128" > "$queue/nr_requests"
    done

    # Internal Storage (UFS User)
    for int in /sys/block/sd*/queue; do
        if grep -q "kyber" "$int/scheduler"; then
            echo "kyber" > "$int/scheduler"
            echo "512" > "$int/read_ahead_kb"
            echo "128" > "$int/nr_requests"
        elif grep -q "noop" "$int/scheduler"; then
            echo "noop" > "$int/scheduler"
            echo "512" > "$int/read_ahead_kb"
            echo "128" > "$int/nr_requests"
        else
            echo "512" > "$int/read_ahead_kb"
            echo "128" > "$int/nr_requests"
        fi
    done

    # External Storage (EMMC User)
    for ext in /sys/block/mmcblk*/queue; do
        if grep -q "kyber" "$ext/scheduler"; then
            echo "kyber" > "$ext/scheduler"
            echo "128" > "$ext/read_ahead_kb"
            echo "128" > "$ext/nr_requests"
            echo "1" > "$ext/rq_affinity"
            echo "0" > "$ext/iosched/slice_idle"
            echo "0" > "$ext/iosched/slice_idle_us"
            echo "0" > "$ext/iosched/group_idle"
            echo "0" > "$ext/iosched/group_idle_us"
            echo "1" > "$ext/iosched/low_latency"
        elif grep -q "noop" "$ext/scheduler"; then
            echo "noop" > "$ext/scheduler"
            echo "128" > "$ext/read_ahead_kb"
            echo "128" > "$ext/nr_requests"
            echo "1" > "$ext/rq_affinity"
            echo "0" > "$ext/iosched/slice_idle"
            echo "0" > "$ext/iosched/slice_idle_us"
            echo "0" > "$ext/iosched/group_idle"
            echo "0" > "$ext/iosched/group_idle_us"
            echo "1" > "$ext/iosched/low_latency"
        else
            echo "128" > "$ext/read_ahead_kb"
            echo "128" > "$ext/nr_requests"
            echo "1" > "$ext/rq_affinity"
            echo "0" > "$ext/iosched/slice_idle"
            echo "0" > "$ext/iosched/slice_idle_us"
            echo "0" > "$ext/iosched/group_idle"
            echo "0" > "$ext/iosched/group_idle_us"
            echo "1" > "$ext/iosched/low_latency"
        fi
    done

    # Looping
    for loop in /sys/block/loop*/queue; do
        if grep -q "kyber" "$loop/scheduler"; then
            echo "kyber" > "$loop/scheduler"
            echo "512" > "$loop/read_ahead_kb"
        else
            echo "none" > "$loop/scheduler"
            echo "512" > "$loop/read_ahead_kb"
        fi
    done

    # RAM
    for ram in /sys/block/ram*/queue; do
        echo "0" > "$ram/rotational"
        echo "write back" > "$ram/write_cache"
    done

    # DM
    for dm in /sys/block/dm*/queue; do
        echo "0" > "$dm/rotational"
        echo "write back" > "$dm/write_cache"
    done

    # ZRAM
    echo "write back" > /sys/block/zram0/queue/write_cache
}

# Apply custom scheduler for a specific block type
apply_block_scheduler() {
    local block_type="$1"
    local scheduler="$2"

    case "$block_type" in
        sd*)
            for queue in /sys/block/sd*/queue; do
                echo "$scheduler" > "$queue/scheduler"
            done
            ;;
        mmcblk*)
            for queue in /sys/block/mmcblk*/queue; do
                echo "$scheduler" > "$queue/scheduler"
            done
            ;;
        loop*)
            for queue in /sys/block/loop*/queue; do
                echo "$scheduler" > "$queue/scheduler"
            done
            ;;
        ram*)
            for queue in /sys/block/ram*/queue; do
                echo "$scheduler" > "$queue/scheduler"
            done
            ;;
        dm*)
            for queue in /sys/block/dm*/queue; do
                echo "$scheduler" > "$queue/scheduler"
            done
            ;;
        zram*)
            for queue in /sys/block/zram*/queue; do
                echo "$scheduler" > "$queue/scheduler"
            done
            ;;
        *)
            log_msg "ERROR: Unknown block type '$block_type'."
            return 1
            ;;
    esac
}

# Apply custom scheduler for a specific block device
apply_specific_block_scheduler() {
    local block_device="$1"
    local scheduler="$2"

    if [ -d "/sys/block/$block_device/queue" ]; then
        echo "$scheduler" > "/sys/block/$block_device/queue/scheduler"
    else
        log_msg "ERROR: Block device '$block_device' not found."
        return 1
    fi
}

# Function to summarize I/O configurations
summarize_io_configurations() {
    OUTPUT_FILE="$DIR/output.conf"

    # Clear output file
    > "$OUTPUT_FILE"

    # Summarize available schedulers
    echo "Available Schedulers:" >> "$OUTPUT_FILE"
    for block_type in sd mmcblk loop ram dm zram; do
        blocks=$(ls /sys/block | grep "^$block_type")
        if [ -n "$blocks" ]; then
            # Get the first block's available schedulers as representative
            first_block=$(echo "$blocks" | head -n 1)
            if [ -d "/sys/block/$first_block/queue" ]; then
                scheduler=$(cat "/sys/block/$first_block/queue/scheduler")
                available_schedulers=$(echo "$scheduler" | tr -d '[]')
                echo "$block_type*: $available_schedulers" >> "$OUTPUT_FILE"
            fi
        fi
    done
    echo "" >> "$OUTPUT_FILE"

    # Function to summarize blocks
    summarize_blocks() {
        local block_type="$1"
        shift  # Remove the first argument (block type)
        
        local common_scheduler=""
        local common_read_ahead_kb=""
        local common_nr_requests=""
        local differences_found=0

        # Process each block
        for block in "$@"; do
            if [ -d "/sys/block/$block/queue" ]; then
                # Extract active scheduler (value inside [])
                scheduler=$(cat "/sys/block/$block/queue/scheduler")
                active_scheduler=$(echo "$scheduler" | sed 's/.*\[\([^]]*\)\].*/\1/')
                
                read_ahead_kb=$(cat "/sys/block/$block/queue/read_ahead_kb")
                nr_requests=$(cat "/sys/block/$block/queue/nr_requests")

                if [ -z "$common_scheduler" ]; then
                    common_scheduler="$active_scheduler"
                    common_read_ahead_kb="$read_ahead_kb"
                    common_nr_requests="$nr_requests"
                else
                    if [ "$active_scheduler" != "$common_scheduler" ] || \
                       [ "$read_ahead_kb" != "$common_read_ahead_kb" ] || \
                       [ "$nr_requests" != "$common_nr_requests" ]; then
                        differences_found=1
                    fi
                fi
            fi
        done

        # Save summary to file
        if [ "$differences_found" -eq 0 ]; then
            echo "All Block $block_type ($common_scheduler, $common_read_ahead_kb, $common_nr_requests)" >> "$OUTPUT_FILE"
        else
            echo "All Block $block_type (varies)" >> "$OUTPUT_FILE"
            for block in "$@"; do
                if [ -d "/sys/block/$block/queue" ]; then
                    # Extract active scheduler (value inside [])
                    scheduler=$(cat "/sys/block/$block/queue/scheduler")
                    active_scheduler=$(echo "$scheduler" | sed 's/.*\[\([^]]*\)\].*/\1/')
                    
                    read_ahead_kb=$(cat "/sys/block/$block/queue/read_ahead_kb")
                    nr_requests=$(cat "/sys/block/$block/queue/nr_requests")
                    echo "$block ($active_scheduler, $read_ahead_kb, $nr_requests)" >> "$OUTPUT_FILE"
                fi
            done
        fi
    }

    # Add "Current Block Configurations" label
    echo "Current Block Configurations:" >> "$OUTPUT_FILE"

    # Summarize for each block type
    for block_type in sd mmcblk loop ram dm zram; do
        blocks=$(ls /sys/block | grep "^$block_type")
        if [ -n "$blocks" ]; then
            summarize_blocks "$block_type" $blocks
        fi
    done
}

# Help function for IO commands
help_io() {
    echo "Usage: Lxcore -io [apply|help|<block>-<scheduler>]"
    echo ""
    echo "Options:"
    echo "  apply                     Apply default I/O optimizations to all block devices."
    echo "  apply <block>-<scheduler> Apply default I/O optimizations and override specific block types or devices with the given scheduler."
    echo "  <block>-<scheduler>       Apply I/O optimizations only to specific block types or devices with the given scheduler."
    echo "  help                      Show this help message."
    echo ""

    # Display content of output.conf
    if [ -f "$DIR/output.conf" ]; then
        cat "$DIR/output.conf"
    else
        echo "No block configurations found."
    fi
    echo ""
    echo "Examples:"
    echo "  Lxcore -io apply                     # Apply default I/O optimizations to all block devices."
    echo "  Lxcore -io apply dm-kyber            # Apply default I/O optimizations and set 'kyber' scheduler for all 'dm' devices."
    echo "  Lxcore -io ram-noop                  # Apply 'noop' scheduler only to all 'ram' devices."
    echo "  Lxcore -io dm9-kyber                 # Apply 'kyber' scheduler only to the specific 'dm-9' device."
    echo "  Lxcore -io apply ram-kyber dm2-noop  # Apply default I/O optimizations, set 'kyber' for all 'ram' devices except 'dm-2', and set 'noop' for 'dm-2'."
}

# Main function for -io command
main_io() {
    case "$2" in
        apply)
            apply_default_io
            shift 2
            while [ -n "$1" ]; do
                if [[ "$1" == *-* ]]; then
                    block="${1%-*}"
                    scheduler="${1#*-}"
                    if echo "$block" | grep -qE '^[a-zA-Z]+$'; then
                        apply_block_scheduler "$block" "$scheduler"
                    else
                        apply_specific_block_scheduler "$block" "$scheduler"
                    fi
                fi
                shift
            done
            ;;
        help)
            summarize_io_configurations
            help_io
            ;;
        *)
            if [ -z "$2" ]; then
                summarize_io_configurations
                help_io
            else
                shift
                while [ -n "$1" ]; do
                    if [[ "$1" == *-* ]]; then
                        block="${1%-*}"
                        scheduler="${1#*-}"
                        if echo "$block" | grep -qE '^[a-zA-Z]+$'; then
                            apply_block_scheduler "$block" "$scheduler"
                        else
                            apply_specific_block_scheduler "$block" "$scheduler"
                        fi
                    fi
                    shift
                done
            fi
            ;;
    esac
}