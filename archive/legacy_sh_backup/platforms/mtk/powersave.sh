#!/system/bin/sh
# Lynx Universal - MediaTek Dimensity & Helio Powersave Profile
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)

MODDIR="/data/adb/modules/Lynx"

write_node() {
    [ -e "$2" ] || return 0
    echo "$1" > "$2" 2>/dev/null && return 0
    chmod 666 "$2" 2>/dev/null
    echo "$1" > "$2" 2>/dev/null
}

# ── 1. CPU Power Mode: Low Power (1) ─────────────────────────────────
write_node "1" "/proc/cpufreq/cpufreq_power_mode"
write_node "0" "/proc/cpufreq/cpufreq_cci_mode"
write_node "0" "/proc/cpufreq/cpufreq_imax_enable"
write_node "0" "/proc/cpufreq/cpufreq_debug"
write_node "0" "/proc/cpufreq/cpufreq_sched_disable"
write_node "Y" "/sys/module/workqueue/parameters/power_efficient"

# Enable EAS
write_node "1" "/sys/devices/system/cpu/eas/enable"
write_node "0" "/sys/devices/system/cpu/perf/enable"

# Cap CPU Cluster Frequencies to 55% of Max Clock
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    min_freq=$(cat "$policy/cpuinfo_min_freq" 2>/dev/null)
    max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    [ -n "$min_freq" ] && write_node "$min_freq" "$policy/scaling_min_freq"
    
    if [ -n "$max_freq" ]; then
        power_cap=$(( max_freq * 55 / 100 ))
        [ "$power_cap" -gt "$min_freq" ] && write_node "$power_cap" "$policy/scaling_max_freq"
    fi

    pol_id=$(basename "$policy" | tr -dc '0-9')
    # Schedutil sluggish downclocking (asymmetric per-cluster)
    for s_dir in "$policy/schedutil" "$policy/scaling_governor"; do
        if [ -d "$s_dir" ]; then
            if [ "$pol_id" = "0" ]; then
                # Little Cluster: Jeda evaluasi naik 10ms, cepat turun 1ms
                write_node "10000" "$s_dir/up_rate_limit_us"
                write_node "1000" "$s_dir/down_rate_limit_us"
            else
                # Big/Prime Cluster: Sangat enggan naik (20ms), langsung turun (500µs)
                write_node "20000" "$s_dir/up_rate_limit_us"
                write_node "500" "$s_dir/down_rate_limit_us"
            fi
            write_node "99" "$s_dir/hispeed_load"
            write_node "0" "$s_dir/pl"
            write_node "0" "$s_dir/iowait_boost_enable"
        fi
    done
done

# Enable MediaTek PPM Driver & All Power Policies
write_node "1" "/proc/ppm/enabled"
write_node "0 0" "/proc/ppm/policy_status"
write_node "1 1" "/proc/ppm/policy_status"
write_node "2 1" "/proc/ppm/policy_status"
write_node "3 1" "/proc/ppm/policy_status"
write_node "4 1" "/proc/ppm/policy_status"
write_node "5 1" "/proc/ppm/policy_status"
write_node "6 1" "/proc/ppm/policy_status"
write_node "7 1" "/proc/ppm/policy_status"
write_node "8 1" "/proc/ppm/policy_status"
write_node "9 1" "/proc/ppm/policy_status"
write_node "1" "/proc/ppm/cpi/cpi_enabled"

# Cap PPM Cluster Table to 50% OPP Index
for c in 0 1 2; do
    table="/proc/ppm/dump_cluster_${c}_dvfs_table"
    [ -f "$table" ] || continue
    total_opp=$(wc -w < "$table" 2>/dev/null)
    ps_cap_idx=$(( total_opp * 50 / 100 ))
    [ "$ps_cap_idx" -lt 2 ] && ps_cap_idx=3
    last_idx=$(( total_opp - 1 ))
    write_node "$c $ps_cap_idx" "/proc/ppm/policy/hard_userlimit_max_cpu_freq"
    write_node "$c $last_idx" "/proc/ppm/policy/hard_userlimit_min_cpu_freq"
done

# ── 2. GPU & GED Subsystem: Sleep Allowed (Coarse Demand) ─────────────
for mali in /sys/devices/platform/*mali* /sys/devices/platform/soc/*mali* /sys/class/misc/mali*/device; do
    [ -d "$mali" ] || continue
    write_node "coarse_demand" "$mali/power_policy"
