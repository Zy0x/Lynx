#!/system/bin/sh
# Lynx Kernel Manager (LKM) - Dynamic CPU Cluster & Topology Manager
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# 1. Output Full CPU Topology & Capabilities as JSON
get_cluster_topology_json() {
    local first_policy=1
    printf '{"clusters":['

    for policy in /sys/devices/system/cpu/cpufreq/policy*; do
        [ -d "$policy" ] || continue
        p_num="${policy##*policy}"

        affected=$(cat "$policy/affected_cpus" 2>/dev/null || cat "$policy/related_cpus" 2>/dev/null || echo "$p_num")
        cur_min=$(cat "$policy/scaling_min_freq" 2>/dev/null || echo 0)
        cur_max=$(cat "$policy/scaling_max_freq" 2>/dev/null || echo 0)
        cur_gov=$(cat "$policy/scaling_governor" 2>/dev/null || echo "schedutil")
        
        # Available frequencies (sorted ascending)
        avail_freqs_raw=$(cat "$policy/scaling_available_frequencies" 2>/dev/null | tr ' ' '\n' | sort -n | tr '\n' ' ')
        freqs_json=""
        if [ -n "$avail_freqs_raw" ]; then
            for f in $avail_freqs_raw; do
                [ -n "$freqs_json" ] && freqs_json="$freqs_json,"
                freqs_json="${freqs_json}${f}"
            done
        fi

        # Available governors
        avail_govs_raw=$(cat "$policy/scaling_available_governors" 2>/dev/null)
        govs_json=""
        if [ -n "$avail_govs_raw" ]; then
            for g in $avail_govs_raw; do
                [ -n "$govs_json" ] && govs_json="$govs_json,"
                govs_json="${govs_json}\"${g}\""
            done
        fi

        # Determine cluster role name
        role="Little"
        case "$p_num" in
            0) role="Efficiency" ;;
            3|4) role="Performance" ;;
            6|7) role="Prime" ;;
        esac

        [ "$first_policy" -eq 0 ] && printf ','
        first_policy=0

        printf '{"id":%d,"role":"%s","cpus":"%s","cur_min":%d,"cur_max":%d,"cur_gov":"%s","avail_freqs":[%s],"avail_govs":[%s]}' \
            "$p_num" "$role" "$affected" "$cur_min" "$cur_max" "$cur_gov" "$freqs_json" "$govs_json"
    done

    printf ']}\n'
}

# 2. Set Cluster Bounds
set_cluster_freq() {
    local p_num="$1"
    local min_freq="$2"
    local max_freq="$3"
    local policy="/sys/devices/system/cpu/cpufreq/policy$p_num"

    if [ -d "$policy" ]; then
        hw_max=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
        [ -n "$hw_max" ] && write_node "$hw_max" "$policy/scaling_max_freq"
        [ -n "$min_freq" ] && [ "$min_freq" -gt 0 ] 2>/dev/null && write_node "$min_freq" "$policy/scaling_min_freq"
        [ -n "$max_freq" ] && [ "$max_freq" -gt 0 ] 2>/dev/null && write_node "$max_freq" "$policy/scaling_max_freq"
        echo "Cluster policy$p_num frequencies updated (Min: $min_freq, Max: $max_freq)."
    else
        echo "Error: Policy policy$p_num does not exist."
        return 1
    fi
}

# 3. Set Cluster Governor
set_cluster_gov() {
    local p_num="$1"
    local target_gov="$2"
    local policy="/sys/devices/system/cpu/cpufreq/policy$p_num"

    if [ -d "$policy" ]; then
        avail=$(cat "$policy/scaling_available_governors" 2>/dev/null)
        if echo "$avail" | grep -qw "$target_gov"; then
            write_node "$target_gov" "$policy/scaling_governor"
            echo "Cluster policy$p_num governor set to $target_gov."
        else
            echo "Error: Governor $target_gov is not supported on policy$p_num."
            return 1
        fi
    else
        echo "Error: Policy policy$p_num does not exist."
        return 1
    fi
}

# CLI Dispatcher
case "$1" in
    topology|get_topology)
        get_cluster_topology_json
        ;;
    set_freq)
        set_cluster_freq "$2" "$3" "$4"
        ;;
    set_gov)
        set_cluster_gov "$2" "$3"
        ;;
    *)
        echo "Usage: cluster_manager [topology | set_freq <policy> <min> <max> | set_gov <policy> <gov>]"
        ;;
esac
