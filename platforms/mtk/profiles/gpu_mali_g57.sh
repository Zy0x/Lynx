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

# 3. Peak Frequency Clamping for G57 via OPP dump (Active in Perf/Extreme)
write_node "0" "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
if [ "$MODE" = "perf" ] || [ "$MODE" = "extreme" ]; then
    write_node "0" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
    for opp_file in /proc/gpufreq/gpufreq_opp_dump /proc/gpufreqv2/gpu_working_opp_table /proc/gpufreqv2/gpufreq_opp_dump; do
        [ -f "$opp_file" ] || continue
        gpu_peak_freq=$(grep -Eo 'freq = [0-9]+' "$opp_file" 2>/dev/null | head -n 1 | cut -d'=' -f2 | tr -d ' ')
        if [ -n "$gpu_peak_freq" ]; then
            write_node "$gpu_peak_freq" "/sys/module/ged/parameters/gpu_cust_upbound_freq"
            write_node "$gpu_peak_freq" "/sys/module/ged/parameters/gpu_cust_boost_freq"
            write_node "$gpu_peak_freq" "/sys/module/ged/parameters/gpu_bottom_freq"
            break
        fi
    done
fi
