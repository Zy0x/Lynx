#!/system/bin/sh
# Lynx Universal - Qualcomm Snapdragon Performance / Extreme Profile
# Tiered Dynamic Scaling & Overclock Stability Engine
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)

MODE="${1:-perf}"
MODDIR="/data/adb/modules/Lynx"

write_node() {
    [ -e "$2" ] || return 0
    echo "$1" > "$2" 2>/dev/null && return 0
    chmod 644 "$2" 2>/dev/null
    echo "$1" > "$2" 2>/dev/null
}

# ── 1. Devfreq Memory Bus Boost ──────────────────────────────────────
for dev in /sys/class/devfreq/*; do
    [ -d "$dev" ] || continue
    gov_node="$dev/governor"
    if [ -f "$gov_node" ]; then
        case "$dev" in
            *ufshc*|*cpubw*|*gpubw*|*llccbw*|*l3-cpu*|*bus_ddr*)
                write_node "performance" "$gov_node"
                ;;
        esac
    fi
    freq_table="$dev/available_frequencies"
    if [ -s "$freq_table" ]; then
        highest_freq=$(tr -s ' ' '\n' < "$freq_table" 2>/dev/null | sort -n | tail -n 1)
        [ -n "$highest_freq" ] && write_node "$highest_freq" "$dev/min_freq"
    fi
done

# ── 2. Adreno KGSL GPU Engine Optimization ───────────────────────────
KGSL="/sys/class/kgsl/kgsl-3d0"
if [ -d "$KGSL" ]; then
    write_node "0" "$KGSL/throttling"
    write_node "0" "$KGSL/thermal_pwrlevel"
    write_node "0" "$KGSL/default_pwrlevel"
    write_node "1" "$KGSL/force_bus_on"
    write_node "1" "$KGSL/force_clk_on"
    write_node "1" "$KGSL/force_rail_on"
    write_node "1" "$KGSL/force_no_nap"
    write_node "performance" "$KGSL/pwrscale/policy"
    write_node "performance" "$KGSL/devfreq/governor"
    if [ "$MODE" = "extreme" ]; then
        write_node "0" "$KGSL/max_pwrlevel"
        write_node "0" "$KGSL/min_pwrlevel"
        write_node "3" "$KGSL/devfreq/adreno_boost"
        write_node "3" "$KGSL/devfreq/adrenoboost"
        for b in /sys/class/devfreq/*kgsl*/adrenoboost /sys/class/devfreq/*kgsl*/adreno_boost; do
            [ -f "$b" ] && write_node "3" "$b"
        done
        write_node "50" "$KGSL/pwrscale/trustzone/target_load"
        write_node "100000" "$KGSL/idle_timer"
    else
        write_node "1" "$KGSL/min_pwrlevel"
        write_node "2" "$KGSL/devfreq/adreno_boost"
        write_node "2" "$KGSL/devfreq/adrenoboost"
        for b in /sys/class/devfreq/*kgsl*/adrenoboost /sys/class/devfreq/*kgsl*/adreno_boost; do
            [ -f "$b" ] && write_node "2" "$b"
        done
        write_node "60" "$KGSL/pwrscale/trustzone/target_load"
        write_node "80" "$KGSL/idle_timer"
    fi
    write_node "0" "$KGSL/bus_split"
fi

# Universal Low-Latency SurfaceFlinger Pipeline
setprop debug.sf.latch_unsignaled 1
setprop debug.sf.disable_backpressure 1
setprop debug.sf.enable_gl_backpressure 0

