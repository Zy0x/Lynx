#!/system/bin/sh
# Lynx Universal - Targeted Profile: Infinix X698 / MT6781 (Helio G96 / Dimensity)
# Applied ONLY when kernel ground truth confirms Infinix X698 / MT6781
# Completely safe against device spoofing!

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# 1. Transsion Touch Screen Ultra-Response (Dynamic based on Profile)
if [ "$MODE" = "perf" ] || [ "$MODE" = "extreme" ]; then
    write_node "1" "/sys/devices/platform/tp_wake_switch/game_mode"
    write_node "1" "/sys/devices/platform/goodix_ts.0/game_mode"
    write_node "240" "/sys/devices/platform/goodix_ts.0/report_rate"
else
    write_node "0" "/sys/devices/platform/tp_wake_switch/game_mode"
    write_node "0" "/sys/devices/platform/goodix_ts.0/game_mode"
    write_node "120" "/sys/devices/platform/goodix_ts.0/report_rate"
fi

# 2. MT6781 Dual-Cluster Sched Load Boost
write_node "1" "/sys/devices/system/cpu/cpu0/sched_load_boost"
write_node "1" "/sys/devices/system/cpu/cpu1/sched_load_boost"
write_node "1" "/sys/devices/system/cpu/cpu6/sched_load_boost"
write_node "1" "/sys/devices/system/cpu/cpu7/sched_load_boost"

# 3. Transsion Charging Controller Bypass Alignment
if [ -f "/sys/class/power_supply/battery/charging_enabled" ]; then
    chmod 644 "/sys/class/power_supply/battery/charging_enabled" 2>/dev/null
fi

# 4. Transsion / Infinix FPSGo Latency Bounds
write_node "100" "/sys/kernel/fpsgo/fbt/light_loading_policy"
write_node "100" "/sys/kernel/fpsgo/fbt/light_loading_policy_90"
write_node "100" "/sys/kernel/fpsgo/fbt/llf_task_policy"
write_node "100" "/sys/kernel/fpsgo/fbt/llf_task_policy_90"
write_node "15" "/sys/kernel/fpsgo/fstb/margin_mode_gpu_dbnc_a"
write_node "15" "/sys/kernel/fpsgo/fstb/margin_mode_gpu_dbnc_b"
