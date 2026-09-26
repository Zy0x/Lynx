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

# 2. Extract and lock OPP Frequency
if [ -f "/proc/gpufreq/gpufreq_opp_dump" ]; then
    opp_val=$(head -n 1 /proc/gpufreq/gpufreq_opp_dump 2>/dev/null)
    gpu_peak_freq=$(echo "$opp_val" | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
    if [ -n "$gpu_peak_freq" ]; then
        write_node "$gpu_peak_freq" "/sys/module/ged/parameters/gpu_cust_boost_freq"
        write_node "$gpu_peak_freq" "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
    fi
fi
