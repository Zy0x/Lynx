#!/system/bin/sh
#By Lynx

# Sync to data in the rare case a device crashes
sync

# Path
BASEDIR=/data/adb/modules/Lynx
LOG=/storage/emulated/0/Lynx/lynx.log

# Get CPU & GPU max freq
FREQ0=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq)
FREQ4=$(cat /sys/devices/system/cpu/cpu4/cpufreq/scaling_max_freq)
FREQ7=$(cat /sys/devices/system/cpu/cpu7/cpufreq/scaling_max_freq)
GPUMAXFREQ=fb5=$(read_file "/sys/class/kgsl/kgsl-3d0/freq_table_mhz" | tr " " "\n" | sort -n | sed '/^$/d' | tail -n 15 | head -n 1)
GPUMAXMHZ=fb5=$(read_file "/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies" | tr " " "\n" | sort -n | sed '/^$/d' | tail -n 15 | head -n 1)
GPUMINFREQ=fb5=$(read_file "/sys/class/kgsl/kgsl-3d0/freq_table_mhz" | tr " " "\n" | sort -n | sed '/^$/d' | tail -n 1 | head -n 1)
GPUMINMHZ=fb5=$(read_file "/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies" | tr " " "\n" | sort -n | sed '/^$/d' | tail -n 1 | head -n 1)

# Class BW balance
for bwbal in /sys/class/devfreq
do
	echo "performance" > $bwbal/sys/class/devfreq/1d84000.ufshc/governor
	echo "performance" > $bwbal/aa00000.qcom,vidc:arm9_bus_ddr/governor
	echo "performance" > $bwbal/aa00000.qcom,vidc:bus_cnoc/governor
	echo "msm-vidc-ddr" > $bwbal/aa00000.qcom,vidc:venus_bus_ddr/governor
	echo "msm-vidc-llcc" > $bwbal/aa00000.qcom,vidc:venus_bus_llcc/governor
	echo "bw_hwmon" > $bwbal/soc:qcom,cpubw/governor
	echo "bw_vbif" > $bwbal/soc:qcom,gpubw/governor
	echo "gpubw_mon" > $bwbal/soc:qcom,kgsl-busmon/governor
	echo "userspace" > $bwbal/soc:qcom,l3-cdsp/governor
	echo "mem_latency" > $bwbal/soc:qcom,l3-cpu0/governor
	echo "mem_latency" > $bwbal/soc:qcom,l3-cpu4/governor
	echo "bw_hwmon" > $bwbal/soc:qcom,llccbw/governor
	echo "mem_latency" > $bwbal/soc:qcom,memlat-cpu0/governor
	echo "mem_latency" > $bwbal/soc:qcom,memlat-cpu4/governor
	echo "compute" > $bwbal/soc:qcom,mincpubw/governor
	echo "powersave" > $bwbal/soc:qcom,snoc_cnoc_keepalive/governor
done

# Cpu Efficient
echo "Y" > /sys/module/workqueue/parameters/power_efficient

# Governor for cpu4-7
GOV47=custom

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

enable_thermal_service()
{
  start android.thermal-hal
  start debug_pid.sec-thermal-1-0
  start mi_thermald
  start thermal
  start thermal-engine
  start thermal_mnt_hal_service
  start thermal-hal
  start thermald
  start thermalloadalgod
  start thermalservice
  start sec-thermal-1-0
  start vendor.thermal-hal-1-0
  start vendor.semc.hardware.thermal-1-0
  start vendor-thermal-1-0
  start vendor.thermal-engine
  start vendor.thermal-manager
  start vendor.thermal-hal-1-0
  start vendor.thermal-hal-2-0
  start vendor.thermal-symlinks
}

downclock_cpu()
{
  #cpu4
   fb4=$(read_file "/sys/devices/system/cpu/cpu4/cpufreq/scaling_available_frequencies" | tr " " "\n" | sort -n | sed '/^$/d' | tail -n 7 | head -n 1)
   echo "$fb4" > /sys/devices/system/cpu/cpu4/cpufreq/scaling_max_freq
  #cpu5
   fb5=$(read_file "/sys/devices/system/cpu/cpu5/cpufreq/scaling_available_frequencies" | tr " " "\n" | sort -n | sed '/^$/d' | tail -n 7 | head -n 1)
   echo "$fb5" > /sys/devices/system/cpu/cpu5/cpufreq/scaling_max_freq
  #cpu6
   fb6=$(read_file "/sys/devices/system/cpu/cpu6/cpufreq/scaling_available_frequencies" | tr " " "\n" | sort -n | sed '/^$/d' | tail -n 7 | head -n 1)
   echo "$fb6" > /sys/devices/system/cpu/cpu6/cpufreq/scaling_max_freq
  #cpu7
   fb7=$(read_file "/sys/devices/system/cpu/cpu7/cpufreq/scaling_available_frequencies" | tr " " "\n" | sort -n | sed '/^$/d' | tail -n 7 | head -n 1)
   echo "$fb7" > /sys/devices/system/cpu/cpu7/cpufreq/scaling_max_freq
}

schedutil_tunables_bal03()
{
for eas in /sys/devices/system/cpu/cpu[0,1,2,3]/cpufreq/schedutil
do
  echo "$FREQ0" > $eas/hispeed_freq
  echo "99" > $eas/hispeed_load
  echo "0" > $eas/up_rate_limit_us
  echo "0" > $eas/down_rate_limit_us
  echo "1" > $eas/pl
done
}

