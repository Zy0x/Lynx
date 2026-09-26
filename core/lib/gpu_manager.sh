#!/system/bin/sh
# Lynx Kernel Manager (LKM) - Dynamic GPU & Devfreq Controller
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

get_gpu_info_json() {
    local vendor="unknown"
    local cur_freq=0
    local min_freq=0
    local max_freq=0
    local cur_gov="unknown"
    local freqs_json=""
    local govs_json=""

    # 1. Qualcomm Adreno Architecture
    if [ -d "/sys/class/kgsl/kgsl-3d0" ]; then
        vendor="Qualcomm Adreno"
        cur_freq=$(cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null || echo 0)
        [ "$cur_freq" -gt 1000000 ] 2>/dev/null && cur_freq=$(( cur_freq / 1000000 ))

        max_freq=$(cat /sys/class/kgsl/kgsl-3d0/max_gpuclk 2>/dev/null || cat /sys/class/kgsl/kgsl-3d0/max_clock_mhz 2>/dev/null || echo 0)
        [ "$max_freq" -gt 1000000 ] 2>/dev/null && max_freq=$(( max_freq / 1000000 ))

        min_freq=$(cat /sys/class/kgsl/kgsl-3d0/min_clock_mhz 2>/dev/null || echo 0)
        cur_gov=$(cat /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null || echo "msm-adreno-tz")

        # Frequencies table
        avail_raw=$(cat /sys/class/kgsl/kgsl-3d0/gpu_available_frequencies 2>/dev/null || cat /sys/class/kgsl/kgsl-3d0/freq_table_mhz 2>/dev/null)
        if [ -n "$avail_raw" ]; then
            for f in $avail_raw; do
                [ "$f" -gt 1000000 ] 2>/dev/null && f=$(( f / 1000000 ))
                [ -n "$freqs_json" ] && freqs_json="$freqs_json,"
                freqs_json="${freqs_json}${f}"
            done
        fi

        # Governors table
        govs_raw=$(cat /sys/class/kgsl/kgsl-3d0/devfreq/available_governors 2>/dev/null)
        if [ -n "$govs_raw" ]; then
            for g in $govs_raw; do
                [ -n "$govs_json" ] && govs_json="$govs_json,"
                govs_json="${govs_json}\"${g}\""
            done
        fi

    # 2. MediaTek Mali / GED Architecture
    elif [ -f "/proc/gpufreq/gpufreq_opp_dump" ] || [ -f "/proc/gpufreq/gpufreq_opp_freq" ]; then
        vendor="MediaTek Mali"
        cur_freq=$(cat /proc/gpufreq/gpufreq_opp_freq 2>/dev/null || echo 0)
        [ "$cur_freq" -gt 10000 ] 2>/dev/null && cur_freq=$(( cur_freq / 1000 ))

        if [ -f "/proc/gpufreq/gpufreq_opp_dump" ]; then
            # Parse frequencies from OPP dump table
            opp_freqs=$(grep -Eo 'freq = [0-9]+' /proc/gpufreq/gpufreq_opp_dump 2>/dev/null | cut -d'=' -f2 | tr -d ' ')
            if [ -n "$opp_freqs" ]; then
                for f in $opp_freqs; do
                    f_mhz=$(( f / 1000 ))
                    [ -n "$freqs_json" ] && freqs_json="$freqs_json,"
                    freqs_json="${freqs_json}${f_mhz}"
                done
                max_freq=$(echo "$freqs_json" | cut -d',' -f1)
                min_freq=$(echo "$freqs_json" | tr ',' '\n' | tail -n 1)
            fi
        fi
        cur_gov=$(cat /sys/module/ged/parameters/cpu_boost_policy 2>/dev/null || echo "ged")
        govs_json="\"ged\",\"performance\",\"balance\""
    fi

    printf '{"vendor":"%s","cur_freq":%d,"min_freq":%d,"max_freq":%d,"cur_gov":"%s","avail_freqs":[%s],"avail_govs":[%s]}\n' \
        "$vendor" "$cur_freq" "$min_freq" "$max_freq" "$cur_gov" "$freqs_json" "$govs_json"
}

set_gpu_freq() {
    local target_mhz="$1"
    [ -z "$target_mhz" ] && return 1

    # Qualcomm Adreno
    if [ -d "/sys/class/kgsl/kgsl-3d0" ]; then
        write_node "$target_mhz" "/sys/class/kgsl/kgsl-3d0/max_clock_mhz"
        echo "Adreno GPU clock capped at ${target_mhz} MHz."
    # MediaTek Mali
    elif [ -f "/proc/gpufreq/gpufreq_opp_dump" ]; then
        target_khz=$(( target_mhz * 1000 ))
        write_node "$target_khz" "/sys/module/ged/parameters/gpu_cust_upbound_freq"
        write_node "$target_khz" "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
        echo "MediaTek Mali GPU clock capped at ${target_mhz} MHz."
    fi
}

case "$1" in
    info|get_info)
        get_gpu_info_json
        ;;
    set_freq)
        set_gpu_freq "$2"
        ;;
    *)
        echo "Usage: gpu_manager [info | set_freq <mhz>]"
        ;;
esac
