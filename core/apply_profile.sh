#!/system/bin/sh
# ==============================================================================
# Lynx Universal - Master Hardware Profile Applicator v3.0.4
# Complete, audited kernel & hardware tuning for Extreme, Performance, Balance, Powersave.
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash).
# Zero hardcoding, multi-platform (MediaTek Dimensity/Helio & Qualcomm Snapdragon).
# ==============================================================================

PROFILE="${1:-balance}"
mkdir -p /data/adb/lynx 2>/dev/null
echo "$PROFILE" > /data/adb/lynx/active_profile 2>/dev/null

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# Target throttler daemons known to clamp FPS and frequencies
OEM_TARGET_PROCS="mi_thermald thermal-engine thermal-engine-v2 ituxd com.samsung.android.game.gos"

is_bluetooth_audio() {
    dumpsys audio 2>/dev/null | grep -iE "a2dp.*connected|device.*bluetooth_a2dp" | grep -qv "state=0"
}

# Ensure frequency files are readable even if Unity trick was previously applied
for p in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$p" ] || continue
    chmod 444 "$p"/cpuinfo_* 2>/dev/null
done
for c in /sys/devices/system/cpu/cpu[0-9]*; do
    [ -d "$c" ] || continue
    chmod 444 "$c"/cpufreq/cpuinfo_* "$c"/cpu_capacity "$c"/topology/physical_package_id 2>/dev/null
done