# ── 3. CPU Cluster Scaling & Governor Tuning ─────────────────────────
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    [ -z "$max_freq" ] && continue

    write_node "$max_freq" "$policy/scaling_max_freq"
    if [ "$MODE" = "extreme" ]; then
        # 100% hardlock for Extreme mode
        write_node "$max_freq" "$policy/scaling_min_freq"
    else
        # Dynamic 85% floor for Performance mode (prevents micro-stutters and VRM droop)
        target_floor=$(( max_freq * 85 / 100 ))
        write_node "$target_floor" "$policy/scaling_min_freq"
    fi

    # Governor Preservation
    avail_govs=$(cat "$policy/scaling_available_governors" 2>/dev/null)
    curr_gov=$(cat "$policy/scaling_governor" 2>/dev/null)
    case "$curr_gov" in
        *schedutil*|*pixel*|*electro*|*blu*) ;;
        *)
            if echo "$avail_govs" | grep -q "schedutil"; then
                write_node "schedutil" "$policy/scaling_governor"
            elif echo "$avail_govs" | grep -q "performance"; then
                write_node "performance" "$policy/scaling_governor"
            fi
            ;;
    esac

    pol_id=$(basename "$policy" | tr -dc '0-9')
    # Schedutil rate limits & hispeed tuning (asymmetric per-cluster)
    for s_dir in "$policy/schedutil" "$policy/$curr_gov"; do
        if [ -d "$s_dir" ]; then
            if [ "$pol_id" = "0" ]; then
                # Little Cluster (Silver): Cepat naik saat butuh, tahan 10ms cegah UI frame drop
                write_node "0" "$s_dir/up_rate_limit_us"
                write_node "10000" "$s_dir/down_rate_limit_us"
                write_node "85" "$s_dir/hispeed_load"
            else
                # Big/Prime Cluster (Gold/Kryo): Lompatan instan 0µs, tahan clock 5ms (ekstrem: 2ms)
                write_node "0" "$s_dir/up_rate_limit_us"
                if [ "$MODE" = "extreme" ]; then
                    write_node "2000" "$s_dir/down_rate_limit_us"
                    write_node "75" "$s_dir/hispeed_load"
                else
                    write_node "5000" "$s_dir/down_rate_limit_us"
                    write_node "80" "$s_dir/hispeed_load"
                fi
            fi
            write_node "$max_freq" "$s_dir/hispeed_freq"
            write_node "1" "$s_dir/pl"
        fi
    done
done

# Core Control Jitter Prevention & Qualcomm Core Retention
for ctl in /sys/devices/system/cpu/cpu*/core_ctl; do
    [ -d "$ctl" ] || continue
    write_node "500" "$ctl/offline_delay_ms"
    write_node "1 1 1 1" "$ctl/not_preferred"
    max_c=$(cat "$ctl/max_cpus" 2>/dev/null)
    [ -n "$max_c" ] && write_node "$max_c" "$ctl/min_cpus"
    write_node "0" "$ctl/busy_up_thres"
    write_node "100" "$ctl/busy_down_thres"
done

# Qualcomm CPU Boost / Input Boost
write_node "1" "/sys/module/cpu_boost/parameters/sched_boost_on_input"
write_node "500" "/sys/module/cpu_boost/parameters/input_boost_ms"

# ── 4. Workqueue, CPUSet, VM, I/O & Network Boost ────────────────────
write_node "N" "/sys/module/workqueue/parameters/power_efficient"
write_node "1" "/proc/sys/kernel/sched_autogroup_enabled"
write_node "0" "/proc/sys/kernel/sched_tunable_scaling"

write_node "0-7" "/dev/cpuset/foreground/cpus"
write_node "0-2" "/dev/cpuset/background/cpus"
write_node "2-7" "/dev/cpuset/system-background/cpus"
write_node "0-7" "/dev/cpuset/top-app/cpus"

write_node "1" "/dev/stune/schedtune.sched_boost_enabled"
write_node "5" "/dev/stune/schedtune.boost"
write_node "0" "/dev/stune/schedtune.prefer_idle"
write_node "5" "/dev/stune/foreground/schedtune.boost"
write_node "5" "/dev/stune/top-app/schedtune.boost"

# VM & Memory Response
sync
write_node "3" "/proc/sys/vm/drop_caches"
write_node "1" "/proc/sys/vm/compact_memory"
write_node "40" "/proc/sys/vm/vfs_cache_pressure"
write_node "10" "/proc/sys/vm/stat_interval"
if [ "$MODE" = "extreme" ]; then
    write_node "200" "/proc/sys/vm/watermark_scale_factor"
    write_node "32" "/proc/sys/kernel/sched_nr_migrate"
else
    write_node "150" "/proc/sys/vm/watermark_scale_factor"
    write_node "16" "/proc/sys/kernel/sched_nr_migrate"
fi
write_node "0" "/proc/sys/vm/watermark_boost_factor"
write_node "0" "/proc/sys/vm/oom_dump_tasks"
write_node "980000" "/proc/sys/kernel/sched_rt_runtime_us"
write_node "1000000" "/proc/sys/kernel/sched_rt_period_us"

