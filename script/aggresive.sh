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
gpumaxfreq=$(cat /sys/class/kgsl/kgsl-3d0/gpu_available_frequencies | tr -s ' ' '\n' | sort -n | tail -n 1)
gpumaxmhz=$(cat /sys/class/kgsl/kgsl-3d0/freq_table_mhz | tr -s ' ' '\n' | sort -n | tail -n 1)

# Class BW performance
BW_dir="/sys/class/devfreq/*"
for bwperf in $BW_dir
do
    case $bwperf in
        *kgsl-3d0* | *kgsl-3d*)
            continue ;;
    esac
    echo "performance" > "$bwperf/governor"
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

# CPU Efficient
echo "N" > /sys/module/workqueue/parameters/power_efficient

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

# UFS Settings
ufs_folders=( $(find /d/* -type d | grep ufs) )
for folder in "${ufs_folders[@]}"; do
    echo "111111" > $folder/power_mode
done
echo "5" > /sys/devices/platform/soc/1d84000.ufshc/clkgate_delay_ms_perf
echo "1000" > /sys/devices/platform/soc/1d84000.ufshc/clkgate_delay_ms_pwr_save

# Cpuset
echo "0-7" > /dev/cpuset/foreground/cpus
echo "0-7" > /dev/cpuset/top-app/cpus
echo "0-7" > /dev/cpuset/restricted/cpus
echo "4-7" > /dev/cpuset/camera-daemon/cpus
echo "0-3" > /dev/cpuset/audio-app/cpus
echo "0-7" > /dev/cpuset/background/cpus
echo "0-7" > /dev/cpuset/system-background/cpus

# Schedtune Boost Base
echo "1" > /dev/stune/schedtune.sched_boost_enabled
echo "5" > /dev/stune/schedtune.boost
echo "0" > /dev/stune/schedtune.sched_boost_no_override
echo "0" > /dev/stune/schedtune.prefer_idle
echo "0" > /dev/stune/schedtune.colocate
echo "0" > /dev/stune/cgroup.clone_children

# Schedtune Boost foreground
echo "1" > /dev/stune/foreground/schedtune.sched_boost_enabled
echo "5" > /dev/stune/foreground/schedtune.boost
echo "1" > /dev/stune/foreground/schedtune.sched_boost_no_override
echo "0" > /dev/stune/foreground/schedtune.prefer_idle

# Schedtune Boost top app
echo "1" > /dev/stune/top-app/schedtune.sched_boost_enabled
echo "5" > /dev/stune/top-app/schedtune.boost
echo "1" > /dev/stune/top-app/schedtune.sched_boost_no_override
echo "0" > /dev/stune/top-app/schedtune.prefer_idle

# Schedtune Boost real time
echo "1" > /dev/stune/rt/schedtune.sched_boost_enabled
echo "5" > /dev/stune/rt/schedtune.boost
echo "0" > /dev/stune/rt/schedtune.sched_boost_no_override
echo "0" > /dev/stune/rt/schedtune.prefer_idle

# Improve real time latencies by reducing the scheduler migration time
echo "32" > /proc/sys/kernel/sched_nr_migrate

# Additional
echo "70" > /proc/sys/vm/vfs_cache_pressure
echo "10000" > /proc/sys/vm/stat_interval
echo "32" > /proc/sys/vm/watermark_scale_factor
echo "0" > /proc/sys/vm/watermark_boost_factor
echo "0" > /proc/sys/vm/oom_dump_tasks

for i in /sys/devices/system/cpu/*/core_ctl; do
    echo "0" > $i/enable
    echo "4" > $i/min_cpus
    echo "4" > $i/max_cpus
    echo "0" > $i/busy_down_thres
    echo "5" > $i/busy_up_thres
    echo "0 0 0 0" > not_preferred
done

# GPU Settings
for gpu in /sys/class/kgsl/kgsl-3d0
do
  echo "3" > $gpu/devfreq/adrenoboost
  echo "0" > $gpu/throttling
  echo "0" > $gpu/thermal_pwrlevel
  echo "0" > $gpu/max_pwrlevel
  echo "0" > $gpu/min_pwrlevel
  echo "0" > $gpu/default_pwrlevel
  echo "0" > $gpu/bus_split
  echo "1" > $gpu/force_clk_on
  echo "1" > $gpu/force_bus_on
  echo "1" > $gpu/force_rail_on
  echo "1" > $gpu/force_no_nap
  echo "$gpumaxmhz" > $gpu/min_clock_mhz
  echo "$gpumaxmhz" > $gpu/max_clock_mhz
  echo "$gpumaxfreq" > $gpu/gpu_clk
  echo "$gpumaxfreq" > $gpu/devfreq/min_freq
  echo "$gpumaxfreq" > $gpu/devfreq/max_freq
  echo "$gpumaxfreq" > $gpu/max_gpuclk
  echo "0" > $gpu/pmqos_active_latency
  echo "0" > $gpu/idle_timer
  echo "10" > $gpu/devfreq/polling_interval
  echo "0" > $gpu/wake_timeout
  echo "15" > $gpu/dispatch/inflight
  echo "0" > $gpu/dispatch/inflight_low_latency
done

# Fs
echo "1" > /proc/sys/fs/lease-break-time

# Enable sched boost
echo "1" > /proc/sys/kernel/sched_boost

# Kill unused process
sync && echo "3" > /proc/sys/vm/drop_caches
am kill-all

# Set perf
setprop lynx.mode aggresive
echo " •> Aggresive mode activated at $(date "+%H:%M:%S")" >> $LOG

# Report
sed -Ei "s/^description=\[.*\]/description=[ ⚡ Aɢɢʀᴇꜱɪᴠᴇ Mᴏᴅᴇ ]/" "$BASEDIR/module.prop"
am start -a android.intent.action.MAIN -e toasttext "⚡ Aɢɢʀᴇꜱɪᴠᴇ Mᴏᴅᴇ" -n bellavita.toast/.MainActivity

exit 0