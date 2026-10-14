#!/system/bin/sh
PROFILE="${1:-balance}"
CALLER="${2:-user}"
TARGET_APP="${3:-}"
mkdir -p /data/adb/lynx 2>/dev/null
echo "$PROFILE" > /data/adb/lynx/active_profile 2>/dev/null
setprop lynx.mode "$PROFILE" 2>/dev/null
if [ "$CALLER" != "watcher" ] && [ "$CALLER" != "automation" ] && [ "$PROFILE" != "auto" ]; then
    echo "$PROFILE" > /data/adb/lynx/baseline_profile 2>/dev/null
fi

# Zero-Fork Fast Path: only chmod if direct write failed
write_node() {
    [ -e "$2" ] || return 0
    echo "$1" > "$2" 2>/dev/null && return 0
    chmod 666 "$2" 2>/dev/null
    echo "$1" > "$2" 2>/dev/null
}

pick_dynamic_gov() {
    avail=$(cat "$1/scaling_available_governors" 2>/dev/null)
    for g in walt sugov_ext schedutil energy_step interactive ondemand; do
        case " $avail " in
            *" $g "*) echo "$g"; return 0 ;;
        esac
    done
    echo "schedutil"
}

OEM_TARGET_PROCS="mi_thermald thermal-engine thermal-engine-v2 ituxd com.samsung.android.game.gos"

is_bluetooth_audio() {
    dumpsys audio 2>/dev/null | grep -iE "a2dp.*connected|device.*bluetooth_a2dp" | grep -qv "state=0"
}

