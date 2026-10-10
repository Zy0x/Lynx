#!/system/bin/sh
# Benchmark comprehensive dynamic tree deep inspector
start_time=$(date +%s%3N 2>/dev/null || date +%s)
out="/storage/emulated/0/Debug/benchmark_output.json"

inspect_node() {
    local n="$1"
    local cat_hint="$2"
    [ -f "$n" ] || return 1

    case "$n" in
        *cpuinfo*|*cur_freq*|*affected_cpus*|*related_cpus*|*available_*|*subsystem*|*uevent*|*modalias*|*driver_override*) return 1 ;;
        */stat|*/stats|*debug_stat|*io_stat|*mm_stat|*capacity*|*charge_counter|*voltage_now|*current_now|*temp) return 1 ;;
        *chunk_sectors|*dax|*discard_*|*hw_sector_size|*logical_block_size|*max_*segments*|*minimum_io_size|*optimal_io_size|*physical_block_size) return 1 ;;
    esac

    local w=false
    if [ -w "$n" ]; then
        w=true
    else
        chmod 644 "$n" 2>/dev/null
        [ -w "$n" ] && w=true
    fi
    [ "$w" = "true" ] || return 1

    local raw
    raw=$(head -n 25 "$n" 2>/dev/null)
    [ -z "$raw" ] && [ ! -s "$n" ] && return 1

    # Extract '#' comment lines as interactive documentation
    local help_text
    help_text=$(echo "$raw" | grep '^[[:space:]]*#' | sed 's/^[[:space:]]*#[[:space:]]*//' | tr '\n' ' ' | sed 's/"/\\"/g' | sed 's/[[:space:]]*$//')

    # Value without comments (trimmed)
    local val_clean
    val_clean=$(echo "$raw" | grep -v '^[[:space:]]*#' | head -n 1 | tr -d '\r\n' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | sed 's/"/\\"/g')
    [ -z "$val_clean" ] && val_clean=$(echo "$raw" | head -n 1 | tr -d '\r\n' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | sed 's/"/\\"/g')

    # UI Type detection
    local typ="text"
    local opts="[]"
    if echo "$val_clean" | grep -q '\[.*\]'; then
        typ="choice"
        local c
        c=$(echo "$val_clean" | grep -o '\[[^]]*\]' | tr -d '[]')
        local all
        all=$(echo "$val_clean" | tr -d '[]')
        opts=$(echo "$all" | awk '{printf "["; for(i=1;i<=NF;i++) printf "\"%s\"%s", $i, (i==NF?"":","); printf "]"}')
        val_clean="$c"
    elif [ "$val_clean" = "0" ] || [ "$val_clean" = "1" ] || [ "$val_clean" = "Y" ] || [ "$val_clean" = "N" ] || [ "$val_clean" = "enabled" ] || [ "$val_clean" = "disabled" ]; then
        typ="bool"
    elif echo "$val_clean" | grep -qE '^-?[0-9]+$'; then
        typ="int"
    else
        local parent="${n%/*}"
        local base="${n##*/}"
        local avail_node="$parent/scaling_available_${base}s"
        [ ! -f "$avail_node" ] && avail_node="$parent/available_${base}s"
        [ ! -f "$avail_node" ] && avail_node="$parent/available_${base}"
        if [ -f "$avail_node" ]; then
            typ="choice"
            opts=$(cat "$avail_node" 2>/dev/null | awk '{printf "["; for(i=1;i<=NF;i++) printf "\"%s\"%s", $i, (i==NF?"":","); printf "]"}')
        fi
    fi

    local base
    base=$(basename "$n")
    local parent
    parent=$(basename "${n%/*}")
    local name="$base"
    [ "$parent" != "queue" ] && [ "$parent" != "vm" ] && [ "$parent" != "parameters" ] && [ "$parent" != "kernel" ] && [ "$parent" != "ipv4" ] && name="$parent/$base"

    local cat="General"
    if [ -n "$cat_hint" ]; then
        cat="$cat_hint"
    else
        case "$n" in
            *cpu*|*sched*|*ppm*|*eara*|*cpufreq*) cat="CPU & Scheduler";;
            *gpu*|*kgsl*|*ged*|*gpufreq*|*mali*|*fbt_cpu*) cat="GPU & Graphics";;
            *vm*|*ksm*|*zram*|*lru*|*swap*|*hugepage*) cat="Memory & VM";;
            *block*|*queue*|*iosched*) cat="Storage & I/O";;
            *charge*|*power*|*battery*|*thermal*) cat="Power & Thermal";;
            *net*|*tcp*) cat="Network & Ping";;
            *touch*|*display*|*kcal*|*klapse*|*vibrator*) cat="Display & Touch";;
        esac
    fi

    [ $first -eq 0 ] && echo "," >> "$out"
    first=0
    echo "{\"path\":\"$n\",\"name\":\"$name\",\"category\":\"$cat\",\"value\":\"$val_clean\",\"writable\":true,\"type\":\"$typ\",\"options\":$opts,\"help\":\"$help_text\"}" >> "$out"
    count=$((count + 1))
}

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

    inspect_node "$n"
done

echo "]" >> "$out"
end_time=$(date +%s%3N 2>/dev/null || date +%s)
echo "Deep Scan finished in $((end_time - start_time)) ms. Discovered nodes: $count"
