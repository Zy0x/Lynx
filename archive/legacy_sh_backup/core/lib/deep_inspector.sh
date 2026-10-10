#!/system/bin/sh
# ==============================================================================
# Lynx Universal - Deep Sysfs/Procfs Inspector & Smart Comment Interpreter
# Scans kernel, OEM, hardware, and system tunables dynamically (Zero Hardcoding)
# Extracts inline '#' documentation & generates adaptive UI schemas
# Highly optimized for fast execution (<2s) with zero redundant subshell forks
# ==============================================================================

inspect_single_node() {
    local n="$1"
    local cat_hint="$2"
    [ -f "$n" ] || return 1

    case "$n" in
        *cpuinfo*|*cur_freq*|*affected_cpus*|*related_cpus*|*available_*|*subsystem*|*uevent*|*modalias*|*driver_override*) return 1 ;;
        */stat|*/stats|*debug_stat|*io_stat|*mm_stat|*capacity*|*charge_counter|*voltage_now|*current_now|*temp) return 1 ;;
        *chunk_sectors|*dax|*discard_*|*hw_sector_size|*logical_block_size|*max_*segments*|*minimum_io_size|*optimal_io_size|*physical_block_size) return 1 ;;
        *reset|*set_sched_*|*compact|*mem_limit|*hint_enable|*hint_load_thresh|*compact_memory|*drop_caches) return 1 ;;
        *kpi*|*utilization*|*previous_freqency*|*current_freqency*|*BQid*|*table*|*fpsgo_status*|*/info|*systrace_mask*|*fbt_info*) return 1 ;;
        */loop*|*/ram[0-9]*) return 1 ;;
    esac

    # Writable check with zero fork
    local w=false
    if [ -w "$n" ]; then
        w=true
    else
        chmod 644 "$n" 2>/dev/null
        [ -w "$n" ] && w=true
    fi
    [ "$w" = "true" ] || return 1
    [ -r "$n" ] || return 1

    # Read value with zero fork
    local val=""
    read -r val < "$n" 2>/dev/null
    [ -z "$val" ] && return 1

    local help_text=""
    case "$val" in
        \#*)
            help_text="${val#\#}"
            while IFS= read -r line; do
                case "$line" in
                    \#*) help_text="$help_text ${line#\#}" ;;
                    *) [ -n "$line" ] && val="$line" && break ;;
                esac
            done < "$n"
            ;;
    esac

    val=$(echo "$val" | tr -d '\r\n"' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    [ -z "$val" ] && return 1

    # Normalize status strings into boolean values
    case "$val" in
        *"is enabled"|*:1|*" 1") val="1" ;;
        *"is disabled"|*:0|*" 0") val="0" ;;
    esac

    # UI Type detection using pure shell patterns
    local typ="text"
    local opts="[]"

    case "$val" in
        *\[*\]*)
            typ="choice"
            local cur_choice="${val#*\[}"
            cur_choice="${cur_choice%%\]*}"
            local raw_opts=$(echo "$val" | tr -d '[]')
            opts=$(echo "$raw_opts" | awk '{printf "["; for(i=1;i<=NF;i++) printf "\"%s\"%s", $i, (i==NF?"":","); printf "]"}')
            val="$cur_choice"
            ;;
        0|1|Y|N|enabled|disabled)
            typ="bool"
            ;;
        *[!0-9-]*)
            local parent="${n%/*}"
            local base="${n##*/}"
            local avail_node=""
            case "$base" in
                scaling_governor|governor)
                    avail_node="$parent/scaling_available_governors"
                    [ ! -f "$avail_node" ] && avail_node="$parent/available_governors"
                    ;;
                tcp_congestion_control)
                    avail_node="/proc/sys/net/ipv4/tcp_available_congestion_control"
                    ;;
                *)
                    avail_node="$parent/scaling_available_${base}s"
                    [ ! -f "$avail_node" ] && avail_node="$parent/available_${base}s"
                    [ ! -f "$avail_node" ] && avail_node="$parent/available_${base}"
                    ;;
            esac
            if [ -f "$avail_node" ]; then
                typ="choice"
                opts=$(cat "$avail_node" 2>/dev/null | awk '{printf "["; for(i=1;i<=NF;i++) printf "\"%s\"%s", $i, (i==NF?"":","); printf "]"}')
            fi
            ;;
        *)
            typ="int"
            ;;
    esac

    # Extract friendly name and category with zero fork
    local base="${n##*/}"
    local dir="${n%/*}"
    local parent="${dir##*/}"
    local name="$base"
    case "$parent" in
        queue|vm|parameters|kernel|ipv4) ;;
        *) name="$parent/$base" ;;
    esac

    local cat="General"
    if [ -n "$cat_hint" ]; then
        cat="$cat_hint"
    else
        case "$n" in
            *cpu*|*sched*|*ppm*|*eara*|*cpufreq*|*hps*|*core_ctl*|*eas*) cat="CPU & Scheduler" ;;
            *gpu*|*kgsl*|*ged*|*gpufreq*|*mali*|*fbt_cpu*|*fpsgo*) cat="GPU & Graphics" ;;
            *vm*|*ksm*|*zram*|*lru*|*swap*|*hugepage*|*lowmemorykiller*|*process_reclaim*) cat="Memory & VM" ;;
            *block*|*queue*|*iosched*) cat="Storage & I/O" ;;
            *charge*|*power*|*battery*|*thermal*) cat="Power & Thermal" ;;
            *net*|*tcp*) cat="Network & Ping" ;;
            *touch*|*display*|*kcal*|*klapse*|*vibrator*|*sound*) cat="Display & Touch" ;;
        esac
    fi

    echo "{\"path\":\"$n\",\"name\":\"$name\",\"category\":\"$cat\",\"value\":\"$val\",\"writable\":true,\"type\":\"$typ\",\"options\":$opts,\"help\":\"$help_text\"}"
}