done
write_node "0" "/proc/mali/always_on"
write_node "1" "/proc/mali/dvfs_enable"
write_node "0" "/proc/gpufreq/gpufreq_opp_freq"
write_node "0 0" "/proc/gpufreq/gpufreq_fixed_freq_volt"
if [ -f "/proc/gpufreq/gpufreq_opp_dump" ]; then
    opp_top=$(head -n 1 /proc/gpufreq/gpufreq_opp_dump 2>/dev/null)
    opp_bot=$(tail -n 1 /proc/gpufreq/gpufreq_opp_dump 2>/dev/null)
    peak_f=$(echo "$opp_top" | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
    min_f=$(echo "$opp_bot" | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
    last_idx=$(echo "$opp_bot" | awk -F'[][]' '{print int($2)}')
    [ -z "$last_idx" ] && last_idx="48"
    write_node "$last_idx" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
    [ -n "$min_f" ] && write_node "$min_f" "/sys/module/ged/parameters/gpu_cust_boost_freq"
    [ -n "$min_f" ] && write_node "$min_f" "/sys/module/ged/parameters/gpu_bottom_freq"
    if [ -n "$peak_f" ]; then
        ps_target=$(( peak_f * 65 / 100 ))
        ps_opp_line=$(awk -v t="$ps_target" '{
            match($0, /freq = [0-9]+/);
            f = substr($0, RSTART+7, RLENGTH-7) + 0;
            if (f >= t) last_line = $0;
        } END { print last_line }' /proc/gpufreq/gpufreq_opp_dump 2>/dev/null)
        ps_cap_f=$(echo "$ps_opp_line" | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
        ps_cap_idx=$(echo "$ps_opp_line" | awk -F'[][]' '{print int($2)}')
        [ -n "$ps_cap_idx" ] && write_node "$ps_cap_idx" "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
        [ -n "$ps_cap_f" ] && write_node "$ps_cap_f" "/sys/module/ged/parameters/gpu_cust_upbound_freq"
    fi
else
    write_node "48" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
fi
for i in 0 1 2 3 4 5 6 7 8; do
    write_node "$i 1 1" "/proc/gpufreq/gpufreq_limit_table"
done
write_node "0" "/sys/kernel/ged/hal/gpu_boost_level"
write_node "0" "/sys/kernel/ged/hal/dvfs_margin_value"

write_node "0" "/sys/kernel/fpsgo/common/gpu_block_boost"
write_node "0" "/sys/kernel/fpsgo/fbt/boost_ta"
write_node "1" "/sys/kernel/fpsgo/fbt/thrm_limit_cpu"
write_node "0" "/sys/module/ged/parameters/boost_amp"
write_node "0" "/sys/module/ged/parameters/boost_extra"
write_node "0" "/sys/module/ged/parameters/boost_gpu_enable"
write_node "0" "/sys/module/ged/parameters/enable_cpu_boost"
write_node "0" "/sys/module/ged/parameters/enable_gpu_boost"
write_node "30" "/sys/module/ged/parameters/g_fb_dvfs_threshold"
write_node "0" "/sys/module/ged/parameters/ged_boost_enable"
write_node "0" "/sys/module/ged/parameters/ged_smart_boost"
write_node "0" "/sys/module/ged/parameters/gx_boost_on"
write_node "0" "/sys/module/ged/parameters/gx_game_mode"

# ── 3. Kernel, Thermal, & VM Power Conservation ──────────────────────
if [ -f "$MODDIR/platforms/mtk/thermal.sh" ]; then
    sh "$MODDIR/platforms/mtk/thermal.sh" enable >/dev/null 2>&1
fi

write_node "1" "/sys/kernel/eara_thermal/enable"
write_node "100 0" "/proc/driver/thermal/clatm_gpu_threshold"

write_node "0" "/dev/stune/schedtune.boost"
write_node "0" "/dev/stune/foreground/schedtune.boost"
write_node "0" "/dev/stune/top-app/schedtune.boost"

# Enforce 60Hz Screen Refresh Rate for Power Conservation
cur_min_rr=$(settings get system min_refresh_rate 2>/dev/null)
cur_peak_rr=$(settings get system peak_refresh_rate 2>/dev/null)
if [ ! -f "/dev/lynx_orig_min_rr" ] && [ -n "$cur_min_rr" ] && [ "$cur_min_rr" != "null" ]; then
    echo "$cur_min_rr" > /dev/lynx_orig_min_rr
fi
if [ ! -f "/dev/lynx_orig_peak_rr" ] && [ -n "$cur_peak_rr" ] && [ "$cur_peak_rr" != "null" ]; then
    echo "$cur_peak_rr" > /dev/lynx_orig_peak_rr
fi
settings put system min_refresh_rate 60.0 2>/dev/null
settings put system peak_refresh_rate 60.0 2>/dev/null

# Revert Unity Trick if applied
for cpu in 0 1 2 3 4 5 6 7; do
    path="/sys/devices/system/cpu/cpu${cpu}"
    [ -e "$path/cpufreq/cpuinfo_max_freq" ] && chmod 444 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
    [ -e "$path/cpu_capacity" ] && chmod 444 "$path/cpu_capacity" 2>/dev/null
done