case "$PROFILE" in
    extreme|performance)
        # ── 1. CPU Governor & Frequency Clamping ──────────────────────────
        # For extreme: use 'performance' governor → CPU always runs at scaling_max_freq
        # This bypasses mtkpower HAL interference on scaling_min_freq
        # For performance: use schedutil with aggressive boost settings
        for p in /sys/devices/system/cpu/cpufreq/policy*; do
            [ -d "$p" ] || continue

            max_freq=$(cat "$p/cpuinfo_max_freq" 2>/dev/null)
            if [ -z "$max_freq" ]; then
                max_freq=$(tr -s ' ' '\n' < "$p/scaling_available_frequencies" 2>/dev/null | sort -n | tail -n 1)
            fi
            min_freq=$(cat "$p/cpuinfo_min_freq" 2>/dev/null)
            if [ -z "$min_freq" ]; then
                min_freq=$(tr -s ' ' '\n' < "$p/scaling_available_frequencies" 2>/dev/null | sort -n | head -n 1)
            fi

            if [ "$PROFILE" = "extreme" ]; then
                # Extreme: 'performance' governor locks to max freq, bypasses all DVFS
                write_node "$max_freq" "$p/scaling_max_freq"
                write_node "performance" "$p/scaling_governor"
            else
                # Performance: schedutil with zero up-limit, 85% floor
                write_node "$max_freq" "$p/scaling_max_freq"
                write_node "schedutil" "$p/scaling_governor"
                write_node "0" "$p/schedutil/up_rate_limit_us"
                write_node "5000" "$p/schedutil/down_rate_limit_us"
                write_node "85" "$p/schedutil/hispeed_load"
                write_node "1" "$p/schedutil/iowait_boost_enable"
                write_node "1" "$p/schedutil/pl"
                write_node "$max_freq" "$p/schedutil/hispeed_freq"
                if [ -n "$max_freq" ] && [ -n "$min_freq" ]; then
                    floor=$(( max_freq * 85 / 100 ))
                    [ "$floor" -lt "$min_freq" ] && floor="$min_freq"
                    write_node "$floor" "$p/scaling_min_freq"
                fi
            fi
        done


        # Core Control Jitter Prevention
        for ctl in /sys/devices/system/cpu/cpu*/core_ctl; do
            [ -d "$ctl" ] || continue
            write_node "500" "$ctl/offline_delay_ms"
            write_node "1 1 1 1" "$ctl/not_preferred"
        done

        # ── 2. Workqueue & Interconnect Mode ────────────────────────────────
        write_node "N" "/sys/module/workqueue/parameters/power_efficient"
        write_node "1" "/sys/devices/system/cpu/perf/enable"
        write_node "0" "/sys/devices/system/cpu/eas/enable"
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
        # hard_userlimit_min/max_cpu_freq uses OPP INDEX, NOT KHz!
        # Index 0 = peak frequency, last_index = minimum frequency
        # Format: echo "<cluster_id> <opp_index>" > node
        for c in 0 1 2; do
            table="/proc/ppm/dump_cluster_${c}_dvfs_table"
            [ -f "$table" ] || continue
            if [ "$PROFILE" = "extreme" ]; then
                # Extreme: lock to index 0 (peak freq) for both min and max
                write_node "$c 0" "/proc/ppm/policy/hard_userlimit_max_cpu_freq"
                write_node "$c 0" "/proc/ppm/policy/hard_userlimit_min_cpu_freq"
            else
                # Performance: max = index 0 (peak), floor = ~15% into table
                # Count OPP entries using wc -w (POSIX, Toybox-safe)
                total_opp=$(wc -w < "$table" 2>/dev/null)
                perf_floor_idx=$(( total_opp * 15 / 100 ))
                [ "$perf_floor_idx" -lt 1 ] 2>/dev/null && perf_floor_idx=2
                write_node "$c 0" "/proc/ppm/policy/hard_userlimit_max_cpu_freq"
                write_node "$c $perf_floor_idx" "/proc/ppm/policy/hard_userlimit_min_cpu_freq"
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
            # GED HAL: custom_boost_gpu_freq & custom_upbound_gpu_freq use OPP INDEX
            # Index 0 = highest freq (950 MHz), Index 48 = lowest (300 MHz)
            # So for maximum boost: write index 0 to both
            write_node "0" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
            write_node "0" "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
            write_node "2" "/sys/kernel/ged/hal/gpu_boost_level"
            write_node "100" "/sys/kernel/ged/hal/dvfs_margin_value"
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

            # Parse peak GPU freq (KHz) from OPP table (line 1 = highest OPP)
            if [ -f "/proc/gpufreq/gpufreq_opp_dump" ]; then
                opp_line=$(head -n 1 /proc/gpufreq/gpufreq_opp_dump 2>/dev/null)
                peak_f=$(echo "$opp_line" | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
                peak_vgpu=$(echo "$opp_line" | grep -Eo 'vgpu = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
                if [ -n "$peak_f" ]; then
                    # gpu_cust_upbound_freq & gpu_cust_boost_freq use KHz values
                    write_node "$peak_f" "/sys/module/ged/parameters/gpu_cust_upbound_freq"
                    write_node "$peak_f" "/sys/module/ged/parameters/gpu_cust_boost_freq"
                    write_node "$peak_f" "/sys/module/ged/parameters/gpu_bottom_freq"
                    # gpufreq_opp_freq uses OPP INDEX not KHz — write index 0 = peak freq
                    write_node "0" "/proc/gpufreq/gpufreq_opp_freq"
                    # Lock GPU to peak freq+volt via fixed_freq_volt if available
                    if [ -n "$peak_vgpu" ]; then
                        write_node "${peak_f} ${peak_vgpu}" "/proc/gpufreq/gpufreq_fixed_freq_volt"
                    fi
                fi
            fi
            # Disable all GPU frequency limiters (THERMAL, PTPOD, PBM, etc.)
            for i in 0 1 2 3 4 5 6 7 8; do
                write_node "$i 0 0" "/proc/gpufreq/gpufreq_limit_table"
            done
        else
            # Performance profile: boost to upper OPP range but allow DVFS
            # GED HAL: write OPP index (0=max). Index 5 ≈ 900 MHz for performance
            write_node "0" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
            write_node "5" "/sys/kernel/ged/hal/custom_upbound_gpu_freq"
            write_node "1" "/sys/kernel/ged/hal/gpu_boost_level"
            write_node "50" "/sys/kernel/ged/hal/dvfs_margin_value"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/min_pwrlevel"
            write_node "1" "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost"
            write_node "60" "/sys/class/kgsl/kgsl-3d0/idle_timer"
            # Performance: set KHz floor to 85% of peak GPU freq
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
        fi


        write_node "1" "/sys/module/ged/parameters/boost_gpu_enable"
        write_node "1" "/sys/module/ged/parameters/ged_smart_boost"
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
        write_node "1" "/sys/module/ged/parameters/gpu_idle"
        write_node "1" "/sys/module/ged/parameters/gx_force_cpu_boost"
        write_node "0" "/proc/gpufreq/gpufreq_aging_enable"
        write_node "1" "/sys/kernel/fpsgo/common/gpu_block_boost"
        write_node "1" "/sys/kernel/fpsgo/common/force_onoff"
        write_node "1" "/sys/kernel/fpsgo/fbt/boost_ta"
        write_node "0" "/sys/kernel/fpsgo/fbt/thrm_limit_cpu"
        write_node "always_on" "/sys/devices/platform/*mali*/power_policy"
        write_node "1" "/proc/mali/always_on"
        write_node "1" "/proc/mali/dvfs_enable"
        write_node "0" "/proc/mali/debug_log"

        # ── 4. UCLAMP & Top-App Process Clamping ──────────────────────────────
        uclamp_val=60
        [ "$PROFILE" = "extreme" ] && uclamp_val=80
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
        write_node "5" "/dev/stune/top-app/schedtune.boost"

        # ── 5. Virtual Memory (VM), MGLRU, & I/O ─────────────────────────────
        if [ -e "/sys/kernel/mm/lru_gen/enabled" ]; then
            write_node "y" "/sys/kernel/mm/lru_gen/enabled"
            write_node "1000" "/sys/kernel/mm/lru_gen/min_ttl_ms"
        fi
        write_node "20" "/proc/sys/vm/dirty_ratio"
        write_node "10" "/proc/sys/vm/dirty_background_ratio"
        write_node "500" "/proc/sys/vm/dirty_expire_centisecs"
        write_node "200" "/proc/sys/vm/dirty_writeback_centisecs"
        write_node "70" "/proc/sys/vm/vfs_cache_pressure"
        write_node "100" "/proc/sys/vm/extfrag_threshold"
        write_node "0" "/proc/sys/vm/oom_dump_tasks"
        write_node "80" "/proc/sys/vm/overcommit_ratio"
        write_node "1" "/proc/sys/vm/compact_unevictable_allowed"
        write_node "16" "/proc/sys/kernel/sched_nr_migrate"
        write_node "40" "/proc/sys/kernel/perf_cpu_time_max_percent"
        write_node "1" "/proc/sys/kernel/sched_boost"
        write_node "512" "/proc/sys/kernel/random/read_wakeup_threshold"
        write_node "2048" "/proc/sys/kernel/random/write_wakeup_threshold"
        swap_v=70
        [ "$PROFILE" = "extreme" ] && swap_v=60
        write_node "$swap_v" "/proc/sys/vm/swappiness"

        # Storage I/O Optimization
        for queue in /sys/block/*/queue; do
            [ -d "$queue" ] || continue
            write_node "0" "$queue/add_random"
            write_node "0" "$queue/iostats"
            write_node "1" "$queue/rq_affinity"
            write_node "128" "$queue/nr_requests"
        done
        for q in /sys/block/*/queue/scheduler; do
            [ -e "$q" ] && echo deadline > "$q" 2>/dev/null
        done
        for ra in /sys/block/sd*/queue/read_ahead_kb; do
            write_node "128" "$ra"
        done
        for ra in /sys/block/mmcblk*/queue/read_ahead_kb; do
            write_node "512" "$ra"
        done
        for ufs in /sys/devices/platform/soc/*ufshc*; do
            [ -d "$ufs" ] || continue
            write_node "5" "$ufs/clkgate_delay_ms_perf"
            write_node "1000" "$ufs/clkgate_delay_ms_pwr_save"
        done

        # ── 6. Display Vsync Offsets, Refresh Rate & TouchBoost ──────────────
        setprop debug.sf.early_phase_offset_ns 500000 2>/dev/null
        setprop debug.sf.early_app_phase_offset_ns 500000 2>/dev/null
        setprop debug.sf.high_fps_late_app_phase_offset_ns 1000000 2>/dev/null
        setprop debug.sf.high_fps_early_phase_offset_ns 1000000 2>/dev/null

        # Refresh rate holding
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

        # ── 7. Wi-Fi & TCP Network Gaming Stack ──────────────────────────────
        cmd wifi set-power-save-mode 0 >/dev/null 2>&1
        cmd wifi force-low-latency-mode enabled >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_low_latency=1 >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_autocorking=0 >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_notsent_lowat=16384 >/dev/null 2>&1
        for ps_node in /sys/module/wlan/parameters/power_save /sys/module/bcmdhd/parameters/op_mode; do
            write_node "0" "$ps_node"
        done

        # Dynamic TCP Congestion Algorithm
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
        setprop af.fast_track_multiplier 1 2>/dev/null
        if is_bluetooth_audio; then
            setprop aaudio.mmap_policy 2 2>/dev/null
            setprop aaudio.hw_burst_min_usec 4000 2>/dev/null
        else
            setprop aaudio.mmap_policy 2 2>/dev/null
            setprop aaudio.mmap_exclusive_policy 2 2>/dev/null
            setprop aaudio.hw_burst_min_usec 2000 2>/dev/null
        fi

        # ── 9. Critical Process Priority Renicing ────────────────────────────
        for proc in "surfaceflinger" "android.hardware.graphics.composer" "vendor.qti.hardware.display.composer" "vendor.mediatek.hardware.pq"; do
            for pid in $(pgrep -f "$proc" 2>/dev/null); do
                renice -n -20 -p "$pid" 2>/dev/null
                write_node "$pid" "/dev/cpuset/top-app/cgroup.procs"
            done
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
        for proc in $OEM_TARGET_PROCS; do
            for pid in $(pidof "$proc" 2>/dev/null); do
                kill -STOP "$pid" 2>/dev/null
            done
        done
        for jpid in $(pgrep -f "com.xiaomi.joyose" 2>/dev/null); do
            kill -STOP "$jpid" 2>/dev/null
        done

        # ── 12. Extreme Exclusive: Full Thermal Bypass & Unity Trick ─────────
        if [ "$PROFILE" = "extreme" ]; then
            write_node "0" "/proc/cpufreq/cpufreq_imax_thermal_protect"
            cmd thermalservice override-status 0 2>/dev/null
            for tz in /sys/class/thermal/thermal_zone*; do
                [ -d "$tz" ] || continue
                write_node "disabled" "$tz/mode"
                write_node "150000" "$tz/trip_point_0_temp"
            done
            for cooling in /sys/class/thermal/cooling_device*; do
                [ -d "$cooling" ] || continue
                max_s=$(cat "$cooling/max_state" 2>/dev/null)
                [ -n "$max_s" ] && write_node "$max_s" "$cooling/min_state"
            done

            # Unity Engine FPS uncap trick
            for cpu in 0 1 2 3 4 5 6 7; do
                path="/sys/devices/system/cpu/cpu${cpu}"
                [ -e "$path/cpufreq/cpuinfo_max_freq" ] && chmod 000 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
                [ -e "$path/cpu_capacity" ] && chmod 000 "$path/cpu_capacity" 2>/dev/null
                [ -e "$path/topology/physical_package_id" ] && chmod 000 "$path/topology/physical_package_id" 2>/dev/null
            done
        else
            # Performance Mode: Safe Thermal Bounds
            write_node "1" "/proc/cpufreq/cpufreq_imax_thermal_protect"
            cmd thermalservice reset 2>/dev/null
            for tz in /sys/class/thermal/thermal_zone*; do
                [ -d "$tz" ] || continue
                write_node "enabled" "$tz/mode"
                write_node "80000" "$tz/trip_point_0_temp"
            done
            for cooling in /sys/class/thermal/cooling_device*; do
                [ -d "$cooling" ] || continue
                write_node "0" "$cooling/min_state"
            done
            # Restore permissions in case Extreme was previously active
            for cpu in 0 1 2 3 4 5 6 7; do
                path="/sys/devices/system/cpu/cpu${cpu}"
                [ -e "$path/cpufreq/cpuinfo_max_freq" ] && chmod 444 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
                [ -e "$path/cpu_capacity" ] && chmod 444 "$path/cpu_capacity" 2>/dev/null
                [ -e "$path/topology/physical_package_id" ] && chmod 444 "$path/topology/physical_package_id" 2>/dev/null
            done
        fi

        setprop lynx.mode "$PROFILE"
        ;;

    powersave)
        # ── 1. CPU Schedutil & Low Frequency Cap (60% Max) ───────────────────
        for p in /sys/devices/system/cpu/cpufreq/policy*; do
            [ -d "$p" ] || continue
            write_node "schedutil" "$p/scaling_governor"
            write_node "20000" "$p/schedutil/up_rate_limit_us"
            write_node "500" "$p/schedutil/down_rate_limit_us"
            write_node "99" "$p/schedutil/hispeed_load"
            write_node "0" "$p/schedutil/iowait_boost_enable"
            write_node "0" "$p/schedutil/pl"

            min_freq=$(cat "$p/cpuinfo_min_freq" 2>/dev/null)
            max_freq=$(cat "$p/cpuinfo_max_freq" 2>/dev/null)
            if [ -z "$max_freq" ]; then
                max_freq=$(tr -s ' ' '\n' < "$p/scaling_available_frequencies" 2>/dev/null | sort -n | tail -n 1)
            fi
            if [ -z "$min_freq" ]; then
                min_freq=$(tr -s ' ' '\n' < "$p/scaling_available_frequencies" 2>/dev/null | sort -n | head -n 1)
            fi
            [ -n "$min_freq" ] && write_node "$min_freq" "$p/scaling_min_freq"
            if [ -n "$max_freq" ]; then
                p_cap=$(( max_freq * 60 / 100 ))
                [ -n "$min_freq" ] && [ "$p_cap" -gt "$min_freq" ] && write_node "$p_cap" "$p/scaling_max_freq"
            fi
        done

        # Core Control
        for cpu in 0 1 2 3 4 5 6 7; do
            write_node "0 0 0 0" "/sys/devices/system/cpu/cpu${cpu}/core_ctl/not_preferred"
        done

        # ── 2. Workqueue & Interconnect Mode: Power Efficient ────────────────
        write_node "Y" "/sys/module/workqueue/parameters/power_efficient"
        write_node "1" "/sys/devices/system/cpu/eas/enable"
        write_node "0" "/sys/devices/system/cpu/perf/enable"

        # MediaTek CPU Low Power Mode
        write_node "1" "/proc/cpufreq/cpufreq_power_mode"
        write_node "0" "/proc/cpufreq/cpufreq_cci_mode"
        write_node "0" "/proc/cpufreq/cpufreq_imax_enable"
        write_node "0" "/proc/cpufreq/cpufreq_sched_disable"
        write_node "1" "/proc/cpuidle/control/armpll_mode"
        write_node "0" "/proc/cpuidle/control/buck_mode"

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
        num_pwr=$(cat "/sys/class/kgsl/kgsl-3d0/num_pwrlevels" 2>/dev/null)
        [ -n "$num_pwr" ] && [ "$num_pwr" -gt 1 ] && write_node "$((num_pwr - 1))" "/sys/class/kgsl/kgsl-3d0/min_pwrlevel"
        write_node "0" "/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost"
        write_node "1" "/sys/class/kgsl/kgsl-3d0/throttling"
        write_node "20" "/sys/class/kgsl/kgsl-3d0/idle_timer"

        # ── 4. UCLAMP, CPUSet & Memory Subsystem ─────────────────────────────
        for u_node in "/dev/cpuset/top-app/cpu.uclamp.min" "/proc/sys/kernel/sched_util_clamp_min"; do
            write_node "0" "$u_node"
        done
        write_node "0-3" "/dev/cpuset/background/cpus"
        write_node "0-3" "/dev/cpuset/system-background/cpus"
        write_node "0-3" "/dev/cpuset/restricted/cpus"
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

        # ── 5. Unfreeze Throttlers, Restore Display, Network & Thermal ────────
        for proc in $OEM_TARGET_PROCS; do
            for pid in $(pidof "$proc" 2>/dev/null); do
                kill -CONT "$pid" 2>/dev/null
            done
        done
        for jpid in $(pgrep -f "com.xiaomi.joyose" 2>/dev/null); do
            kill -CONT "$jpid" 2>/dev/null
        done
        cmd wifi set-power-save-mode 1 >/dev/null 2>&1
        cmd wifi force-low-latency-mode disabled >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_low_latency=0 >/dev/null 2>&1
        setprop debug.sf.early_phase_offset_ns "" 2>/dev/null
        setprop debug.sf.early_app_phase_offset_ns "" 2>/dev/null
        setprop af.fast_track_multiplier 2 2>/dev/null
        setprop aaudio.mmap_policy 1 2>/dev/null
        write_node "" "/proc/sys/kernel/sched_lib_name"
        write_node "0" "/proc/sys/kernel/sched_lib_mask_force"

        # Restore Min Refresh Rate
        if [ -f "/dev/lynx_orig_min_rr" ]; then
            orig_min=$(cat "/dev/lynx_orig_min_rr" 2>/dev/null)
            [ -n "$orig_min" ] && settings put system min_refresh_rate "$orig_min" 2>/dev/null
            rm -f "/dev/lynx_orig_min_rr" 2>/dev/null
        fi

        # Restore Thermal Protection
        write_node "1" "/proc/cpufreq/cpufreq_imax_thermal_protect"
        cmd thermalservice reset 2>/dev/null
        for tz in /sys/class/thermal/thermal_zone*; do
            [ -d "$tz" ] || continue
            write_node "enabled" "$tz/mode"
            write_node "80000" "$tz/trip_point_0_temp"
        done
        for cooling in /sys/class/thermal/cooling_device*; do
            [ -d "$cooling" ] || continue
            write_node "0" "$cooling/min_state"
        done

        # Restore permissions if Unity trick was previously applied
        for cpu in 0 1 2 3 4 5 6 7; do
            path="/sys/devices/system/cpu/cpu${cpu}"
            [ -e "$path/cpufreq/cpuinfo_max_freq" ] && chmod 444 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
            [ -e "$path/cpu_capacity" ] && chmod 444 "$path/cpu_capacity" 2>/dev/null
            [ -e "$path/topology/physical_package_id" ] && chmod 444 "$path/topology/physical_package_id" 2>/dev/null
        done

        setprop lynx.mode powersave
        ;;

    balance|auto|*)
        # ── 1. CPU Schedutil & Uncapped Frequencies ──────────────────────────
        for p in /sys/devices/system/cpu/cpufreq/policy*; do
            [ -d "$p" ] || continue
            write_node "schedutil" "$p/scaling_governor"
            write_node "500" "$p/schedutil/up_rate_limit_us"
            write_node "20000" "$p/schedutil/down_rate_limit_us"
            write_node "99" "$p/schedutil/hispeed_load"
            write_node "1" "$p/schedutil/iowait_boost_enable"
            write_node "1" "$p/schedutil/pl"

            min_freq=$(cat "$p/cpuinfo_min_freq" 2>/dev/null)
            max_freq=$(cat "$p/cpuinfo_max_freq" 2>/dev/null)
            if [ -z "$max_freq" ]; then
                max_freq=$(tr -s ' ' '\n' < "$p/scaling_available_frequencies" 2>/dev/null | sort -n | tail -n 1)
            fi
            if [ -z "$min_freq" ]; then
                min_freq=$(tr -s ' ' '\n' < "$p/scaling_available_frequencies" 2>/dev/null | sort -n | head -n 1)
            fi
            [ -n "$min_freq" ] && write_node "$min_freq" "$p/scaling_min_freq"
            [ -n "$max_freq" ] && write_node "$max_freq" "$p/scaling_max_freq"
        done

        # Core Control
        for cpu in 0 1 2 3 4 5 6 7; do
            write_node "0 0 0 0" "/sys/devices/system/cpu/cpu${cpu}/core_ctl/not_preferred"
        done

        # ── 2. Workqueue & Interconnect Mode: Balanced ───────────────────────
        write_node "Y" "/sys/module/workqueue/parameters/power_efficient"
        write_node "1" "/sys/devices/system/cpu/eas/enable"
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
        write_node "48" "/sys/kernel/ged/hal/custom_boost_gpu_freq"
        write_node "0" "/sys/kernel/ged/hal/gpu_boost_level"
        write_node "0" "/sys/kernel/ged/hal/dvfs_margin_value"
        write_node "1" "/sys/module/ged/parameters/boost_gpu_enable"
        write_node "1" "/sys/module/ged/parameters/ged_smart_boost"
        write_node "0" "/sys/module/ged/parameters/gx_game_mode"
        write_node "0" "/sys/module/ged/parameters/gx_boost_on"
        write_node "1" "/sys/kernel/fpsgo/common/gpu_block_boost"
        write_node "always_on" "/sys/devices/platform/*mali*/power_policy"
        write_node "1" "/proc/mali/always_on"
        write_node "1" "/proc/mali/dvfs_enable"

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
            write_node "0" "$u_node"
        done
        write_node "0-7" "/dev/cpuset/foreground/cpus"
        write_node "0-2" "/dev/cpuset/background/cpus"
        write_node "0-5" "/dev/cpuset/system-background/cpus"
        write_node "0-7" "/dev/cpuset/top-app/cpus"
        write_node "5" "/dev/stune/foreground/schedtune.boost"
        write_node "5" "/dev/stune/top-app/schedtune.boost"

        if [ -e "/sys/kernel/mm/lru_gen/enabled" ]; then
            write_node "y" "/sys/kernel/mm/lru_gen/enabled"
        fi
        write_node "15" "/proc/sys/vm/dirty_ratio"
        write_node "5" "/proc/sys/vm/dirty_background_ratio"
        write_node "80" "/proc/sys/vm/swappiness"
        write_node "100" "/proc/sys/vm/vfs_cache_pressure"
        for q in /sys/block/*/queue/scheduler; do
            [ -e "$q" ] && echo deadline > "$q" 2>/dev/null
        done
        for ra in /sys/block/*/queue/read_ahead_kb; do
            write_node "128" "$ra"
        done

        # ── 5. Unfreeze Throttlers, Restore Display, Network & Thermal ────────
        for proc in $OEM_TARGET_PROCS; do
            for pid in $(pidof "$proc" 2>/dev/null); do
                kill -CONT "$pid" 2>/dev/null
            done
        done
        for jpid in $(pgrep -f "com.xiaomi.joyose" 2>/dev/null); do
            kill -CONT "$jpid" 2>/dev/null
        done
        cmd wifi set-power-save-mode 1 >/dev/null 2>&1
        cmd wifi force-low-latency-mode disabled >/dev/null 2>&1
        sysctl -w net.ipv4.tcp_low_latency=0 >/dev/null 2>&1
        setprop debug.sf.early_phase_offset_ns "" 2>/dev/null
        setprop debug.sf.early_app_phase_offset_ns "" 2>/dev/null
        setprop af.fast_track_multiplier 2 2>/dev/null
        setprop aaudio.mmap_policy 1 2>/dev/null
        write_node "" "/proc/sys/kernel/sched_lib_name"
        write_node "0" "/proc/sys/kernel/sched_lib_mask_force"

        # Restore Min Refresh Rate
        if [ -f "/dev/lynx_orig_min_rr" ]; then
            orig_min=$(cat "/dev/lynx_orig_min_rr" 2>/dev/null)
            [ -n "$orig_min" ] && settings put system min_refresh_rate "$orig_min" 2>/dev/null
            rm -f "/dev/lynx_orig_min_rr" 2>/dev/null
        fi

        # Reset Touch Boost Nodes
        for tn in /sys/class/touch/touch_dev/touch_game_mode \
                 /sys/devices/virtual/touch/touch_dev/bump_sample_rate \
                 /proc/touchscreen/game_mode \
                 /sys/devices/platform/goodix_ts.*/game_mode \
                 /sys/devices/platform/tp_wake_switch/game_mode \
                 /sys/devices/virtual/input/input*/touch_game_mode; do
            write_node "0" "$tn"
        done

        # Restore Thermal Protection
        write_node "1" "/proc/cpufreq/cpufreq_imax_thermal_protect"
        cmd thermalservice reset 2>/dev/null
        for tz in /sys/class/thermal/thermal_zone*; do
            [ -d "$tz" ] || continue
            write_node "enabled" "$tz/mode"
            write_node "80000" "$tz/trip_point_0_temp"
        done
        for cooling in /sys/class/thermal/cooling_device*; do
            [ -d "$cooling" ] || continue
            write_node "0" "$cooling/min_state"
        done

        # Restore permissions if Unity trick was applied
        for cpu in 0 1 2 3 4 5 6 7; do
            path="/sys/devices/system/cpu/cpu${cpu}"
            [ -e "$path/cpufreq/cpuinfo_max_freq" ] && chmod 444 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
            [ -e "$path/cpu_capacity" ] && chmod 444 "$path/cpu_capacity" 2>/dev/null
            [ -e "$path/topology/physical_package_id" ] && chmod 444 "$path/topology/physical_package_id" 2>/dev/null
        done

        setprop lynx.mode balance
        ;;
esac
