#!/system/bin/sh
# Powered by AI Controller 4.0

# Sync to data in the rare case a device crashes
sync

# Path
BASEDIR="/data/adb/modules/Lynx"
LOG=/storage/emulated/0/Lynx/lynx.log
mode_file="/storage/emulated/0/Lynx/mode"

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

# Get CPU & GPU max freq
FREQ0=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq)
FREQ4=$(cat /sys/devices/system/cpu/cpu4/cpufreq/scaling_max_freq)
FREQ7=$(cat /sys/devices/system/cpu/cpu7/cpufreq/scaling_max_freq)
gpumaxfreq=$(cat /sys/class/kgsl/kgsl-3d0/gpu_available_frequencies | tr -s ' ' '\n' | sort -n | tail -n 1)
gpuminfreq=$(cat /sys/class/kgsl/kgsl-3d0/gpu_available_frequencies | tr -s ' ' '\n' | sort -n | head -n 1)
gpumaxmhz=$(cat /sys/class/kgsl/kgsl-3d0/freq_table_mhz | tr -s ' ' '\n' | sort -n | tail -n 1)
gpuminmhz=$(cat /sys/class/kgsl/kgsl-3d0/freq_table_mhz | tr -s ' ' '\n' | sort -n | head -n 1)

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
	echo "cpufreq" > $bwbal/soc:qcom,mincpubw/governor
	echo "powersave" > $bwbal/soc:qcom,snoc_cnoc_keepalive/governor
done
for freq_file in /sys/class/devfreq/*/available_frequencies; do
    if [ -s "$freq_file" ]; then
        all_frequencies=$(cat "$freq_file")
        highest_freq=$(echo $all_frequencies | tr ' ' '\n' | sort -n | tail -n 1)
        dir_path=$(dirname "$freq_file")
        min_freq_file="$dir_path/min_freq"
        max_freq_file="$dir_path/max_freq"
        echo "$highest_freq" > "$min_freq_file"
        echo "$highest_freq" > "$max_freq_file"
    fi
done

# Cpu Freq
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    name=$(basename "$policy")
    min_freq=$(cat "$policy/cpuinfo_min_freq")
    max_freq=$(cat "$policy/cpuinfo_max_freq")
    echo "Reset $name → Min: $((min_freq / 1000)) MHz | Max: $((max_freq / 1000)) MHz"
    echo "$min_freq" > "$policy/scaling_min_freq"
    echo "$max_freq" > "$policy/scaling_max_freq"
done

# Cpu Efficient
echo "Y" > /sys/module/workqueue/parameters/power_efficient

# Governor for cpu4-7
GOV47=custom

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

# Cpuset
echo "0-7" > /dev/cpuset/foreground/cpus
echo "0-7" > /dev/cpuset/top-app/cpus
echo "0-7" > /dev/cpuset/restricted/cpus
echo "0-7" > /dev/cpuset/camera-daemon/cpus
echo "4-7" > /dev/cpuset/audio-app/cpus
echo "0-7" > /dev/cpuset/background/cpus
echo "0-7" > /dev/cpuset/system-background/cpus

# Improve real time latencies by reducing the scheduler migration time
echo "32" > /proc/sys/kernel/sched_nr_migrate

# Limit max perf event processing time to this much CPU usage
echo "15" > /proc/sys/kernel/perf_cpu_time_max_percent

# Schedtune
echo "1" > /dev/stune/schedtune.sched_boost_enabled
echo "0" > /dev/stune/schedtune.boost
echo "0" > /dev/stune/schedtune.sched_boost_no_override
echo "1" > /dev/stune/schedtune.prefer_idle
# Schedtune Boost background
echo "1" > /dev/stune/background/schedtune.sched_boost_enabled
echo "0" > /dev/stune/background/schedtune.boost
echo "0" > /dev/stune/background/schedtune.sched_boost_no_override
echo "0" > /dev/stune/background/schedtune.prefer_idle

# GPU settings
for gpu in /sys/class/kgsl/kgsl-3d0
do
  echo "1" > $gpu/devfreq/adrenoboost
  echo "1" > $gpu/throttling
  echo "1" > $gpu/thermal_pwrlevel
  echo "1" > $gpu/bus_split
  echo "1" > $gpu/force_clk_on
  echo "0" > $gpu/force_bus_on
  echo "1" > $gpu/force_rail_on
  echo "0" > $gpu/force_no_nap
  echo "$gpuminmhz" > $gpu/min_clock_mhz
  echo "$gpumaxmhz" > $gpu/max_clock_mhz
  echo "$gpumaxfreq" > $gpu/gpu_clk
  echo "$gpuminfreq" > $gpu/devfreq/min_freq
  echo "$gpumaxfreq" > $gpu/devfreq/max_freq
  echo "$gpumaxfreq" > $gpu/max_gpuclk
  echo "5" > $gpu/default_pwrlevel
  echo "5" > $gpu/min_pwrlevel
  echo "0" > $gpu/max_pwrlevel
  echo "518" > $gpu/pmqos_active_latency
  echo "10000" > $gpu/idle_timer
  echo "1000" > $gpu/devfreq/polling_interval
  echo "100" > $gpu/wake_timeout
  echo "0" > $gpu/dispatch/inflight
  echo "0" > $gpu/dispatch/inflight_low_latency
done

# Additional
echo "40" > /proc/sys/vm/vfs_cache_pressure
echo "1" > /proc/sys/vm/stat_interval
echo "100" > /proc/sys/vm/watermark_scale_factor
echo "1500" > /proc/sys/vm/watermark_boost_factor

# For user UFS
echo "100" > /sys/devices/platform/soc/1d84000.ufshc/clkgate_delay_ms_perf
echo "5" > /sys/devices/platform/soc/1d84000.ufshc/clkgate_delay_ms_pwr_save

# Fs
echo "50" > /proc/sys/fs/lease-break-time

# Enable sched boost
echo "1" > /proc/sys/kernel/sched_boost

# Entropy
echo "64" > /proc/sys/kernel/random/read_wakeup_threshold
echo "512" > /proc/sys/kernel/random/write_wakeup_threshold

# Restore Boost CPU
for i in $(seq 0 7); do echo "1" > /sys/devices/system/cpu/cpu$i/sched_load_boost; done
echo "1 1 1 1" > /sys/devices/system/cpu/cpu0/core_ctl/not_preferred
echo "0 0 0 0" > /sys/devices/system/cpu/cpu4/core_ctl/not_preferred

# Kill unused process
sync && echo "3" > /proc/sys/vm/drop_caches
am kill-all

# Set balance
setprop lynx.mode balance
echo " •> Balance mode activated at $(date "+%H:%M:%S")" >> $LOG

# Report
sed -Ei "s/^description=\[.*\]/description=[ ❄️ Bᴀʟᴀɴᴄᴇ Mᴏᴅᴇ ]/" "$BASEDIR/module.prop"
am start -a android.intent.action.MAIN -e toasttext "❄️ Bᴀʟᴀɴᴄᴇ Mᴏᴅᴇ" -n bellavita.toast/.MainActivity

exit 0