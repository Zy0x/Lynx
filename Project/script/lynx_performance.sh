#!/system/bin/sh
#By Noir

# Sync to data in the rare case a device crashes
sync

# Path
BASEDIR=/data/adb/modules/Lynx
LOG=/storage/emulated/0/Lynx/lynx.log

# Duration in nanoseconds of one scheduling period
SCHED_PERIOD_PERF="$((10 * 1000 * 1000))"

# How many tasks should we have at a maximum in one scheduling period
SCHED_TASKS_PERF="5"

# Get CPU & GPU max freq
FREQ0=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq)
FREQ4=$(cat /sys/devices/system/cpu/cpu4/cpufreq/scaling_max_freq)
FREQ7=$(cat /sys/devices/system/cpu/cpu7/cpufreq/scaling_max_freq)
GPUMAXFREQ=fb5=$(read_file "/sys/class/kgsl/kgsl-3d0/freq_table_mhz" | tr " " "\n" | sort -n | sed '/^$/d' | tail -n 15 | head -n 1)
GPUMAXMHZ=fb5=$(read_file "/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies" | tr " " "\n" | sort -n | sed '/^$/d' | tail -n 15 | head -n 1)

# Class BW performance
for bwperf in /sys/class/devfreq
do
	echo "performance" > $bwperf/sys/class/devfreq/1d84000.ufshc/governor
	echo "performance" > $bwperf/aa00000.qcom,vidc:arm9_bus_ddr/governor
	echo "performance" > $bwperf/aa00000.qcom,vidc:bus_cnoc/governor
	echo "performance" > $bwperf/aa00000.qcom,vidc:venus_bus_ddr/governor
	echo "performance" > $bwperf/aa00000.qcom,vidc:venus_bus_llcc/governor
	echo "performance" > $bwperf/soc:qcom,cpubw/governor
	echo "performance" > $bwperf/soc:qcom,gpubw/governor
	echo "performance" > $bwperf/soc:qcom,kgsl-busmon/governor
	echo "performance" > $bwperf/soc:qcom,l3-cdsp/governor
	echo "performance" > $bwperf/soc:qcom,l3-cpu0/governor
	echo "performance" > $bwperf/soc:qcom,l3-cpu4/governor
	echo "performance" > $bwperf/soc:qcom,llccbw/governor
	echo "performance" > $bwperf/soc:qcom,memlat-cpu0/governor
	echo "performance" > $bwperf/soc:qcom,memlat-cpu4/governor
	echo "performance" > $bwperf/soc:qcom,mincpubw/governor
	echo "performance" > $bwperf/soc:qcom,snoc_cnoc_keepalive/governor
done

# Governor
##cpu0-3
  for gov in /sys/devices/system/cpu/cpu[0,1,2,3]/cpufreq
  do
    echo "performance" > $gov/scaling_governor
  done
##cpu4-7
  for gov in /sys/devices/system/cpu/cpu[4,5,6,7]/cpufreq
  do
    echo "performance" > $gov/scaling_governor
  done

# Disable Cpu Efficient
echo "N" > /sys/module/workqueue/parameters/power_efficient

# Functions
read_file(){
  if [[ -f $1 ]]; then
    if [[ ! -r $1 ]]; then
      chmod +r "$1"
    fi
    cat "$1"
  else
    echo "File $1 not found"
  fi
}

disable_thermal_service()
{
  stop android.thermal-hal
  stop debug_pid.sec-thermal-1-0
  stop mi_thermald
  stop thermal
  stop thermal-engine
  stop thermal_mnt_hal_service
  stop thermal-hal
  stop thermald
  stop thermalloadalgod
  stop thermalservice
  stop sec-thermal-1-0
  stop vendor.thermal-hal-1-0
  stop vendor.semc.hardware.thermal-1-0
  stop vendor-thermal-1-0
  stop vendor.thermal-engine
  stop vendor.thermal-manager
  stop vendor.thermal-hal-1-0
  stop vendor.thermal-hal-2-0
  stop vendor.thermal-symlinks
}

