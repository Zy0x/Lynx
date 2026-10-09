#!/system/bin/sh
# Lynx Kernel Manager (LKM) - Dynamic GPU & Devfreq Controller
# Universal Qualcomm Adreno, MediaTek GED/GPUFreq v1-v2, and Devfreq/RDNA Support
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
    local platform="generic"
    local cur_freq=0
    local min_freq=0
    local max_freq=0
    local cur_gov="unknown"
    local load=0
    local boost=0
    local freqs_json=""
    local govs_json=""

    # 1. Qualcomm Adreno Architecture
    if [ -d "/sys/class/kgsl/kgsl-3d0" ]; then
        vendor="Qualcomm Adreno"
        platform="adreno"
        cur_freq=$(cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null || cat /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq 2>/dev/null || echo 0)
        [ "$cur_freq" -gt 1000000 ] 2>/dev/null && cur_freq=$(( cur_freq / 1000000 ))
        [ "$cur_freq" -gt 10000 ] 2>/dev/null && cur_freq=$(( cur_freq / 1000 ))

        max_freq=$(cat /sys/class/kgsl/kgsl-3d0/max_gpuclk 2>/dev/null || cat /sys/class/kgsl/kgsl-3d0/max_clock_mhz 2>/dev/null || cat /sys/class/kgsl/kgsl-3d0/devfreq/max_freq 2>/dev/null || echo 0)
        [ "$max_freq" -gt 1000000 ] 2>/dev/null && max_freq=$(( max_freq / 1000000 ))
        [ "$max_freq" -gt 10000 ] 2>/dev/null && max_freq=$(( max_freq / 1000 ))

        min_freq=$(cat /sys/class/kgsl/kgsl-3d0/min_clock_mhz 2>/dev/null || cat /sys/class/kgsl/kgsl-3d0/devfreq/min_freq 2>/dev/null || echo 0)
        [ "$min_freq" -gt 1000000 ] 2>/dev/null && min_freq=$(( min_freq / 1000000 ))
        [ "$min_freq" -gt 10000 ] 2>/dev/null && min_freq=$(( min_freq / 1000 ))

        cur_gov=$(cat /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null || echo "msm-adreno-tz")

        load=$(cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null | tr -dc '0-9')
        [ -z "$load" ] && load=$(cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null | awk '{if ($2>0) printf "%d", ($1*100)/$2; else print 0}')
        boost=$(cat /sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost 2>/dev/null | tr -dc '0-9')

        # Frequencies table
        avail_raw=$(cat /sys/class/kgsl/kgsl-3d0/gpu_available_frequencies 2>/dev/null || cat /sys/class/kgsl/kgsl-3d0/freq_table_mhz 2>/dev/null || cat /sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies 2>/dev/null)
        if [ -n "$avail_raw" ]; then
            for f in $avail_raw; do
                [ "$f" -gt 1000000 ] 2>/dev/null && f=$(( f / 1000000 ))
                [ "$f" -gt 10000 ] 2>/dev/null && f=$(( f / 1000 ))
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
        else
            govs_json="\"msm-adreno-tz\",\"performance\",\"powersave\",\"simple_ondemand\""
        fi

    # 2. MediaTek Mali / GED Architecture (GPUFreq v1 & v2)
    elif [ -d "/proc/gpufreq" ] || [ -d "/proc/gpufreqv2" ] || [ -d "/sys/module/ged" ] || [ -d "/sys/kernel/ged/hal" ]; then
        vendor="MediaTek Mali"
        platform="mali_ged"
        if [ -r "/sys/kernel/ged/hal/current_freqency" ]; then
            cur_raw=$(cat /sys/kernel/ged/hal/current_freqency 2>/dev/null | awk '{if(NF>=2) print $2; else print $1}')
            [ "$cur_raw" -gt 0 ] 2>/dev/null && cur_freq=$cur_raw
        fi
        if [ "$cur_freq" -eq 0 ] 2>/dev/null; then
            cur_raw=$(cat /proc/gpufreq/gpufreq_opp_freq /proc/gpufreqv2/gpufreq_opp_freq 2>/dev/null | grep -Eo 'freq = [0-9]+' | head -n 1 | cut -d'=' -f2 | tr -d ' ')
            [ -n "$cur_raw" ] && [ "$cur_raw" -gt 0 ] 2>/dev/null && cur_freq=$cur_raw
        fi
        if [ "$cur_freq" -eq 0 ] 2>/dev/null; then
            cur_raw=$(cat /proc/gpufreq/gpufreq_var_dump 2>/dev/null | grep -o 'freq: [0-9]*' | head -n1 | cut -d' ' -f2)
            [ -n "$cur_raw" ] && [ "$cur_raw" -gt 0 ] 2>/dev/null && cur_freq=$cur_raw
        fi
        [ "$cur_freq" -gt 10000 ] 2>/dev/null && cur_freq=$(( cur_freq / 1000 ))

        # Active floor and ceiling
        bot_raw=$(cat /sys/module/ged/parameters/gpu_bottom_freq 2>/dev/null | tr -dc '0-9')
        bst_raw=$(cat /sys/module/ged/parameters/gpu_cust_boost_freq 2>/dev/null | tr -dc '0-9')
        up_raw=$(cat /sys/module/ged/parameters/gpu_cust_upbound_freq 2>/dev/null | tr -dc '0-9')
        [ -n "$bot_raw" ] && [ "$bot_raw" -gt 0 ] 2>/dev/null && min_freq=$bot_raw
        [ -n "$bst_raw" ] && [ "$bst_raw" -gt "$min_freq" ] 2>/dev/null && min_freq=$bst_raw
        [ -n "$up_raw" ] && [ "$up_raw" -gt 0 ] 2>/dev/null && max_freq=$up_raw

        # Parse frequencies from OPP dump table (v1 or v2)
        opp_freqs=$(cat /proc/gpufreq/gpufreq_opp_dump /proc/gpufreqv2/gpu_working_opp_table /proc/gpufreqv2/gpufreq_opp_dump 2>/dev/null | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ' | sort -nu)
        if [ -n "$opp_freqs" ]; then
            for f in $opp_freqs; do
                f_mhz=$(( f / 1000 ))
                [ -n "$freqs_json" ] && freqs_json="$freqs_json,"
                freqs_json="${freqs_json}${f_mhz}"
            done
            [ "$min_freq" -eq 0 ] 2>/dev/null && min_freq=$(echo "$opp_freqs" | head -n 1)
            [ "$max_freq" -eq 0 ] 2>/dev/null && max_freq=$(echo "$opp_freqs" | tail -n 1)
        fi
        [ "$min_freq" -gt 10000 ] 2>/dev/null && min_freq=$(( min_freq / 1000 ))
        [ "$max_freq" -gt 10000 ] 2>/dev/null && max_freq=$(( max_freq / 1000 ))

        cur_gov=$(cat /sys/kernel/ged/hal/dvfs_loading_mode 2>/dev/null || cat /sys/module/ged/parameters/cpu_boost_policy 2>/dev/null || echo "ged")
        govs_json="\"0\",\"1\",\"2\",\"ged\""

        load=$(cat /sys/kernel/ged/hal/gpu_utilization 2>/dev/null | awk '{print int($1)}')
        [ -z "$load" ] && load=$(cat /sys/module/ged/parameters/gpu_loading 2>/dev/null | tr -dc '0-9')
        [ -z "$load" ] && load=$(cat /proc/gpufreq/gpufreq_var_dump 2>/dev/null | grep -i 'gpu_loading' | cut -d '=' -f 2 | tr -dc '0-9')
        boost=$(cat /sys/module/ged/parameters/boost_amp 2>/dev/null | tr -dc '0-9')
        [ -z "$boost" ] || [ "$boost" -eq 0 ] 2>/dev/null && boost=$(cat /sys/module/ged/parameters/ged_boost_enable 2>/dev/null | tr -dc '0-9')

    # 3. Generic Devfreq / ARM Mali Kbase / Samsung Xclipse AMD RDNA
    else
        devpath=""
        for d in /sys/class/devfreq/*sgpu* /sys/class/devfreq/*gpu* /sys/class/devfreq/*mali*; do
            if [ -d "$d" ]; then devpath="$d"; break; fi
        done
        if [ -n "$devpath" ]; then
            case "$devpath" in
                *sgpu*)
                    vendor="Samsung Xclipse AMD RDNA"
                    platform="rdna"
                    ;;
                *mali*)
                    vendor="ARM Mali Devfreq"
                    platform="mali"
                    ;;
                *)
                    vendor="Generic Devfreq GPU"
                    platform="generic"
                    ;;
            esac
            cur_freq=$(cat "$devpath/cur_freq" 2>/dev/null || echo 0)
            [ "$cur_freq" -gt 1000000 ] 2>/dev/null && cur_freq=$(( cur_freq / 1000000 ))
            [ "$cur_freq" -gt 10000 ] 2>/dev/null && cur_freq=$(( cur_freq / 1000 ))

            min_freq=$(cat "$devpath/min_freq" 2>/dev/null || echo 0)
            [ "$min_freq" -gt 1000000 ] 2>/dev/null && min_freq=$(( min_freq / 1000000 ))
            [ "$min_freq" -gt 10000 ] 2>/dev/null && min_freq=$(( min_freq / 1000 ))

            max_freq=$(cat "$devpath/max_freq" 2>/dev/null || echo 0)
            [ "$max_freq" -gt 1000000 ] 2>/dev/null && max_freq=$(( max_freq / 1000000 ))
            [ "$max_freq" -gt 10000 ] 2>/dev/null && max_freq=$(( max_freq / 1000 ))

            cur_gov=$(cat "$devpath/governor" 2>/dev/null || echo "simple_ondemand")
            load=$(cat "$devpath/load" 2>/dev/null | tr -dc '0-9')
            boost=0

            avail_raw=$(cat "$devpath/available_frequencies" 2>/dev/null)
            if [ -n "$avail_raw" ]; then
                for f in $avail_raw; do
                    [ "$f" -gt 1000000 ] 2>/dev/null && f=$(( f / 1000000 ))
                    [ "$f" -gt 10000 ] 2>/dev/null && f=$(( f / 1000 ))
                    [ -n "$freqs_json" ] && freqs_json="$freqs_json,"
                    freqs_json="${freqs_json}${f}"
                done
            fi
            govs_raw=$(cat "$devpath/available_governors" 2>/dev/null)
            if [ -n "$govs_raw" ]; then
                for g in $govs_raw; do
                    [ -n "$govs_json" ] && govs_json="$govs_json,"
                    govs_json="${govs_json}\"${g}\""
                done
            else
                govs_json="\"simple_ondemand\",\"performance\",\"powersave\""
            fi
        fi
    fi

    [ -z "$load" ] && load=0
    [ -z "$boost" ] && boost=0

    printf '{"vendor":"%s","platform":"%s","cur_freq":%d,"cur_mhz":%d,"min_freq":%d,"min_mhz":%d,"max_freq":%d,"max_mhz":%d,"cur_gov":"%s","load":%d,"busy":%d,"boost":%d,"avail_freqs":[%s],"avail_govs":[%s]}\n' \
        "$vendor" "$platform" "$cur_freq" "$cur_freq" "$min_freq" "$min_freq" "$max_freq" "$max_freq" "$cur_gov" "$load" "$load" "$boost" "$freqs_json" "$govs_json"
}

set_gpu_freq() {
    local min_mhz="$1"
    local max_mhz="$2"
    [ -z "$min_mhz" ] && return 1
    [ -z "$max_mhz" ] && max_mhz="$min_mhz"

    # Qualcomm Adreno
    if [ -d "/sys/class/kgsl/kgsl-3d0" ]; then
        write_node "$max_mhz" "/sys/class/kgsl/kgsl-3d0/max_clock_mhz"
        write_node "$min_mhz" "/sys/class/kgsl/kgsl-3d0/min_clock_mhz"
        write_node "$(( max_mhz * 1000000 ))" "/sys/class/kgsl/kgsl-3d0/max_gpuclk"
        write_node "$(( min_mhz * 1000000 ))" "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq"
        write_node "$(( max_mhz * 1000000 ))" "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq"
        echo "Adreno GPU clock set to ${min_mhz} - ${max_mhz} MHz."

    # MediaTek Mali / GED
    elif [ -d "/proc/gpufreq" ] || [ -d "/proc/gpufreqv2" ] || [ -d "/sys/module/ged" ] || [ -d "/sys/kernel/ged/hal" ]; then
        min_khz=$(( min_mhz * 1000 ))
        max_khz=$(( max_mhz * 1000 ))

        if [ "$min_mhz" -eq "$max_mhz" ]; then
            write_node "$max_khz" "/proc/gpufreq/gpufreq_opp_freq"
            write_node "$max_khz" "/proc/gpufreqv2/gpufreq_opp_freq"
        else
            write_node "0" "/proc/gpufreq/gpufreq_opp_freq"
            write_node "0" "/proc/gpufreqv2/gpufreq_opp_freq"
        fi

        min_idx=$(awk -F'[][]' -v f="freq = ${min_khz}," '$0 ~ f {print int($2); exit}' /proc/gpufreq/gpufreq_opp_dump /proc/gpufreqv2/gpu_working_opp_table /proc/gpufreqv2/gpufreq_opp_dump 2>/dev/null)
        max_idx=$(awk -F'[][]' -v f="freq = ${max_khz}," '$0 ~ f {print int($2); exit}' /proc/gpufreq/gpufreq_opp_dump /proc/gpufreqv2/gpu_working_opp_table /proc/gpufreqv2/gpufreq_opp_dump 2>/dev/null)

        [ -n "$min_idx" ] && write_node "$min_idx" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
        [ -n "$max_idx" ] && write_node "$max_idx" "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
        write_node "$min_khz" "/sys/module/ged/parameters/gpu_bottom_freq"
        write_node "$min_khz" "/sys/module/ged/parameters/gpu_cust_boost_freq"
        write_node "$max_khz" "/sys/module/ged/parameters/gpu_cust_upbound_freq"
        echo "MediaTek Mali GPU clock set to ${min_mhz} - ${max_mhz} MHz."

    # Generic Devfreq
    else
        for d in /sys/class/devfreq/*sgpu* /sys/class/devfreq/*gpu* /sys/class/devfreq/*mali*; do
            if [ -d "$d" ]; then
                write_node "$(( min_mhz * 1000000 ))" "$d/min_freq"
                write_node "$(( max_mhz * 1000000 ))" "$d/max_freq"
                echo "Devfreq GPU clock set to ${min_mhz} - ${max_mhz} MHz."
                break
            fi
        done
    fi
}

set_gpu_gov() {
    local target_gov="$1"
    [ -z "$target_gov" ] && return 1

    if [ -d "/sys/class/kgsl/kgsl-3d0/devfreq" ]; then
        write_node "$target_gov" "/sys/class/kgsl/kgsl-3d0/devfreq/governor"
        echo "Qualcomm Adreno GPU governor set to $target_gov."
    elif [ -d "/sys/kernel/ged/hal" ]; then
        write_node "$target_gov" "/sys/kernel/ged/hal/dvfs_loading_mode"
        echo "MediaTek GED GPU loading mode set to $target_gov."
    else
        for d in /sys/class/devfreq/*sgpu* /sys/class/devfreq/*gpu* /sys/class/devfreq/*mali*; do
            if [ -d "$d" ]; then
                write_node "$target_gov" "$d/governor"
                echo "Devfreq GPU governor set to $target_gov."
                break
            fi
        done
    fi
}

set_gpu_boost() {
    local lvl="$1"
    [ -z "$lvl" ] && return 1

    # Qualcomm Adreno
    if [ -f "/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost" ]; then
        write_node "$lvl" "/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost"
        echo "Adreno GPU adrenoboost set to $lvl."

    # MediaTek Mali GED
    elif [ -d "/sys/module/ged/parameters" ]; then
        case "$lvl" in
            0)
                write_node 0 "/sys/module/ged/parameters/ged_boost_enable"
                write_node 0 "/sys/module/ged/parameters/gx_game_mode"
                write_node 0 "/sys/module/ged/parameters/boost_amp"
                write_node 0 "/sys/module/ged/parameters/gx_boost_on"
                ;;
            1)
                write_node 1 "/sys/module/ged/parameters/ged_boost_enable"
                write_node 1 "/sys/module/ged/parameters/gx_game_mode"
                write_node 1 "/sys/module/ged/parameters/boost_amp"
                write_node 0 "/sys/module/ged/parameters/gx_boost_on"
                ;;
            2)
                write_node 1 "/sys/module/ged/parameters/ged_boost_enable"
                write_node 1 "/sys/module/ged/parameters/gx_game_mode"
                write_node 2 "/sys/module/ged/parameters/boost_amp"
                write_node 1 "/sys/module/ged/parameters/gx_boost_on"
                ;;
        esac
        echo "MediaTek GED boost set to $lvl."
    else
        echo "GPU boost level $lvl requested (generic devfreq)."
    fi
}

case "$1" in
    info|get_info)
        get_gpu_info_json
        ;;
    set_freq)
        set_gpu_freq "$2" "$3"
        ;;
    set_gov)
        set_gpu_gov "$2"
        ;;
    set_boost|boost)
        set_gpu_boost "$2"
        ;;
    help|--help|-h)
        echo "Usage: gpu_manager {info|set_freq <min_mhz> [max_mhz]|set_gov <governor>|set_boost <lvl>}"
        ;;
    *)
        # Silently do nothing when sourced by other scripts
        ;;
esac