# Block I/O Cache Locality
for queue in /sys/block/sd[a-z]/queue /sys/block/mmcblk[0-9]/queue; do
    [ -d "$queue" ] || continue
    write_node "0" "$queue/add_random"
    write_node "0" "$queue/iostats"
    write_node "0" "$queue/nomerges"
    write_node "0" "$queue/rotational"
    write_node "2" "$queue/rq_affinity"
    write_node "512" "$queue/nr_requests"
done

# UFS Storage Controller Anti-Gating
for ufs in /sys/devices/platform/soc/*ufshc*; do
    [ -d "$ufs" ] || continue
    write_node "1000" "$ufs/clkgate_delay_ms"
    write_node "0" "$ufs/clkgate_delay_ms_perf"
    write_node "1000" "$ufs/clkgate_delay_ms_pwr_save"
    write_node "0" "$ufs/auto_hibern8_enable"
done

# Network Gaming Stack
sysctl -w net.ipv4.tcp_slow_start_after_idle=0 >/dev/null 2>&1
sysctl -w net.ipv4.tcp_low_latency=1 >/dev/null 2>&1
sysctl -w net.ipv4.tcp_autocorking=0 >/dev/null 2>&1
sysctl -w net.ipv4.tcp_notsent_lowat=16384 >/dev/null 2>&1
sysctl -w net.core.netdev_max_backlog=5000 >/dev/null 2>&1

# ── 5. Mode Specific Integrations (Extreme vs Perf) ───────────────────
if [ "$MODE" = "extreme" ]; then
    # Full Thermal Daemon Stop & Thermal Zones Override
    if [ -f "$MODDIR/platforms/qcom/thermal.sh" ]; then
        sh "$MODDIR/platforms/qcom/thermal.sh" disable >/dev/null 2>&1
    fi

    # Universal Scheduler EAS Bypass & Core Activation
    write_node "0" "/proc/sys/kernel/sched_energy_aware"
    write_node "1" "/proc/sys/kernel/sched_sync_hint_enable"
    write_node "1024" "/proc/sys/kernel/sched_uclamp_util_min"
    write_node "1024" "/proc/sys/kernel/sched_util_clamp_min"
    write_node "1024" "/dev/cpuset/top-app/cpu.uclamp.min"
    write_node "60" "/proc/sys/kernel/sched_upmigrate"
    write_node "40" "/proc/sys/kernel/sched_downmigrate"
    write_node "3000000" "/proc/sys/kernel/sched_latency_ns"
    write_node "40" "/proc/sys/vm/vfs_cache_pressure"
    write_node "50" "/proc/sys/vm/swappiness"

    for c in 0 1 2 3 4 5 6 7; do
        write_node "1" "/sys/devices/system/cpu/cpu$c/online"
        write_node "4" "/sys/devices/system/cpu/cpu$c/core_ctl/min_cpus"
    done

    # Qualcomm Adreno & Display IRQ SMP affinity to big cores
    for irq in $(grep -iE "kgsl|adreno|msm_drm|mdss" /proc/interrupts 2>/dev/null | awk '{print $1}' | tr -d ':'); do
        write_node "f0" "/proc/irq/$irq/smp_affinity" 2>/dev/null || write_node "3f" "/proc/irq/$irq/smp_affinity" 2>/dev/null
    done

    # Ensure CPU capabilities and topology are always readable by Game Engines and EAS
    for cpu in 0 1 2 3 4 5 6 7; do
        path="/sys/devices/system/cpu/cpu${cpu}"
        [ -e "$path/cpufreq/cpuinfo_max_freq" ] && chmod 444 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
        [ -e "$path/cpu_capacity" ] && chmod 444 "$path/cpu_capacity" 2>/dev/null
    done
else
    # Re-enable thermal protection in normal Performance mode
    if [ -f "$MODDIR/platforms/qcom/thermal.sh" ]; then
        sh "$MODDIR/platforms/qcom/thermal.sh" enable >/dev/null 2>&1
    fi
fi

# ── 6. Targeted Hardware Sub-Profiles (Anti-Spoofing Protected) ───────
if [ -f "$MODDIR/core/lib/hw_probe.sh" ]; then
    . "$MODDIR/core/lib/hw_probe.sh"
    load_hardware_subprofiles "qcom"
fi

