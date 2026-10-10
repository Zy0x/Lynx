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
        *latfloor*)    write_node "compute" "$gov_node" ;;
        *ufshc*)       write_node "simple_ondemand" "$gov_node" ;;
        *cpubw*|*cpu-ddr-bw*|*ddr-bw*) write_node "bw_hwmon" "$gov_node" ;;
        *gpubw*)       write_node "bw_vbif" "$gov_node" ;;
        *kgsl-busmon*) write_node "gpubw_mon" "$gov_node" ;;
        *llccbw*)      write_node "bw_hwmon" "$gov_node" ;;
        *l3-cpu*|*cpu-ddr-lat*|*memlat-cpu*) write_node "mem_latency" "$gov_node" ;;
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
    write_node "0" "$KGSL/devfreq/adreno_boost"
    write_node "0" "$KGSL/devfreq/adrenoboost"
    for b in /sys/class/devfreq/*kgsl*/adrenoboost /sys/class/devfreq/*kgsl*/adreno_boost; do
        [ -f "$b" ] && write_node "0" "$b"
    done
    write_node "80" "$KGSL/pwrscale/trustzone/target_load"

    # Restore Adreno Devfreq Governor & Pwrscale Policy
    avail_govs=$(cat "$KGSL/devfreq/available_governors" 2>/dev/null)
    if echo "$avail_govs" | grep -q "msm-adreno-tz"; then
        write_node "msm-adreno-tz" "$KGSL/devfreq/governor"
    elif echo "$avail_govs" | grep -q "simple_ondemand"; then
        write_node "simple_ondemand" "$KGSL/devfreq/governor"
    fi
    write_node "trustzone" "$KGSL/pwrscale/policy"
fi

# Reset SurfaceFlinger Frame Latency Overrides
setprop debug.sf.disable_backpressure 0
setprop debug.sf.enable_gl_backpressure 1

# ── 3. Restore CPU Frequency Bounds & Schedutil Defaults ─────────────
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    min_freq=$(cat "$policy/cpuinfo_min_freq" 2>/dev/null)
    max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    [ -n "$min_freq" ] && write_node "$min_freq" "$policy/scaling_min_freq"
    [ -n "$max_freq" ] && write_node "$max_freq" "$policy/scaling_max_freq"
    
    pol_id=$(basename "$policy" | tr -dc '0-9')
    schedutil="$policy/schedutil"
    if [ -d "$schedutil" ]; then
        if [ "$pol_id" = "0" ]; then
            # Little Cluster (Silver/Efficiency): Jeda naik 1ms, tahan 20ms
            write_node "1000" "$schedutil/up_rate_limit_us"
            write_node "20000" "$schedutil/down_rate_limit_us"
            write_node "85" "$schedutil/hispeed_load"
        else
            # Big/Prime Cluster (Gold/Performance/Kryo): Respon instan 0µs, tahan 10ms
            write_node "0" "$schedutil/up_rate_limit_us"
            write_node "10000" "$schedutil/down_rate_limit_us"
            write_node "80" "$schedutil/hispeed_load"
        fi
        write_node "0" "$schedutil/sched_load_boost"
        write_node "1" "$schedutil/pl"
        write_node "1" "$schedutil/iowait_boost_enable"
        if [ -n "$max_freq" ] && [ "$max_freq" -gt 0 ] 2>/dev/null; then
            hi_f=$(( max_freq * 75 / 100 ))
            write_node "$hi_f" "$schedutil/hispeed_freq"
        fi
    fi
    write_node "0" "$policy/sched_load_boost"
done

# Core Ctl & CPU Preferred
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

# UCLAMP
for u_node in "/dev/cpuset/top-app/cpu.uclamp.min" "/proc/sys/kernel/sched_util_clamp_min"; do
    if [ -e "$u_node" ]; then
        max_sc=100
        [ -e "/dev/cpuset/top-app/cpu.uclamp.max" ] && max_sc=$(cat "/dev/cpuset/top-app/cpu.uclamp.max" 2>/dev/null)
        if [ "$max_sc" -gt 100 ] 2>/dev/null; then
            write_node "100" "$u_node"
        else
            write_node "10" "$u_node"
        fi
    fi
