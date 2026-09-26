ufs_folders=( $(find /sys/devices/platform/soc/* -type d | grep ufs) )
for folder in "${ufs_folders[@]}"; do
  echo "0" > $folder/clkgate_enable
  echo "1" > $folder/clkscale_enable
  echo "0" > $folder/hibern8_on_idle_enable
done

# Sched Features
if [ -f /sys/kernel/debug/sched_features ]; then
    echo "NO_GENTLE_FAIR_SLEEPERS" > /sys/kernel/debug/sched_features
    echo "START_DEBIT" > /sys/kernel/debug/sched_features
    echo "NO_NEXT_BUDDY" > /sys/kernel/debug/sched_features
    echo "LAST_BUDDY" > /sys/kernel/debug/sched_features
    echo "STRICT_SKIP_BUDDY" > /sys/kernel/debug/sched_features
    echo "CACHE_HOT_BUDDY" > /sys/kernel/debug/sched_features
    echo "WAKEUP_PREEMPTION" > /sys/kernel/debug/sched_features
    echo "NO_HRTICK" > /sys/kernel/debug/sched_features
    echo "NO_DOUBLE_TICK" > /sys/kernel/debug/sched_features
    echo "LB_BIAS" > /sys/kernel/debug/sched_features
    echo "NONTASK_CAPACITY" > /sys/kernel/debug/sched_features
    echo "NO_TTWU_QUEUE" > /sys/kernel/debug/sched_features
    echo "SIS_AVG_CPU" > /sys/kernel/debug/sched_features
    echo "SIS_PROP" > /sys/kernel/debug/sched_features
    echo "NO_WARN_DOUBLE_CLOCK" > /sys/kernel/debug/sched_features
    echo "RT_PUSH_IPI" > /sys/kernel/debug/sched_features
    echo "NO_RT_RUNTIME_SHARE" > /sys/kernel/debug/sched_features
    echo "NO_LB_MIN" > /sys/kernel/debug/sched_features
    echo "ATTACH_AGE_LOAD" > /sys/kernel/debug/sched_features
    echo "NO_WA_IDLE" > /sys/kernel/debug/sched_features
    echo "WA_WEIGHT" > /sys/kernel/debug/sched_features
    echo "WA_BIAS" > /sys/kernel/debug/sched_features
    echo "NO_UTIL_EST" > /sys/kernel/debug/sched_features
    echo "NO_ENERGY_AWARE" > /sys/kernel/debug/sched_features
    echo "NO_EAS_PREFER_IDLE" > /sys/kernel/debug/sched_features
    echo "FIND_BEST_TARGET" > /sys/kernel/debug/sched_features
    echo "NO_FBT_STRICT_ORDER" > /sys/kernel/debug/sched_features
    echo "NO_SCHEDTUNE_BOOST_HOLD_ALL" > /sys/kernel/debug/sched_features
fi

# Test
echo "1" > /dev/memcg/memory.use_hierarchy
echo "1" > /dev/memcg/apps/memory.move_charge_at_immigrate
echo "0" > /sys/block/sda/queue/iosched/slice_idle
echo "1" > /sys/module/rcupdate/parameters/rcu_cpu_stall_suppress
echo "0" > /sys/module/rcupdate/parameters/rcu_cpu_stall_timeout
echo "1" > /sys/module/tcp_cubic/parameters/hystart
echo "1" > /sys/module/tcp_cubic/parameters/hystart_detect
echo "0" > /dev/cpuset/top-app/mem_exclusive
echo "0" > /dev/cpuset/foreground/cpu_exclusive
echo "0" > /dev/cpuset/top-app/memory_spread_slab
echo "0" > /dev/cpuset/foreground/memory_spread_slab
echo "0" > /dev/cpuset/top-app/memory_spread_page
echo "0" > /dev/cpuset/foreground/memory_spread_page
echo "0" > /dev/cpuset/cpu_exclusive 
echo "0" > /sys/kernel/fpsgo/common/fpsgo_enable
echo "0" > /sys/kernel/fpsgo/fbt/enable_switch_cap_margin
echo "0" > /sys/kernel/fpsgo/fbt/enable_switch_down_throttle
echo "0" > /sys/kernel/fpsgo/fbt/enable_switch_sync_flag
echo "0" > /sys/kernel/fpsgo/fbt/switch_idleprefer
echo "0" > /sys/kernel/fpsgo/fbt/thrm_activate_fps
echo "1000" > /sys/kernel/fpsgo/fbt/thrm_iso_pcbtemp_th
echo "0" > /sys/kernel/fpsgo/fbt/thrm_sub_cpu
echo "1500" > /sys/kernel/fpsgo/fbt/thrm_temp_th
echo "0" > /sys/kernel/fpsgo/fstb/fstb_debug
echo "0" > /sys/kernel/fpsgo/minitop/enable

echo "1111" > /sys/kernel/fpsgo/common/systrace_mask
echo "0" > /sys/kernel/fpsgo/fbt/enable_switch_cap_margin
echo "0" > /sys/kernel/fpsgo/fbt/enable_switch_down_throttle
echo "0" > /sys/kernel/fpsgo/fbt/limit_cfreq
echo "0" > /sys/kernel/fpsgo/fbt/limit_cfreq_m
echo "0" > /sys/kernel/fpsgo/fbt/limit_rfreq
echo "0" > /sys/kernel/fpsgo/fbt/limit_rfreq_m
echo "0" > /sys/kernel/fpsgo/fbt/thrm_activate_fps
echo "500" > /sys/kernel/fpsgo/fbt/thrm_iso_pcbtemp_th
echo "0" >/sys/kernel/fpsgo/fbt/thrm_limit_cpu

# Idle Proccess
echo "0" > /sys/kernel/fpsgo/fbt/switch_idleprefer

# Disable sync flag (cause tearing/stuttering)
echo "0" > /sys/kernel/fpsgo/fbt/enable_switch_sync_flag

# Increase Light Proccess
echo "100" > /sys/kernel/fpsgo/fbt/light_loading_policy_90
echo "100" > /sys/kernel/fpsgo/fbt/light_loading_policy
echo "100" > /sys/kernel/fpsgo/fbt/llf_task_policy
echo "100" > /sys/kernel/fpsgo/fbt/llf_task_policy_90

echo "0" > /sys/kernel/fpsgo/fstb/fstb_debug

# Set Margin
echo "0" > /sys/kernel/fpsgo/fstb/margin_mode
echo "0" > /sys/kernel/fpsgo/fstb/margin_mode_gpu
echo "15" > /sys/kernel/fpsgo/fstb/margin_mode_gpu_dbnc_a
echo "15" > /sys/kernel/fpsgo/fstb/margin_mode_gpu_dbnc_b
echo "15" > /sys/kernel/fpsgo/fstb/margin_mode_dbnc_a
echo "15" > /sys/kernel/fpsgo/fstb/margin_mode_dbnc_b

echo "0" > /sys/devices/virtual/thermal/thermal_message/suspend_temp

echo "0" > /proc/sys/kernel/sched_tunable_scaling

# Mem Opt
echo '100' > /dev/memcg/memory.swappiness
echo '90' > /dev/memcg/system/memory.swappiness
echo '100' > /dev/memcg/apps/memory.swappiness

for clear in $(cat /dev/memcg/system/cgroup.procs); do
    echo $clear > /dev/memcg/cgroup.procs
done

echo "1" > /sys/devices/platform/odm/odm:tran_battery/OTG_CTL
echo "1" > /proc/cpufreq/cpufreq_cci_mode
# Power level settings
for pl in /sys/devices/system/cpu/perf; do
    echo "1" > "$pl/gpu_pmu_enable"
    echo "1" > "$pl/fuel_gauge_enable"
    echo "1" > "$pl/enable"
    echo "1" > "$pl/charger_enable"
done