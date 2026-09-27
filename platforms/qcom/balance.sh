#!/system/bin/sh
# Lynx Universal - Qualcomm Snapdragon Balanced Profile
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# ── 1. Reset Devfreq Memory Bus Governors ────────────────────────────
for dev in /sys/class/devfreq/*; do
    [ -d "$dev" ] || continue
    gov_node="$dev/governor"
    case "$dev" in
        *ufshc*)       write_node "simple_ondemand" "$gov_node" ;;
        *cpubw*)       write_node "bw_hwmon" "$gov_node" ;;
        *gpubw*)       write_node "bw_vbif" "$gov_node" ;;
        *kgsl-busmon*) write_node "gpubw_mon" "$gov_node" ;;
        *llccbw*)      write_node "bw_hwmon" "$gov_node" ;;
        *l3-cpu*)      write_node "mem_latency" "$gov_node" ;;
        *bus_ddr*)     write_node "msm-vidc-ddr" "$gov_node" ;;
    esac
    freq_table="$dev/available_frequencies"
    if [ -s "$freq_table" ]; then
        lowest_freq=$(tr -s ' ' '\n' < "$freq_table" 2>/dev/null | sort -n | head -n 1)
        [ -n "$lowest_freq" ] && write_node "$lowest_freq" "$dev/min_freq"
    fi
done

# ── 2. Reset Adreno KGSL Parameters ──────────────────────────────────
KGSL="/sys/class/kgsl/kgsl-3d0"
if [ -d "$KGSL" ]; then
    write_node "1" "$KGSL/throttling"
    write_node "0" "$KGSL/force_bus_on"
    write_node "0" "$KGSL/force_clk_on"
    write_node "0" "$KGSL/force_rail_on"
    write_node "80" "$KGSL/idle_timer"
    
    num_pwr=$(cat "$KGSL/num_pwrlevels" 2>/dev/null)
    if [ -n "$num_pwr" ] && [ "$num_pwr" -gt 1 ]; then
        write_node "$((num_pwr - 1))" "$KGSL/min_pwrlevel"
    fi
fi

# ── 3. Restore CPU Frequency Bounds & Schedutil Defaults ─────────────
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    min_freq=$(cat "$policy/cpuinfo_min_freq" 2>/dev/null)
    max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    [ -n "$min_freq" ] && write_node "$min_freq" "$policy/scaling_min_freq"
    [ -n "$max_freq" ] && write_node "$max_freq" "$policy/scaling_max_freq"
    
    schedutil="$policy/schedutil"
    if [ -d "$schedutil" ]; then
        write_node "500" "$schedutil/up_rate_limit_us"
        write_node "20000" "$schedutil/down_rate_limit_us"
        write_node "99" "$schedutil/hispeed_load"
        write_node "1" "$schedutil/pl"
    fi
done

# Core Ctl & CPU Prefered
for cpu in 0 1 2 3 4 5 6 7; do
    write_node "0 0 0 0" "/sys/devices/system/cpu/cpu${cpu}/core_ctl/not_preferred"
done

# ── 4. Workqueue & Scheduler Efficiency ──────────────────────────────
write_node "Y" "/sys/module/workqueue/parameters/power_efficient"
write_node "1" "/proc/sys/kernel/sched_autogroup_enabled"
write_node "0" "/proc/sys/kernel/sched_tunable_scaling"

# CPUSet & SchedTune Balanced
write_node "0-7" "/dev/cpuset/foreground/cpus"
write_node "0-2" "/dev/cpuset/background/cpus"
write_node "0-5" "/dev/cpuset/system-background/cpus"
write_node "0-7" "/dev/cpuset/top-app/cpus"
write_node "5" "/dev/stune/foreground/schedtune.boost"
write_node "5" "/dev/stune/top-app/schedtune.boost"

# Revert Unity Trick if applied
for cpu in 0 1 2 3 4 5 6 7; do
    path="/sys/devices/system/cpu/cpu${cpu}"
    [ -e "$path/cpufreq/cpuinfo_max_freq" ] && chmod 444 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
    [ -e "$path/cpu_capacity" ] && chmod 444 "$path/cpu_capacity" 2>/dev/null
done

# Restore Display Refresh Rates
if [ -f "/dev/lynx_orig_min_rr" ]; then
    orig_min=$(cat "/dev/lynx_orig_min_rr" 2>/dev/null)
    [ -n "$orig_min" ] && settings put system min_refresh_rate "$orig_min" 2>/dev/null
    rm -f "/dev/lynx_orig_min_rr" 2>/dev/null
fi
if [ -f "/dev/lynx_orig_peak_rr" ]; then
    orig_peak=$(cat "/dev/lynx_orig_peak_rr" 2>/dev/null)
    [ -n "$orig_peak" ] && settings put system peak_refresh_rate "$orig_peak" 2>/dev/null
    rm -f "/dev/lynx_orig_peak_rr" 2>/dev/null
fi
