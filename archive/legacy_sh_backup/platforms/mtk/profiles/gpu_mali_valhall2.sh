#!/system/bin/sh
# Lynx Universal - Targeted Profile: ARM Mali Valhall Gen-2 (G68 / G77 / G78)
# High-density core execution engine optimizations for Dimensity chips

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# 1. Valhall Gen-2 Shader Core Workload Ramp
write_node "1" "/sys/module/ged/parameters/ged_smart_boost"
write_node "1" "/sys/module/ged/parameters/gx_force_cpu_boost"
write_node "0" "/sys/module/ged/parameters/deboost_reduce"
write_node "1" "/sys/kernel/fpsgo/common/gpu_block_boost"

# 2. Extract and lock OPP Frequency (Active in Perf/Extreme)
write_node "0" "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
if [ "$MODE" = "perf" ] || [ "$MODE" = "extreme" ]; then
    write_node "0" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
    for opp_file in /proc/gpufreq/gpufreq_opp_dump /proc/gpufreqv2/gpu_working_opp_table /proc/gpufreqv2/gpufreq_opp_dump; do
        [ -f "$opp_file" ] || continue
        gpu_peak_freq=$(grep -Eo 'freq = [0-9]+' "$opp_file" 2>/dev/null | head -n 1 | cut -d'=' -f2 | tr -d ' ')
        if [ -n "$gpu_peak_freq" ]; then
            write_node "$gpu_peak_freq" "/sys/module/ged/parameters/gpu_cust_boost_freq"
            write_node "$gpu_peak_freq" "/sys/module/ged/parameters/gpu_cust_upbound_freq"
            break
        fi
    done
fi
