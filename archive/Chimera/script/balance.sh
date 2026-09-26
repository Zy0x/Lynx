#!/system/bin/sh
#by Noir

# Path
MODDIR="/data/adb/modules/Chimera/"
MODPROP="$MODDIR/module.prop"

# Governor for cpu 4-7
GOV47=custom

# Get CPU freq
FREQ0=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq)
FREQ4=$(cat /sys/devices/system/cpu/cpu4/cpufreq/scaling_max_freq)
FREQ7=$(cat /sys/devices/system/cpu/cpu7/cpufreq/scaling_max_freq)

schedutil_tunables_bal03()
{
for eas in /sys/devices/system/cpu/cpu[0,1,2,3]/cpufreq/schedutil
do
  echo "$FREQ0" > $eas/hispeed_freq
  echo "99" > $eas/hispeed_load
  echo "500" > $eas/up_rate_limit_us
  echo "20000" > $eas/down_rate_limit_us
  echo "1" > $eas/pl
done
}

schedutil_tunables_bal47()
{
for eas in /sys/devices/system/cpu/cpu[4,5]/cpufreq/schedutil
do
  echo "$FREQ4" > $eas/hispeed_freq
  echo "99" > $eas/hispeed_load
  echo "500" > $eas/up_rate_limit_us
  echo "20000" > $eas/down_rate_limit_us
  echo "1" > $eas/pl
done

for eas in /sys/devices/system/cpu/cpu[6,7]/cpufreq/schedutil
do
  echo "$FREQ7" > $eas/hispeed_freq
  echo "99" > $eas/hispeed_load
  echo "0" > $eas/up_rate_limit_us
  echo "0" > $eas/down_rate_limit_us
  echo "1" > $eas/pl
done
}

# Switch power mode
# (0) default, (1) low, (2) moderate, (3) high performance/sport
echo "0" > /proc/cpufreq/cpufreq_power_mode 

# Switch cci mode
# (0) balance, (1) high performance
echo "0" > /proc/cpufreq/cpufreq_cci_mode

# Disable log cpu
echo "0" > /proc/cpufreq/cpufreq_debug

# Force CPU frequency to maximum mode
echo "0" > /proc/cpufreq/cpufreq_imax_enable
echo "Y" > /sys/module/workqueue/parameters/power_efficient

# Set Governor
for cpu in /sys/devices/system/cpu/cpu[0,1,2,3]
do
    echo "schedutil" > "$cpu/cpufreq/scaling_governor"
    echo "0" > "$cpu/sched_load_boost"
done
for cpu in /sys/devices/system/cpu/cpu[4,5,6,7]
do
    echo "$GOV47" > "$cpu/cpufreq/scaling_governor"
    echo "0" > "$cpu/sched_load_boost"
done

# Schedutil tunables
schedutil_tunables_bal03
#schedutil_tunables_bal47

echo "schedutil" > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor
echo "schedutil" > /sys/devices/system/cpu/cpufreq/policy6/scaling_governor

# CPU balance
for i in $(seq 0 7); do
  freqmax=$(cat /sys/devices/system/cpu/cpufreq/policy$i/cpuinfo_max_freq)
  freqmin=$(cat /sys/devices/system/cpu/cpufreq/policy$i/cpuinfo_min_freq)
  echo "$freqmax" > /sys/devices/system/cpu/cpufreq/policy$i/scaling_max_freq
  echo "$freqmin" > /sys/devices/system/cpu/cpufreq/policy$i/scaling_min_freq
done

# Enable PPM
echo "1" > /proc/ppm/enabled
echo "0 0" > /proc/ppm/policy_status
echo "1 1" > /proc/ppm/policy_status
echo "2 0" > /proc/ppm/policy_status
echo "3 0" > /proc/ppm/policy_status
echo "4 0" > /proc/ppm/policy_status
echo "5 0" > /proc/ppm/policy_status
echo "6 1" > /proc/ppm/policy_status
echo "7 1" > /proc/ppm/policy_status
echo "8 0" > /proc/ppm/policy_status
echo "9 1" > /proc/ppm/policy_status
echo "0" > /proc/ppm/cpi/cpi_enabled

#clust. 0
freq0="/proc/ppm/dump_cluster_0_dvfs_table"
if [ -f "$freq0" ]; then
    freqmax=$(awk '{print $1}' "$freq0")
    freqmin=$(tail -n 1 "$freq0" | awk '{print $NF}')
    echo "0 $freqmax" >/proc/ppm/policy/hard_userlimit_max_cpu_freq
    echo "0 $freqmin" >/proc/ppm/policy/hard_userlimit_min_cpu_freq
fi
#clust. 1
freq1="/proc/ppm/dump_cluster_1_dvfs_table"
if [ -f "$freq1" ]; then
    freqmax=$(awk '{print $1}' "$freq1")
    freqmin=$(tail -n 1 "$freq1" | awk '{print $NF}')
    echo "0 $freqmax" >/proc/ppm/policy/hard_userlimit_max_cpu_freq
    echo "0 $freqmin" >/proc/ppm/policy/hard_userlimit_min_cpu_freq