restore_cpu_clock()
{
  #cpu4
   fp4=$(read_file "/sys/devices/system/cpu/cpu4/cpufreq/cpuinfo_max_freq")
   echo "$fp4" > /sys/devices/system/cpu/cpu4/cpufreq/scaling_max_freq
  #cpu5
   fp5=$(read_file "/sys/devices/system/cpu/cpu5/cpufreq/cpuinfo_max_freq")
   echo "$fp5" > /sys/devices/system/cpu/cpu5/cpufreq/scaling_max_freq
  #cpu6
   fp6=$(read_file "/sys/devices/system/cpu/cpu6/cpufreq/cpuinfo_max_freq")
   echo "$fp6" > /sys/devices/system/cpu/cpu6/cpufreq/scaling_max_freq
  #cpu7
   fp7=$(read_file "/sys/devices/system/cpu/cpu7/cpufreq/cpuinfo_max_freq")
   echo "$fp7" > /sys/devices/system/cpu/cpu7/cpufreq/scaling_max_freq
}

performance_tunables_game03()
{
for eas in /sys/devices/system/cpu/cpu[0,1,2,3]/cpufreq/performance
do
  echo "$FREQ0" > $eas/hispeed_freq
  echo "80" > $eas/hispeed_load
  echo "0" > $eas/up_rate_limit_us
  echo "0" > $eas/down_rate_limit_us
  echo "0" > $eas/pl
done
}

performance_tunables_game47()
{
for eas in /sys/devices/system/cpu/cpu[4,5,6]/cpufreq/performance
do
  echo "$FREQ4" > $eas/hispeed_freq
  echo "80" > $eas/hispeed_load
  echo "0" > $eas/up_rate_limit_us
  echo "0" > $eas/down_rate_limit_us
  echo "0" > $eas/pl
done

for eas in /sys/devices/system/cpu/cpu[7]/cpufreq/schedutil
do
  echo "$FREQ7" > $eas/hispeed_freq
  echo "80" > $eas/hispeed_load
  echo "0" > $eas/up_rate_limit_us
  echo "0" > $eas/down_rate_limit_us
  echo "0" > $eas/pl
done
}

enableallcore()
{
  chmod 644 /sys/devices/system/cpu/cpu0/online
  echo "1" > /sys/devices/system/cpu/cpu0/online
  chmod 444 /sys/devices/system/cpu/cpu0/online
  chmod 644 /sys/devices/system/cpu/cpu1/online
  echo "1" > /sys/devices/system/cpu/cpu1/online
  chmod 444 /sys/devices/system/cpu/cpu1/online
  chmod 644 /sys/devices/system/cpu/cpu2/online
  echo "1" > /sys/devices/system/cpu/cpu2/online
  chmod 444 /sys/devices/system/cpu/cpu2/online
  chmod 644 /sys/devices/system/cpu/cpu3/online
  echo "1" > /sys/devices/system/cpu/cpu3/online
  chmod 444 /sys/devices/system/cpu/cpu3/online
  chmod 644 /sys/devices/system/cpu/cpu4/online
  echo "1" > /sys/devices/system/cpu/cpu4/online
  chmod 444 /sys/devices/system/cpu/cpu4/online
  chmod 644 /sys/devices/system/cpu/cpu5/online
  echo "1" > /sys/devices/system/cpu/cpu5/online
  chmod 444 /sys/devices/system/cpu/cpu5/online
  chmod 644 /sys/devices/system/cpu/cpu6/online
  echo "1" > /sys/devices/system/cpu/cpu6/online
  chmod 444 /sys/devices/system/cpu/cpu6/online
  chmod 644 /sys/devices/system/cpu/cpu7/online
  echo "1" > /sys/devices/system/cpu/cpu7/online
  chmod 444 /sys/devices/system/cpu/cpu7/online
}

# Disable Thermal
#disable_thermal_service

# Cpu core control
#enableallcore
#restore_cpu_clock

# Cpuset
echo "0-7" > /dev/cpuset/foreground/cpus
echo "0-7" > /dev/cpuset/top-app/cpus
echo "0-7" > /dev/cpuset/restricted/cpus
echo "0-3" > /dev/cpuset/camera-daemon/cpus
echo "0-3" > /dev/cpuset/audio-app/cpus
echo "0-3" > /dev/cpuset/background/cpus
echo "0-3" > /dev/cpuset/system-background/cpus

# performance tunables
performance_tunables_game03
performance_tunables_game47

# Improve real time latencies by reducing the scheduler migration time
echo "16" > /proc/sys/kernel/sched_nr_migrate

# Additional
echo "150" > /proc/sys/vm/vfs_cache_pressure
echo "1" > /proc/sys/vm/stat_interval
echo "32" > /proc/sys/vm/watermark_scale_factor
echo "0" > /proc/sys/vm/watermark_boost_factor
echo "0" > /proc/sys/vm/oom_dump_tasks

