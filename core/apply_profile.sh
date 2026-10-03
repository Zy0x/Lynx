#!/system/bin/sh
# ==============================================================================
# Lynx Universal - Master Hardware Profile Applicator
# Complete, audited kernel & hardware tuning for Extreme, Performance, Balance, Powersave, Auto.
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash).
# Zero hardcoding, multi-platform (MediaTek Dimensity/Helio & Qualcomm Snapdragon).
# ==============================================================================

PROFILE="${1:-balance}"
CALLER="${2:-user}"
TARGET_APP="${3:-}"
mkdir -p /data/adb/lynx 2>/dev/null
echo "$PROFILE" > /data/adb/lynx/active_profile 2>/dev/null
setprop lynx.mode "$PROFILE" 2>/dev/null
if [ "$CALLER" != "watcher" ] && [ "$CALLER" != "automation" ] && [ "$PROFILE" != "auto" ]; then
    echo "$PROFILE" > /data/adb/lynx/baseline_profile 2>/dev/null
fi
if [ -z "$TARGET_APP" ]; then
    TARGET_APP=$(dumpsys activity activities 2>/dev/null | grep -m1 "topResumedActivity" | grep -oE '[a-zA-Z0-9._]+/[a-zA-Z0-9._]+' | head -n1 | cut -d'/' -f1)
fi
TARGET_SOC="generic"
if [ -f "/data/adb/modules/Lynx/target_soc" ]; then
    TARGET_SOC=$(cat "/data/adb/modules/Lynx/target_soc" 2>/dev/null | tr -d '[:space:]')
elif [ -f "/data/adb/lynx/target_soc" ]; then
    TARGET_SOC=$(cat "/data/adb/lynx/target_soc" 2>/dev/null | tr -d '[:space:]')
fi
if [ -z "$TARGET_SOC" ] || [ "$TARGET_SOC" = "generic" ]; then
    if [ -d "/proc/ppm" ] || [ -d "/proc/ged" ] || [ -c "/dev/ged" ]; then
        TARGET_SOC="mtk"
    elif [ -d "/sys/class/kgsl" ] || [ -c "/dev/kgsl-3d0" ] || [ -d "/sys/devices/soc0" ]; then
        TARGET_SOC="qcom"
    fi
fi

# Zero-Fork Fast Path: only chmod if direct write failed and node is not writable
write_node() {
    [ -e "$2" ] || return 0
    echo "$1" > "$2" 2>/dev/null && return 0
    if [ ! -w "$2" ]; then
        chmod 666 "$2" 2>/dev/null
        echo "$1" > "$2" 2>/dev/null
    fi
}

# Target throttler daemons known to clamp FPS and frequencies
OEM_TARGET_PROCS="mi_thermald thermal-engine thermal-engine-v2 ituxd com.samsung.android.game.gos"

is_bluetooth_audio() {
    dumpsys audio 2>/dev/null | grep -iE "a2dp.*connected|device.*bluetooth_a2dp" | grep -qv "state=0"
}

# Ensure frequency files are readable even if Unity trick was previously applied
if [ ! -r "/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_max_freq" ]; then
    for p in /sys/devices/system/cpu/cpufreq/policy*; do
        [ -d "$p" ] || continue
        chmod 444 "$p"/cpuinfo_* 2>/dev/null
    done
    for c in /sys/devices/system/cpu/cpu[0-9]*; do
        [ -d "$c" ] || continue
        chmod 444 "$c"/cpufreq/cpuinfo_* "$c"/cpu_capacity "$c"/topology/physical_package_id 2>/dev/null
    done
fi

