#!/system/bin/sh
#By Noir

# Waiting for boot completed
while [ "$(getprop sys.boot_completed | tr -d '\r')" != "1" ]; do sleep 3; done

# Detect temproot
if [ -e /data/local/tmp/magisk ]; then
  sleep 10
else
  sleep 3
fi

# Path
MODDIR=${0%/*}

# Variables
ZRAMSIZE=0
SWAPSIZE=0

# Device online functions
wait_until_login()
{
    # whether in lock screen, tested on Android 7.1 & 10.0
    # in case of other magisk module remounting /data as RW
    while [ "$(dumpsys window policy | grep mInputRestricted=true)" != "" ]; do
        sleep 3
    done
    # we doesn't have the permission to rw "/sdcard" before the user unlocks the screen
    while [ ! -d "/sdcard/Android" ]; do
        sleep 2
    done
}

# $1:task_name $2:cgroup_name $3:"cpuset"/"stune"
change_task_cgroup()
{
    # avoid matching grep itself
    # ps -Ao pid,args | grep kswapd
    # 150 [kswapd0]
    # 16490 grep kswapd
    local ps_ret
    ps_ret="$(ps -Ao pid,args)"
    for temp_pid in $(echo "$ps_ret" | grep "$1" | awk '{print $1}'); do
        for temp_tid in $(ls "/proc/$temp_pid/task/"); do
            echo "$temp_tid" > "/dev/$3/$2/tasks"
        done
    done
}

# $1:task_name $2:hex_mask(0x00000003 is CPU0 and CPU1)
change_task_affinity()
{
    # avoid matching grep itself
    # ps -Ao pid,args | grep kswapd
    # 150 [kswapd0]
    # 16490 grep kswapd
    local ps_ret
    ps_ret="$(ps -Ao pid,args)"
    for temp_pid in $(echo "$ps_ret" | grep "$1" | awk '{print $1}'); do
        for temp_tid in $(ls "/proc/$temp_pid/task/"); do
            taskset -p "$2" "$temp_tid"
        done
    done
}

# $1:task_name $2:nice(relative to 120)
change_task_nice()
{
    # avoid matching grep itself
    # ps -Ao pid,args | grep kswapd
    # 150 [kswapd0]
    # 16490 grep kswapd
    local ps_ret
    ps_ret="$(ps -Ao pid,args)"
    for temp_pid in $(echo "$ps_ret" | grep "$1" | awk '{print $1}'); do
        for temp_tid in $(ls "/proc/$temp_pid/task/"); do
            renice "$2" -p "$temp_tid"
        done
    done
}

# Zram functions
disable_zram()
{
    echo "3" > /proc/sys/vm/drop_caches
    swapoff /dev/block/zram0
    echo "0" > /sys/class/zram-control/hot_remove
}

change_zram()
{
    echo "3" > /proc/sys/vm/drop_caches
    swapoff /dev/block/zram0
    echo "1" > /sys/block/zram0/reset
    echo "$ZRAMSIZE" > /sys/block/zram0/disksize
    mkswap /dev/block/zram0
    swapon /dev/block/zram0
	su -lp 2000 -c "cmd notification post -S bigtext -t 'Lynx' tag '☢️ Zram Enabled ☢️'" >/dev/null 2>&1
}

# Swap functions
change_swap()
{
    if [ ! -e $MODDIR/swapram_installed ]; then
      if test -f "/data/swap"; then
        rm -f /data/swap
        dd if=/dev/zero of=/data/swap bs=1024 count=$SWAPSIZE
        mkswap /data/swap
        swapon /data/swap
      else
        dd if=/dev/zero of=/data/swap bs=1024 count=$SWAPSIZE
        mkswap /data/swap
        swapon /data/swap
      fi
      touch $MODDIR/swapram_installed
    else
      if test -f "/data/swap"; then
        swapon /data/swap
      else
        dd if=/dev/zero of=/data/swap bs=1024 count=$SWAPSIZE
        mkswap /data/swap
        swapon /data/swap
      fi
    fi
	su -lp 2000 -c "cmd notification post -S bigtext -t 'Lynx' tag '☣️ Swap Enabled ☣️'" >/dev/null 2>&1
}

# GMS doze functions
gms_doze_enable()
{
GMS="com.google.android.gms"
GC1="auth.managed.admin.DeviceAdminReceiver"
GC2="mdm.receivers.MdmDeviceAdminReceiver"
NLL="/dev/null"
for U in $(ls /data/user); do
  for C in $GC1 $GC2 $GC3; do
    pm disable --user $U "$GMS/$GMS.$C" &> $NLL
  done
done
dumpsys deviceidle whitelist -com.google.android.gms &> $NLL
}

# Dex2oat opt function
dex2oat_opt_enable()
{
sed -Ei 's/^description=(\[.*][[:space:]]*)?/description=[ ⛔ Dex2oat Optimizer is running... ] /g' "$MODDIR/module.prop"
su -lp 2000 -c "cmd notification post -S bigtext -t 'Lynx' tag '⛔ Dex2oat Optimizer is running...'" >/dev/null 2>&1
dexoat_opt
}

# Deepsleep functions
doze_default()
{
    dumpsys deviceidle enable
}

doze_light()
{
    sleep 3
    dumpsys deviceidle enable && settings put global device_idle_constants light_after_inactive_to=120000,light_pre_idle_to=120000,light_idle_to=600000,light_max_idle_to=3600000,locating_to=120000,location_accuracy=50,inactive_to=600000,sensing_to=120000,motion_inactive_to=300000,idle_after_inactive_to=300000
}

doze_moderate()
{
    sleep 3
    dumpsys deviceidle enable && settings put global device_idle_constants light_after_inactive_to=60000,light_pre_idle_to=60000,light_idle_to=900000,light_max_idle_to=10800000,locating_to=60000,location_accuracy=100,inactive_to=60000,sensing_to=60000,motion_inactive_to=60000,idle_after_inactive_to=60000,idle_to=7200000,max_idle_to=28800000,quick_doze_delay_to=30000,min_time_to_alarm=1800000
}

doze_high()
{
    sleep 3
    dumpsys deviceidle enable && settings put global device_idle_constants light_after_inactive_to=5000,light_pre_idle_to=30000,light_idle_to=1800000,light_max_idle_to=21600000,locating_to=10000,location_accuracy=500,inactive_to=30000,sensing_to=30000,motion_inactive_to=30000,idle_after_inactive_to=30000,idle_to=14400000,max_idle_to=43200000,quick_doze_delay_to=10000,min_time_to_alarm=600000
}

doze_extreme()
{
    sleep 3
    dumpsys deviceidle enable && settings put global device_idle_constants light_after_inactive_to=0,light_pre_idle_to=5000,light_idle_to=3600000,light_max_idle_to=43200000,locating_to=5000,location_accuracy=1000,inactive_to=0,sensing_to=0,motion_inactive_to=0,idle_after_inactive_to=0,idle_to=21600000,max_idle_to=172800000,quick_doze_delay_to=5000,min_time_to_alarm=300000
}

# Device online
wait_until_login

# Enable all tweak
sed -Ei 's/^description=(\[.*][[:space:]]*)?/description=[ 🚴 Apply tweaks please wait... ] /g' "$MODDIR/module.prop"
su -lp 2000 -c "cmd notification post -S bigtext -t 'Lynx' tag '🚴 Apply tweaks please wait...'" >/dev/null 2>&1

# Sync to data in the rare case a device crashes
sync

# Change zram
#change_zram

# Swap ram
#change_swap

# Color management
service call SurfaceFlinger 1023 i32 1

# Increase Display Saturation
service call SurfaceFlinger 1022 f 1.05

# DNS Changer
DNS_CloudflareXGoogle()
(
	iptables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination 1.1.1.1:53
	iptables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 1.0.0.1:53
	iptables -t nat -I OUTPUT -p tcp --dport 53 -j DNAT --to-destination 1.1.1.1:53
	iptables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination 1.0.0.1:53
)

DNS_Google()
(
	iptables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination 8.8.8.8:53
	iptables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 8.8.4.4:53
	iptables -t nat -I OUTPUT -p tcp --dport 53 -j DNAT --to-destination 8.8.8.8:53
	iptables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination 8.8.4.4:53
)

DNS_Cloudflare()
(
	iptables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination 1.1.1.1:53
	iptables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 1.0.0.1:53
	iptables -t nat -I OUTPUT -p tcp --dport 53 -j DNAT --to-destination 1.1.1.1:53
	iptables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination 1.0.0.1:53
)

# DNS
#change_dns

# Task scheduler
echo "0" > /proc/sys/kernel/sched_tunable_scaling
echo "0" > /proc/sys/kernel/sched_child_runs_first
echo "0" > /proc/sys/kernel/timer_migration
echo "1" > /proc/sys/kernel/sched_autogroup_enabled
echo "15" > /proc/sys/kernel/sched_min_task_util_for_boost
echo "100" > /proc/sys/kernel/sched_rr_timeslice_ns
echo "1000" > /proc/sys/kernel/sched_min_task_util_for_colocation
echo "950000" > /proc/sys/kernel/sched_rt_runtime_us
echo "1000000" > /proc/sys/kernel/sched_rt_period_us
echo "5000000" > /proc/sys/kernel/sched_migration_cost_ns
echo "0" > /sys/kernel/rcu_expedited

# Disable kernel tracking
echo "0" > /proc/sys/kernel/tracing/tracing_on

# Timeslice process
echo "550" > /proc/sys/kernel/sched_rr_timeslice_ms

# Enable dynamic ravg window
echo "1" > /proc/sys/kernel/sched_dynamic_ravg_window_enable
echo "4" > /proc/sys/kernel/sched_ravg_window_nr_ticks

# Enable kernel fsync
echo "1" > /sys/kernel/dyn_fsync/Dyn_fsync_active

# Enable scroll acceleration
echo "Y" > /sys/module/hid_magicmouse/parameters/scroll_acceleration

# Disable spi CRC
echo "0" > /sys/module/mmc_core/parameters/use_spi_crc
echo "0" > /sys/module/mmc_core/parameters/removable
echo "0" > /sys/module/mmc_core/parameters/crc

# Mglru
echo "5000" > /sys/kernel/mm/lru_gen/min_ttl_ms

# Lpm
echo "0" > /sys/module/lpm_levels/parameters/lpm_prediction
echo "0" > /sys/module/lpm_levels/parameters/sleep_disabled

# Disable printk
echo "0 0 0 0" > /proc/sys/kernel/printk
echo "off" > /proc/sys/kernel/printk_devkmsg

# Enable scroll acceleration
echo "Y" > /sys/module/hid_magicmouse/parameters/scroll_acceleration

# Disable kernel panic
echo "0" > /proc/sys/kernel/panic
echo "0" > /proc/sys/kernel/panic_on_oops
echo "0" > /proc/sys/kernel/panic_on_warn
echo "0" > /sys/module/kernel/parameters/panic
echo "0" > /sys/module/kernel/parameters/panic_on_warn
echo "0" > /sys/module/kernel/parameters/pause_on_oops

# Cpu Efficient
echo "Y" > /sys/module/workqueue/parameters/power_efficient

# Enable fast socket open for receiver and sender
echo "3" > /proc/sys/net/ipv4/tcp_fastopen

# Disable cpu input boost
echo "0" > /sys/module/cpu_boost/parameters/sched_boost_on_input
echo "0" > /sys/module/cpu_boost/parameters/input_boost_ms
echo "0:0" > /sys/module/cpu_boost/parameters/input_boost_freq
echo "1:0" > /sys/module/cpu_boost/parameters/input_boost_freq
echo "2:0" > /sys/module/cpu_boost/parameters/input_boost_freq
echo "3:0" > /sys/module/cpu_boost/parameters/input_boost_freq
echo "4:0" > /sys/module/cpu_boost/parameters/input_boost_freq
echo "5:0" > /sys/module/cpu_boost/parameters/input_boost_freq
echo "6:0" > /sys/module/cpu_boost/parameters/input_boost_freq
echo "7:0" > /sys/module/cpu_boost/parameters/input_boost_freq

# Disable Adreno snapshot crashdumper
echo "0" > /sys/class/kgsl/kgsl-3d0/snapshot/snapshot_crashdumper

# Cgroup Tweak
if [ -e /dev/stune/top-app/uclamp.max ]; then
  # Uclamp Tweak
  for ta in /dev/cpuset/*/top-app
  do
    echo "max" > "$ta/uclamp.max"
    echo "10" > "$ta/uclamp.min"
    echo "1" > "$ta/uclamp.boosted"
    echo "1" > "$ta/uclamp.latency_sensitive"
  done
  for fd in /dev/cpuset/*/foreground
  do
    echo "50" > "$fd/uclamp.max"
    echo "0" > "$fd/uclamp.min"
    echo "0" > "$fd/uclamp.boosted"
    echo "0" > "$fd/uclamp.latency_sensitive"
  done
  for bd in /dev/cpuset/*/background
  do
    echo "max" > "$bd/uclamp.max"
    echo "20" > "$bd/uclamp.min"
    echo "0" > "$bd/uclamp.boosted"
    echo "0" > "$bd/uclamp.latency_sensitive"
  done
  for sb in /dev/cpuset/*/system-background
  do
    echo "40" > "$sb/uclamp.max"
    echo "0" > "$sb/uclamp.min"
    echo "0" > "$sb/uclamp.boosted"
    echo "0" > "$sb/uclamp.latency_sensitive"
  done
  sysctl -w kernel.sched_util_clamp_min_rt_default=0
  sysctl -w kernel.sched_util_clamp_min=128
fi

# Disable Ramdumps
if [ -d "/sys/module/subsystem_restart/parameters" ]
then
    echo "0" > /sys/module/subsystem_restart/parameters/enable_ramdumps
    echo "0" > /sys/module/subsystem_restart/parameters/enable_mini_ramdumps
fi

# Virtual memory tweaks
echo "7" > /proc/sys/vm/dirty_background_ratio
echo "15" > /proc/sys/vm/dirty_ratio
echo "3000" > /proc/sys/vm/dirty_expire_centisecs
echo "500" > /proc/sys/vm/dirty_writeback_centisecs
echo "750" > /proc/sys/vm/extfrag_threshold
echo "100" > /proc/sys/vm/swappiness
echo "0" > /proc/sys/vm/page-cluster
echo "0" > /proc/sys/vm/oom_kill_allocating_task
echo "8192" > /proc/sys/vm/min_free_kbytes
echo "0" > /proc/sys/kernel/sched_schedstats
echo "0 0 0 0" > /proc/sys/kernel/printk
echo "off" > /proc/sys/kernel/printk_devkmsg
echo "11007" > /proc/sys/vm/min_free_kbytes
echo "29615" > /proc/sys/vm/extra_free_kbytes
echo "N" > /sys/module/lpm_levels/parameters/lpm_prediction

# Disable all Log and Debug Mask
for dbgmsk in $(find /sys/ -name debug_mask)
do
echo "0" > $dbgmsk
done

for dbglv in $(find /sys/ -name debug_level)
do
echo "0" > $dbglv
done

for evlog in $(find /sys/ -name enable_event_log)
do
echo "0" > $evlog
done

# LMK
echo "1" > /sys/module/lowmemorykiller/parameters/enable_lmk
echo "0" > /sys/module/lowmemorykiller/parameters/enable_adaptive_lmk
chmod 666 /sys/module/lowmemorykiller/parameters/minfree
chown root /sys/module/lowmemorykiller/parameters/minfree
echo "14535,29070,43605,58112,72675,87210" > /sys/module/lowmemorykiller/parameters/minfree
chmod 444 /sys/module/lowmemorykiller/parameters/minfree

# I/O scheduler
for queue in /sys/block/*/queue
do
	# Do not use I/O as a source of randomness
	echo "0" > "$queue/add_random"
	# Disable I/O statistics accounting
	echo "0" > "$queue/iostats"
	# I/O Affinity
	echo "1" > "$queue/rq_affinity"
	# Reduce the maximum number of I/O requests in exchange for latency
	echo "128" > "$queue/nr_requests"
	# Disable Merges
	echo "2" > "$queue/nomerges"	
done
#ram speed
for qram in /sys/block/ram*/queue
do
	echo "4096" > $qram/read_ahead_kb
done
#internal storage
 for int in /sys/block/sd*/queue
 do
   echo "cfq" > $int/scheduler
   echo "4096" > $int/read_ahead_kb
   echo "128" > $int/nr_requests
   echo "0" > $int/iosched/slice_idle
   echo "0" > $int/iosched/slice_idle_us
   echo "0" > $int/iosched/group_idle
   echo "0" > $int/iosched/group_idle_us
   echo "1" > $int/iosched/low_latency
 done
#external storage
 for ext in /sys/block/mmc*/queue
 do
   echo "cfq" > $ext/scheduler
   echo "4096" > $ext/read_ahead_kb
   echo "128" > $ext/nr_requests
   echo "2" > $ext/nomerges
   echo "1" > $ext/rq_affinity
   echo "0" > $ext/iostats
   echo "0" > $ext/add_random
   echo "0" > $ext/iosched/slice_idle
   echo "0" > $ext/iosched/slice_idle_us
   echo "0" > $ext/iosched/group_idle
   echo "0" > $ext/iosched/group_idle_us
   echo "1" > $ext/iosched/low_latency
 done
#dm
 for dmblk in /sys/block/dm-*/queue
 do
   echo "4096" > $dmblk/read_ahead_kb
 done
#loop
 for loop in /sys/block/loop*/queue
 do
   echo "4096" > $dmblk/read_ahead_kb
 done

# Disable debuggers
for bl in /sys/module/bluetooth/parameters/
do
if [[ -e "${bl}disable_ertm" ]]
then
echo "Y" > $bl/disable_ertm
echo "Y" > $bl/disable_esco
fi
done

# Reduce the maximum scheduling period for lower latency
echo "250" > /proc/sys/kernel/sched_latency_ns

# Require preeptive tasks to surpass half of a sched period in vmruntime
echo "1000" > /proc/sys/kernel/sched_wakeup_granularity_ns

# Schedule this ratio of tasks in the guarenteed sched period
echo "500" > /proc/sys/kernel/sched_min_granularity_ns

# Additional
echo "0" > /sys/module/usb_bam/parameters/enable_event_log
echo "Y" > /sys/module/printk/parameters/ignore_loglevel
echo "N" > /sys/module/printk/parameters/time
echo "Y" > /sys/module/bluetooth/parameters/disable_ertm
echo "Y" > /sys/module/bluetooth/parameters/disable_esco
echo "0" > /sys/module/hid_apple/parameters/fnmode
echo "N" > /sys/module/ip6_tunnel/parameters/log_ecn_error
echo "N" > /sys/module/sit/parameters/log_ecn_error
echo "N" > /sys/module/hid_magicmouse/parameters/emulate_3button
echo "N" > /sys/module/hid_magicmouse/parameters/emulate_scroll_wheel
echo "0" > /sys/module/hid_magicmouse/parameters/scroll_speed
echo "0" > /sys/module/service_locator/parameters/enable
echo "1" > /sys/module/subsystem_restart/parameters/disable_restart_work
echo "0" > /sys/module/rmnet_data/parameters/rmnet_data_log_level
echo "0" > /sys/module/diagchar/parameters/diag_mask_clear_param
echo "0" > /sys/module/icnss/parameters/dynamic_feature_mask
echo "N" > /sys/module/ppp_generic/parameters/mp_protocol_compress
echo "0" > /sys/module/dwc3/parameters/ep_addr_rxdbg_mask
echo "0" > /sys/module/dwc3/parameters/ep_addr_txdbg_mask
echo "0" > /sys/module/dwc3/parameters/enable_dwc3_u1u2
echo "0" > /sys/module/edac_core/parameters/edac_mc_log_ce
echo "0" > /sys/module/edac_core/parameters/edac_mc_log_ue
echo "0" > /sys/module/glink/parameters/debug_mask
echo "N" > /sys/module/hid_magicmouse/parameters/emulate_3button
echo "0" > /sys/module/hid_apple/parameters/fnmode
echo "N" > /sys/module/hid_magicmouse/parameters/emulate_scroll_wheel
echo "0" > /sys/module/lowmemorykiller/parameters/debug_level
echo "0" > /sys/module/msm_smem/parameters/debug_mask
echo "0" > /sys/module/service_locator/parameters/enable
echo "N" > /sys/module/sit/parameters/log_ecn_error
echo "0" > /sys/module/msm_smp2p/parameters/debug_mask
echo "0" > /sys/module/usb_bam/parameters/enable_event_log
echo "0" > /proc/sys/fs/dir-notify-enable
echo "2000" > /sys/power/pm_freeze_timeout
echo "1" > /sys/class/devfreq/5000000.qcom,kgsl-3d0/device/kgsl/kgsl-3d0/pwrscale
echo "0" > /sys/class/devfreq/5000000.qcom,kgsl-3d0/device/kgsl/kgsl-3d0/snapshot/force_panic
echo "0" > /sys/class/devfreq/5000000.qcom,kgsl-3d0/device/kgsl/kgsl-3d0/dispatch/fault_throttle_burst
echo "10" > /sys/class/devfreq/soc:qcom,gpubw/polling_interval
echo "10" > /sys/class/devfreq/soc:qcom,l3-cdsp/polling_interval
echo "400" > /sys/class/devfreq/soc:qcom,l3-cpu4/mem_latency/ratio_ceil

# Disable LPM for performance
for lpmpc in /sys/module/lpm_levels/L3/*/pc
do
	echo "N" > $lpmpc/idle_enabled
done

for lpmrail in /sys/module/lpm_levels/L3/*/rail-pc
do
	echo "N" > $lpmrail/idle_enabled
done

# Core performance
echo "1" > /d/dri/0/debug/core_perf/perf_mode

# Internet Function
Internet_Tweak()
(
	echo "1" > /proc/sys/net/ipv4/tcp_ecn
	echo "0" > /proc/sys/net/ipv4/tcp_timestamps
	echo "1" > /proc/sys/net/ipv4/tcp_rfc1337
	echo "1" > /proc/sys/net/ipv4/tcp_tw_reuse
	echo "1" > /proc/sys/net/ipv4/tcp_tw_recycle
	echo "5" > /proc/sys/net/ipv4/tcp_keepalive_probes
	echo "6" > /proc/sys/net/ipv4/tcp_probe_threshold
	echo "15" > /proc/sys/net/ipv4/tcp_keepalive_intvl
	echo "7" > /proc/sys/net/ipv4/tcp_fin_timeout
	echo "80" > /proc/sys/net/ipv4/tcp_pacing_ca_ratio
	echo "150" > /proc/sys/net/ipv4/tcp_pacing_ss_ratio
	echo "12582912" > /proc/sys/net/core/wmem_max
	echo "12582912" > /proc/sys/net/core/rmem_max
	echo "31457280" > /proc/sys/net/core/rmem_default
	echo "31457280" > /proc/sys/net/core/wmem_default
	echo "400" > /proc/sys/net/ipv4/tcp_probe_interval
	echo "16384" > /proc/sys/net/ipv4/udp_rmem_min
	echo "8192 65536 16777216" > /proc/sys/net/ipv4/tcp_wmem
	echo "8192 87380 16777216" > /proc/sys/net/ipv4/tcp_rmem
	echo "16384" > /proc/sys/net/ipv4/udp_wmem_min 
	echo "65536 131072 262144" > /proc/sys/net/ipv4/tcp_mem
	echo "4296 87380 404480" > /proc/sys/net/ipv4
	echo "1" > /proc/sys/net/ipv4/tcp_low_latency
	echo "0" > /proc/sys/net/ipv4/tcp_slow_start_after_idle
	echo "65536 131072 262144" > /proc/sys/net/ipv4/udp_mem
	echo "2" > /proc/sys/net/ipv4/tcp_synack_retries
	echo "65536" > /proc/sys/net/core/netdev_max_backlog
	echo "1440000" > /proc/sys/net/ipv4/tcp_max_tw_buckets
	echo "2" > /proc/sys/net/ipv4/tcp_syn_retries
	echo "300" > /proc/sys/net/ipv4/tcp_keepalive_time
	echo "1" > /proc/sys/net/ipv4/tcp_no_metrics_save
	echo "0" > /proc/sys/net/ipv4/conf/all/send_redirects
	echo "1" > /proc/sys/net/ipv4/conf/all/rp_filter
	echo "1" > /proc/sys/net/ipv4/conf/all/log_martians
	echo "0" > /proc/sys/net/ipv4/conf/all/secure_redirects
	echo "3" > /proc/sys/net/ipv4/tcp_retries1
	echo "15" > /proc/sys/net/ipv4/tcp_retries2
	echo "50" > /proc/sys/net/unix/max_dgram_qlen
	echo "196608" > /proc/sys/net/ipv6/ip6frag_low_thresh
	echo "262144" > /proc/sys/net/ipv6/ip6frag_high_thresh
	echo "196608" > /proc/sys/net/ipv4/ipfrag_low_thresh
	echo "262144" > /proc/sys/net/ipv4/ipfrag_high_thresh
	echo "fq" > /proc/sys/net/core/default_qdisc
	echo "16384" > /proc/sys/net/ipv4/tcp_notsent_lowat
	echo "4096" > /proc/sys/net/core/somaxconn
	echo "25165824" > /proc/sys/net/core/optmem_max
	echo "10000000" > /proc/sys/net/netfilter/nf_conntrack_max
	echo "0" > /proc/sys/net/netfilter/nf_conntrack_tcp_loose
	echo "1800" > /proc/sys/net/netfilter/nf_conntrack_tcp_timeout_established
	echo "10" > /proc/sys/net/netfilter/nf_conntrack_tcp_timeout_close_wait
	echo "20" > /proc/sys/net/netfilter/nf_conntrack_tcp_timeout_fin_wait
	echo "20" > /proc/sys/net/netfilter/nf_conntrack_tcp_timeout_last_ack
	echo "20" > /proc/sys/net/netfilter/nf_conntrack_tcp_timeout_syn_recv
	echo "20" > /proc/sys/net/netfilter/nf_conntrack_tcp_timeout_syn_sent
	echo "10" > /proc/sys/net/netfilter/nf_conntrack_tcp_timeout_time_wait
	echo "16384 65535" > /proc/sys/net/ipv4/ip_local_port_range
	echo "0" > /proc/sys/net/ipv4/ip_no_pmtu_disc
	echo "1" > /proc/sys/net/ipv4/route.flush
	echo "1" > /proc/sys/net/ipv4/tcp_rfc1337
	echo "1" > /proc/sys/net/ipv4/tcp_sack
	echo "1" > /proc/sys/net/ipv4/tcp_fack
	echo "1" > /proc/sys/net/ipv4/tcp_window_scaling
	echo "0" > /proc/sys/net/ipv4/tcp_syncookies
	echo "3" > /proc/sys/vm/drop_caches
)

# Internet Tweak
#Internet_Tweak

# For UI System
window=
trans=
anim=
animation_system()
{
settings put global window_animation_scale $window
settings put global transition_animation_scale $trans
settings put global animator_duration_scale $anim
}

# Animation Tweak
#animation
settings put secure long_press_timeout 280
settings put secure multi_press_timeout 80

# Entropy
echo "64" > /proc/sys/kernel/random/read_wakeup_threshold
echo "256" > /proc/sys/kernel/random/write_wakeup_threshold

# Blkio
if [ -d /dev/blkio ]; then
  echo "1000" > /dev/blkio/blkio.weight
  echo "200" > /dev/blkio/background/blkio.weight
  echo "2000" > /dev/blkio/blkio.group_idle
  echo "0" > /dev/blkio/background/blkio.group_idle
fi

# Activity manager
if [ $(getprop ro.build.version.sdk) -gt 28 ]; then
  device_config set_sync_disabled_for_tests none
  device_config put activity_manager max_cached_processes 64
  device_config put activity_manager max_empty_time_millis 1800000
  device_config put activity_manager max_phantom_processes 32
  settings put global settings_enable_monitor_phantom_procs true
else
  settings put global activity_manager_constants max_cached_processes=64
fi

#Dexoat Optimizer
#dex2oat_opt_enable

#x1

# Enable GMS doze
#gms_doze_enable

# Doze mode
#dozemode

# Treat crtc_commit as background, avoid display preemption on big
change_task_cgroup "crtc_commit" "system-background" "cpuset"

# Fix laggy scrolling
change_task_cgroup "servicemanager" "top-app" "cpuset"
change_task_cgroup "servicemanager" "foreground" "stune"
change_task_cgroup "android.phone" "top-app" "cpuset"
change_task_cgroup "android.phone" "foreground" "stune"

# Fix laggy home gesture
change_task_cgroup "system_server" "top-app" "cpuset"
change_task_cgroup "system_server" "foreground" "stune"

# Reduce render thread waiting time
change_task_cgroup "surfaceflinger" "top-app" "cpuset"
change_task_cgroup "surfaceflinger" "foreground" "stune"
change_task_cgroup "android.hardware.graphics.composer" "top-app" "cpuset"
change_task_cgroup "android.hardware.graphics.composer" "foreground" "stune"
change_task_nice "surfaceflinger" "-20"
change_task_nice "android.hardware.graphics.composer" "-20"

# Reduce big cluster wakeup except fingerprint and camera
change_task_affinity ".hardware." "0f"
change_task_affinity ".hardware.biometrics.fingerprint" "ff"
change_task_affinity ".hardware.camera.provider" "ff"

# Kernel reclaim threads run on more power-efficient cores
change_task_nice "kswapd" "-2"
change_task_nice "oom_reaper" "-2"
change_task_affinity "kswapd" "fe"
change_task_affinity "oom_reaper" "8"

# System smoothness
change_task_nice "system_server" "-20"

# Unitytrick functions
unitytrick_enable()
{
    chmod 000 /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq
    chmod 000 /sys/devices/system/cpu/cpu1/cpufreq/cpuinfo_max_freq
    chmod 000 /sys/devices/system/cpu/cpu2/cpufreq/cpuinfo_max_freq
    chmod 000 /sys/devices/system/cpu/cpu3/cpufreq/cpuinfo_max_freq
    chmod 000 /sys/devices/system/cpu/cpu4/cpufreq/cpuinfo_max_freq
    chmod 000 /sys/devices/system/cpu/cpu5/cpufreq/cpuinfo_max_freq
    chmod 000 /sys/devices/system/cpu/cpu6/cpufreq/cpuinfo_max_freq
    chmod 000 /sys/devices/system/cpu/cpu7/cpufreq/cpuinfo_max_freq
    chmod 000 /sys/devices/system/cpu/cpu0/cpu_capacity
    chmod 000 /sys/devices/system/cpu/cpu1/cpu_capacity
    chmod 000 /sys/devices/system/cpu/cpu2/cpu_capacity
    chmod 000 /sys/devices/system/cpu/cpu3/cpu_capacity
    chmod 000 /sys/devices/system/cpu/cpu4/cpu_capacity
    chmod 000 /sys/devices/system/cpu/cpu5/cpu_capacity
    chmod 000 /sys/devices/system/cpu/cpu6/cpu_capacity
    chmod 000 /sys/devices/system/cpu/cpu7/cpu_capacity
    chmod 000 /sys/devices/system/cpu/cpu0/topology/physical_package_id
    chmod 000 /sys/devices/system/cpu/cpu1/topology/physical_package_id
    chmod 000 /sys/devices/system/cpu/cpu2/topology/physical_package_id
    chmod 000 /sys/devices/system/cpu/cpu3/topology/physical_package_id
    chmod 000 /sys/devices/system/cpu/cpu4/topology/physical_package_id
    chmod 000 /sys/devices/system/cpu/cpu5/topology/physical_package_id
    chmod 000 /sys/devices/system/cpu/cpu6/topology/physical_package_id
    chmod 000 /sys/devices/system/cpu/cpu7/topology/physical_package_id
}

# Unity Big.Little trick by lybxlpsv 
#unitytrick_enable

# Fstrim
fstrim /system
fstrim /vendor
fstrim /metadata
fstrim /odm
fstrim /system_ext
fstrim /product
fstrim /data
fstrim /cache
for sd in /storage/*; do
  fstrim -v ${sd}
done

# Disable System Log
pm uninstall --user 0 com.android.traceur

# Disable Some Service Log
stop logcat logcatd logd tcpdump cnss_diag statsd traced idd-logreader idd-logreadermain stats dumpstate aplogd vendor_tcpdump vendor.tcpdump vendor.cnss_diag

# Low Latency Wi-Fi
cmd wifi force-low-latency-mode enabled

# Clear Wi-Fi Logs
rm -rf /data/vendor/wlan_logs
touch /data/vendor/wlan_logs
chmod 000 /data/vendor/wlan_logs

# Disable Kernel Debugging
for i in 'debug_mask' 'log_level*' 'debug_level*' '*debug_mode' 'edac_mc_log*' 'enable_event_log' '*log_level*' '*log_ue*' '*log_ce*' 'log_ecn_error' 'snapshot_crashdumper' 'seclog*' 'compat-log' '*log_enabled' 'tracing_on' 'mballoc_debug'; do
    for o in $(find /sys/ -type f -name "$i"); do
        echo '0' > "$o"
    done
done

#Disable Adreno GPU Logging
echo "0" > /sys/kernel/debug/kgsl/kgsl-3d0/log_level_cmd
echo "0" > /sys/kernel/debug/kgsl/kgsl-3d0/log_level_ctxt
echo "0" > /sys/kernel/debug/kgsl/kgsl-3d0/log_level_drv
echo "0" > /sys/kernel/debug/kgsl/kgsl-3d0/log_level_mem
echo "0" > /sys/kernel/debug/kgsl/kgsl-3d0/log_level_pwr

# Ram Cleaner
while true; do echo 20 > /proc/sys/vm/drop_caches; sleep 3; done &
su -lp 2000 -c "cmd notification post -S bigtext -t 'Lynx' tag '🚀 Auto Ram Cleaner Enabled'" >/dev/null 2>&1

# Cache Cleaner
sleep 1
echo "" >> /storage/emulated/0/Lynx/Cleaner.log
echo "# Cache Cleaner" >> /storage/emulated/0/Lynx/Cleaner.log
echo " • Status : Executed" >> /storage/emulated/0/Lynx/Cleaner.log
echo " • Date : $(date '+%A, %d/%m/%Y at %H:%M:%S')" >> /storage/emulated/0/Lynx/Cleaner.log
echo "--------LOG--------" >> /storage/emulated/0/Lynx/Cleaner.log
sleep 1
find /data/data/* -type d -iname '*cache*' -not \( -ipath "*/*hkrpgoversea*/*" -o -ipath "*/*genshin*/*" \) -exec find {} -type f -delete \;
find /data/data/*/* -type d -iname '*cache*' -not \( -ipath "*/*hkrpgoversea*/*" -o -ipath "*/*genshin*/*" \) -exec find {} -type f -delete \;
find /data/media/0/Android/data/* -type d -iname '*cache*' -not \( -ipath "*/*hkrpgoversea*/*" -o -ipath "*/*genshin*/*" \) -exec find {} -type f -delete \;
find /data/media/0/Android/data/*/* -type d -iname '*cache*' -not \( -ipath "*/*hkrpgoversea*/*" -o -ipath "*/*genshin*/*" \) -exec find {} -type f -delete \;
find /data/media/0/Android/data/*/*/* -type d -iname '*cache*' -not \( -ipath "*/*hkrpgoversea*/*" -o -ipath "*/*genshin*/*" \) -exec find {} -type f -delete \;
find /data_mirror/data_ce/null/0/* -type d -iname '*cache*' -not \( -ipath "*/*hkrpgoversea*/*" -o -ipath "*/*genshin*/*" \) -exec find {} -type f -delete \;
find /data_mirror/data_ce/null/0/*/* -type d -iname '*cache*' -not \( -ipath "*/*hkrpgoversea*/*" -o -ipath "*/*genshin*/*" \) -exec find {} -type f -delete \;
find /data_mirror/data_de/null/0/* -type d -iname '*cache*' -not \( -ipath "*/*hkrpgoversea*/*" -o -ipath "*/*genshin*/*" \) -exec find {} -type f -delete \;
find /data_mirror/data_de/null/0/*/* -type d -iname '*cache*' -not \( -ipath "*/*hkrpgoversea*/*" -o -ipath "*/*genshin*/*" \) -exec find {} -type f -delete \;
find /data/user_de/0/* -type d -iname '*cache*' -not \( -ipath "*/*hkrpgoversea*/*" -o -ipath "*/*genshin*/*" \) -exec find {} -type f -delete \;
find /data/user_de/0/*/* -type d -iname '*cache*' -not \( -ipath "*/*hkrpgoversea*/*" -o -ipath "*/*genshin*/*" \) -exec find {} -type f -delete \;
find /data/user_de/0/*/*/* -type d -iname '*cache*' -not \( -ipath "*/*hkrpgoversea*/*" -o -ipath "*/*genshin*/*" \) -exec find {} -type f -delete \;
find /data/user/0/* -type d -iname '*cache*' -not \( -ipath "*/*hkrpgoversea*/*" -o -ipath "*/*genshin*/*" \) -exec find {} -type f -delete \;
find /data/user/0/*/* -type d -iname '*cache*' -not \( -ipath "*/*hkrpgoversea*/*" -o -ipath "*/*genshin*/*" \) -exec find {} -type f -delete \;
find /data/user/0/*/*/* -type d -iname '*cache*' -not \( -ipath "*/*hkrpgoversea*/*" -o -ipath "*/*genshin*/*" \) -exec find {} -type f -delete \;
find /cache/ -mindepth 1 ! -name 'magisk.log' -exec rm -rf {} \;
rm -rf /data/vendor/thermal/*
rm -rf /data/vendor/wlan_logs
rm -rf /data/anr/*
rm -rf /data/log_other_mode/*
rm -rf /cache/*.tmp
rm -rf /dev/log/*
rm -rf /data/tombstones/*
rm -rf /data/system/dropbox/*
rm -rf /data/log/*
rm -rf /sys/kernel/debug/*
rm -rf /dev/log/main
rm -rf /data/system/package_cache/*
rm -rf /data/media/0/mtklog
rm -rf /data/media/0/MIUI/.debug_log
rm -rf /data/media/0/MIUI/BugReportCache
rm -rf /data/log/*
rm -rf /data/logger/*
rm -rf /data/tombstones/*
rm -rf /data/system/usagestats/*
rm -rf /sdcard/LOST.DIR
rm -rf /sdcard/DCIM/.thumbnails
rm -rf /sdcard/bugreports/*
echo "Cache Cleaned" >> /storage/emulated/0/Lynx/Cleaner.log
am start -a android.intent.action.MAIN -e toasttext "Cache Cleaned" -n bellavita.toast/.MainActivity
sleep 1

# Fast Charging
Fast_Charge_Enable()
{
nohup sh /$MODDIR/script/fast_charge.sh > /dev/null 2>&1 & setsid sh -c 'echo $$ >> /$MODDIR/script/pidfile'
}
Fast_Charge_Enable

# Done
sed -Ei 's/^description=(\[.*][[:space:]]*)?/description=[ ✅ All tweaks is applied... ] /g' "$MODDIR/module.prop"
su -lp 2000 -c "cmd notification post -S bigtext -t 'Lynx' tag '✅ All tweaks is applied...'" >/dev/null 2>&1

# Run Ai
sleep 3
nohup sh $MODDIR/script/lynx_auto.sh &