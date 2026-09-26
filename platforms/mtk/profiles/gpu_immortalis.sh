#!/system/bin/sh
# Lynx Universal - Targeted Profile: ARM Immortalis / High-End Mali (G710/G715/G720)
# Designed for MediaTek Dimensity 9000 / 9200 / 9300 flagship series
# Pure POSIX /system/bin/sh compliance

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# 1. Flagship Mali / Immortalis Shader Engine Tuning
write_node "1" "/sys/module/ged/parameters/ged_smart_boost"
write_node "1" "/sys/module/ged/parameters/gx_force_cpu_boost"
write_node "1" "/sys/module/ged/parameters/boost_gpu_enable"
write_node "1" "/sys/module/ged/parameters/gx_game_mode"
write_node "0" "/sys/module/ged/parameters/deboost_reduce"
write_node "1" "/sys/kernel/fpsgo/common/gpu_block_boost"
write_node "0" "/sys/kernel/fpsgo/fbt/switch_idle"

# 2. Extract and lock peak OPP Frequency
if [ -f "/proc/gpufreq/gpufreq_opp_dump" ]; then
    opp_val=$(head -n 1 /proc/gpufreq/gpufreq_opp_dump 2>/dev/null)
    gpu_peak_freq=$(echo "$opp_val" | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
    if [ -n "$gpu_peak_freq" ]; then
        write_node "$gpu_peak_freq" "/sys/module/ged/parameters/gpu_cust_boost_freq"
        write_node "$gpu_peak_freq" "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
        write_node "$gpu_peak_freq" "/sys/module/ged/parameters/gpu_cust_upbound_freq"
    fi
fi