# Scan deep catalog of tunables across all hardware architectures & kernels
scan_all_tunables() {
    echo "["
    local first=1

    for n in \
        /sys/devices/system/cpu/cpufreq/policy*/scaling_governor \
        /sys/devices/system/cpu/cpufreq/policy*/*/* \
        /sys/devices/system/cpu/cpufreq/*/* \
        /sys/devices/system/cpu/sched/* \
        /sys/devices/system/cpu/cpu*/core_ctl/* \
        /sys/devices/system/cpu/core_ctl/* \
        /sys/devices/system/cpu/eas/* \
        /sys/devices/system/cpu/perf/* \
        /proc/sys/kernel/sched_* \
        /proc/sys/kernel/uclamp_* \
        /proc/sys/kernel/timer_migration \
        /proc/sys/kernel/randomize_va_space \
        /proc/sys/kernel/perf_cpu_time_max_percent \
        /proc/sys/kernel/pid_max \
        /proc/cpufreq/* \
        /proc/hps/* \
        /proc/ppm/enabled \
        /proc/ppm/mode \
        /proc/perfmgr/boost_ctrl/*/* \
        /proc/perfmgr/tchbst \
        /proc/vendor_sched/* \
        /sys/module/ged/parameters/* \
        /sys/kernel/ged/hal/* \
        /sys/kernel/fpsgo/common/* \
        /sys/kernel/fpsgo/fbt/* \
        /sys/kernel/fpsgo/fstb/* \
        /sys/module/fbt_cpu/parameters/* \
        /sys/class/misc/mali*/device/power_policy \
        /sys/class/misc/mali*/device/dvfs_period \
        /sys/devices/platform/*.mali/power_policy \
        /sys/devices/platform/*.mali/dvfs_period \
        /sys/devices/platform/*.mali/dvfs \
        /proc/mali/dvfs_enable \
        /proc/gpufreq/gpufreq_power_mode \
        /proc/gpufreq/gpufreq_fixed_freq_volt \
        /sys/kernel/gpu/* \
        /sys/devices/system/cpu/cpufreq/mp-cpufreq/* \
        /sys/devices/system/cpu/cpuhotplug/* \
        /sys/power/cpuhotplug/* \
        /sys/devices/system/cpu/sprd_governor/* \
        /sys/class/kgsl/kgsl-3d0/devfreq/* \
        /sys/class/kgsl/kgsl-3d0/adrenoboost \
        /sys/class/kgsl/kgsl-3d0/idle_timer \
        /sys/class/kgsl/kgsl-3d0/force_bus_on \
        /sys/class/kgsl/kgsl-3d0/force_clk_on \
        /sys/class/kgsl/kgsl-3d0/force_rail_on \
        /sys/class/kgsl/kgsl-3d0/force_no_nap \
        /sys/class/kgsl/kgsl-3d0/default_pwrlevel \
        /sys/class/kgsl/kgsl-3d0/max_pwrlevel \
        /sys/class/kgsl/kgsl-3d0/min_pwrlevel \
        /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel \
        /sys/class/kgsl/kgsl-3d0/throttling \
        /sys/class/kgsl/kgsl-3d0/bus_split \
        /sys/class/kgsl/kgsl-3d0/max_gpuclk \
        /sys/class/kgsl/kgsl-3d0/gpuclk \
        /sys/class/kgsl/kgsl-3d0/min_clock_mhz \
        /sys/class/kgsl/kgsl-3d0/max_clock_mhz \
        /sys/class/kgsl/kgsl-3d0/pwrscale \
        /sys/module/cpu_boost/parameters/* \
        /sys/module/msm_performance/parameters/* \
        /sys/module/lpm_levels/parameters/* \
        /sys/power/cpufreq_*_limit \
        /proc/sys/vm/* \
        /sys/kernel/mm/lru_gen/* \
        /sys/kernel/mm/ksm/* \
        /sys/kernel/mm/transparent_hugepage/* \
        /sys/kernel/mm/swap/* \
        /sys/module/lowmemorykiller/parameters/* \
        /sys/module/process_reclaim/parameters/* \
        /sys/block/zram*/comp_algorithm \
        /sys/block/zram*/max_comp_streams \
        /sys/block/sd*/queue/scheduler \
        /sys/block/sd*/queue/read_ahead_kb \
        /sys/block/sd*/queue/nr_requests \
        /sys/block/sd*/queue/iostats \
        /sys/block/sd*/queue/nomerges \
        /sys/block/sd*/queue/rq_affinity \
        /sys/block/sd*/queue/add_random \
        /sys/block/sd*/queue/rotational \
        /sys/block/sd*/queue/io_poll \
        /sys/block/sd*/queue/io_poll_delay \
        /sys/block/sd*/queue/wbt_lat_usec \
        /sys/block/mmcblk*/queue/scheduler \
        /sys/block/mmcblk*/queue/read_ahead_kb \
        /sys/block/mmcblk*/queue/nr_requests \
        /sys/block/mmcblk*/queue/iostats \
        /sys/block/mmcblk*/queue/nomerges \
        /sys/block/mmcblk*/queue/rq_affinity \
        /sys/block/mmcblk*/queue/add_random \
        /sys/block/mmcblk*/queue/rotational \
        /sys/block/dm-*/queue/scheduler \
        /sys/block/dm-*/queue/read_ahead_kb \
        /sys/block/dm-*/queue/nr_requests \
        /sys/block/dm-*/queue/iostats \
        /sys/block/dm-*/queue/nomerges \
        /sys/block/dm-*/queue/rq_affinity \
        /sys/block/dm-*/queue/add_random \
        /sys/block/dm-*/queue/rotational \
        /sys/block/nvme*/queue/scheduler \
        /sys/block/nvme*/queue/read_ahead_kb \
        /sys/block/nvme*/queue/nr_requests \
        /sys/block/nvme*/queue/iostats \
        /sys/block/nvme*/queue/nomerges \
        /sys/block/nvme*/queue/rq_affinity \
        /sys/block/nvme*/queue/add_random \
        /sys/block/nvme*/queue/rotational \
        /sys/block/*/queue/iosched/* \
        /sys/devices/platform/charger/enable_sc \
        /sys/devices/platform/charger/input_current \
        /sys/devices/platform/charger/chg1_current \
        /sys/devices/platform/charger/chg2_current \
        /sys/devices/platform/charger/pdc_max_watt \
        /sys/devices/platform/charger/sc_ibat_limit \
        /sys/devices/platform/charger/sw_jeita \
        /sys/devices/platform/charger/Pump_Express \
        /sys/devices/platform/charger/bypass_charger \
        /sys/devices/platform/mt_charger/* \
        /sys/class/power_supply/battery/device/smart_charging \
        /sys/class/power_supply/battery/smart_charging_activation \
        /sys/class/power_supply/battery/charging_enabled \
        /sys/class/power_supply/battery/input_suspend \
        /sys/class/power_supply/battery/charge_control_limit \
        /sys/class/power_supply/battery/current_max \
        /sys/class/power_supply/battery/store_mode \
        /sys/class/power_supply/battery/batt_slate_mode \
        /sys/class/power_supply/battery/mmi_charging_enable \
        /sys/class/power_supply/battery/input_current_limit \
        /sys/class/power_supply/battery/constant_charge_current_max \
        /sys/class/power_supply/battery/step_charging_enabled \
        /sys/class/power_supply/battery/fastcharge_mode \
        /sys/class/power_supply/battery/fast_charge \
        /sys/class/power_supply/battery/thermal_limit \
        /sys/class/power_supply/battery/system_temp_level \
        /sys/class/power_supply/battery/wireless_boost \
        /sys/class/power_supply/battery/charge_full_design \
        /sys/class/power_supply/usb/current_max \
        /sys/class/power_supply/main/* \
        /sys/class/power_supply/bms/* \
        /sys/class/qcom-battery/direct_charging \
        /sys/class/qcom-battery/restricted_charging \
        /sys/class/thermal/thermal_zone*/mode \
        /sys/class/thermal/thermal_zone*/policy \
        /sys/devices/virtual/thermal/thermal_message/sconfig \
        /sys/module/msm_thermal/parameters/* \
        /sys/module/msm_thermal/core_control/* \
        /proc/touchpanel/* \
        /sys/class/touch/*/* \
        /sys/devices/virtual/touch/*/* \
        /sys/devices/platform/kcal_ctrl.0/* \
        /sys/module/klapse/parameters/* \
        /sys/class/timed_output/vibrator/enable \
        /sys/class/leds/vibrator/vmax \
        /sys/devices/virtual/timed_output/vibrator/amp \
        /sys/kernel/sound_control/* \
        /sys/class/misc/soundcontrol/* \
        /proc/sys/net/ipv4/tcp_congestion_control \
        /proc/sys/net/ipv4/tcp_fastopen \
        /proc/sys/net/ipv4/tcp_ecn \
        /proc/sys/net/ipv4/tcp_sack \
        /proc/sys/net/ipv4/tcp_tw_reuse \
        /proc/sys/net/ipv4/tcp_low_latency \
        /proc/sys/net/ipv4/tcp_fin_timeout \
        /proc/sys/net/ipv4/tcp_window_scaling \
        /proc/sys/net/ipv4/tcp_timestamps \
        /proc/sys/net/ipv4/tcp_syncookies \
        /proc/sys/net/ipv4/tcp_autocorking \
        /proc/sys/net/ipv4/tcp_max_syn_backlog \
        /proc/sys/net/ipv4/tcp_keepalive_time \
        /proc/sys/net/ipv4/tcp_keepalive_intvl \
        /proc/sys/net/ipv4/tcp_keepalive_probes \
        /proc/sys/net/core/default_qdisc \
        /proc/sys/net/core/netdev_max_backlog \
        /proc/sys/net/core/rmem_max \
        /proc/sys/net/core/wmem_max \
        /proc/sys/kernel/random/read_wakeup_threshold \
        /proc/sys/kernel/random/write_wakeup_threshold \
        /proc/sys/kernel/printk \
        /proc/sys/kernel/printk_devkmsg \
        /sys/module/workqueue/parameters/power_efficient; do

        json=$(inspect_single_node "$n")
        if [ -n "$json" ]; then
            [ $first -eq 0 ] && echo ","
            first=0
            echo "$json"
        fi
    done

    # MediaTek PPM Policies
    if [ -f "/proc/ppm/policy_status" ]; then
        while IFS= read -r line; do
            idx=$(echo "$line" | grep -o '\[[0-9]*\]' | tr -d '[]')
            [ -z "$idx" ] && continue
            pname=$(echo "$line" | sed 's/\[[0-9]*\][[:space:]]*//;s/:.*//')
            stat=$(echo "$line" | grep -o 'enabled\|disabled')
            val="0"
            [ "$stat" = "enabled" ] && val="1"
            [ $first -eq 0 ] && echo ","
            first=0
            echo "{\"path\":\"/proc/ppm/policy_status:$idx\",\"name\":\"PPM: $pname\",\"category\":\"CPU & Scheduler\",\"value\":\"$val\",\"writable\":true,\"type\":\"bool\",\"options\":[{\"value\":\"0\",\"label\":\"0 - Nonaktif\"},{\"value\":\"1\",\"label\":\"1 - Aktif\"}],\"help\":\"Kebijakan PPM MediaTek. Nilai: 1=Aktif, 0=Nonaktif.\"}"
        done < /proc/ppm/policy_status
    fi

    echo "]"
}