# Limit max perf event processing time to this much CPU usage
echo "25" > /proc/sys/kernel/perf_cpu_time_max_percent

# For user UFS
echo "5" > /sys/devices/platform/soc/1d84000.ufshc/clkgate_delay_ms_perf
echo "1000" > /sys/devices/platform/soc/1d84000.ufshc/clkgate_delay_ms_pwr_save

# Schedtune Boost Base
echo "1" > /dev/stune/schedtune.sched_boost_enabled
echo "0" > /dev/stune/schedtune.boost
echo "0" > /dev/stune/schedtune.sched_boost_no_override
echo "1" > /dev/stune/schedtune.prefer_idle
echo "0" > /dev/stune/schedtune.colocate
echo "0" > /dev/stune/cgroup.clone_children

# Schedtune Boost background
echo "1" > /dev/stune/background/schedtune.sched_boost_enabled
echo "-15" > /dev/stune/background/schedtune.boost
echo "0" > /dev/stune/background/schedtune.sched_boost_no_override
echo "0" > /dev/stune/background/schedtune.prefer_idle

# Schedtune Boost foreground
echo "1" > /dev/stune/foreground/schedtune.sched_boost_enabled
echo "5" > /dev/stune/foreground/schedtune.boost
echo "1" > /dev/stune/foreground/schedtune.sched_boost_no_override
echo "1" > /dev/stune/foreground/schedtune.prefer_idle

# Schedtune Boost top app
echo "1" > /dev/stune/top-app/schedtune.sched_boost_enabled
echo "5" > /dev/stune/top-app/schedtune.boost
echo "1" > /dev/stune/top-app/schedtune.sched_boost_no_override
echo "1" > /dev/stune/top-app/schedtune.prefer_idle

# Schedtune Boost real time
echo "1" > /dev/stune/rt/schedtune.sched_boost_enabled
echo "0" > /dev/stune/rt/schedtune.boost
echo "0" > /dev/stune/rt/schedtune.sched_boost_no_override
echo "0" > /dev/stune/rt/schedtune.prefer_idle

# GPU settings
for gpu in /sys/class/kgsl/kgsl-3d0
do
  echo "0" > $gpu/throttling
  echo "0" > $gpu/thermal_pwrlevel
  echo "0" > $gpu/max_pwrlevel
  echo "0" > $gpu/bus_split
  echo "1" > $gpu/force_clk_on
  echo "1" > $gpu/force_bus_on
  echo "1" > $gpu/force_rail_on
  echo "1" > $gpu/force_no_nap
  echo "$GPUMAXMHZ" > $gpu/min_clock_mhz
  echo "$GPUMAXMHZ" > $gpu/max_clock_mhz
  echo "$GPUMAXFREQ" > $gpu/gpu_clk
  echo "$GPUMAXFREQ" > $gpu/devfreq/min_freq
  echo "$GPUMAXFREQ" > $gpu/devfreq/max_freq
  echo "$GPUMAXFREQ" > $gpu/max_gpuclk
  echo "0" > $gpu/default_pwrlevel
  echo "0" > $gpu/min_pwrlevel
  echo "0" > $gpu/max_pwrlevel
  echo "150" > $gpu/pmqos_active_latency
  echo "0" > $gpu/idle_timer
  echo "0" > $gpu/devfreq/polling_interval
  echo "0" > $gpu/wake_timeout
done

# Fs
echo "1" > /proc/sys/fs/lease-break-time

# Enable sched boost
echo "1" > /proc/sys/kernel/sched_boost

# Entropy
echo "512" > /proc/sys/kernel/random/read_wakeup_threshold
echo "2048" > /proc/sys/kernel/random/write_wakeup_threshold

# Disable Sector Thermal
for therm in /sys/class/thermal/thermal_zone*
do
  echo "disabled" > $therm/mode
  echo "1" > $therm/sustainable_power
done

# Ram Boost
flush5

# Kill unused process
echo "3" > /proc/sys/vm/drop_caches
am kill-all

# Set perf
setprop lynx.mode performance
echo " •> Performance mode activated at $(date "+%H:%M:%S")" >> $LOG

# Report
sed -Ei 's/^description=(\[.*][[:space:]]*)?/description=[ 🔥 Performance Mode... ] /g' "$BASEDIR/module.prop"
am start -a android.intent.action.MAIN -e toasttext "🔥 Performance Mode..." -n bellavita.toast/.MainActivity

exit 0