case "$PROFILE" in
    extreme|performance)
        for c in /sys/devices/system/cpu/cpu[0-9]*; do
            [ -d "$c" ] || continue
            [ -e "$c/online" ] && echo 1 > "$c/online" 2>/dev/null
        done

        for p in /sys/devices/system/cpu/cpufreq/policy*; do
            [ -d "$p" ] || continue
            chmod 644 "$p/scaling_min_freq" "$p/scaling_max_freq" 2>/dev/null
            dyn_gov=$(pick_dynamic_gov "$p")

            max_freq=$(cat "$p/cpuinfo_max_freq" 2>/dev/null)
            if [ -z "$max_freq" ]; then
                max_freq=$(tr -s ' ' '
' < "$p/scaling_available_frequencies" 2>/dev/null | sort -n | tail -n 1)
            fi
            min_freq=$(cat "$p/cpuinfo_min_freq" 2>/dev/null)
            if [ -z "$min_freq" ]; then
                min_freq=$(tr -s ' ' '
' < "$p/scaling_available_frequencies" 2>/dev/null | sort -n | head -n 1)
            fi

            if [ "$PROFILE" = "extreme" ]; then
                write_node "$max_freq" "$p/scaling_max_freq"
                write_node "$max_freq" "$p/scaling_min_freq"
                avail_govs=$(cat "$p/scaling_available_governors" 2>/dev/null)
                if echo "$avail_govs" | grep -q "performance"; then
                    write_node "performance" "$p/scaling_governor"
                else
                    write_node "$dyn_gov" "$p/scaling_governor"
                    for gdir in "$p/schedutil" "$p/walt" "$p/sugov_ext"; do
                        [ -d "$gdir" ] || continue
                        write_node "0" "$gdir/up_rate_limit_us"
                        write_node "0" "$gdir/down_rate_limit_us"
                        write_node "1" "$gdir/pl"
                        write_node "$max_freq" "$gdir/hispeed_freq"
                    done
                fi
            else
                write_node "$max_freq" "$p/scaling_max_freq"
                write_node "$dyn_gov" "$p/scaling_governor"
                for gdir in "$p/schedutil" "$p/walt" "$p/sugov_ext"; do
                    [ -d "$gdir" ] || continue
                    write_node "0" "$gdir/up_rate_limit_us"
                    write_node "5000" "$gdir/down_rate_limit_us"
                    write_node "85" "$gdir/hispeed_load"
                    write_node "1" "$gdir/iowait_boost_enable"
                    write_node "1" "$gdir/pl"
                    write_node "$max_freq" "$gdir/hispeed_freq"
                done
                if [ -n "$max_freq" ] && [ -n "$min_freq" ]; then
                    floor=$(( max_freq * 85 / 100 ))
                    snapped_floor=""
                    for f in $(tr -s ' ' '
' < "$p/scaling_available_frequencies" 2>/dev/null | sort -n); do
                        if [ "$f" -le "$floor" ]; then
                            snapped_floor="$f"
                        fi
                    done
                    [ -z "$snapped_floor" ] && snapped_floor="$floor"
                    [ "$snapped_floor" -lt "$min_freq" ] && snapped_floor="$min_freq"
                    write_node "$snapped_floor" "$p/scaling_min_freq"
                fi
            fi
        done

        for ctl in /sys/devices/system/cpu/cpu*/core_ctl; do
            [ -d "$ctl" ] || continue
            write_node "1000" "$ctl/offline_delay_ms"
            write_node "1 1 1 1" "$ctl/not_preferred"
            [ "$PROFILE" = "extreme" ] && write_node "4" "$ctl/min_cpus"
        done

        write_node "N" "/sys/module/workqueue/parameters/power_efficient"
        write_node "1" "/sys/devices/system/cpu/perf/enable"
        # Respect user preferred architecture or preserve Hybrid (2) / EAS (1)
        if [ -f /data/adb/lynx/preferred_architecture ]; then
            pref_arch=$(cat /data/adb/lynx/preferred_architecture 2>/dev/null | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')
            if [ "$pref_arch" = "hybrid" ]; then
                write_node "2" "/sys/devices/system/cpu/eas/enable"
            elif [ "$pref_arch" = "eas" ]; then
                write_node "1" "/sys/devices/system/cpu/eas/enable"
            elif [ "$pref_arch" = "hmp" ]; then
                write_node "0" "/sys/devices/system/cpu/eas/enable"
            fi
        elif [ -f /sys/devices/system/cpu/eas/enable ]; then
            cur_eas=$(cat /sys/devices/system/cpu/eas/enable 2>/dev/null | tr '[:upper:]' '[:lower:]')
            case "$cur_eas" in
                *hybrid*|2) write_node "2" "/sys/devices/system/cpu/eas/enable" ;;
            esac
        fi
        write_node "1" "/proc/sys/kernel/sched_autogroup_enabled"
        write_node "0" "/proc/sys/kernel/sched_tunable_scaling"
        write_node "1" "/proc/sys/kernel/sched_sync_hint_enable"

        write_node "3" "/proc/cpufreq/cpufreq_power_mode"
        write_node "1" "/proc/cpufreq/cpufreq_cci_mode"
        write_node "1" "/proc/cpufreq/cpufreq_imax_enable"
        write_node "0" "/proc/cpufreq/cpufreq_debug"
        write_node "1" "/proc/cpufreq/cpufreq_sched_disable"
        write_node "0" "/proc/cpuidle/control/armpll_mode"
        write_node "0" "/proc/cpuidle/control/buck_mode"
        write_node "1" "/proc/perfmgr/syslimiter/syslimiter_force_disable"
        write_node "0" "/sys/kernel/eara_thermal/enable"
        write_node "0" "/sys/kernel/eara_thermal/fake_throttle"
        write_node "100 99" "/proc/driver/thermal/clatm_gpu_threshold"

        write_node "100" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost"
        write_node "100" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost"
        write_node "100" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_uclamp_min"
        write_node "100" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_uclamp_min"
        write_node "100" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_uclamp_min"
        write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_prefer_idle"
        write_node "1" "/proc/perfmgr/boost_ctrl/eas_ctrl/sched_big_task_rotation"

        write_node "0" "/proc/ppm/enabled"
        write_node "0 0" "/proc/ppm/policy_status"
        write_node "1 1" "/proc/ppm/policy_status"
        write_node "2 0" "/proc/ppm/policy_status"
        write_node "3 0" "/proc/ppm/policy_status"
        write_node "4 0" "/proc/ppm/policy_status"
        write_node "5 0" "/proc/ppm/policy_status"
        write_node "6 1" "/proc/ppm/policy_status"
        write_node "7 1" "/proc/ppm/policy_status"
        write_node "8 0" "/proc/ppm/policy_status"
        write_node "9 1" "/proc/ppm/policy_status"
        write_node "0" "/proc/ppm/cpi/cpi_enabled"

        for c in 0 1 2; do
            table="/proc/ppm/dump_cluster_${c}_dvfs_table"
            [ -f "$table" ] || continue
            c_max=$(awk '{print $1}' "$table" 2>/dev/null | head -n 1)
            c_min=$(awk '{print $NF}' "$table" 2>/dev/null | tail -n 1)
            [ -z "$c_max" ] && continue
            if [ "$PROFILE" = "extreme" ]; then
                write_node "$c $c_max" "/proc/ppm/policy/hard_userlimit_max_cpu_freq"
                write_node "$c $c_max" "/proc/ppm/policy/hard_userlimit_min_cpu_freq"
            else
                total_opp=$(wc -w < "$table" 2>/dev/null)
                perf_floor_idx=$(( total_opp * 15 / 100 ))
                [ "$perf_floor_idx" -lt 1 ] 2>/dev/null && perf_floor_idx=2
                perf_floor=$(awk -v idx="$perf_floor_idx" '{print $idx}' "$table" 2>/dev/null)
                [ -z "$perf_floor" ] && perf_floor=$c_max
                write_node "$c $c_max" "/proc/ppm/policy/hard_userlimit_max_cpu_freq"
                write_node "$c $perf_floor" "/proc/ppm/policy/hard_userlimit_min_cpu_freq"
            fi
        done

        for dev in /sys/class/devfreq/*; do
            [ -d "$dev" ] || continue
            case "$dev" in
                *ufshc*|*cpubw*|*gpubw*|*llccbw*|*l3-cpu*|*bus_ddr*)
                    write_node "performance" "$dev/governor"
                    if [ -f "$dev/max_freq" ]; then
                        write_node "$(cat "$dev/max_freq" 2>/dev/null)" "$dev/min_freq"
                    else
                        freq_table="$dev/available_frequencies"
                        if [ -s "$freq_table" ]; then
                            h_freq=$(tr -s ' ' '
' < "$freq_table" 2>/dev/null | sort -n | tail -n 1)
                            [ -n "$h_freq" ] && write_node "$h_freq" "$dev/min_freq"
                        fi
                    fi
                    ;;
            esac
        done

        if [ "$PROFILE" = "extreme" ]; then
            write_node "0" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
            write_node "0" "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
            write_node "2" "/sys/kernel/ged/hal/gpu_boost_level"
            write_node "50" "/sys/kernel/ged/hal/dvfs_margin_value"
            write_node "50" "/sys/module/ged/parameters/gx_fb_dvfs_margin"
            write_node "performance" "/sys/class/kgsl/kgsl-3d0/pwrscale/policy"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/force_no_nap"
            write_node "0" "/sys/class/kgsl/kgsl-3d0/min_pwrlevel"
            write_node "0" "/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel"
            write_node "0" "/sys/class/kgsl/kgsl-3d0/default_pwrlevel"
            write_node "3" "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/force_bus_on"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/force_clk_on"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/force_rail_on"
            write_node "0" "/sys/class/kgsl/kgsl-3d0/throttling"
            write_node "120" "/sys/class/kgsl/kgsl-3d0/idle_timer"
            write_node "0" "/sys/class/kgsl/kgsl-3d0/bus_split"

            if [ -f "/proc/gpufreq/gpufreq_opp_dump" ]; then
                opp_line=$(head -n 1 /proc/gpufreq/gpufreq_opp_dump 2>/dev/null)
                peak_f=$(echo "$opp_line" | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
                peak_vgpu=$(echo "$opp_line" | grep -Eo 'vgpu = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
                if [ -n "$peak_f" ]; then
                    write_node "$peak_f" "/sys/module/ged/parameters/gpu_cust_upbound_freq"
                    write_node "$peak_f" "/sys/module/ged/parameters/gpu_cust_boost_freq"
                    write_node "$peak_f" "/sys/module/ged/parameters/gpu_bottom_freq"
                    write_node "$peak_f" "/proc/gpufreq/gpufreq_opp_freq"
                    if [ -n "$peak_vgpu" ]; then
                        write_node "$peak_f $peak_vgpu" "/proc/gpufreq/gpufreq_fixed_freq_volt"
                    fi
                fi
            fi
            for i in 0 1 2 3 4 5 6 7 8; do
                write_node "$i 0 0" "/proc/gpufreq/gpufreq_limit_table"
            done
            write_node "1" "/proc/mali/dvfs_enable"
            write_node "1" "/proc/mali/always_on"
            write_node "150 149" "/proc/driver/thermal/clatm_gpu_threshold"
        else
            if grep -q "is enabled" /proc/gpufreq/gpufreq_fixed_freq_volt 2>/dev/null; then
                write_node "0 0" "/proc/gpufreq/gpufreq_fixed_freq_volt"
            fi
            write_node "0" "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
            write_node "1" "/sys/kernel/ged/hal/gpu_boost_level"
            write_node "30" "/sys/kernel/ged/hal/dvfs_margin_value"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/min_pwrlevel"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost"
            write_node "60" "/sys/class/kgsl/kgsl-3d0/idle_timer"
            if [ -f "/proc/gpufreq/gpufreq_opp_dump" ]; then
                opp_line=$(head -n 1 /proc/gpufreq/gpufreq_opp_dump 2>/dev/null)
                peak_f=$(echo "$opp_line" | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
                if [ -n "$peak_f" ]; then
                    perf_target=$(( peak_f * 75 / 100 ))
                    perf_opp_line=$(awk -v t="$perf_target" '{
                        match($0, /freq = [0-9]+/);
                        f = substr($0, RSTART+7, RLENGTH-7) + 0;
                        if (f >= t) last_line = $0;
                    } END { print last_line }' /proc/gpufreq/gpufreq_opp_dump 2>/dev/null)
                    perf_floor=$(echo "$perf_opp_line" | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
                    perf_idx=$(echo "$perf_opp_line" | awk -F'[][]' '{print int($2)}')
                    [ -z "$perf_floor" ] && perf_floor="$perf_target"
                    [ -z "$perf_idx" ] && perf_idx="12"
                    write_node "$perf_idx" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
                    write_node "$peak_f" "/sys/module/ged/parameters/gpu_cust_upbound_freq"
                    write_node "$perf_floor" "/sys/module/ged/parameters/gpu_cust_boost_freq"
                    write_node "$perf_floor" "/sys/module/ged/parameters/gpu_bottom_freq"
                fi
            else
                write_node "12" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
            fi
            write_node "0" "/proc/gpufreq/gpufreq_opp_freq"
            for i in 0 1 2 3 4 5 6 7 8; do
                write_node "$i 0 0" "/proc/gpufreq/gpufreq_limit_table"
            done
            write_node "1" "/proc/mali/dvfs_enable"
            write_node "1" "/proc/mali/always_on"
        fi

        write_node "1" "/sys/module/ged/parameters/boost_gpu_enable"
        write_node "1" "/sys/module/ged/parameters/enable_gpu_boost"
        write_node "1" "/sys/module/ged/parameters/enable_cpu_boost"
        write_node "1" "/sys/module/ged/parameters/gx_game_mode"
        write_node "1" "/sys/module/ged/parameters/gx_boost_on"
        write_node "1" "/sys/module/ged/parameters/boost_amp"
        write_node "1" "/sys/module/ged/parameters/boost_extra"
        write_node "1" "/sys/module/ged/parameters/cpu_boost_policy"
        write_node "0" "/sys/module/ged/parameters/deboost_reduce"
        write_node "100" "/sys/module/ged/parameters/g_fb_dvfs_threshold"
        write_node "1" "/sys/module/ged/parameters/ged_boost_enable"
        write_node "1" "/sys/module/ged/parameters/ged_force_mdp_enable"
        write_node "1" "/sys/module/ged/parameters/ged_monitor_3D_fence_disable"
        if [ "$PROFILE" = "extreme" ]; then
            write_node "0" "/sys/module/ged/parameters/gpu_idle"
            write_node "0" "/sys/module/ged/parameters/ged_smart_boost"
            write_node "100" "/sys/module/ged/parameters/boost_upper_bound"
            # Disable Mali DVFS — freq dikunci via hardware PLL
            write_node "0" "/proc/mali/dvfs_enable"
            # Improve CPU response for the render thread (240 Hz budget).
            write_node "4166666" "/sys/module/ged/parameters/target_t_cpu_remained"
            # Dynamically route Mali/GPU IRQs to the big cores.
            big_mask=0
            max_cap=0
            for c in /sys/devices/system/cpu/cpu[0-9]*; do
                [ -d "$c" ] || continue
                cap=$(cat "$c/cpu_capacity" 2>/dev/null)
                [ -z "$cap" ] && cap=$(cat "$c/cpufreq/cpuinfo_max_freq" 2>/dev/null)
                [ -z "$cap" ] && cap=0
                [ "$cap" -gt "$max_cap" ] && max_cap="$cap"
            done
            for c in /sys/devices/system/cpu/cpu[0-9]*; do
                [ -d "$c" ] || continue
                id=$(basename "$c" | tr -d 'cpu')
                cap=$(cat "$c/cpu_capacity" 2>/dev/null)
                [ -z "$cap" ] && cap=$(cat "$c/cpufreq/cpuinfo_max_freq" 2>/dev/null)
                [ -z "$cap" ] && cap=0
                if [ "$cap" -ge "$max_cap" ] && [ "$max_cap" -gt 0 ]; then
                    big_mask=$(( big_mask | (1 << id) ))
                fi
            done
            [ "$big_mask" -eq 0 ] && big_mask=192
            gpu_irq_mask=$(printf "%x" "$big_mask")
            for irq_dir in /proc/irq/*/actions; do
                [ -f "$irq_dir" ] || continue
                irq_name=$(cat "$irq_dir" 2>/dev/null)
                case "$irq_name" in
                    *mali*|*gpu*|*g3d*|*kgsl*)
                        irq_num=$(echo "$irq_dir" | grep -Eo '/[0-9]+/' | tr -d '/')
                        [ -f "/proc/irq/$irq_num/smp_affinity" ] && \
                            write_node "$gpu_irq_mask" "/proc/irq/$irq_num/smp_affinity"
                        ;;
                esac
            done
        else
            write_node "1" "/sys/module/ged/parameters/gpu_idle"
            write_node "1" "/sys/module/ged/parameters/ged_smart_boost"
            write_node "80" "/sys/module/ged/parameters/boost_upper_bound"
            write_node "1" "/proc/mali/dvfs_enable"
            write_node "8333333" "/sys/module/ged/parameters/target_t_cpu_remained"
        fi
        write_node "1" "/sys/module/ged/parameters/gx_force_cpu_boost"
        write_node "0" "/proc/gpufreq/gpufreq_aging_enable"


        # Disable MediaTek FPSGO in extreme mode (no FPS cap); enable it in performance mode.
        if [ "$PROFILE" = "extreme" ]; then
            write_node "0" "/sys/kernel/fpsgo/common/fpsgo_enable"
            write_node "0" "/sys/kernel/fpsgo/fbt/thrm_limit_cpu"
            write_node "200" "/sys/kernel/fpsgo/fbt/thrm_temp_th"
        else
            write_node "1" "/sys/kernel/fpsgo/common/fpsgo_enable"
            write_node "1" "/sys/kernel/fpsgo/common/force_onoff"
            write_node "1" "/sys/kernel/fpsgo/common/gpu_block_boost"
            write_node "1" "/sys/kernel/fpsgo/fbt/boost_ta"
            write_node "1" "/sys/kernel/fpsgo/fbt/ultra_rescue"
            write_node "0" "/sys/kernel/fpsgo/fbt/switch_idleprefer"
            write_node "0" "/sys/kernel/fpsgo/fbt/enable_switch_down_throttle"
            write_node "0" "/sys/kernel/fpsgo/fbt/light_loading_policy"
            write_node "0" "/sys/kernel/fpsgo/fbt/light_loading_policy_90"
            write_node "0" "/sys/kernel/fpsgo/fbt/llf_task_policy"
            write_node "0" "/sys/kernel/fpsgo/fbt/llf_task_policy_90"
            write_node "0" "/sys/kernel/fpsgo/fbt/thrm_limit_cpu"
            write_node "0" "/sys/kernel/fpsgo/fstb/fstb_soft_level"
        fi

        # Mali power policy — universal glob
        for pp in /sys/devices/platform/*mali*/power_policy \
                  /sys/devices/platform/soc/*mali*/power_policy \
                  /sys/class/misc/mali*/device/power_policy; do
            [ -e "$pp" ] && write_node "always_on" "$pp"
        done
        write_node "1" "/proc/mali/always_on"

        write_node "0" "/proc/mali/debug_log"

        uclamp_val=75
        stune_boost=15
        if [ "$PROFILE" = "extreme" ]; then
            uclamp_val=100
            stune_boost=25
        fi
        for u_node in "/dev/cpuset/top-app/cpu.uclamp.min" "/proc/sys/kernel/sched_util_clamp_min"; do
            if [ -e "$u_node" ]; then
                max_sc=100
                [ -e "/dev/cpuset/top-app/cpu.uclamp.max" ] && max_sc=$(cat "/dev/cpuset/top-app/cpu.uclamp.max" 2>/dev/null)
                if [ "$max_sc" -gt 100 ] 2>/dev/null; then
                    write_node "$(( (uclamp_val * 1024) / 100 ))" "$u_node"
                else
                    write_node "$uclamp_val" "$u_node"
                fi
            fi
        done
        write_node "1" "/dev/cpuset/top-app/cpu.uclamp.latency_sensitive"
        write_node "1" "/dev/cpuset/foreground/boost/cpu.uclamp.latency_sensitive"

        write_node "0-5" "/dev/cpuset/foreground/cpus"
        write_node "0-1" "/dev/cpuset/background/cpus"
        write_node "0-2" "/dev/cpuset/system-background/cpus"
        write_node "0-7" "/dev/cpuset/top-app/cpus"
        write_node "0-1" "/dev/cpuset/restricted/cpus"
        write_node "1" "/dev/stune/schedtune.sched_boost_enabled"
        write_node "5" "/dev/stune/schedtune.boost"
        write_node "0" "/dev/stune/schedtune.prefer_idle"
        write_node "5" "/dev/stune/foreground/schedtune.boost"
        write_node "$stune_boost" "/dev/stune/top-app/schedtune.boost"

        # CFS Low-Latency Scheduler & VM Optimization
        sync
        write_node "3" "/proc/sys/vm/drop_caches"
        write_node "1" "/proc/sys/vm/compact_memory"
        if [ "$PROFILE" = "extreme" ]; then
            write_node "3000000" "/proc/sys/kernel/sched_latency_ns"
            write_node "400000" "/proc/sys/kernel/sched_min_granularity_ns"
            write_node "800000" "/proc/sys/kernel/sched_wakeup_granularity_ns"
            write_node "30000" "/proc/sys/kernel/sched_migration_cost_ns"
            write_node "60" "/proc/sys/kernel/sched_upmigrate"
            write_node "40" "/proc/sys/kernel/sched_downmigrate"
            write_node "0" "/proc/sys/kernel/sched_schedstats"
            write_node "1" "/proc/sys/kernel/sched_child_runs_first"
            write_node "0" "/proc/sys/kernel/sched_cstate_aware"
            write_node "10" "/proc/sys/vm/stat_interval"
            write_node "40" "/proc/sys/vm/vfs_cache_pressure"
            write_node "50" "/proc/sys/vm/swappiness"
            write_node "25" "/proc/sys/vm/dirty_ratio"
            write_node "200" "/proc/sys/vm/watermark_scale_factor"
        else
            write_node "6000000" "/proc/sys/kernel/sched_latency_ns"
            write_node "750000" "/proc/sys/kernel/sched_min_granularity_ns"
            write_node "1500000" "/proc/sys/kernel/sched_wakeup_granularity_ns"
            write_node "100000" "/proc/sys/kernel/sched_migration_cost_ns"
            write_node "85" "/proc/sys/kernel/sched_upmigrate"
            write_node "65" "/proc/sys/kernel/sched_downmigrate"
            write_node "0" "/proc/sys/kernel/sched_schedstats"
            write_node "1" "/proc/sys/kernel/sched_child_runs_first"
            write_node "0" "/proc/sys/kernel/sched_cstate_aware"
            write_node "1" "/proc/sys/vm/stat_interval"
            write_node "70" "/proc/sys/vm/vfs_cache_pressure"
            write_node "70" "/proc/sys/vm/swappiness"
            write_node "20" "/proc/sys/vm/dirty_ratio"
            write_node "150" "/proc/sys/vm/watermark_scale_factor"
        fi

        write_node "0" "/proc/sys/vm/watermark_boost_factor"
        write_node "980000" "/proc/sys/kernel/sched_rt_runtime_us"
        write_node "1000000" "/proc/sys/kernel/sched_rt_period_us"
        if [ -e "/sys/kernel/mm/lru_gen/enabled" ]; then
            write_node "y" "/sys/kernel/mm/lru_gen/enabled"
            write_node "1000" "/sys/kernel/mm/lru_gen/min_ttl_ms"
        fi
        write_node "10" "/proc/sys/vm/dirty_background_ratio"
        write_node "500" "/proc/sys/vm/dirty_expire_centisecs"
        write_node "200" "/proc/sys/vm/dirty_writeback_centisecs"
        write_node "100" "/proc/sys/vm/extfrag_threshold"
        write_node "0" "/proc/sys/vm/oom_dump_tasks"
        write_node "80" "/proc/sys/vm/overcommit_ratio"
        write_node "1" "/proc/sys/vm/compact_unevictable_allowed"
        write_node "32" "/proc/sys/kernel/sched_nr_migrate"
        write_node "40" "/proc/sys/kernel/perf_cpu_time_max_percent"
        write_node "1" "/proc/sys/kernel/sched_boost"
        write_node "512" "/proc/sys/kernel/random/read_wakeup_threshold"
        write_node "2048" "/proc/sys/kernel/random/write_wakeup_threshold"

        # Storage I/O Optimization (Zero Stutter Streaming for 3D Game Worlds)
        ra_val=1024
        [ "$PROFILE" = "extreme" ] && ra_val=2048
        for queue in /sys/block/*/queue; do
            [ -d "$queue" ] || continue
            write_node "0" "$queue/add_random"
            write_node "0" "$queue/iostats"
            write_node "0" "$queue/nomerges"
            write_node "0" "$queue/rotational"
            write_node "2" "$queue/rq_affinity"
            write_node "512" "$queue/nr_requests"
        done
        for q in /sys/block/*/queue/scheduler; do
            [ -e "$q" ] && echo deadline > "$q" 2>/dev/null
        done
        for ra in /sys/block/sd*/queue/read_ahead_kb /sys/block/mmcblk*/queue/read_ahead_kb; do
            write_node "$ra_val" "$ra"
        done
        for ufs in /sys/devices/platform/soc/*ufshc* /sys/devices/platform/bootdevice /sys/devices/platform/*ufshc*; do
            [ -d "$ufs" ] || continue
            write_node "1000" "$ufs/clkgate_delay_ms"
            write_node "0" "$ufs/clkgate_delay_ms_perf"
            write_node "1000" "$ufs/clkgate_delay_ms_pwr_save"
            write_node "0" "$ufs/auto_hibern8_enable"
        done

        # Network Gaming Stack
        cmd wifi force-low-latency-mode enabled >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_slow_start_after_idle=0 >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_low_latency=1 >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_autocorking=0 >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_notsent_lowat=16384 >/dev/null 2>&1
        sysctl -w net.core.netdev_max_backlog=5000 >/dev/null 2>&1

        setprop debug.sf.latch_unsignaled 1 2>/dev/null
        if [ "$PROFILE" = "extreme" ]; then
            setprop debug.sf.early_phase_offset_ns 500000 2>/dev/null
            setprop debug.sf.early_app_phase_offset_ns 500000 2>/dev/null
            setprop debug.sf.disable_backpressure 1 2>/dev/null
            setprop debug.sf.enable_gl_backpressure 0 2>/dev/null
        elif which resetprop >/dev/null 2>&1; then
            resetprop -p --delete debug.sf.early_phase_offset_ns 2>/dev/null
            resetprop -p --delete debug.sf.early_app_phase_offset_ns 2>/dev/null
            resetprop -p --delete debug.sf.disable_backpressure 2>/dev/null
            resetprop -p --delete debug.sf.enable_gl_backpressure 2>/dev/null
        fi

        if which resetprop >/dev/null 2>&1; then
            for p in debug.renderengine.backend \
                     debug.hwui.renderer \
                     debug.hwui.use_buffer_age \
                     debug.hwui.fps_divisor \
                     debug.sf.early_gl_phase_offset_ns \
                     debug.sf.high_fps_early_phase_offset_ns \
                     debug.sf.high_fps_early_gl_phase_offset_ns \
                     debug.sf.high_fps_late_app_phase_offset_ns \
                     debug.composition.type \
                     persist.sys.composition.type \
                     ro.hwui.render_dirty_regions; do
                resetprop -p --delete "$p" 2>/dev/null
            done
        fi
        setprop vendor.perf.gestureFlingBoost.enable 1 2>/dev/null
        setprop vendor.perf.gestureflingboost.enable true 2>/dev/null

        peak_rr=$(settings get system peak_refresh_rate 2>/dev/null)
        if [ -n "$peak_rr" ] && [ "$peak_rr" != "null" ]; then
            if [ ! -f "/dev/lynx_orig_min_rr" ]; then
                orig_min=$(settings get system min_refresh_rate 2>/dev/null)
                echo "${orig_min:-60.0}" > "/dev/lynx_orig_min_rr"
            fi
            settings put system min_refresh_rate "$peak_rr" 2>/dev/null
        fi

        for tn in /sys/class/touch/touch_dev/touch_game_mode \
                 /sys/devices/virtual/touch/touch_dev/bump_sample_rate \
                 /proc/touchscreen/game_mode \
                 /sys/devices/platform/goodix_ts.*/game_mode \
                 /sys/devices/platform/tp_wake_switch/game_mode \
                 /sys/devices/virtual/input/input*/touch_game_mode; do
            write_node "1" "$tn"
        done

        # Network Low Latency
        sysctl -w net.ipv4.tcp_low_latency=1 >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_autocorking=0 >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_fastopen=3 >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_notsent_lowat=16384 >/dev/null 2>&1
        for ps_node in /sys/module/wlan/parameters/power_save /sys/module/bcmdhd/parameters/op_mode; do
            write_node "0" "$ps_node"
        done

        # Multi-SoC GPU & Display IRQ SMP Affinity
        for irq in $(grep -iE "mali|kgsl|adreno|msm_drm|mdss" /proc/interrupts 2>/dev/null | awk '{print $1}' | tr -d ':'); do
            write_node "3f" "/proc/irq/$irq/smp_affinity"
        done

        GAME_LIBS="com.miHoYo., com.miHoYo.GenshinImpact, com.activision., com.epicgames, com.dts., UnityMain, libunity.so, libil2cpp.so, libmain.so, libcri_vip_unity.so, libopus.so, libxlua.so, libUE4.so, libAsphalt9.so, libnative-lib.so, libRiotGamesApi.so, libResources.so, libagame.so, libapp.so, libflutter.so, libMSDKCore.so, libFIFAMobileNeon.so, libUnreal.so, libEOSSDK.so, libcocos2dcpp.so, libfb.so"
        write_node "$GAME_LIBS" "/proc/sys/kernel/sched_lib_name"
        write_node "255" "/proc/sys/kernel/sched_lib_mask_force"

        sf="/sys/kernel/debug/sched_features"
        [ -f "$sf" ] || sf="/d/sched_features"
        if [ -f "$sf" ]; then
            for feat in "NO_GENTLE_FAIR_SLEEPERS" "START_DEBIT" "NO_NEXT_BUDDY" "LAST_BUDDY" "WAKEUP_PREEMPTION" "NO_HRTICK" "NO_DOUBLE_TICK"; do
                echo "$feat" > "$sf" 2>/dev/null
            done
        fi

        if [ "$PROFILE" = "extreme" ]; then
            write_node "0" "/proc/cpufreq/cpufreq_imax_thermal_protect"
            for tz in /sys/class/thermal/thermal_zone*; do
                [ -d "$tz" ] || continue
                write_node "disabled" "$tz/mode"
                write_node "150000" "$tz/trip_point_0_temp"
            done

            for path in /sys/devices/system/cpu/cpu[0-9]*; do
                [ -d "$path" ] || continue
                [ -e "$path/cpufreq/cpuinfo_max_freq" ] && chmod 444 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
                [ -e "$path/cpu_capacity" ] && chmod 444 "$path/cpu_capacity" 2>/dev/null
                [ -e "$path/topology/physical_package_id" ] && chmod 444 "$path/topology/physical_package_id" 2>/dev/null
            done
        else
            write_node "1" "/proc/cpufreq/cpufreq_imax_thermal_protect"
            for tz in /sys/class/thermal/thermal_zone*; do
                [ -d "$tz" ] || continue
                write_node "enabled" "$tz/mode"
                write_node "85000" "$tz/trip_point_0_temp"
            done
            for path in /sys/devices/system/cpu/cpu[0-9]*; do
                [ -d "$path" ] || continue
                [ -e "$path/cpufreq/cpuinfo_max_freq" ] && chmod 444 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
                [ -e "$path/cpu_capacity" ] && chmod 444 "$path/cpu_capacity" 2>/dev/null
                [ -e "$path/topology/physical_package_id" ] && chmod 444 "$path/topology/physical_package_id" 2>/dev/null
            done
        fi

        # Async Fast Background Fork (Zero-delay profile completion)
        (
            if [ "$PROFILE" = "extreme" ] || [ -f "/dev/lynx_extreme_charging" ]; then
                cmd thermalservice override-status 0 2>/dev/null
            else
                cmd thermalservice reset 2>/dev/null
            fi
            cmd wifi set-power-save-mode 0 2>/dev/null
            cmd wifi force-low-latency-mode enabled 2>/dev/null

            for proc in "surfaceflinger" "android.hardware.graphics.composer" "vendor.qti.hardware.display.composer" "vendor.mediatek.hardware.pq"; do
                for pid in $(pgrep -f "$proc" 2>/dev/null); do
                    renice -n -20 -p "$pid" 2>/dev/null
                    write_node "$pid" "/dev/cpuset/top-app/cgroup.procs"
                done
            done

            for proc in $OEM_TARGET_PROCS; do
                for pid in $(pidof "$proc" 2>/dev/null); do
                    kill -STOP "$pid" 2>/dev/null
                done
            done
            for jpid in $(pgrep -f "com.xiaomi.joyose" 2>/dev/null); do
                kill -STOP "$jpid" 2>/dev/null
            done
        ) >/dev/null 2>&1 &

        # MediaTek DVFSRC / Interconnect & DDR RAM Clock Lock (4.266 GHz peak memory bandwidth)
        if [ "$PROFILE" = "extreme" ]; then
            for dvfsrc_node in /sys/devices/platform/*dvfsrc*/helio-dvfsrc/dvfsrc_force_vcore_dvfs_opp; do
                write_node "0" "$dvfsrc_node"
            done
            for ddr_node in /sys/devices/platform/*dvfsrc*/helio-dvfsrc/dvfsrc_req_ddr_opp; do
                write_node "0" "$ddr_node"
            done
            for m_dir in /sys/devices/platform/*mali* /sys/devices/platform/soc/*mali* /sys/class/misc/mali*/device; do
                [ -d "$m_dir" ] || continue
                write_node "25" "$m_dir/js_scheduling_period"
                write_node "20" "$m_dir/dvfs_period"
            done
        else
            for ddr_node in /sys/devices/platform/*dvfsrc*/helio-dvfsrc/dvfsrc_req_ddr_opp; do
                write_node "0" "$ddr_node"
            done
            for dvfsrc_node in /sys/devices/platform/*dvfsrc*/helio-dvfsrc/dvfsrc_force_vcore_dvfs_opp; do
                write_node "-1" "$dvfsrc_node"
            done
            for m_dir in /sys/devices/platform/*mali* /sys/devices/platform/soc/*mali* /sys/class/misc/mali*/device; do
                [ -d "$m_dir" ] || continue
                write_node "50" "$m_dir/js_scheduling_period"
                write_node "30" "$m_dir/dvfs_period"
            done
        fi

        # ── Extreme Charging & Bypass Alignment ─────────────────────────────
        if [ "$PROFILE" = "extreme" ]; then
            cfg_bypass="false"
            cfg_max=80
            for c_path in "/data/adb/modules/Lynx/config.json" "/data/adb/lynx/config.json" "/data/user/0/com.noir.lynx/files/config.json" "/data/user/0/com.noir.lynx.debug/files/config.json"; do
                if [ -f "$c_path" ]; then
                    cfg_bypass=$(awk -F': ' '/"bypass_enabled"/ {print $2}' "$c_path" 2>/dev/null | grep -q "true" && echo "true" || echo "false")
                    cfg_max=$(awk -F': ' '/"max_battery_percent"/ {gsub(/[^0-9]/,"",$2); print $2}' "$c_path" 2>/dev/null)
                    [ -z "$cfg_max" ] && cfg_max=80
                    break
                fi
            done

            cur_cap=$(cat /sys/class/power_supply/battery/capacity 2>/dev/null || echo 50)
            if [ "$cfg_bypass" = "true" ] && { [ "$cur_cap" -ge "$cfg_max" ] || [ "$cur_cap" -ge 100 ]; }; then
                write_node "0" "/sys/class/power_supply/battery/input_suspend"
                write_node "6000" "/sys/devices/platform/charger/input_current"
                write_node "4500000" "/sys/class/power_supply/usb/current_max"
                write_node "4500000" "/sys/class/power_supply/main/current_max"
                write_node "1" "/sys/devices/platform/charger/bypass_charger"
                write_node "1" "/sys/class/power_supply/battery/device/smart_charging"
                write_node "1" "/sys/class/power_supply/battery/smart_charging_activation"
                write_node "1" "/sys/class/qcom-battery/direct_charging"
                write_node "1" "/sys/class/power_supply/battery/store_mode"
                write_node "1" "/sys/class/power_supply/battery/batt_slate_mode"
                write_node "1" "/sys/devices/platform/charger/enable_sc"
                write_node "$cur_cap" "/sys/devices/platform/charger/sc_tuisoc"
                write_node "0" "/sys/devices/platform/charger/sc_ibat_limit"
                write_node "0" "/sys/devices/platform/charger/chg1_current"
                write_node "0" "/sys/devices/platform/charger/chg2_current"
                write_node "0" "/sys/class/power_supply/battery/constant_charge_current"
                write_node "0" "/sys/class/power_supply/battery/constant_charge_current_max"
                write_node "0" "/sys/class/power_supply/battery/charging_enabled"
            else
                write_node "0" "/sys/devices/platform/charger/bypass_charger"
                write_node "0" "/sys/devices/platform/charger/sw_jeita"
                write_node "1" "/sys/devices/platform/charger/pe20"
                write_node "1" "/sys/devices/platform/charger/pe40"
                write_node "68" "/sys/devices/platform/charger/pdc_max_watt"
                write_node "6000" "/sys/devices/platform/charger/input_current"
                write_node "6000" "/sys/devices/platform/charger/chg1_current"
                write_node "6000" "/sys/devices/platform/charger/chg2_current"
                write_node "8000" "/sys/devices/platform/charger/sc_ibat_limit"
                write_node "1" "/sys/devices/platform/charger/enable_sc"
                write_node "0" "/sys/class/power_supply/battery/input_suspend"
                write_node "1" "/sys/class/power_supply/battery/charging_enabled"
                write_node "6000000" "/sys/class/power_supply/battery/constant_charge_current_max"
                write_node "6000000" "/sys/class/power_supply/battery/constant_charge_current"
                write_node "6000000" "/sys/class/power_supply/main/current_max"
                write_node "6000000" "/sys/class/power_supply/usb/current_max"
                write_node "0" "/sys/class/qcom-battery/restricted_charging"
                write_node "6000000" "/sys/class/qcom-battery/restrict_cur"
                write_node "1" "/sys/class/power_supply/battery/fastcharge_mode"
            fi
        else
            # Non-extreme profiles: release bypass locks if battery is below 100%
            cur_cap=$(cat /sys/class/power_supply/battery/capacity 2>/dev/null || echo 50)
            if [ "$cur_cap" -lt 100 ]; then
                write_node "0" "/sys/devices/platform/charger/bypass_charger"
                write_node "1" "/sys/devices/platform/charger/enable_sc"
                write_node "6000" "/sys/devices/platform/charger/sc_ibat_limit"
                write_node "5376" "/sys/devices/platform/charger/chg1_current"
                write_node "5376" "/sys/devices/platform/charger/chg2_current"
                write_node "1" "/sys/class/power_supply/battery/charging_enabled"
                write_node "0" "/sys/class/power_supply/battery/input_suspend"
            fi
        fi

        setprop lynx.mode "$PROFILE"
        ;;

    powersave)
        for c in /sys/devices/system/cpu/cpu[0-9]*; do
            [ -d "$c" ] || continue
            write_node "1" "$c/online"
        done

        for p in /sys/devices/system/cpu/cpufreq/policy*; do
            [ -d "$p" ] || continue
            chmod 644 "$p/scaling_min_freq" "$p/scaling_max_freq" 2>/dev/null
            pol_num=$(basename "$p" | tr -dc '0-9')
            dyn_gov=$(pick_dynamic_gov "$p")
            write_node "$dyn_gov" "$p/scaling_governor"
            for gdir in "$p/schedutil" "$p/walt" "$p/sugov_ext"; do
                [ -d "$gdir" ] || continue
                write_node "20000" "$gdir/up_rate_limit_us"
                write_node "500" "$gdir/down_rate_limit_us"
                write_node "99" "$gdir/hispeed_load"
                write_node "0" "$gdir/iowait_boost_enable"
                write_node "0" "$gdir/pl"
            done

            min_freq=$(cat "$p/cpuinfo_min_freq" 2>/dev/null)
            max_freq=$(cat "$p/cpuinfo_max_freq" 2>/dev/null)
            avail_f=$(cat "$p/scaling_available_frequencies" 2>/dev/null)
            sorted_f=$(echo "$avail_f" | tr -s ' ' '
' | sort -n)
            if [ -z "$max_freq" ]; then
                max_freq=$(echo "$sorted_f" | tail -n 1)
            fi
            if [ -z "$min_freq" ]; then
                min_freq=$(echo "$sorted_f" | head -n 1)
            fi
            [ -n "$min_freq" ] && write_node "$min_freq" "$p/scaling_min_freq"
            if [ -n "$max_freq" ]; then
                if [ "$pol_num" = "0" ]; then
                    raw_cap=$(( max_freq * 65 / 100 ))
                else
                    raw_cap=$(( max_freq * 55 / 100 ))
                fi
                snapped_cap=""
                for f in $sorted_f; do
                    if [ "$f" -le "$raw_cap" ] 2>/dev/null; then
                        snapped_cap="$f"
                    fi
                done
                [ -z "$snapped_cap" ] && snapped_cap="$raw_cap"
                [ -n "$min_freq" ] && [ "$snapped_cap" -lt "$min_freq" ] 2>/dev/null && snapped_cap="$min_freq"
                write_node "$snapped_cap" "$p/scaling_max_freq"
            fi
        done

        for np in /sys/devices/system/cpu/cpu*/core_ctl/not_preferred; do
            [ -f "$np" ] && write_node "0 0 0 0" "$np"
        done

        # Respect user preferred architecture or preserve Hybrid (2) / EAS (1)
        if [ -f /data/adb/lynx/preferred_architecture ]; then
            pref_arch=$(cat /data/adb/lynx/preferred_architecture 2>/dev/null | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')
            if [ "$pref_arch" = "hybrid" ]; then
                write_node "2" "/sys/devices/system/cpu/eas/enable"
            elif [ "$pref_arch" = "eas" ]; then
                write_node "1" "/sys/devices/system/cpu/eas/enable"
            elif [ "$pref_arch" = "hmp" ]; then
                write_node "0" "/sys/devices/system/cpu/eas/enable"
            fi
        elif [ -f /sys/devices/system/cpu/eas/enable ]; then
            cur_eas=$(cat /sys/devices/system/cpu/eas/enable 2>/dev/null | tr '[:upper:]' '[:lower:]')
            case "$cur_eas" in
                *hybrid*|2) write_node "2" "/sys/devices/system/cpu/eas/enable" ;;
                *) write_node "1" "/sys/devices/system/cpu/eas/enable" ;;
            esac
        fi
        write_node "0" "/sys/devices/system/cpu/perf/enable"

        write_node "1" "/proc/cpufreq/cpufreq_power_mode"
        write_node "0" "/proc/cpufreq/cpufreq_cci_mode"
        write_node "0" "/proc/cpufreq/cpufreq_imax_enable"
        write_node "0" "/proc/cpufreq/cpufreq_sched_disable"
        write_node "1" "/proc/cpuidle/control/armpll_mode"
        write_node "0" "/proc/cpuidle/control/buck_mode"
        for dvfsrc_node in /sys/devices/platform/*dvfsrc*/helio-dvfsrc/dvfsrc_force_vcore_dvfs_opp \
                           /sys/devices/platform/*dvfsrc*/helio-dvfsrc/dvfsrc_req_ddr_opp; do
            write_node "-1" "$dvfsrc_node"
        done
        for m_dir in /sys/devices/platform/*mali* /sys/devices/platform/soc/*mali* /sys/class/misc/mali*/device; do
            [ -d "$m_dir" ] || continue
            write_node "100" "$m_dir/js_scheduling_period"
            write_node "100" "$m_dir/dvfs_period"
        done

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

        c_idx=0
        for p in /sys/devices/system/cpu/cpufreq/policy*; do
            [ -d "$p" ] || continue
            cur_max=$(cat "$p/scaling_max_freq" 2>/dev/null)
            cur_min=$(cat "$p/scaling_min_freq" 2>/dev/null)
            if [ -f /proc/ppm/policy/hard_userlimit_max_cpu_freq ]; then
                [ -n "$cur_max" ] && write_node "$c_idx $cur_max" "/proc/ppm/policy/hard_userlimit_max_cpu_freq"
                [ -n "$cur_min" ] && write_node "$c_idx $cur_min" "/proc/ppm/policy/hard_userlimit_min_cpu_freq"
            fi
            c_idx=$(( c_idx + 1 ))
        done

        little_cpus=$(cat /sys/devices/system/cpu/cpufreq/policy0/related_cpus /sys/devices/system/cpu/cpufreq/policy0/affected_cpus 2>/dev/null | head -n1)
        for c in /sys/devices/system/cpu/cpu[0-9]*; do
            [ -d "$c" ] || continue
            cid="${c##*cpu}"
            [ "$cid" = "0" ] && continue
            case " $little_cpus " in
                *" $cid "*) [ -e "$c/online" ] && write_node "1" "$c/online" ;;
                *) [ -e "$c/online" ] && write_node "0" "$c/online" ;;
            esac
        done

        for dev in /sys/class/devfreq/*; do
            [ -d "$dev" ] || continue
            case "$dev" in
                *ufshc*|*cpubw*|*gpubw*|*llccbw*|*l3-cpu*|*bus_ddr*)
                    write_node "powersave" "$dev/governor"
                    ;;
            esac
        done

        write_node "0" "/proc/gpufreq/gpufreq_opp_freq"
        if grep -q "is enabled" /proc/gpufreq/gpufreq_fixed_freq_volt 2>/dev/null; then
            write_node "0 0" "/proc/gpufreq/gpufreq_fixed_freq_volt"
        fi
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
        write_node "0" "/sys/module/ged/parameters/boost_gpu_enable"
        write_node "0" "/sys/module/ged/parameters/ged_smart_boost"
        write_node "0" "/sys/module/ged/parameters/enable_gpu_boost"
        write_node "0" "/sys/module/ged/parameters/enable_cpu_boost"
        write_node "0" "/sys/module/ged/parameters/gx_game_mode"
        write_node "0" "/sys/module/ged/parameters/gx_boost_on"
        write_node "0" "/sys/module/ged/parameters/ged_boost_enable"
        write_node "0" "/sys/module/ged/parameters/boost_amp"
        write_node "0" "/sys/kernel/fpsgo/common/gpu_block_boost"
        for pp in /sys/devices/platform/*mali*/power_policy /sys/devices/platform/soc/*mali*/power_policy; do
            [ -e "$pp" ] && write_node "coarse_demand" "$pp"
        done
        write_node "0" "/proc/mali/always_on"
        write_node "1" "/proc/mali/dvfs_enable"
        num_pwr=$(cat "/sys/class/kgsl/kgsl-3d0/num_pwrlevels" 2>/dev/null)
        [ -n "$num_pwr" ] && [ "$num_pwr" -gt 1 ] && write_node "$((num_pwr - 1))" "/sys/class/kgsl/kgsl-3d0/min_pwrlevel"
        write_node "0" "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost"
        write_node "1" "/sys/class/kgsl/kgsl-3d0/throttling"
        write_node "20" "/sys/class/kgsl/kgsl-3d0/idle_timer"

        for u_node in "/dev/cpuset/top-app/cpu.uclamp.min" "/proc/sys/kernel/sched_util_clamp_min"; do
            write_node "0" "$u_node"
        done
        write_node "0-5" "/dev/cpuset/top-app/cpus"
        write_node "0-5" "/dev/cpuset/foreground/cpus"
        write_node "0-1" "/dev/cpuset/background/cpus"
        write_node "0-2" "/dev/cpuset/system-background/cpus"
        write_node "0-1" "/dev/cpuset/restricted/cpus"
        write_node "10" "/proc/sys/vm/dirty_ratio"
        write_node "5" "/proc/sys/vm/dirty_background_ratio"
        write_node "100" "/proc/sys/vm/swappiness"
        write_node "50" "/proc/sys/vm/vfs_cache_pressure"
        for q in /sys/block/*/queue/scheduler; do
            [ -e "$q" ] && echo noop > "$q" 2>/dev/null
        done
        for ra in /sys/block/*/queue/read_ahead_kb; do
            write_node "64" "$ra"
        done

        (
            for proc in $OEM_TARGET_PROCS; do
                for pid in $(pidof "$proc" 2>/dev/null); do kill -CONT "$pid" 2>/dev/null; done
            done
            for jpid in $(pgrep -f "com.xiaomi.joyose" 2>/dev/null); do kill -CONT "$jpid" 2>/dev/null; done
            cmd wifi set-power-save-mode 1 >/dev/null 2>&1
            cmd wifi force-low-latency-mode disabled >/dev/null 2>&1
            [ ! -f "/dev/lynx_extreme_charging" ] && cmd thermalservice reset 2>/dev/null
        ) >/dev/null 2>&1 &

        sysctl -w net.ipv4.tcp_low_latency=0 >/dev/null 2>&1
        if which resetprop >/dev/null 2>&1; then
            for p in debug.sf.latch_unsignaled \
                     debug.sf.enable_gl_backpressure \
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
            setprop debug.sf.latch_unsignaled "" 2>/dev/null
            setprop debug.sf.enable_gl_backpressure "" 2>/dev/null
            setprop debug.sf.disable_backpressure "" 2>/dev/null
        fi
        setprop af.fast_track_multiplier 2 2>/dev/null
        setprop aaudio.mmap_policy 1 2>/dev/null
        write_node "" "/proc/sys/kernel/sched_lib_name"
        write_node "0" "/proc/sys/kernel/sched_lib_mask_force"

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

        write_node "1" "/proc/cpufreq/cpufreq_imax_thermal_protect"
        for tz in /sys/class/thermal/thermal_zone*; do
            [ -d "$tz" ] || continue
            write_node "enabled" "$tz/mode"
        done

        # Reset MediaTek EAS perfmgr
        write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost"
        write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost"
        write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_uclamp_min"
        write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_uclamp_min"
        write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_uclamp_min"
        write_node "1" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_prefer_idle"

        setprop lynx.mode powersave
        ;;

    balance|auto|*)
        for c in /sys/devices/system/cpu/cpu[0-9]*; do
            [ -d "$c" ] || continue
            [ -e "$c/online" ] && echo 1 > "$c/online" 2>/dev/null
        done

        for p in /sys/devices/system/cpu/cpufreq/policy*; do
            [ -d "$p" ] || continue
            chmod 644 "$p/scaling_min_freq" "$p/scaling_max_freq" 2>/dev/null
            dyn_gov=$(pick_dynamic_gov "$p")
            write_node "$dyn_gov" "$p/scaling_governor"
            for gdir in "$p/schedutil" "$p/walt" "$p/sugov_ext"; do
                [ -d "$gdir" ] || continue
                write_node "0" "$gdir/up_rate_limit_us"
                write_node "15000" "$gdir/down_rate_limit_us"
                write_node "80" "$gdir/hispeed_load"
                write_node "1" "$gdir/iowait_boost_enable"
                write_node "1" "$gdir/pl"
            done

            min_freq=$(cat "$p/cpuinfo_min_freq" 2>/dev/null)
            max_freq=$(cat "$p/cpuinfo_max_freq" 2>/dev/null)
            if [ -z "$max_freq" ]; then
                max_freq=$(tr -s ' ' '
' < "$p/scaling_available_frequencies" 2>/dev/null | sort -n | tail -n 1)
            fi
            if [ -z "$min_freq" ]; then
                min_freq=$(tr -s ' ' '
' < "$p/scaling_available_frequencies" 2>/dev/null | sort -n | head -n 1)
            fi
            [ -n "$min_freq" ] && write_node "$min_freq" "$p/scaling_min_freq"
            [ -n "$max_freq" ] && write_node "$max_freq" "$p/scaling_max_freq"
            if [ -n "$max_freq" ] && [ "$max_freq" -gt 0 ] 2>/dev/null; then
                hi_f=$(( max_freq * 75 / 100 ))
                for gdir in "$p/schedutil" "$p/walt" "$p/sugov_ext"; do
                    [ -d "$gdir" ] && write_node "$hi_f" "$gdir/hispeed_freq"
                done
            fi
        done

        for np in /sys/devices/system/cpu/cpu*/core_ctl/not_preferred; do
            [ -f "$np" ] && write_node "0 0 0 0" "$np"
        done

        # Respect user preferred architecture or preserve Hybrid (2) / EAS (1)
        if [ -f /data/adb/lynx/preferred_architecture ]; then
            pref_arch=$(cat /data/adb/lynx/preferred_architecture 2>/dev/null | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')
            if [ "$pref_arch" = "hybrid" ]; then
                write_node "2" "/sys/devices/system/cpu/eas/enable"
            elif [ "$pref_arch" = "eas" ]; then
                write_node "1" "/sys/devices/system/cpu/eas/enable"
            elif [ "$pref_arch" = "hmp" ]; then
                write_node "0" "/sys/devices/system/cpu/eas/enable"
            fi
        elif [ -f /sys/devices/system/cpu/eas/enable ]; then
            cur_eas=$(cat /sys/devices/system/cpu/eas/enable 2>/dev/null | tr '[:upper:]' '[:lower:]')
            case "$cur_eas" in
                *hybrid*|2) write_node "2" "/sys/devices/system/cpu/eas/enable" ;;
                *) write_node "1" "/sys/devices/system/cpu/eas/enable" ;;
            esac
        fi
        write_node "1" "/sys/devices/system/cpu/perf/enable"
        write_node "1" "/proc/sys/kernel/sched_autogroup_enabled"
        write_node "0" "/proc/sys/kernel/sched_tunable_scaling"

        write_node "0" "/proc/cpufreq/cpufreq_power_mode"
        write_node "0" "/proc/cpufreq/cpufreq_cci_mode"
        write_node "0" "/proc/cpufreq/cpufreq_imax_enable"
        write_node "0" "/proc/cpufreq/cpufreq_sched_disable"
        write_node "1" "/proc/cpuidle/control/armpll_mode"
        write_node "0" "/proc/cpuidle/control/buck_mode"
        for dvfsrc_node in /sys/devices/platform/*dvfsrc*/helio-dvfsrc/dvfsrc_force_vcore_dvfs_opp \
                           /sys/devices/platform/*dvfsrc*/helio-dvfsrc/dvfsrc_req_ddr_opp; do
            write_node "-1" "$dvfsrc_node"
        done
        for m_dir in /sys/devices/platform/*mali* /sys/devices/platform/soc/*mali* /sys/class/misc/mali*/device; do
            [ -d "$m_dir" ] || continue
            write_node "50" "$m_dir/js_scheduling_period"
            write_node "50" "$m_dir/dvfs_period"
        done

        # MediaTek EAS perfmgr Responsive Balanced
        write_node "15" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost"
        write_node "10" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost"
        write_node "10" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_uclamp_min"
        write_node "5" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_uclamp_min"
        write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_uclamp_min"
        write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_prefer_idle"
        write_node "1" "/proc/perfmgr/boost_ctrl/eas_ctrl/sched_big_task_rotation"
        write_node "1" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ext_launch_mon"

        write_node "1" "/proc/ppm/enabled"
        write_node "0 0" "/proc/ppm/policy_status"
        write_node "1 1" "/proc/ppm/policy_status"
        write_node "2 0" "/proc/ppm/policy_status"
        write_node "3 0" "/proc/ppm/policy_status"
        write_node "4 0" "/proc/ppm/policy_status"
        write_node "5 0" "/proc/ppm/policy_status"
        write_node "6 1" "/proc/ppm/policy_status"
        write_node "7 1" "/proc/ppm/policy_status"
        write_node "8 0" "/proc/ppm/policy_status"
        write_node "9 1" "/proc/ppm/policy_status"
        write_node "0" "/proc/ppm/cpi/cpi_enabled"

        for c in 0 1 2; do
            dvfs_table="/proc/ppm/dump_cluster_${c}_dvfs_table"
            if [ -f "$dvfs_table" ]; then
                c_max=$(awk '{print $1}' "$dvfs_table" 2>/dev/null | head -n 1)
                c_min=$(tail -n 1 "$dvfs_table" 2>/dev/null | awk '{print $NF}')
                [ -n "$c_max" ] && write_node "$c $c_max" "/proc/ppm/policy/hard_userlimit_max_cpu_freq"
                [ -n "$c_min" ] && write_node "$c $c_min" "/proc/ppm/policy/hard_userlimit_min_cpu_freq"
            fi
        done

        for dev in /sys/class/devfreq/*; do
            [ -d "$dev" ] || continue
            case "$dev" in
                *ufshc*)       write_node "simple_ondemand" "$dev/governor" ;;
                *cpubw*|*llccbw*) write_node "bw_hwmon" "$dev/governor" ;;
                *gpubw*)       write_node "bw_vbif" "$dev/governor" ;;
                *l3-cpu*)      write_node "mem_latency" "$dev/governor" ;;
                *bus_ddr*)     write_node "msm-vidc-ddr" "$dev/governor" ;;
            esac
            freq_table="$dev/available_frequencies"
            if [ -s "$freq_table" ]; then
                l_freq=$(tr -s ' ' '
' < "$freq_table" 2>/dev/null | sort -n | head -n 1)
                [ -n "$l_freq" ] && write_node "$l_freq" "$dev/min_freq"
            fi
        done

        write_node "0" "/proc/gpufreq/gpufreq_opp_freq"
        if grep -q "is enabled" /proc/gpufreq/gpufreq_fixed_freq_volt 2>/dev/null; then
            write_node "0 0" "/proc/gpufreq/gpufreq_fixed_freq_volt"
        fi
        write_node "0" "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
        if [ -f "/proc/gpufreq/gpufreq_opp_dump" ]; then
            opp_top=$(head -n 1 /proc/gpufreq/gpufreq_opp_dump 2>/dev/null)
            opp_bot=$(tail -n 1 /proc/gpufreq/gpufreq_opp_dump 2>/dev/null)
            peak_f=$(echo "$opp_top" | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
            min_f=$(echo "$opp_bot" | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
            last_idx=$(echo "$opp_bot" | awk -F'[][]' '{print int($2)}')
            [ -z "$last_idx" ] && last_idx="48"
            write_node "$last_idx" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
            [ -n "$peak_f" ] && write_node "$peak_f" "/sys/module/ged/parameters/gpu_cust_upbound_freq"
            [ -n "$min_f" ] && write_node "$min_f" "/sys/module/ged/parameters/gpu_cust_boost_freq"
            [ -n "$min_f" ] && write_node "$min_f" "/sys/module/ged/parameters/gpu_bottom_freq"
        else
            write_node "48" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
        fi
        for i in 0 1 2 3 4 5 6 7 8; do
            write_node "$i 1 1" "/proc/gpufreq/gpufreq_limit_table"
        done
        write_node "0" "/sys/kernel/ged/hal/gpu_boost_level"
        write_node "10" "/sys/kernel/ged/hal/dvfs_margin_value"
        write_node "1" "/sys/module/ged/parameters/boost_gpu_enable"
        write_node "1" "/sys/module/ged/parameters/ged_smart_boost"
        write_node "0" "/sys/module/ged/parameters/gx_game_mode"
        write_node "0" "/sys/module/ged/parameters/gx_boost_on"
        write_node "0" "/sys/module/ged/parameters/ged_boost_enable"
        write_node "0" "/sys/module/ged/parameters/boost_amp"
        write_node "1" "/sys/kernel/fpsgo/common/gpu_block_boost"
        for pp in /sys/devices/platform/*mali*/power_policy /sys/devices/platform/soc/*mali*/power_policy; do
            [ -e "$pp" ] && write_node "always_on" "$pp"
        done
        write_node "1" "/proc/mali/always_on"
        write_node "1" "/proc/mali/dvfs_enable"

        cur_peak_rr=$(settings get system peak_refresh_rate 2>/dev/null)
        case "$cur_peak_rr" in
            144*|144|120*|120|90*|90)
                write_node "8333333" "/sys/module/ged/parameters/target_t_cpu_remained"
                ;;
            *)
                write_node "16666666" "/sys/module/ged/parameters/target_t_cpu_remained"
                ;;
        esac

        write_node "1" "/sys/class/kgsl/kgsl-3d0/throttling"
        write_node "0" "/sys/class/kgsl/kgsl-3d0/force_bus_on"
        write_node "0" "/sys/class/kgsl/kgsl-3d0/force_clk_on"
        write_node "0" "/sys/class/kgsl/kgsl-3d0/force_rail_on"
        write_node "80" "/sys/class/kgsl/kgsl-3d0/idle_timer"
        num_pwr=$(cat "/sys/class/kgsl/kgsl-3d0/num_pwrlevels" 2>/dev/null)
        [ -n "$num_pwr" ] && [ "$num_pwr" -gt 1 ] && write_node "$((num_pwr - 1))" "/sys/class/kgsl/kgsl-3d0/min_pwrlevel"
        write_node "0" "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost"

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

        write_node "0-7" "/dev/cpuset/foreground/cpus"
        write_node "0-3" "/dev/cpuset/background/cpus"
        write_node "0-3" "/dev/cpuset/system-background/cpus"
        write_node "0-7" "/dev/cpuset/top-app/cpus"
        write_node "0-5" "/dev/cpuset/restricted/cpus"
        write_node "1" "/dev/stune/schedtune.sched_boost_enabled"
        write_node "5" "/dev/stune/schedtune.boost"
        write_node "0" "/dev/stune/schedtune.prefer_idle"
        write_node "10" "/dev/stune/foreground/schedtune.boost"
        write_node "1" "/dev/stune/foreground/schedtune.prefer_idle"
        write_node "15" "/dev/stune/top-app/schedtune.boost"
        write_node "1" "/dev/stune/top-app/schedtune.prefer_idle"
        write_node "1" "/proc/sys/kernel/sched_big_task_rotation"
        write_node "1" "/proc/sys/kernel/sched_sync_hint_enable"

        if [ -e "/sys/kernel/mm/lru_gen/enabled" ]; then
            write_node "y" "/sys/kernel/mm/lru_gen/enabled"
        fi
        write_node "20" "/proc/sys/vm/dirty_ratio"
        write_node "5" "/proc/sys/vm/dirty_background_ratio"
        write_node "80" "/proc/sys/vm/swappiness"
        write_node "16" "/proc/sys/vm/watermark_scale_factor"
        write_node "60" "/proc/sys/vm/vfs_cache_pressure"
        for q in /sys/block/*/queue/scheduler; do
            [ -e "$q" ] && echo deadline > "$q" 2>/dev/null
        done
        for ra in /sys/block/*/queue/read_ahead_kb; do
            write_node "512" "$ra"
        done

        # Restore Scheduler & VM Balanced Tunables
        write_node "5000000" "/proc/sys/kernel/sched_latency_ns"
        write_node "1000000" "/proc/sys/kernel/sched_min_granularity_ns"
        write_node "800000" "/proc/sys/kernel/sched_wakeup_granularity_ns"
        write_node "200000" "/proc/sys/kernel/sched_migration_cost_ns"
        write_node "32" "/proc/sys/kernel/sched_nr_migrate"
        write_node "0" "/proc/sys/kernel/sched_schedstats"
        write_node "0" "/proc/sys/kernel/sched_child_runs_first"
        write_node "1" "/proc/sys/kernel/sched_cstate_aware"
        write_node "1" "/proc/sys/vm/stat_interval"

        # Restore FPSGO & GED Parameters
        write_node "1" "/sys/module/ged/parameters/gpu_idle"
        write_node "0" "/sys/module/ged/parameters/gx_top_app_pid"
        write_node "0" "/sys/module/ged/parameters/ged_force_mdp_enable"
        write_node "0" "/sys/kernel/fpsgo/fbt/ultra_rescue"
        write_node "1" "/sys/kernel/fpsgo/fstb/fstb_soft_level"
        write_node "1" "/sys/kernel/fpsgo/fbt/light_loading_policy"
        write_node "1" "/sys/kernel/fpsgo/fbt/light_loading_policy_90"

        (
            for proc in $OEM_TARGET_PROCS; do
                for pid in $(pidof "$proc" 2>/dev/null); do kill -CONT "$pid" 2>/dev/null; done
            done
            for jpid in $(pgrep -f "com.xiaomi.joyose" 2>/dev/null); do kill -CONT "$jpid" 2>/dev/null; done
            cmd wifi set-power-save-mode 1 >/dev/null 2>&1
            cmd wifi force-low-latency-mode disabled >/dev/null 2>&1
            [ ! -f "/dev/lynx_extreme_charging" ] && cmd thermalservice reset 2>/dev/null
        ) >/dev/null 2>&1 &

        sysctl -w net.ipv4.tcp_low_latency=0 >/dev/null 2>&1

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

        setprop af.fast_track_multiplier 2 2>/dev/null
        setprop aaudio.mmap_policy 1 2>/dev/null
        write_node "" "/proc/sys/kernel/sched_lib_name"
        write_node "0" "/proc/sys/kernel/sched_lib_mask_force"

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

        for tn in /sys/class/touch/touch_dev/touch_game_mode \
                 /sys/devices/virtual/touch/touch_dev/bump_sample_rate \
                 /proc/touchscreen/game_mode \
                 /sys/devices/platform/goodix_ts.*/game_mode \
                 /sys/devices/platform/tp_wake_switch/game_mode \
                 /sys/devices/virtual/input/input*/touch_game_mode; do
            write_node "0" "$tn"
        done

        write_node "1" "/proc/cpufreq/cpufreq_imax_thermal_protect"
        for tz in /sys/class/thermal/thermal_zone*; do
            [ -d "$tz" ] || continue
            write_node "enabled" "$tz/mode"
        done

        if [ "$PROFILE" = "auto" ]; then
            setprop lynx.mode auto
        else
            setprop lynx.mode balance
        fi
        ;;
esac