done
write_node "1" "/dev/cpuset/top-app/cpu.uclamp.latency_sensitive"
write_node "1" "/dev/cpuset/foreground/boost/cpu.uclamp.latency_sensitive"

# SchedTune
write_node "1" "/dev/stune/schedtune.sched_boost_enabled"
write_node "5" "/dev/stune/schedtune.boost"
write_node "0" "/dev/stune/schedtune.prefer_idle"
write_node "0" "/dev/stune/schedtune.prefer_high_cap"
write_node "10" "/dev/stune/foreground/schedtune.boost"
write_node "1" "/dev/stune/foreground/schedtune.prefer_idle"
write_node "0" "/dev/stune/foreground/schedtune.prefer_high_cap"
write_node "15" "/dev/stune/top-app/schedtune.boost"
write_node "1" "/dev/stune/top-app/schedtune.prefer_idle"
write_node "0" "/dev/stune/top-app/schedtune.prefer_high_cap"
write_node "1" "/proc/sys/kernel/sched_big_task_rotation"
write_node "1" "/proc/sys/kernel/sched_sync_hint_enable"

# VM & Scheduler Responsiveness
write_node "60" "/proc/sys/vm/vfs_cache_pressure"
write_node "16" "/proc/sys/vm/watermark_scale_factor"
write_node "20" "/proc/sys/vm/dirty_ratio"
write_node "5" "/proc/sys/vm/dirty_background_ratio"
write_node "5000000" "/proc/sys/kernel/sched_latency_ns"
write_node "1000000" "/proc/sys/kernel/sched_min_granularity_ns"
write_node "800000" "/proc/sys/kernel/sched_wakeup_granularity_ns"
write_node "200000" "/proc/sys/kernel/sched_migration_cost_ns"
write_node "32" "/proc/sys/kernel/sched_nr_migrate"
write_node "0" "/proc/sys/kernel/sched_schedstats"
write_node "0" "/proc/sys/kernel/sched_child_runs_first"
write_node "1" "/proc/sys/kernel/sched_cstate_aware"

# Storage Read Ahead
for queue in /sys/block/sd[a-z]/queue /sys/block/mmcblk[0-9]/queue; do
    [ -d "$queue" ] || continue
    write_node "1" "$queue/rq_affinity"
done
for q in /sys/block/sd[a-z]/queue/scheduler /sys/block/mmcblk[0-9]/queue/scheduler; do
    [ -e "$q" ] && echo deadline > "$q" 2>/dev/null
done
for ra in /sys/block/sd[a-z]/queue/read_ahead_kb /sys/block/mmcblk[0-9]/queue/read_ahead_kb; do
    write_node "512" "$ra"
done

# SurfaceFlinger Low-Latency Frame Latching for butter-smooth scrolling
setprop debug.sf.latch_unsignaled 1 2>/dev/null
setprop vendor.perf.gestureFlingBoost.enable 1 2>/dev/null
setprop vendor.perf.gestureflingboost.enable true 2>/dev/null

if which resetprop >/dev/null 2>&1; then
    for p in debug.sf.enable_gl_backpressure \
             debug.sf.disable_backpressure \
             debug.renderengine.backend \
             debug.hwui.renderer \
             debug.hwui.use_buffer_age \
             debug.hwui.fps_divisor \
             debug.sf.early_phase_offset_ns \
             debug.sf.early_app_phase_offset_ns \
             debug.sf.early_gl_phase_offset_ns \
             debug.sf.high_fps_early_phase_offset_ns \
             debug.sf.high_fps_early_gl_phase_offset_ns \
             debug.sf.high_fps_late_app_phase_offset_ns \
             debug.composition.type \
             persist.sys.composition.type \
             ro.hwui.render_dirty_regions; do
        resetprop -p --delete "$p" 2>/dev/null
    done
else
    setprop debug.sf.enable_gl_backpressure "" 2>/dev/null
    setprop debug.sf.disable_backpressure "" 2>/dev/null
fi

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
