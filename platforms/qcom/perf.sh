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
    write_node "0" "$KGSL/min_pwrlevel"
    write_node "0" "$KGSL/default_pwrlevel"
    write_node "1" "$KGSL/force_bus_on"
    write_node "1" "$KGSL/force_clk_on"
    write_node "1" "$KGSL/force_rail_on"
    write_node "1" "$KGSL/force_no_nap"
    write_node "performance" "$KGSL/pwrscale/policy"
    write_node "performance" "$KGSL/devfreq/governor"
    write_node "1" "$KGSL/devfreq/adreno_boost"
    write_node "120" "$KGSL/idle_timer"
    write_node "0" "$KGSL/bus_split"
fi

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

    # Schedutil rate limits & hispeed tuning
    for s_dir in "$policy/schedutil" "$policy/$curr_gov"; do
        if [ -d "$s_dir" ]; then
            write_node "0" "$s_dir/up_rate_limit_us"
            write_node "5000" "$s_dir/down_rate_limit_us"
            write_node "$max_freq" "$s_dir/hispeed_freq"
            write_node "85" "$s_dir/hispeed_load"
            write_node "1" "$s_dir/pl"
        fi
    done
done

# Core Control Jitter Prevention
for ctl in /sys/devices/system/cpu/cpu*/core_ctl; do
    [ -d "$ctl" ] || continue
    write_node "500" "$ctl/offline_delay_ms"
    write_node "1 1 1 1" "$ctl/not_preferred"
done

# ── 4. Workqueue, CPUSet, & SchedTune Boost ──────────────────────────
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

    # Apply Unity FPS uncap trick
    for cpu in 0 1 2 3 4 5 6 7; do
        path="/sys/devices/system/cpu/cpu${cpu}"
        [ -e "$path/cpufreq/cpuinfo_max_freq" ] && chmod 000 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
        [ -e "$path/cpu_capacity" ] && chmod 000 "$path/cpu_capacity" 2>/dev/null
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