schedutil_tunables_bal47()
{
for eas in /sys/devices/system/cpu/cpu[4,5,6]/cpufreq/schedutil
do
  echo "$FREQ4" > $eas/hispeed_freq
  echo "99" > $eas/hispeed_load
  echo "0" > $eas/up_rate_limit_us
  echo "0" > $eas/down_rate_limit_us
  echo "1" > $eas/pl
done

for eas in /sys/devices/system/cpu/cpu[7]/cpufreq/schedutil
do
  echo "$FREQ7" > $eas/hispeed_freq
  echo "99" > $eas/hispeed_load
  echo "0" > $eas/up_rate_limit_us
  echo "0" > $eas/down_rate_limit_us
  echo "1" > $eas/pl
done
}

disable2core()
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
  echo "0" > /sys/devices/system/cpu/cpu3/online
  chmod 444 /sys/devices/system/cpu/cpu3/online
  chmod 644 /sys/devices/system/cpu/cpu4/online
  echo "1" > /sys/devices/system/cpu/cpu4/online
  chmod 444 /sys/devices/system/cpu/cpu4/online
  chmod 644 /sys/devices/system/cpu/cpu5/online
  echo "1" > /sys/devices/system/cpu/cpu5/online
  chmod 444 /sys/devices/system/cpu/cpu5/online
  chmod 644 /sys/devices/system/cpu/cpu6/online
  echo "0" > /sys/devices/system/cpu/cpu6/online
  chmod 444 /sys/devices/system/cpu/cpu6/online
  chmod 644 /sys/devices/system/cpu/cpu7/online
  echo "1" > /sys/devices/system/cpu/cpu7/online
  chmod 444 /sys/devices/system/cpu/cpu7/online
}

# Enable Thermal
#enable_thermal_service

# Cpu core control 
#disable2core
#downclock_cpu

# Governor
##cpu0-3
  for gov in /sys/devices/system/cpu/cpu[0,1,2,3]/cpufreq
  do
    echo "schedutil" > $gov/scaling_governor
  done
##cpu4-7
  for gov in /sys/devices/system/cpu/cpu[4,5,6,7]/cpufreq
  do
    echo "$GOV47" > $gov/scaling_governor
  done

# Schedutil tunables
schedutil_tunables_bal03
#schedutil_tunables_bal47

# Improve real time latencies by reducing the scheduler migration time
echo "32" > /proc/sys/kernel/sched_nr_migrate

# Limit max perf event processing time to this much CPU usage
echo "15" > /proc/sys/kernel/perf_cpu_time_max_percent

# Schedtune
echo "0" > /dev/stune/schedtune.sched_boost_enabled
echo "0" > /dev/stune/schedtune.boost
echo "0" > /dev/stune/schedtune.sched_boost_no_override
echo "0" > /dev/stune/schedtune.prefer_idle

# GPU settings
for gpu in /sys/class/kgsl/kgsl-3d0
do
  echo "1" > $gpu/throttling
  echo "3" > $gpu/thermal_pwrlevel
  echo "1" > $gpu/bus_split
  echo "0" > $gpu/force_clk_on
  echo "0" > $gpu/force_bus_on
  echo "0" > $gpu/force_rail_on
  echo "0" > $gpu/force_no_nap
  echo "$GPUMINMHZ" > $gpu/min_clock_mhz
  echo "$GPUMAXMHZ" > $gpu/max_clock_mhz
  echo "$GPUMAXFREQ" > $gpu/gpu_clk
  echo "$GPUMINFREQ" > $gpu/devfreq/min_freq
  echo "$GPUMAXFREQ" > $gpu/devfreq/max_freq
  echo "$GPUMAXFREQ" > $gpu/max_gpuclk
  echo "5" > $gpu/default_pwrlevel
  echo "5" > $gpu/min_pwrlevel
  echo "0" > $gpu/max_pwrlevel
  echo "10" > $gpu/pmqos_active_latency
  echo "60" > $gpu/idle_timer
  echo "10" > $gpu/devfreq/polling_interval
  echo "0" > $gpu/wake_timeout
done

# Additional
echo "50" > /proc/sys/vm/vfs_cache_pressure
echo "1" > /proc/sys/vm/stat_interval
echo "100" > /proc/sys/vm/watermark_scale_factor
echo "1500" > /proc/sys/vm/watermark_boost_factor

# For user UFS
echo "100" > /sys/devices/platform/soc/1d84000.ufshc/clkgate_delay_ms_perf
echo "5" > /sys/devices/platform/soc/1d84000.ufshc/clkgate_delay_ms_pwr_save

# Fs
echo "50" > /proc/sys/fs/lease-break-time

# Disable sched boost
echo "0" > /proc/sys/kernel/sched_boost

# Entropy
echo "64" > /proc/sys/kernel/random/read_wakeup_threshold
echo "512" > /proc/sys/kernel/random/write_wakeup_threshold

# Enable Sector Thermal
for therm in /sys/class/thermal/thermal_zone*
do
  echo "enabled" > $therm/mode
  echo "0" > $therm/sustainable_power
done

# Kill unused process
echo "3" > /proc/sys/vm/drop_caches
am kill-all

# Set balance
setprop lynx.mode balance
echo " •> Balance mode activated at $(date "+%H:%M:%S")" >> $LOG

# Report
sed -Ei 's/^description=(\[.*][[:space:]]*)?/description=[ ❄️ Balance Mode... ] /g' "$BASEDIR/module.prop"
am start -a android.intent.action.MAIN -e toasttext "❄️ Balance Mode..." -n bellavita.toast/.MainActivity

exit 0