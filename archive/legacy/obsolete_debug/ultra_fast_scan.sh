#!/system/bin/sh
# Ultra-fast Zero-Fork Dynamic Tree Deep Inspector
start_time=$(date +%s%3N 2>/dev/null || date +%s)
out="/storage/emulated/0/Debug/ultra_fast_output.json"

echo "[" > "$out"
first=1
count=0

for n in \
    /sys/devices/system/cpu/cpufreq/policy*/scaling_governor \
    /sys/devices/system/cpu/cpufreq/policy*/*/* \
    /sys/devices/system/cpu/cpufreq/*/* \
    /sys/devices/system/cpu/sched/* \
    /proc/sys/kernel/sched_* \
    /proc/sys/kernel/uclamp_* \
    /proc/cpufreq/* \
    /proc/perfmgr/boost_ctrl/*/* \
    /proc/perfmgr/tchbst \
    /sys/module/ged/parameters/* \
    /sys/module/fbt_cpu/parameters/* \
    /sys/class/misc/mali*/device/power_policy \
    /sys/class/misc/mali*/device/dvfs_period \
    /proc/mali/dvfs_enable \
    /proc/gpufreq/gpufreq_power_mode \
    /proc/gpufreq/gpufreq_fixed_freq_volt \
    /sys/class/kgsl/kgsl-3d0/devfreq/* \
    /sys/class/kgsl/kgsl-3d0/adrenoboost \
    /sys/class/kgsl/kgsl-3d0/idle_timer \
    /sys/class/kgsl/kgsl-3d0/force_bus_on \
    /sys/class/kgsl/kgsl-3d0/force_clk_on \
    /sys/class/kgsl/kgsl-3d0/force_rail_on \
    /sys/class/kgsl/kgsl-3d0/default_pwrlevel \
    /sys/class/kgsl/kgsl-3d0/max_pwrlevel \
    /sys/class/kgsl/kgsl-3d0/min_pwrlevel \
    /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel \
    /sys/class/kgsl/kgsl-3d0/throttling \
    /sys/class/kgsl/kgsl-3d0/bus_split \
    /sys/module/cpu_boost/parameters/* \
    /sys/module/msm_performance/parameters/* \
    /sys/power/cpufreq_*_limit \
    /proc/sys/vm/* \
    /sys/kernel/mm/lru_gen/enabled \
    /sys/kernel/mm/ksm/* \
    /sys/kernel/mm/transparent_hugepage/* \
    /sys/kernel/mm/swap/* \
    /sys/block/zram*/comp_algorithm \
    /sys/block/zram*/max_comp_streams \
    /sys/block/zram*/mem_limit \
    /sys/block/zram*/compact \
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
    /sys/class/power_supply/usb/current_max \
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

    [ -f "$n" ] || continue

    case "$n" in
        *cpuinfo*|*cur_freq*|*affected_cpus*|*related_cpus*|*available_*|*subsystem*|*uevent*|*modalias*|*driver_override*) continue ;;
        */stat|*/stats|*debug_stat|*io_stat|*mm_stat|*capacity*|*charge_counter|*voltage_now|*current_now|*temp) continue ;;
        *chunk_sectors|*dax|*discard_*|*hw_sector_size|*logical_block_size|*max_*segments*|*minimum_io_size|*optimal_io_size|*physical_block_size) continue ;;
        *reset|*set_sched_*|*compact|*mem_limit|*hint_enable|*hint_load_thresh|*compact_memory|*drop_caches) continue ;;
    esac

    # Writable and readable check with zero fork
    if [ ! -w "$n" ]; then
        chmod 644 "$n" 2>/dev/null
        [ -w "$n" ] || continue
    fi
    [ -r "$n" ] || continue

    # Read value with zero fork
    val=""
    read -r val < "$n" 2>/dev/null
    [ -z "$val" ] && continue

    help_text=""
    # If first line was a comment, read further lines
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

    # Clean value
    val=$(echo "$val" | tr -d '\r\n"' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')

    # UI Type detection using pure shell patterns
    typ="text"
    opts="[]"

    case "$val" in
        *\[*\]*)
            typ="choice"
            cur_choice="${val#*\[}"
            cur_choice="${cur_choice%%\]*}"
            raw_opts=$(echo "$val" | tr -d '[]')
            opts=$(echo "$raw_opts" | awk '{printf "["; for(i=1;i<=NF;i++) printf "\"%s\"%s", $i, (i==NF?"":","); printf "]"}')
            val="$cur_choice"
            ;;
        0|1|Y|N|enabled|disabled)
            typ="bool"
            ;;
        *[!0-9-]*)
            parent="${n%/*}"
            base="${n##*/}"
            avail_node="$parent/scaling_available_${base}s"
            [ ! -f "$avail_node" ] && avail_node="$parent/available_${base}s"
            [ ! -f "$avail_node" ] && avail_node="$parent/available_${base}"
            if [ -f "$avail_node" ]; then
                typ="choice"
                opts=$(cat "$avail_node" 2>/dev/null | awk '{printf "["; for(i=1;i<=NF;i++) printf "\"%s\"%s", $i, (i==NF?"":","); printf "]"}')
            fi
            ;;
        *)
            typ="int"
            ;;
    esac

    # Name and Category with zero fork
    base="${n##*/}"
    dir="${n%/*}"
    parent="${dir##*/}"
    name="$base"
    case "$parent" in
        queue|vm|parameters|kernel|ipv4) ;;
        *) name="$parent/$base" ;;
    esac

    cat="General"
    case "$n" in
        *cpu*|*sched*|*ppm*|*eara*|*cpufreq*) cat="CPU & Scheduler" ;;
        *gpu*|*kgsl*|*ged*|*gpufreq*|*mali*|*fbt_cpu*) cat="GPU & Graphics" ;;
        *vm*|*ksm*|*zram*|*lru*|*swap*|*hugepage*) cat="Memory & VM" ;;
        *block*|*queue*|*iosched*) cat="Storage & I/O" ;;
        *charge*|*power*|*battery*|*thermal*) cat="Power & Thermal" ;;
        *net*|*tcp*) cat="Network & Ping" ;;
        *touch*|*display*|*kcal*|*klapse*|*vibrator*) cat="Display & Touch" ;;
    esac

    [ $first -eq 0 ] && echo "," >> "$out"
    first=0
    echo "{\"path\":\"$n\",\"name\":\"$name\",\"category\":\"$cat\",\"value\":\"$val\",\"writable\":true,\"type\":\"$typ\",\"options\":$opts,\"help\":\"$help_text\"}" >> "$out"
    count=$((count + 1))
done

echo "]" >> "$out"
end_time=$(date +%s%3N 2>/dev/null || date +%s)
echo "Ultra-fast scan finished in $((end_time - start_time)) ms. Discovered nodes: $count"
