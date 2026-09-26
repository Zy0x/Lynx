#!/system/bin/sh
# Lynx Universal - Targeted Profile: ARM Mali-G57 (Valhall Gen-1)
# Restores Chimera's specialized Mali-G57 gaming optimizations
# Applied ONLY when GPU hardware matches Mali-G57!

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# 1. Mali-G57 Power & DVFS Policy
for mali in /sys/devices/platform/*mali*; do
    [ -d "$mali" ] || continue
    write_node "always_on" "$mali/power_policy"
done
write_node "1" "/proc/mali/always_on"
write_node "0" "/proc/mali/debug_log"

# 2. Valhall Gen-1 Framebuffer & Margin Optimization
write_node "100" "/sys/module/ged/parameters/g_fb_dvfs_threshold"
write_node "1" "/sys/module/ged/parameters/boost_extra"
write_node "1" "/sys/module/ged/parameters/boost_amp"
write_node "1" "/sys/module/ged/parameters/boost_gpu_enable"
write_node "1" "/sys/module/ged/parameters/gx_boost_on"
write_node "1" "/sys/module/ged/parameters/gx_game_mode"

# 3. Peak Frequency Clamping for G57 via OPP dump
if [ -f "/proc/gpufreq/gpufreq_opp_dump" ]; then
    opp_val=$(head -n 1 /proc/gpufreq/gpufreq_opp_dump 2>/dev/null)
    gpu_peak_freq=$(echo "$opp_val" | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
    if [ -n "$gpu_peak_freq" ]; then
        write_node "$gpu_peak_freq" "/sys/module/ged/parameters/gpu_cust_upbound_freq"
        write_node "$gpu_peak_freq" "/sys/module/ged/parameters/gpu_cust_boost_freq"
        write_node "$gpu_peak_freq" "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
        write_node "$gpu_peak_freq" "/sys/module/ged/parameters/gpu_bottom_freq"
    fi
fi