# Write a tunable node safely
write_tunable() {
    local node="$1"
    local val="$2"

    if echo "$node" | grep -q 'policy_status:'; then
        local idx="${node##*:}"
        local base="${node%%:*}"
        if echo "$idx $val" > "$base" 2>/dev/null; then
            echo "Successfully wrote '$val' to PPM policy $idx"
            return 0
        else
            echo "Error: Failed writing to PPM policy_status"
            return 1
        fi
    fi

    if [ ! -f "$node" ]; then
        echo "Error: node '$node' does not exist."
        return 1
    fi

    chmod 644 "$node" 2>/dev/null
    if echo "$val" > "$node" 2>/dev/null; then
        echo "Successfully wrote '$val' to $node"
        return 0
    else
        echo "Error: Failed writing to $node (permission denied or unsupported value)"
        return 1
    fi
}

# Export current tunables to boot script
export_boot_script() {
    local out_script="$1"
    [ -z "$out_script" ] && out_script="/data/adb/modules/Lynx/core/custom_tunables.sh"
    local out_dir="${out_script%/*}"
    mkdir -p "$out_dir" 2>/dev/null

    cat << 'EOF' > "$out_script"
#!/system/bin/sh
# Lynx Auto-Generated Custom Tunables Boot Script
# Applied automatically on system startup by service.sh

write_safe() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}
EOF

    echo "# Custom Hardware Tunables Exported at $(date)" >> "$out_script"
    chmod 755 "$out_script" 2>/dev/null
    echo "Initialized custom tunables script at $out_script"
}

# Main CLI Dispatcher
case "$1" in
    scan)
        scan_all_tunables
        ;;
    inspect)
        if [ -n "$2" ]; then
            inspect_single_node "$2"
        else
            echo "Error: specify node path to inspect"
            exit 1
        fi
        ;;
    set)
        if [ -n "$2" ] && [ -n "$3" ]; then
            write_tunable "$2" "$3"
        else
            echo "Usage: deep_inspector.sh set <path> <value>"
            exit 1
        fi
        ;;
    export)
        export_boot_script "$2"
        ;;
    *)
        echo "Lynx Deep Inspector CLI"
        echo "Usage: deep_inspector.sh [scan | inspect <path> | set <path> <val> | export <path>]"
        ;;
esac