fi
#clust. 2
freq2="/proc/ppm/dump_cluster_2_dvfs_table"
if [ -f "$freq2" ]; then
    freqmax=$(awk '{print $1}' "$freq2")
    freqmin=$(tail -n 1 "$freq2" | awk '{print $NF}')
    echo "0 $freqmax" >/proc/ppm/policy/hard_userlimit_max_cpu_freq
    echo "0 $freqmin" >/proc/ppm/policy/hard_userlimit_min_cpu_freq
fi

# Enable EAS
echo "1" > /sys/devices/system/cpu/eas/enable
echo "1" > /sys/devices/system/cpu/perf/enable

# GPU Tweak
echo "always_on" > /sys/devices/platform/13000000.mali/power_policy
echo "1" > /proc/mali/always_on
echo "1" > /proc/mali/dvfs_enable
echo "0" > /proc/mali/debug_log
echo "1" > /sys/kernel/fpsgo/common/gpu_block_boost
echo "1" > /sys/module/ged/parameters/boost_amp
echo "0" > /sys/module/ged/parameters/boost_extra
echo "1" > /sys/module/ged/parameters/boost_gpu_enable
echo "100" > /sys/module/ged/parameters/cpu_boost_policy
echo "0" > /sys/module/ged/parameters/deboost_reduce
echo "1" > /sys/module/ged/parameters/enable_game_self_frc_detect
echo "1" > /sys/module/ged/parameters/enable_cpu_boost
echo "1" > /sys/module/ged/parameters/enable_gpu_boost
echo "60" > /sys/module/ged/parameters/g_fb_dvfs_threshold
echo "0" > /sys/module/ged/parameters/ged_boost_enable
echo "0" > /sys/module/ged/parameters/ged_force_mdp_enable
echo "0" > /sys/module/ged/parameters/ged_log_perf_trace_enable
echo "0" > /sys/module/ged/parameters/ged_log_trace_enable
echo "0" > /sys/module/ged/parameters/ged_monitor_3D_fence_debug
echo "0" > /sys/module/ged/parameters/ged_monitor_3D_fence_systrace
echo "0" > /sys/module/ged/parameters/ged_monitor_3D_fence_disable
echo "80" > /sys/module/ged/parameters/ged_smart_boost
echo "20" > /sys/module/ged/parameters/gpu_idle
max_val=$(head -n 1 /proc/gpufreq/gpufreq_opp_dump)
max_freq=$(echo "$val" | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
min_val=$(tail -n 1 /proc/gpufreq/gpufreq_opp_dump)
mix_freq=$(echo "$val" | grep -Eo 'freq = [0-9]+' | cut -d'=' -f2 | tr -d ' ')
chmod 644 /sys/module/ged/parameters/gpu_cust_upbound_freq
chmod 644 /sys/module/ged/parameters/gpu_cust_boost_freq
chmod 644 /sys/kernel/ged/hal/custom_upbound_gpu_freq
chmod 644 /sys/module/ged/parameters/gpu_bottom_freq
echo "$max_freq" > /sys/module/ged/parameters/gpu_cust_upbound_freq
echo "$max_freq" > /sys/module/ged/parameters/gpu_cust_boost_freq
echo "$max_freq" > /sys/kernel/ged/hal/custom_upbound_gpu_freq
echo "$min_freq" > /sys/module/ged/parameters/gpu_bottom_freq
echo "1" > /sys/module/ged/parameters/gx_boost_on
echo "0" >/sys/module/ged/parameters/gx_force_cpu_boost
echo "0" >/sys/module/ged/parameters/gx_frc_mode
echo "0" >/sys/module/ged/parameters/gx_game_mode
# echo "[masukkan pid game]" >/sys/module/ged/parameters/gx_top_app_pid
echo "3" > /proc/gpufreq/gpufreq_opp_freq
echo "1" > /proc/gpufreq/gpufreq_aging_enable
echo "1" > /sys/module/ged/parameters/gpu_dvfs_enable

# Enable sched. CPU freq.
echo "0" > /proc/cpufreq/cpufreq_sched_disable

# Arm performance level
#(0) on (1) interactive (2) off
echo "1" > /proc/cpuidle/control/armpll_mode

# Buck mode/supplying power to the CPU
#(0) on (1) low power (2) off
echo "0" > /proc/cpuidle/control/buck_mode

# CPU set
echo "0-7" > /dev/cpuset/foreground/cpus
echo "0-2" > /dev/cpuset/background/cpus
echo "0-5" > /dev/cpuset/system-background/cpus
echo "0-7" > /dev/cpuset/top-app/cpus
echo "0" > /dev/cpuset/restricted/cpus
echo "0-3" > /dev/cpuset/camera-daemon/cpus
echo "0-3" > /dev/cpuset/audio-app/cpus

# Disable Logging
echo "0" > /sys/kernel/ccci/debug

# Schedtune Boost Base
echo "1" > /dev/stune/schedtune.sched_boost_enabled
echo "5" > /dev/stune/schedtune.boost
echo "0" > /dev/stune/schedtune.sched_boost_no_override
echo "1" > /dev/stune/schedtune.prefer_idle
echo "0" > /dev/stune/schedtune.colocate
echo "0" > /dev/stune/cgroup.clone_children
echo "0" > /dev/stune/schedtune.util.min
echo "1024" > /dev/stune/schedtune.util.max
echo "1024" > /dev/stune/schedtune.util.max.effective
echo "0" > /dev/stune/schedtune.util.min.effective

# Schedtune Boost real time
echo "1" > /dev/stune/rt/schedtune.sched_boost_enabled
echo "5" > /dev/stune/rt/schedtune.boost
echo "0" > /dev/stune/rt/schedtune.sched_boost_no_override
echo "0" > /dev/stune/rt/schedtune.prefer_idle

# Schedtune Boost background
echo "1" > /dev/stune/background/schedtune.sched_boost_enabled
echo "0" > /dev/stune/background/schedtune.boost
echo "0" > /dev/stune/background/schedtune.sched_boost_no_override
echo "0" > /dev/stune/background/schedtune.prefer_idle
echo "0" > /dev/stune/background/schedtune.util.max.effective
echo "0" > /dev/stune/background/schedtune.util.min.effective
echo "0" > /dev/stune/background/schedtune.util.max
echo "0" > /dev/stune/background/schedtune.util.min

# Schedtune Boost foreground
echo "1" > /dev/stune/foreground/schedtune.sched_boost_enabled
echo "5" > /dev/stune/foreground/schedtune.boost
echo "1" > /dev/stune/foreground/schedtune.sched_boost_no_override
echo "0" > /dev/stune/foreground/schedtune.prefer_idle
echo "1024" > /dev/stune/foreground/schedtune.util.max.effective
echo "0" > /dev/stune/foreground/schedtune.util.min.effective
echo "1024" > /dev/stune/foreground/schedtune.util.max
echo "0" > /dev/stune/foreground/schedtune.util.min

# Schedtune Boost top app
echo "1" > /dev/stune/top-app/schedtune.sched_boost_enabled
echo "5" > /dev/stune/top-app/schedtune.boost
echo "0" > /dev/stune/top-app/schedtune.sched_boost_no_override
echo "0" > /dev/stune/top-app/schedtune.prefer_idle
echo "1024" > /dev/stune/top-app/schedtune.util.max.effective
echo "0" > /dev/stune/top-app/schedtune.util.min.effective
echo "1024" > /dev/stune/top-app/schedtune.util.max
echo "0" > /dev/stune/top-app/schedtune.util.min

# Additional
echo "40" > /proc/sys/vm/vfs_cache_pressure
echo "1" > /proc/sys/vm/stat_interval
echo "100" > /proc/sys/vm/watermark_scale_factor
echo "1500" > /proc/sys/vm/watermark_boost_factor
echo "0" > /proc/sys/vm/oom_dump_tasks

# Improve real time latencies by reducing the scheduler migration time
echo "32" > /proc/sys/kernel/sched_nr_migrate

# Limit max perf event processing time to this much CPU usage
echo "25" > /proc/sys/kernel/perf_cpu_time_max_percent

# For user UFS
echo "100" > /sys/devices/platform/soc/1d84000.ufshc/clkgate_delay_ms_perf
echo "5" > /sys/devices/platform/soc/1d84000.ufshc/clkgate_delay_ms_pwr_save

# Set load boost
for i in $(seq 0 7); do 
    echo "1" > /sys/devices/system/cpu/cpu$i/sched_load_boost
done

# Set cpu preferred
for cpu in 0 1 2 3 4 5 6 7
do
    echo "0 0 0 0" > "/sys/devices/system/cpu/cpu${cpu}/core_ctl/not_preferred"
done

# Fs
echo "50" > /proc/sys/fs/lease-break-time

# Thermal Interface
# Enabled Eara Thermal
echo "1" > /sys/kernel/eara_thermal/enable
echo "0" > /sys/kernel/eara_thermal/fake_throttle
echo "100 0" > /proc/driver/thermal/clatm_gpu_threshold
# FPSGO
echo "1" > /sys/kernel/fpsgo/common/gpu_block_boost
echo "1" > /sys/kernel/fpsgo/common/force_onoff
echo "1" > /sys/kernel/fpsgo/fbt/boost_ta
# Enable Thermal CPU
echo "1" > /sys/kernel/fpsgo/fbt/thrm_limit_cpu

# Enable sched boost
echo "1" > /proc/sys/kernel/sched_boost

# Entropy
echo "64" > /proc/sys/kernel/random/read_wakeup_threshold
echo "512" > /proc/sys/kernel/random/write_wakeup_threshold

sed -Ei "s/^description=\[.*\]/description=[ ❄️ Bᴀʟᴀɴᴄᴇ Mᴏᴅᴇ ]/" "$BASEDIR/module.prop"
am start -a android.intent.action.MAIN -e toasttext "❄️ Bᴀʟᴀɴᴄᴇ Mᴏᴅᴇ" -n bellavita.toast/.MainActivity