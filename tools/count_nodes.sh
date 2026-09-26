#!/system/bin/sh
count=0
out="/storage/emulated/0/Debug/candidate_writable_nodes.txt"
rm -f "$out"

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
    /sys/class/kgsl/kgsl-3d0/devfreq/* \
    /sys/class/kgsl/kgsl-3d0/adrenoboost \
    /sys/class/kgsl/kgsl-3d0/idle_timer \
    /sys/class/kgsl/kgsl-3d0/force_bus_on \
    /sys/class/kgsl/kgsl-3d0/force_clk_on \
    /sys/class/kgsl/kgsl-3d0/force_rail_on \
    /sys/class/kgsl/kgsl-3d0/default_pwrlevel \
    /sys/class/kgsl/kgsl-3d0/max_pwrlevel \
    /sys/class/kgsl/kgsl-3d0/min_pwrlevel \
    /sys/class/kgsl/kgsl-3d0/throttling \
    /sys/module/cpu_boost/parameters/* \
    /sys/module/msm_performance/parameters/* \
    /proc/sys/vm/* \
    /sys/kernel/mm/lru_gen/enabled \
    /sys/kernel/mm/ksm/* \
    /sys/kernel/mm/transparent_hugepage/* \
    /sys/kernel/mm/swap/* \
    /sys/block/zram*/comp_algorithm \
    /sys/block/zram*/max_comp_streams \
    /sys/block/zram*/mem_limit \
    /sys/block/zram*/compact \
    /sys/block/sd*/queue/* \
    /sys/block/mmcblk*/queue/* \
    /sys/block/dm-*/queue/* \
    /sys/block/nvme*/queue/* \
    /sys/block/*/queue/iosched/* \
    /sys/devices/platform/charger/* \
    /sys/devices/platform/mt_charger/* \
    /sys/class/power_supply/battery/* \
    /sys/class/power_supply/usb/current_max \
    /sys/class/thermal/thermal_zone*/mode \
    /sys/class/thermal/thermal_zone*/policy \
    /sys/class/thermal/cooling_device*/cur_state \
    /sys/module/msm_thermal/parameters/* \
    /sys/module/msm_thermal/core_control/* \
    /proc/touchpanel/* \
    /sys/class/touch/*/* \
    /sys/devices/virtual/touch/*/* \
    /sys/devices/platform/kcal_ctrl.0/* \
    /sys/module/klapse/parameters/* \
    /proc/sys/net/ipv4/tcp_* \
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
    esac
    w=false
    if [ -w "$n" ]; then
        w=true
    else
        chmod 644 "$n" 2>/dev/null
        [ -w "$n" ] && w=true
    fi
    if [ "$w" = "true" ]; then
        count=$((count + 1))
        echo "$n" >> "$out"
    fi
done

echo "Total writable high-impact hardware nodes found: $count"