case "$PROFILE" in
    extreme|performance)
        # ── 1. CPU Governor & Frequency Clamping ──────────────────────────
        # Extreme: 'performance' governor locks all cores to scaling_max_freq (100% hardlock)
        # Performance: 'schedutil' with 0us up-rate limit and 85% floor frequency
        for p in /sys/devices/system/cpu/cpufreq/policy*; do
            [ -d "$p" ] || continue
            pol_num=$(basename "$p" | tr -dc '0-9')
            max_freq=$(cat "$p/cpuinfo_max_freq" 2>/dev/null)
            min_freq=$(cat "$p/cpuinfo_min_freq" 2>/dev/null)
            avail_f=$(cat "$p/scaling_available_frequencies" 2>/dev/null)
            if [ -z "$max_freq" ] || [ -z "$min_freq" ]; then
                sorted_f=$(echo "$avail_f" | tr -s ' ' '\n' | sort -n)
                [ -z "$min_freq" ] && min_freq=$(echo "$sorted_f" | head -n 1)
                [ -z "$max_freq" ] && max_freq=$(echo "$sorted_f" | tail -n 1)
            fi
            if [ -n "$max_freq" ] && [ -n "$min_freq" ]; then
                if [ "$max_freq" -lt "$min_freq" ] 2>/dev/null; then
                    tmp="$max_freq"
                    max_freq="$min_freq"
                    min_freq="$tmp"
                fi
            fi

            if [ "$PROFILE" = "extreme" ]; then
                if [ "$TARGET_SOC" = "mtk" ]; then
                    write_node "schedutil" "$p/scaling_governor"
                    write_node "0" "$p/schedutil/up_rate_limit_us"
                    write_node "0" "$p/schedutil/down_rate_limit_us"
                    write_node "1" "$p/schedutil/pl"
                    write_node "$max_freq" "$p/schedutil/hispeed_freq"
                else
                    write_node "performance" "$p/scaling_governor"
                fi
                write_node "$max_freq" "$p/scaling_max_freq"
                write_node "$max_freq" "$p/scaling_min_freq"
                eval "saved_floor_${pol_num}=\"$max_freq\""
            else
                write_node "schedutil" "$p/scaling_governor"
                write_node "$max_freq" "$p/scaling_max_freq"
                write_node "0" "$p/schedutil/up_rate_limit_us"
                write_node "5000" "$p/schedutil/down_rate_limit_us"
                write_node "85" "$p/schedutil/hispeed_load"
                write_node "1" "$p/schedutil/iowait_boost_enable"
                write_node "1" "$p/schedutil/pl"
                write_node "$max_freq" "$p/schedutil/hispeed_freq"
                if [ -n "$max_freq" ]; then
                    floor=$(( max_freq * 85 / 100 ))
                    snapped_floor=""
                    for f in $(echo "$avail_f" | tr -s ' ' '\n' | sort -n); do
                        if [ "$f" -le "$floor" ]; then
                            snapped_floor="$f"
                        fi
                    done
                    [ -z "$snapped_floor" ] && snapped_floor="$floor"
                    [ -n "$min_freq" ] && [ "$snapped_floor" -lt "$min_freq" ] && snapped_floor="$min_freq"
                    write_node "$snapped_floor" "$p/scaling_min_freq"
                    eval "saved_floor_${pol_num}=\"$snapped_floor\""
                fi
            fi
        done

        # Ensure all CPU cores are online
        for c in /sys/devices/system/cpu/cpu[0-9]*; do
            [ -d "$c" ] || continue
            write_node "1" "$c/online"
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

        # ── 2. Workqueue & Interconnect Mode ────────────────────────────────
        write_node "N" "/sys/module/workqueue/parameters/power_efficient"
        write_node "1" "/sys/devices/system/cpu/perf/enable"
        # Respect user preferred architecture or preserve Hybrid (2) / EAS (1)
        if [ -f "/data/adb/lynx/preferred_architecture" ]; then
            pref_arch=$(cat /data/adb/lynx/preferred_architecture 2>/dev/null | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')
            if [ "$pref_arch" = "hybrid" ]; then
                write_node "2" "/sys/devices/system/cpu/eas/enable"
            elif [ "$pref_arch" = "eas" ]; then
                write_node "1" "/sys/devices/system/cpu/eas/enable"
            elif [ "$pref_arch" = "hmp" ]; then
                write_node "0" "/sys/devices/system/cpu/eas/enable"
            fi
        elif [ -f "/sys/devices/system/cpu/eas/enable" ]; then
            cur_eas=$(cat /sys/devices/system/cpu/eas/enable 2>/dev/null | tr '[:upper:]' '[:lower:]')
            if [[ "$cur_eas" == *"hybrid"* ]] || [ "$cur_eas" = "2" ]; then
                write_node "2" "/sys/devices/system/cpu/eas/enable"
            fi
        fi
        write_node "1" "/proc/sys/kernel/sched_autogroup_enabled"
        write_node "0" "/proc/sys/kernel/sched_tunable_scaling"

        # MediaTek CPU Sport & CCI Interconnect Mode
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

        # MediaTek EAS perfmgr Kernel Turbo
        write_node "100" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost"
        write_node "100" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost"
        write_node "100" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_uclamp_min"
        write_node "100" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_uclamp_min"
        write_node "100" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_uclamp_min"
        write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_prefer_idle"
        write_node "1" "/proc/perfmgr/boost_ctrl/eas_ctrl/sched_big_task_rotation"
        write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_schedplus_down_throttle"
        write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_schedplus_up_throttle"

        # MediaTek PPM Throttler Release
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

        # MediaTek PPM DVFS Cluster Clamping
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

        # Qualcomm Devfreq Memory Bus Boost
        for dev in /sys/class/devfreq/*; do
            [ -d "$dev" ] || continue
            case "$dev" in
                *ufshc*|*cpubw*|*gpubw*|*llccbw*|*l3-cpu*|*bus_ddr*)
                    write_node "performance" "$dev/governor"
                    freq_table="$dev/available_frequencies"
                    if [ -s "$freq_table" ]; then
                        h_freq=$(tr -s ' ' '\n' < "$freq_table" 2>/dev/null | sort -n | tail -n 1)
                        [ -n "$h_freq" ] && write_node "$h_freq" "$dev/min_freq"
                    fi
                    ;;
            esac
        done

        # ── 3. GPU Subsystem Boost (Mali GED & Qualcomm Adreno KGSL) ─────────
        if [ "$PROFILE" = "extreme" ]; then
            write_node "1" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
            write_node "1" "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
            write_node "2" "/sys/kernel/ged/hal/gpu_boost_level"
            write_node "50" "/sys/kernel/ged/hal/dvfs_margin_value"
            write_node "50" "/sys/module/ged/parameters/gx_fb_dvfs_margin"
            write_node "0" "/sys/class/kgsl/kgsl-3d0/max_pwrlevel"
            write_node "0" "/sys/class/kgsl/kgsl-3d0/min_pwrlevel"
            write_node "0" "/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel"
            write_node "0" "/sys/class/kgsl/kgsl-3d0/default_pwrlevel"
            write_node "3" "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost"
            write_node "performance" "/sys/class/kgsl/kgsl-3d0/devfreq/governor"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/force_bus_on"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/force_clk_on"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/force_rail_on"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/force_no_nap"
            write_node "0" "/sys/class/kgsl/kgsl-3d0/throttling"
            write_node "100000" "/sys/class/kgsl/kgsl-3d0/idle_timer"
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
            # Performance: high sustained OPP range
            if grep -q "is enabled" /proc/gpufreq/gpufreq_fixed_freq_volt 2>/dev/null; then
                write_node "0 0" "/proc/gpufreq/gpufreq_fixed_freq_volt"
            fi
            write_node "1" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
            write_node "5" "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
            write_node "1" "/sys/kernel/ged/hal/gpu_boost_level"
            write_node "50" "/sys/kernel/ged/hal/dvfs_margin_value"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/min_pwrlevel"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost"
            write_node "60" "/sys/class/kgsl/kgsl-3d0/idle_timer"
            if [ -f "/proc/gpufreq/gpufreq_opp_dump" ]; then
                opp_line=$(head -n 1 /proc/gpufreq/gpufreq_opp_dump 2>/dev/null)
                peak_f=$(echo "$opp_line" | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
                if [ -n "$peak_f" ]; then
                    perf_floor=$(( peak_f * 85 / 100 ))
                    write_node "$peak_f" "/sys/module/ged/parameters/gpu_cust_upbound_freq"
                    write_node "$perf_floor" "/sys/module/ged/parameters/gpu_cust_boost_freq"
                    write_node "$perf_floor" "/sys/module/ged/parameters/gpu_bottom_freq"
                fi
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
            # Disable Mali DVFS — freq sudah dikunci via hardware PLL
            write_node "0" "/proc/mali/dvfs_enable"
            # Percepat CPU response untuk render thread (240Hz budget)
            write_node "4166666" "/sys/module/ged/parameters/target_t_cpu_remained"
            # Disable GPU aging (mengurangi voltase/freq saat panas)
            write_node "0" "/proc/gpufreq/gpufreq_aging_enable"
            # Route Mali IRQ ke big cores secara dinamis untuk latency rendah
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
                    *mali*|*gpu*|*g3d*)
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
            # Re-enable Mali DVFS untuk performance (managed DVFS)
            write_node "1" "/proc/mali/dvfs_enable"
            # Standard 120Hz CPU response time
            write_node "8333333" "/sys/module/ged/parameters/target_t_cpu_remained"
        fi
        write_node "1" "/sys/module/ged/parameters/gx_force_cpu_boost"
        write_node "0" "/proc/gpufreq/gpufreq_aging_enable"


        # MediaTek FPSGO — dimatikan di extreme (no frame budget cap), aktif di performance
        if [ "$PROFILE" = "extreme" ]; then
            # Matikan FPSGO sepenuhnya — tidak ada frame budget enforcement, GPU bisa maks
            write_node "0" "/sys/kernel/fpsgo/common/fpsgo_enable"
            # Thermal FPSGO throttle disable
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

        # Mali power policy — fix untuk sh (tidak bisa glob di write_node)
        for pp in /sys/devices/platform/13000000.mali/power_policy \
                  /sys/devices/platform/13040000.mali/power_policy \
                  /sys/devices/platform/mali.0/power_policy; do
            write_node "always_on" "$pp"
        done
        write_node "1" "/proc/mali/always_on"
        write_node "0" "/proc/mali/debug_log"


        # ── 4. UCLAMP & Top-App Process Clamping ──────────────────────────────
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

        # CPUSet & SchedTune Boost
        write_node "0-7" "/dev/cpuset/foreground/cpus"
        write_node "0-2" "/dev/cpuset/background/cpus"
        write_node "2-7" "/dev/cpuset/system-background/cpus"
        write_node "0-7" "/dev/cpuset/top-app/cpus"
        write_node "0" "/dev/cpuset/restricted/cpus"
        write_node "1" "/dev/stune/schedtune.sched_boost_enabled"
        write_node "5" "/dev/stune/schedtune.boost"
        write_node "0" "/dev/stune/schedtune.prefer_idle"
        write_node "5" "/dev/stune/foreground/schedtune.boost"
        write_node "$stune_boost" "/dev/stune/top-app/schedtune.boost"

        # ── 5. Virtual Memory (VM), CFS Low-Latency Scheduler, & I/O ─────────
        # Proactively flush pagecache and defrag memory to eliminate Direct Reclaim stutters
        sync
        write_node "3" "/proc/sys/vm/drop_caches"
        write_node "1" "/proc/sys/vm/compact_memory"

        if [ "$PROFILE" = "extreme" ]; then
            write_node "3000000" "/proc/sys/kernel/sched_latency_ns"
            write_node "500000" "/proc/sys/kernel/sched_min_granularity_ns"
            write_node "1000000" "/proc/sys/kernel/sched_wakeup_granularity_ns"
            write_node "50000" "/proc/sys/kernel/sched_migration_cost_ns"
            write_node "32" "/proc/sys/kernel/sched_nr_migrate"
            write_node "0" "/proc/sys/kernel/sched_schedstats"
            write_node "1" "/proc/sys/kernel/sched_child_runs_first"
            write_node "0" "/proc/sys/kernel/sched_cstate_aware"
            # Disable RT throttling sepenuhnya — game render thread tidak di-throttle
            write_node "-1" "/proc/sys/kernel/sched_rt_runtime_us"
            write_node "1000000" "/proc/sys/kernel/sched_rt_period_us"
            # Schedutil rate limit lebih cepat (500µs) — CPU clock naik lebih responsif
            for pol_dir in /sys/devices/system/cpu/cpu0/cpufreq/schedutil \
                           /sys/devices/system/cpu/cpu4/cpufreq/schedutil \
                           /sys/devices/system/cpu/cpu6/cpufreq/schedutil; do
                write_node "500" "$pol_dir/rate_limit_us"
            done
            write_node "10" "/proc/sys/vm/stat_interval"
            write_node "40" "/proc/sys/vm/vfs_cache_pressure"
            # Swappiness minimal — jangan geser data game ke swap
            write_node "1" "/proc/sys/vm/swappiness"
            write_node "200" "/proc/sys/vm/watermark_scale_factor"

        else
            write_node "6000000" "/proc/sys/kernel/sched_latency_ns"
            write_node "750000" "/proc/sys/kernel/sched_min_granularity_ns"
            write_node "1500000" "/proc/sys/kernel/sched_wakeup_granularity_ns"
            write_node "100000" "/proc/sys/kernel/sched_migration_cost_ns"
            write_node "16" "/proc/sys/kernel/sched_nr_migrate"
            write_node "0" "/proc/sys/kernel/sched_schedstats"
            write_node "1" "/proc/sys/kernel/sched_child_runs_first"
            write_node "0" "/proc/sys/kernel/sched_cstate_aware"
            write_node "980000" "/proc/sys/kernel/sched_rt_runtime_us"
            write_node "1000000" "/proc/sys/kernel/sched_rt_period_us"
            write_node "1" "/proc/sys/vm/stat_interval"
            write_node "60" "/proc/sys/vm/vfs_cache_pressure"
            write_node "70" "/proc/sys/vm/swappiness"
            write_node "150" "/proc/sys/vm/watermark_scale_factor"
        fi

        write_node "0" "/proc/sys/vm/watermark_boost_factor"
        if [ -e "/sys/kernel/mm/lru_gen/enabled" ]; then
            write_node "y" "/sys/kernel/mm/lru_gen/enabled"
            write_node "1000" "/sys/kernel/mm/lru_gen/min_ttl_ms"
        fi
        if [ "$PROFILE" = "extreme" ]; then
            write_node "3" "/proc/sys/vm/dirty_ratio"
            write_node "2" "/proc/sys/vm/dirty_background_ratio"
            write_node "100" "/proc/sys/vm/dirty_expire_centisecs"
            write_node "100" "/proc/sys/vm/dirty_writeback_centisecs"
            write_node "65536" "/proc/sys/vm/min_free_kbytes"
        else
            write_node "20" "/proc/sys/vm/dirty_ratio"
            write_node "10" "/proc/sys/vm/dirty_background_ratio"
            write_node "500" "/proc/sys/vm/dirty_expire_centisecs"
            write_node "200" "/proc/sys/vm/dirty_writeback_centisecs"
        fi
        write_node "100" "/proc/sys/vm/extfrag_threshold"
        write_node "0" "/proc/sys/vm/oom_dump_tasks"
        write_node "80" "/proc/sys/vm/overcommit_ratio"
        write_node "1" "/proc/sys/vm/compact_unevictable_allowed"
        write_node "40" "/proc/sys/kernel/perf_cpu_time_max_percent"
        write_node "1" "/proc/sys/kernel/sched_boost"
        write_node "512" "/proc/sys/kernel/random/read_wakeup_threshold"
        write_node "2048" "/proc/sys/kernel/random/write_wakeup_threshold"

        ra_val=1024
        [ "$PROFILE" = "extreme" ] && ra_val=2048
        for queue in /sys/block/sd[a-z]/queue /sys/block/mmcblk[0-9]/queue; do
            [ -d "$queue" ] || continue
            write_node "0" "$queue/add_random"
            write_node "0" "$queue/iostats"
            write_node "0" "$queue/nomerges"
            write_node "0" "$queue/rotational"
            write_node "2" "$queue/rq_affinity"
            write_node "512" "$queue/nr_requests"
        done
        for q in /sys/block/sd[a-z]/queue/scheduler /sys/block/mmcblk[0-9]/queue/scheduler; do
            [ -e "$q" ] && echo deadline > "$q" 2>/dev/null
        done
        for ra in /sys/block/sd[a-z]/queue/read_ahead_kb /sys/block/mmcblk[0-9]/queue/read_ahead_kb; do
            write_node "$ra_val" "$ra"
        done
        # Universal UFS Storage Controller Anti-Gating (Qualcomm & MediaTek)
        for ufs in /sys/devices/platform/soc/*ufshc* /sys/devices/platform/bootdevice /sys/devices/platform/*ufshc*; do
            [ -d "$ufs" ] || continue
            write_node "1000" "$ufs/clkgate_delay_ms"
            write_node "0" "$ufs/clkgate_delay_ms_perf"
            write_node "1000" "$ufs/clkgate_delay_ms_pwr_save"
            write_node "0" "$ufs/auto_hibern8_enable"
        done

        # ── 6. Display Refresh Rate & Touch Responsiveness ──────────────
        (
            # SF: extreme/performance mode = latch_unsignaled ON & fling boost
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

            # Clean any dangling experimental properties cleanly with resetprop
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

            # Vendor Touch & Gesture Fling Boost (safe frameworks)
            setprop vendor.perf.gestureFlingBoost.enable 1 2>/dev/null
            setprop vendor.perf.gestureflingboost.enable true 2>/dev/null
        ) >/dev/null 2>&1 &

        # Lock to true hardware peak display refresh rate (cached fast path)
        max_hw_rr=""
        if [ -f "/dev/lynx_orig_peak_rr" ]; then
            max_hw_rr=$(cat "/dev/lynx_orig_peak_rr" 2>/dev/null | tr -d '[:space:]')
        elif [ -f "/data/adb/lynx/max_hw_rr" ]; then
            max_hw_rr=$(cat "/data/adb/lynx/max_hw_rr" 2>/dev/null | tr -d '[:space:]')
        fi
        if [ -z "$max_hw_rr" ] || [ "$max_hw_rr" = "60.0" ] || [ "$max_hw_rr" = "60" ] || [ "$max_hw_rr" = "null" ]; then
            max_hw_rr=$(dumpsys display 2>/dev/null | grep -oE "fps=[0-9.]+" | cut -d'=' -f2 | sort -rn | head -n 1)
        fi
        if [ -z "$max_hw_rr" ] || [ "$max_hw_rr" = "null" ]; then
            max_hw_rr=$(settings get system peak_refresh_rate 2>/dev/null)
        fi
        case "$max_hw_rr" in
            144*|144) max_hw_rr="144.0" ;;
            120*|120) max_hw_rr="120.0" ;;
            90*|90)   max_hw_rr="90.0" ;;
            *)        max_hw_rr="${max_hw_rr:-120.0}" ;;
        esac
        [ -n "$max_hw_rr" ] && echo "$max_hw_rr" > /data/adb/lynx/max_hw_rr 2>/dev/null

        if [ ! -f "/dev/lynx_orig_min_rr" ]; then
            orig_min=$(settings get system min_refresh_rate 2>/dev/null)
            echo "${orig_min:-60.0}" > "/dev/lynx_orig_min_rr"
        fi
        [ "$(settings get system peak_refresh_rate 2>/dev/null)" != "$max_hw_rr" ] && settings put system peak_refresh_rate "$max_hw_rr" 2>/dev/null
        [ "$(settings get system min_refresh_rate 2>/dev/null)" != "$max_hw_rr" ] && settings put system min_refresh_rate "$max_hw_rr" 2>/dev/null

        for tn in /sys/class/touch/touch_dev/touch_game_mode \
                 /sys/devices/virtual/touch/touch_dev/bump_sample_rate \
                 /proc/touchscreen/game_mode \
                 /sys/devices/platform/goodix_ts.*/game_mode \
                 /sys/devices/platform/tp_wake_switch/game_mode; do
            [ -e "$tn" ] && write_node "1" "$tn"
        done

        # ── 7. Wi-Fi & TCP Network Gaming Stack ──────────────────────────────
        cmd wifi force-low-latency-mode enabled >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_slow_start_after_idle=0 >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_low_latency=1 >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_autocorking=0 >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_notsent_lowat=16384 >/dev/null 2>&1
        sysctl -w net.core.netdev_max_backlog=5000 >/dev/null 2>&1
        for ps_node in /sys/module/wlan/parameters/power_save /sys/module/bcmdhd/parameters/op_mode; do
            write_node "0" "$ps_node"
        done

        avail_tcp=$(cat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null)
        for target_algo in bbrv3 bbr westwood cubic; do
            case " $avail_tcp " in
                *" $target_algo "*)
                    sysctl -w net.ipv4.tcp_congestion_control="$target_algo" >/dev/null 2>&1
                    break
                    ;;
            esac
        done

        # ── 8. Audio Pipeline Acceleration ───────────────────────────────────
        (
            setprop af.fast_track_multiplier 1
            setprop aaudio.hw_burst_min_usec ""
            if is_bluetooth_audio; then
                setprop aaudio.mmap_policy 2
            else
                setprop aaudio.mmap_policy 2
                setprop aaudio.mmap_exclusive_policy 2
            fi
        ) >/dev/null 2>&1 &

        # ── 9. Critical Process Priority Renicing & Game Thread Pacing ───────
        if [ -f "/data/adb/modules/Lynx/core/lib/game_pacing.sh" ]; then
            . "/data/adb/modules/Lynx/core/lib/game_pacing.sh"
            apply_render_pipeline_priority
            [ -n "$TARGET_APP" ] && optimize_game_process "$TARGET_APP"
        elif [ -f "/data/adb/lynx/game_pacing.sh" ]; then
            . "/data/adb/lynx/game_pacing.sh"
            apply_render_pipeline_priority
            [ -n "$TARGET_APP" ] && optimize_game_process "$TARGET_APP"
        else
            pids=$(pidof surfaceflinger android.hardware.graphics.composer vendor.qti.hardware.display.composer vendor.mediatek.hardware.pq android.hardware.graphics.allocator@4.0-service-mediatek 2>/dev/null)
            if [ -n "$pids" ]; then
                renice -n -20 -p $pids 2>/dev/null
                for pid in $pids; do
                    write_node "$pid" "/dev/cpuset/top-app/cgroup.procs"
                done
            fi
        fi

        # Route GPU Interrupts to Big Cores (Dynamic Topology)
        if [ -z "$gpu_irq_mask" ]; then
            big_mask=0; max_cap=0
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
        fi
        for irq in $(grep -iE "mali|ged|kgsl|adreno" /proc/interrupts 2>/dev/null | awk '{print $1}' | tr -d ':'); do
            write_node "$gpu_irq_mask" "/proc/irq/$irq/smp_affinity" 2>/dev/null
        done

        # ── 10. Kernel Game Library Prioritization & Sched Features ─────────
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

        # ── 11. OEM Throttler Neutralizer (Freeze with SIGSTOP) ──────────────
        oem_pids=$(pidof $OEM_TARGET_PROCS 2>/dev/null)
        [ -n "$oem_pids" ] && kill -STOP $oem_pids 2>/dev/null
        if [ -d "/data/data/com.xiaomi.joyose" ]; then
            for jpid in $(pgrep -f "com.xiaomi.joyose" 2>/dev/null); do
                kill -STOP "$jpid" 2>/dev/null
            done
        fi

        # ── 12. Thermal Policy & Unity Trick ─────────────────────────────────
        if [ "$PROFILE" = "extreme" ]; then
            write_node "0" "/proc/cpufreq/cpufreq_imax_thermal_protect"
            cmd thermalservice override-status 0 2>/dev/null
            for tz in /sys/class/thermal/thermal_zone*; do
                [ -d "$tz" ] || continue
                write_node "disabled" "$tz/mode"
                write_node "150000" "$tz/trip_point_0_temp"
            done

            # Ensure CPU capabilities and topology are always readable by Game Engines (Unity/Unreal) and EAS
            for path in /sys/devices/system/cpu/cpu[0-9]*; do
                [ -d "$path" ] || continue
                [ -e "$path/cpufreq/cpuinfo_max_freq" ] && chmod 444 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
                [ -e "$path/cpu_capacity" ] && chmod 444 "$path/cpu_capacity" 2>/dev/null
                [ -e "$path/topology/physical_package_id" ] && chmod 444 "$path/topology/physical_package_id" 2>/dev/null
            done

            # MTK CLATM GPU power budget bypass — naikkan threshold agar 950MHz tidak didowngrade
            write_node "150 149" "/proc/driver/thermal/clatm_gpu_threshold"
            # Disable GPU aging — mencegah freq/volt dikurangi saat panas
            write_node "0" "/proc/gpufreq/gpufreq_aging_enable"
            # Mali DVFS params — threshold tinggi agar tidak downscale
            [ -e "/proc/mali/dvfs_threshold" ] && echo "99 20" > /proc/mali/dvfs_threshold 2>/dev/null
            [ -e "/proc/mali/dvfs_deferred_count" ] && echo "1" > /proc/mali/dvfs_deferred_count 2>/dev/null

            # ── Extreme Charging & Bypass Alignment ─────────────────────────────
            # Memastikan saat bermain game di mode Extreme dengan charger terpasang,
            # baterai TIDAK DROP. Jika bypass aktif: latch baterai & suplai via Vsys.
            # Jika bypass nonaktif: buka arus maksimal (Pump Express & 6A headroom).
            cfg_bypass="false"
            for c_path in "/data/adb/modules/Lynx/config.json" "/data/adb/lynx/config.json" "/data/user/0/com.noir.lynx/files/config.json"; do
                if [ -f "$c_path" ]; then
                    cfg_bypass=$(awk -F': ' '/"bypass_enabled"/ {print $2}' "$c_path" 2>/dev/null | grep -q "true" && echo "true" || echo "false")
                    break
                fi
            done

            if [ "$cfg_bypass" = "true" ]; then
                # True Hardware Bypass: input daya charger tetap hidup untuk menyuplai motherboard,
                # tetapi pengisian sel baterai di-latch agar persentase tidak naik/turun dan tetap dingin.
                write_node "0" "/sys/class/power_supply/battery/input_suspend"
                write_node "4294967295" "/sys/devices/platform/charger/input_current"
                write_node "4500000" "/sys/class/power_supply/usb/current_max"
                write_node "4500000" "/sys/class/power_supply/main/current_max"
                write_node "1" "/sys/devices/platform/charger/bypass_charger"
                write_node "1" "/sys/class/power_supply/battery/device/smart_charging"
                write_node "1" "/sys/class/power_supply/battery/smart_charging_activation"
                write_node "1" "/sys/class/qcom-battery/direct_charging"
                write_node "1" "/sys/class/power_supply/battery/store_mode"
                write_node "1" "/sys/class/power_supply/battery/batt_slate_mode"
                cur_cap=$(cat /sys/class/power_supply/battery/capacity 2>/dev/null)
                [ -n "$cur_cap" ] && write_node "1" "/sys/devices/platform/charger/enable_sc" && write_node "$cur_cap" "/sys/devices/platform/charger/sc_tuisoc"
                write_node "0" "/sys/devices/platform/charger/sc_ibat_limit"
                write_node "0" "/sys/devices/platform/charger/chg1_current"
                write_node "0" "/sys/devices/platform/charger/chg2_current"
                write_node "0" "/sys/class/power_supply/battery/constant_charge_current"
                write_node "0" "/sys/class/power_supply/battery/constant_charge_current_max"
                write_node "0" "/sys/class/power_supply/battery/charging_enabled"
            else
                # Extreme Charging: Bebaskan batasan arus, Pump Express, dan disable JEITA
                write_node "0" "/sys/devices/platform/charger/sw_jeita"
                write_node "2" "/sys/devices/platform/charger/Pump_Express"
                write_node "1" "/sys/devices/platform/charger/pe20"
                write_node "1" "/sys/devices/platform/charger/pe40"
                write_node "68" "/sys/devices/platform/charger/pdc_max_watt"
                write_node "4294967295" "/sys/devices/platform/charger/input_current"
                write_node "4294967295" "/sys/devices/platform/charger/chg1_current"
                write_node "4294967295" "/sys/devices/platform/charger/chg2_current"
                write_node "6000" "/sys/devices/platform/charger/sc_ibat_limit"
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
            # Performance Mode: Safe Relaxed Thermal Bounds (85°C trip point)
            write_node "1" "/proc/cpufreq/cpufreq_imax_thermal_protect"
            cmd thermalservice reset 2>/dev/null
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

        # MediaTek DVFSRC / Interconnect & DDR RAM Clock Lock (4.266 GHz peak memory bandwidth)
        if [ "$PROFILE" = "extreme" ]; then
            for dvfsrc_node in /sys/devices/platform/*dvfsrc*/helio-dvfsrc/dvfsrc_force_vcore_dvfs_opp; do
                write_node "0" "$dvfsrc_node"
            done
            for ddr_node in /sys/devices/platform/*dvfsrc*/helio-dvfsrc/dvfsrc_req_ddr_opp; do
                write_node "0" "$ddr_node"
            done
            # Mali G77 Job Scheduling Period & DVFS Period (4x faster dispatch)
            for m_dir in /sys/devices/platform/13000000.mali /sys/devices/platform/13040000.mali /sys/devices/platform/mali.0; do
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
            for m_dir in /sys/devices/platform/13000000.mali /sys/devices/platform/13040000.mali /sys/devices/platform/mali.0; do
                [ -d "$m_dir" ] || continue
                write_node "50" "$m_dir/js_scheduling_period"
                write_node "30" "$m_dir/dvfs_period"
            done
        fi

        # Final hardware interlock: ensure MediaTek interconnect is in Perf mode after thermal/HAL callbacks
        write_node "1" "/proc/cpufreq/cpufreq_cci_mode"
        for p in /sys/devices/system/cpu/cpufreq/policy*; do
            [ -d "$p" ] || continue
            pol_num=$(basename "$p" | tr -dc '0-9')
            eval "sf=\$saved_floor_${pol_num}"
            [ -n "$sf" ] && write_node "$sf" "$p/scaling_min_freq"
        done

        setprop lynx.mode "$PROFILE"
        ;;

    powersave)
        # ── 1. CPU Schedutil & Low Frequency Cap (55% Max) ───────────────────
        for p in /sys/devices/system/cpu/cpufreq/policy*; do
            [ -d "$p" ] || continue
            write_node "schedutil" "$p/scaling_governor"
            write_node "20000" "$p/schedutil/up_rate_limit_us"
            write_node "500" "$p/schedutil/down_rate_limit_us"
            write_node "99" "$p/schedutil/hispeed_load"
            write_node "0" "$p/schedutil/iowait_boost_enable"
            write_node "0" "$p/schedutil/pl"

            max_freq=$(cat "$p/cpuinfo_max_freq" 2>/dev/null)
            min_freq=$(cat "$p/cpuinfo_min_freq" 2>/dev/null)
            avail_f=$(cat "$p/scaling_available_frequencies" 2>/dev/null)
            if [ -z "$max_freq" ] || [ -z "$min_freq" ]; then
                sorted_f=$(echo "$avail_f" | tr -s ' ' '\n' | sort -n)
                [ -z "$min_freq" ] && min_freq=$(echo "$sorted_f" | head -n 1)
                [ -z "$max_freq" ] && max_freq=$(echo "$sorted_f" | tail -n 1)
            fi
            if [ -n "$max_freq" ] && [ -n "$min_freq" ]; then
                if [ "$max_freq" -lt "$min_freq" ] 2>/dev/null; then
                    tmp="$max_freq"
                    max_freq="$min_freq"
                    min_freq="$tmp"
                fi
            fi
            [ -n "$min_freq" ] && write_node "$min_freq" "$p/scaling_min_freq"
            if [ -n "$max_freq" ]; then
                p_cap=$(( max_freq * 55 / 100 ))
                [ -n "$min_freq" ] && [ "$p_cap" -gt "$min_freq" ] && write_node "$p_cap" "$p/scaling_max_freq"
            fi
        done

        # Core Control: allow power saving core sleeping
        for np in /sys/devices/system/cpu/cpu*/core_ctl/not_preferred; do
            [ -f "$np" ] && write_node "0 0 0 0" "$np"
        done
        for ctl in /sys/devices/system/cpu/cpu*/core_ctl; do
            [ -d "$ctl" ] || continue
            write_node "1" "$ctl/min_cpus"
            write_node "60" "$ctl/busy_up_thres"
            write_node "20" "$ctl/busy_down_thres"
        done
        write_node "0" "/sys/module/cpu_boost/parameters/sched_boost_on_input"

        # Respect user preferred architecture or preserve Hybrid (2) / EAS (1)
        if [ -f "/data/adb/lynx/preferred_architecture" ]; then
            pref_arch=$(cat /data/adb/lynx/preferred_architecture 2>/dev/null | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')
            if [ "$pref_arch" = "hybrid" ]; then
                write_node "2" "/sys/devices/system/cpu/eas/enable"
            elif [ "$pref_arch" = "eas" ]; then
                write_node "1" "/sys/devices/system/cpu/eas/enable"
            elif [ "$pref_arch" = "hmp" ]; then
                write_node "0" "/sys/devices/system/cpu/eas/enable"
            fi
        elif [ -f "/sys/devices/system/cpu/eas/enable" ]; then
            cur_eas=$(cat /sys/devices/system/cpu/eas/enable 2>/dev/null | tr '[:upper:]' '[:lower:]')
            if [[ "$cur_eas" == *"hybrid"* ]] || [ "$cur_eas" = "2" ]; then
                write_node "2" "/sys/devices/system/cpu/eas/enable"
            else
                write_node "1" "/sys/devices/system/cpu/eas/enable"
            fi
        fi
        write_node "0" "/sys/devices/system/cpu/perf/enable"

        # MediaTek CPU Low Power Mode
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
        for m_dir in /sys/devices/platform/13000000.mali /sys/devices/platform/13040000.mali /sys/devices/platform/mali.0; do
            [ -d "$m_dir" ] || continue
            write_node "100" "$m_dir/js_scheduling_period"
            write_node "100" "$m_dir/dvfs_period"
        done

        # Reset MediaTek EAS perfmgr
        write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost"
        write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost"
        write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_uclamp_min"
        write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_uclamp_min"
        write_node "0" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_uclamp_min"
        write_node "1" "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_prefer_idle"

        # MediaTek PPM All Active
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

        # Cap MediaTek PPM DVFS Cluster Table to 50% OPP Index
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

        # Qualcomm Devfreq Bus Powersave
        for dev in /sys/class/devfreq/*; do
            [ -d "$dev" ] || continue
            case "$dev" in
                *ufshc*|*cpubw*|*gpubw*|*llccbw*|*l3-cpu*|*bus_ddr*)
                    write_node "powersave" "$dev/governor"
                    ;;
            esac
        done

        # ── 3. GPU Coarse Demand & Power Down ────────────────────────────────
        write_node "48" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
        write_node "0" "/sys/kernel/ged/hal/gpu_boost_level"
        write_node "0" "/sys/kernel/ged/hal/dvfs_margin_value"
        write_node "0" "/sys/module/ged/parameters/boost_gpu_enable"
        write_node "0" "/sys/module/ged/parameters/ged_smart_boost"
        write_node "0" "/sys/module/ged/parameters/enable_gpu_boost"
        write_node "0" "/sys/module/ged/parameters/enable_cpu_boost"
        write_node "0" "/sys/module/ged/parameters/gx_game_mode"
        write_node "0" "/sys/module/ged/parameters/gx_boost_on"
        write_node "0" "/sys/kernel/fpsgo/common/gpu_block_boost"
        write_node "coarse_demand" "/sys/devices/platform/*mali*/power_policy"
        write_node "0" "/proc/mali/always_on"
        write_node "1" "/proc/mali/dvfs_enable"
        write_node "0" "/proc/gpufreq/gpufreq_opp_freq"
        write_node "0 0" "/proc/gpufreq/gpufreq_fixed_freq_volt"
        write_node "2" "/sys/module/ged/parameters/gpu_idle"
        for i in 0 1 2 3 4 5 6 7 8; do
            write_node "$i 1 1" "/proc/gpufreq/gpufreq_limit_table"
        done
        num_pwr=$(cat "/sys/class/kgsl/kgsl-3d0/num_pwrlevels" 2>/dev/null)
        [ -n "$num_pwr" ] && [ "$num_pwr" -gt 1 ] && write_node "$((num_pwr - 1))" "/sys/class/kgsl/kgsl-3d0/min_pwrlevel"
        write_node "0" "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost"
        write_node "1" "/sys/class/kgsl/kgsl-3d0/throttling"
        write_node "20" "/sys/class/kgsl/kgsl-3d0/idle_timer"

        # ── 4. UCLAMP, CPUSet & Memory Subsystem ─────────────────────────────
        for u_node in "/dev/cpuset/top-app/cpu.uclamp.min" "/proc/sys/kernel/sched_util_clamp_min"; do
            write_node "0" "$u_node"
        done
        write_node "0" "/dev/cpuset/top-app/cpu.uclamp.latency_sensitive"
        write_node "0" "/dev/cpuset/foreground/boost/cpu.uclamp.latency_sensitive"

        write_node "0-3" "/dev/cpuset/background/cpus"
        write_node "0-3" "/dev/cpuset/system-background/cpus"
        write_node "0-3" "/dev/cpuset/restricted/cpus"
        write_node "10" "/proc/sys/vm/dirty_ratio"
        write_node "5" "/proc/sys/vm/dirty_background_ratio"
        write_node "100" "/proc/sys/vm/swappiness"
        write_node "50" "/proc/sys/vm/vfs_cache_pressure"
        write_node "10" "/proc/sys/vm/watermark_scale_factor"
        write_node "950000" "/proc/sys/kernel/sched_rt_runtime_us"
        for queue in /sys/block/sd[a-z]/queue /sys/block/mmcblk[0-9]/queue; do
            [ -d "$queue" ] || continue
            write_node "1" "$queue/rq_affinity"
        done
        for q in /sys/block/sd[a-z]/queue/scheduler /sys/block/mmcblk[0-9]/queue/scheduler; do
            [ -e "$q" ] && echo noop > "$q" 2>/dev/null
        done
        for ra in /sys/block/sd[a-z]/queue/read_ahead_kb /sys/block/mmcblk[0-9]/queue/read_ahead_kb; do
            write_node "64" "$ra"
        done
        for ufs in /sys/devices/platform/soc/*ufshc* /sys/devices/platform/bootdevice /sys/devices/platform/*ufshc*; do
            [ -d "$ufs" ] || continue
            write_node "15" "$ufs/clkgate_delay_ms"
        done

        # ── 5. Unfreeze Throttlers, Enforce 60Hz Screen, Network & Thermal ───
        oem_pids=$(pidof $OEM_TARGET_PROCS 2>/dev/null)
        [ -n "$oem_pids" ] && kill -CONT $oem_pids 2>/dev/null
        if [ -d "/data/data/com.xiaomi.joyose" ]; then
            for jpid in $(pgrep -f "com.xiaomi.joyose" 2>/dev/null); do
                kill -CONT "$jpid" 2>/dev/null
            done
        fi
        cmd wifi set-power-save-mode 1 >/dev/null 2>&1
        cmd wifi force-low-latency-mode disabled >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_slow_start_after_idle=1 >/dev/null 2>&1
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

        # Enforce 60Hz Display Refresh Rate for Battery Endurance
        cur_min_rr=$(settings get system min_refresh_rate 2>/dev/null)
        cur_peak_rr=$(settings get system peak_refresh_rate 2>/dev/null)
        if [ ! -f "/dev/lynx_orig_min_rr" ] && [ -n "$cur_min_rr" ] && [ "$cur_min_rr" != "null" ]; then
            echo "$cur_min_rr" > /dev/lynx_orig_min_rr
        fi
        if [ ! -f "/dev/lynx_orig_peak_rr" ] && [ -n "$cur_peak_rr" ] && [ "$cur_peak_rr" != "null" ]; then
            echo "$cur_peak_rr" > /dev/lynx_orig_peak_rr
        fi
        [ "$cur_min_rr" != "60.0" ] && settings put system min_refresh_rate 60.0 2>/dev/null
        [ "$cur_peak_rr" != "60.0" ] && settings put system peak_refresh_rate 60.0 2>/dev/null

        # Reset Touch Boost Nodes
        for tn in /sys/class/touch/touch_dev/touch_game_mode \
                 /sys/devices/virtual/touch/touch_dev/bump_sample_rate \
                 /proc/touchscreen/game_mode \
                 /sys/devices/platform/goodix_ts.*/game_mode \
                 /sys/devices/platform/tp_wake_switch/game_mode; do
            [ -e "$tn" ] && write_node "0" "$tn"
        done

        # Full Thermal Protection Active
        write_node "1" "/proc/cpufreq/cpufreq_imax_thermal_protect"
        cmd thermalservice reset 2>/dev/null
        for tz in /sys/class/thermal/thermal_zone*; do
            [ -d "$tz" ] || continue
            write_node "enabled" "$tz/mode"
            write_node "80000" "$tz/trip_point_0_temp"
        done

        # Restore permissions if Unity trick was previously applied
        for path in /sys/devices/system/cpu/cpu[0-9]*; do
            [ -d "$path" ] || continue
            [ -e "$path/cpufreq/cpuinfo_max_freq" ] && chmod 444 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
            [ -e "$path/cpu_capacity" ] && chmod 444 "$path/cpu_capacity" 2>/dev/null
            [ -e "$path/topology/physical_package_id" ] && chmod 444 "$path/topology/physical_package_id" 2>/dev/null
        done

        setprop lynx.mode powersave
        ;;

    balance|auto|*)
        # ── 1. CPU Schedutil & Uncapped Frequencies (Full Idle Downclock) ────
        for p in /sys/devices/system/cpu/cpufreq/policy*; do
            [ -d "$p" ] || continue
            write_node "schedutil" "$p/scaling_governor"
            write_node "0" "$p/schedutil/up_rate_limit_us"
            write_node "15000" "$p/schedutil/down_rate_limit_us"
            write_node "80" "$p/schedutil/hispeed_load"
            write_node "1" "$p/schedutil/iowait_boost_enable"
            write_node "1" "$p/schedutil/pl"

            max_freq=$(cat "$p/cpuinfo_max_freq" 2>/dev/null)
            min_freq=$(cat "$p/cpuinfo_min_freq" 2>/dev/null)
            avail_f=$(cat "$p/scaling_available_frequencies" 2>/dev/null)
            if [ -z "$max_freq" ] || [ -z "$min_freq" ]; then
                sorted_f=$(echo "$avail_f" | tr -s ' ' '\n' | sort -n)
                [ -z "$min_freq" ] && min_freq=$(echo "$sorted_f" | head -n 1)
                [ -z "$max_freq" ] && max_freq=$(echo "$sorted_f" | tail -n 1)
            fi
            if [ -n "$max_freq" ] && [ -n "$min_freq" ]; then
                if [ "$max_freq" -lt "$min_freq" ] 2>/dev/null; then
                    tmp="$max_freq"
                    max_freq="$min_freq"
                    min_freq="$tmp"
                fi
            fi
            [ -n "$min_freq" ] && write_node "$min_freq" "$p/scaling_min_freq"
            [ -n "$max_freq" ] && write_node "$max_freq" "$p/scaling_max_freq"
            if [ -n "$max_freq" ] && [ "$max_freq" -gt 0 ] 2>/dev/null; then
                hi_f=$(( max_freq * 75 / 100 ))
                write_node "$hi_f" "$p/schedutil/hispeed_freq"
            fi
        done

        # Core Control
        for np in /sys/devices/system/cpu/cpu*/core_ctl/not_preferred; do
            [ -f "$np" ] && write_node "0 0 0 0" "$np"
        done
        for ctl in /sys/devices/system/cpu/cpu*/core_ctl; do
            [ -d "$ctl" ] || continue
            write_node "1" "$ctl/min_cpus"
            write_node "40" "$ctl/busy_up_thres"
            write_node "40" "$ctl/busy_down_thres"
        done
        write_node "0" "/sys/module/cpu_boost/parameters/sched_boost_on_input"

        # Respect user preferred architecture or preserve Hybrid (2) / EAS (1)
        if [ -f "/data/adb/lynx/preferred_architecture" ]; then
            pref_arch=$(cat /data/adb/lynx/preferred_architecture 2>/dev/null | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')
            if [ "$pref_arch" = "hybrid" ]; then
                write_node "2" "/sys/devices/system/cpu/eas/enable"
            elif [ "$pref_arch" = "eas" ]; then
                write_node "1" "/sys/devices/system/cpu/eas/enable"
            elif [ "$pref_arch" = "hmp" ]; then
                write_node "0" "/sys/devices/system/cpu/eas/enable"
            fi
        elif [ -f "/sys/devices/system/cpu/eas/enable" ]; then
            cur_eas=$(cat /sys/devices/system/cpu/eas/enable 2>/dev/null | tr '[:upper:]' '[:lower:]')
            if [[ "$cur_eas" == *"hybrid"* ]] || [ "$cur_eas" = "2" ]; then
                write_node "2" "/sys/devices/system/cpu/eas/enable"
            else
                write_node "1" "/sys/devices/system/cpu/eas/enable"
            fi
        fi
        write_node "1" "/sys/devices/system/cpu/perf/enable"
        write_node "1" "/proc/sys/kernel/sched_autogroup_enabled"
        write_node "0" "/proc/sys/kernel/sched_tunable_scaling"

        # MediaTek CPU Balanced Mode
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
        for m_dir in /sys/devices/platform/13000000.mali /sys/devices/platform/13040000.mali /sys/devices/platform/mali.0; do
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

        # MediaTek PPM Balanced
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

        # Restore MTK DVFS cluster bounds
        for c in 0 1 2; do
            dvfs_table="/proc/ppm/dump_cluster_${c}_dvfs_table"
            if [ -f "$dvfs_table" ]; then
                c_max=$(awk '{print $1}' "$dvfs_table" 2>/dev/null | head -n 1)
                c_min=$(tail -n 1 "$dvfs_table" 2>/dev/null | awk '{print $NF}')
                [ -n "$c_max" ] && write_node "$c $c_max" "/proc/ppm/policy/hard_userlimit_max_cpu_freq"
                [ -n "$c_min" ] && write_node "$c $c_min" "/proc/ppm/policy/hard_userlimit_min_cpu_freq"
            fi
        done

        # Qualcomm Devfreq Memory Bus Dynamic
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
                l_freq=$(tr -s ' ' '\n' < "$freq_table" 2>/dev/null | sort -n | head -n 1)
                [ -n "$l_freq" ] && write_node "$l_freq" "$dev/min_freq"
            fi
        done

        # ── 3. GPU Dynamic & Responsive ──────────────────────────────────────
        write_node "36" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
        write_node "0" "/sys/kernel/ged/hal/gpu_boost_level"
        write_node "10" "/sys/kernel/ged/hal/dvfs_margin_value"
        write_node "1" "/sys/module/ged/parameters/boost_gpu_enable"
        write_node "1" "/sys/module/ged/parameters/ged_smart_boost"
        write_node "0" "/sys/module/ged/parameters/gx_game_mode"
        write_node "0" "/sys/module/ged/parameters/gx_boost_on"
        write_node "1" "/sys/kernel/fpsgo/common/gpu_block_boost"
        write_node "always_on" "/sys/devices/platform/*mali*/power_policy"
        write_node "1" "/proc/mali/always_on"
        write_node "1" "/proc/mali/dvfs_enable"
        write_node "0" "/proc/gpufreq/gpufreq_opp_freq"
        write_node "0 0" "/proc/gpufreq/gpufreq_fixed_freq_volt"
        write_node "1" "/sys/kernel/fpsgo/fbt/switch_idleprefer"
        write_node "1" "/sys/kernel/fpsgo/fbt/enable_switch_down_throttle"

        cur_peak_rr=$(settings get system peak_refresh_rate 2>/dev/null)
        case "$cur_peak_rr" in
            144*|144|120*|120|90*|90)
                write_node "8333333" "/sys/module/ged/parameters/target_t_cpu_remained"
                ;;
            *)
                write_node "16666666" "/sys/module/ged/parameters/target_t_cpu_remained"
                ;;
        esac

        for i in 0 1 2 3 4 5 6 7 8; do
            write_node "$i 1 1" "/proc/gpufreq/gpufreq_limit_table"
        done

        # Qualcomm Adreno KGSL
        write_node "1" "/sys/class/kgsl/kgsl-3d0/throttling"
        write_node "0" "/sys/class/kgsl/kgsl-3d0/force_bus_on"
        write_node "0" "/sys/class/kgsl/kgsl-3d0/force_clk_on"
        write_node "0" "/sys/class/kgsl/kgsl-3d0/force_rail_on"
        write_node "80" "/sys/class/kgsl/kgsl-3d0/idle_timer"
        num_pwr=$(cat "/sys/class/kgsl/kgsl-3d0/num_pwrlevels" 2>/dev/null)
        [ -n "$num_pwr" ] && [ "$num_pwr" -gt 1 ] && write_node "$((num_pwr - 1))" "/sys/class/kgsl/kgsl-3d0/min_pwrlevel"
        write_node "0" "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost"

        # ── 4. UCLAMP, CPUSet & Memory Subsystem ─────────────────────────────
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
        write_node "0-2" "/dev/cpuset/background/cpus"
        write_node "0-5" "/dev/cpuset/system-background/cpus"
        write_node "0-7" "/dev/cpuset/top-app/cpus"
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
        write_node "950000" "/proc/sys/kernel/sched_rt_runtime_us"
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
        for ufs in /sys/devices/platform/soc/*ufshc* /sys/devices/platform/bootdevice /sys/devices/platform/*ufshc*; do
            [ -d "$ufs" ] || continue
            write_node "15" "$ufs/clkgate_delay_ms"
        done

        # High-Responsiveness Scheduler & VM Balanced Tunables
        write_node "5000000" "/proc/sys/kernel/sched_latency_ns"
        write_node "1000000" "/proc/sys/kernel/sched_min_granularity_ns"
        write_node "800000" "/proc/sys/kernel/sched_wakeup_granularity_ns"
        write_node "200000" "/proc/sys/kernel/sched_migration_cost_ns"
        write_node "32" "/proc/sys/kernel/sched_nr_migrate"
        write_node "0" "/proc/sys/kernel/sched_schedstats"
        write_node "0" "/proc/sys/kernel/sched_child_runs_first"
        write_node "1" "/proc/sys/kernel/sched_cstate_aware"
        write_node "1" "/proc/sys/vm/stat_interval"
        write_node "60" "/proc/sys/vm/vfs_cache_pressure"

        # Restore FPSGO & GED Parameters
        write_node "1" "/sys/module/ged/parameters/gpu_idle"
        write_node "0" "/sys/module/ged/parameters/gx_top_app_pid"
        write_node "0" "/sys/module/ged/parameters/ged_force_mdp_enable"
        write_node "0" "/sys/kernel/fpsgo/fbt/ultra_rescue"
        write_node "1" "/sys/kernel/fpsgo/fstb/fstb_soft_level"
        write_node "1" "/sys/kernel/fpsgo/fbt/light_loading_policy"
        write_node "1" "/sys/kernel/fpsgo/fbt/light_loading_policy_90"

        # ── 5. Unfreeze Throttlers, Restore Display, Network & Thermal ────────
        oem_pids=$(pidof $OEM_TARGET_PROCS 2>/dev/null)
        [ -n "$oem_pids" ] && kill -CONT $oem_pids 2>/dev/null
        if [ -d "/data/data/com.xiaomi.joyose" ]; then
            for jpid in $(pgrep -f "com.xiaomi.joyose" 2>/dev/null); do
                kill -CONT "$jpid" 2>/dev/null
            done
        fi
        cmd wifi set-power-save-mode 1 >/dev/null 2>&1
        cmd wifi force-low-latency-mode disabled >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_slow_start_after_idle=1 >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_low_latency=0 >/dev/null 2>&1

        # SurfaceFlinger Low-Latency Frame Latching for butter-smooth scrolling
        setprop debug.sf.latch_unsignaled 1 2>/dev/null
        setprop vendor.perf.gestureFlingBoost.enable 1 2>/dev/null
        setprop vendor.perf.gestureflingboost.enable true 2>/dev/null

        # Clean broken empty-string overrides using resetprop if available
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

        # Restore Display Refresh Rates (min 60.0, peak panel max cached)
        max_hw_rr=""
        if [ -f "/dev/lynx_orig_peak_rr" ]; then
            max_hw_rr=$(cat "/dev/lynx_orig_peak_rr" 2>/dev/null | tr -d '[:space:]')
            rm -f "/dev/lynx_orig_peak_rr" 2>/dev/null
        elif [ -f "/data/adb/lynx/max_hw_rr" ]; then
            max_hw_rr=$(cat "/data/adb/lynx/max_hw_rr" 2>/dev/null | tr -d '[:space:]')
        fi
        if [ -z "$max_hw_rr" ] || [ "$max_hw_rr" = "60.0" ] || [ "$max_hw_rr" = "60" ] || [ "$max_hw_rr" = "null" ]; then
            max_hw_rr=$(dumpsys display 2>/dev/null | grep -oE "fps=[0-9.]+" | cut -d'=' -f2 | sort -rn | head -n 1)
        fi
        case "$max_hw_rr" in
            144*|144) max_hw_rr="144.0" ;;
            120*|120) max_hw_rr="120.0" ;;
            90*|90)   max_hw_rr="90.0" ;;
            *)        max_hw_rr="${max_hw_rr:-120.0}" ;;
        esac
        [ -n "$max_hw_rr" ] && echo "$max_hw_rr" > /data/adb/lynx/max_hw_rr 2>/dev/null

        orig_min="60.0"
        if [ -f "/dev/lynx_orig_min_rr" ]; then
            saved_min=$(cat "/dev/lynx_orig_min_rr" 2>/dev/null | tr -d '[:space:]')
            rm -f "/dev/lynx_orig_min_rr" 2>/dev/null
            if [ -n "$saved_min" ] && [ "$saved_min" != "$max_hw_rr" ] && [ "$saved_min" != "null" ]; then
                orig_min="$saved_min"
            fi
        fi
        [ "$(settings get system min_refresh_rate 2>/dev/null)" != "$orig_min" ] && settings put system min_refresh_rate "$orig_min" 2>/dev/null
        [ "$(settings get system peak_refresh_rate 2>/dev/null)" != "$max_hw_rr" ] && settings put system peak_refresh_rate "$max_hw_rr" 2>/dev/null

        # Reset Touch Boost Nodes
        for tn in /sys/class/touch/touch_dev/touch_game_mode \
                 /sys/devices/virtual/touch/touch_dev/bump_sample_rate \
                 /proc/touchscreen/game_mode \
                 /sys/devices/platform/goodix_ts.*/game_mode \
                 /sys/devices/platform/tp_wake_switch/game_mode; do
            [ -e "$tn" ] && write_node "0" "$tn"
        done

        # Restore Thermal Protection
        write_node "1" "/proc/cpufreq/cpufreq_imax_thermal_protect"
        cmd thermalservice reset 2>/dev/null
        for tz in /sys/class/thermal/thermal_zone*; do
            [ -d "$tz" ] || continue
            write_node "enabled" "$tz/mode"
            write_node "80000" "$tz/trip_point_0_temp"
        done

        # Restore permissions if Unity trick was applied
        for path in /sys/devices/system/cpu/cpu[0-9]*; do
            [ -d "$path" ] || continue
            [ -e "$path/cpufreq/cpuinfo_max_freq" ] && chmod 444 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
            [ -e "$path/cpu_capacity" ] && chmod 444 "$path/cpu_capacity" 2>/dev/null
            [ -e "$path/topology/physical_package_id" ] && chmod 444 "$path/topology/physical_package_id" 2>/dev/null
        done

        if [ "$PROFILE" = "auto" ]; then
            setprop lynx.mode auto
        else
            setprop lynx.mode balance
        fi
        ;;
esac

# ── Verification & Granular Debugging Audit Dispatch ────────────────────────
DO_VERIFY=false
case "$2" in
    --verify|-v|verify) DO_VERIFY=true ;;
esac

VERIFY_SCRIPT=""
if [ -f "/data/adb/modules/Lynx/core/lib/verify_profile.sh" ]; then
    VERIFY_SCRIPT="/data/adb/modules/Lynx/core/lib/verify_profile.sh"
elif [ -f "/data/adb/lynx/verify_profile.sh" ]; then
    VERIFY_SCRIPT="/data/adb/lynx/verify_profile.sh"
fi

if [ -n "$VERIFY_SCRIPT" ] && [ "$DO_VERIFY" = "true" ]; then
    sh "$VERIFY_SCRIPT" "$PROFILE"
fi
