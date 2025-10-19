#!/system/bin/sh
# Powered by AI Controller 4.0

# Sync to data in the rare case a device crashes
sync

# Path
BASEDIR="/data/adb/modules/Lynx"
Baseflow="${BASEDIR}/script/flow"
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

# Governor (little/big/prime)
for cpu in /sys/devices/system/cpu/cpu[0-7]
do
    echo "performance" > "$cpu/cpufreq/scaling_governor"
    echo "1" > "$cpu/sched_load_boost"
done

# Cpu Freq
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    freq=$(cat "$policy/cpuinfo_max_freq")
    name=$(basename "$policy")
    echo "Lock $name → $((freq / 1000)) MHz"
    echo "$freq" > "$policy/scaling_min_freq"
    echo "$freq" > "$policy/scaling_max_freq"
done

# CPU Efficient
echo "N" > /sys/module/workqueue/parameters/power_efficient

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

enableallcore()
{
for cpu in $(seq 0 7)
do
  cpu_file="/sys/devices/system/cpu/cpu${cpu}/online"
  chmod 644 "$cpu_file"
  echo "1" > "$cpu_file"
  chmod 444 "$cpu_file"
done
}

# Cpu core control
#enableallcore
#restore_cpu_clock

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
echo "7" > /dev/cpuset/restricted/cpus
echo "0-3" > /dev/cpuset/camera-daemon/cpus
echo "0-3" > /dev/cpuset/audio-app/cpus
echo "0-7" > /dev/cpuset/background/cpus
echo "0-7" > /dev/cpuset/system-background/cpus

# Improve real time latencies by reducing the scheduler migration time
echo "32" > /proc/sys/kernel/sched_nr_migrate

# Additional
echo "200" > /proc/sys/vm/vfs_cache_pressure
echo "10000" > /proc/sys/vm/stat_interval
echo "32" > /proc/sys/vm/watermark_scale_factor
echo "0" > /proc/sys/vm/watermark_boost_factor
echo "0" > /proc/sys/vm/oom_dump_tasks

# Limit max perf event processing time to this much CPU usage
echo "5" > /proc/sys/kernel/perf_cpu_time_max_percent

for i in /sys/devices/system/cpu/*/core_ctl; do
    echo "0" > $i/enable
    echo "4" > $i/min_cpus
    echo "4" > $i/max_cpus
    echo "0" > $i/busy_down_thres
    echo "5" > $i/busy_up_thres
    echo "0 0 0 0" > not_preferred
done

# Schedtune Boost Base
echo "1" > /dev/stune/schedtune.sched_boost_enabled
echo "0" > /dev/stune/schedtune.boost
echo "0" > /dev/stune/schedtune.sched_boost_no_override
echo "0" > /dev/stune/schedtune.prefer_idle
echo "0" > /dev/stune/schedtune.colocate
echo "0" > /dev/stune/cgroup.clone_children

# Schedtune Boost foreground
echo "1" > /dev/stune/foreground/schedtune.sched_boost_enabled
echo "5" > /dev/stune/foreground/schedtune.boost
echo "1" > /dev/stune/foreground/schedtune.sched_boost_no_override
echo "0" > /dev/stune/foreground/schedtune.prefer_idle
echo "1" > /dev/stune/foreground/cgroup.clone_children
echo "0" > /dev/stune/foreground/schedtune.colocate

# Schedtune Boost background
echo "1" > /dev/stune/background/schedtune.sched_boost_enabled
echo "0" > /dev/stune/background/schedtune.boost
echo "0" > /dev/stune/background/schedtune.sched_boost_no_override
echo "1" > /dev/stune/background/schedtune.prefer_idle
echo "0" > /dev/stune/foreground/cgroup.clone_children
echo "0" > /dev/stune/foreground/schedtune.colocate

# Schedtune Boost top app
echo "1" > /dev/stune/top-app/schedtune.sched_boost_enabled
echo "5" > /dev/stune/top-app/schedtune.boost
echo "1" > /dev/stune/top-app/schedtune.sched_boost_no_override
echo "0" > /dev/stune/top-app/schedtune.prefer_idle
echo "1" > /dev/stune/top-app/cgroup.clone_children
echo "0" > /dev/stune/top-app/schedtune.colocate

# Schedtune Boost real time
echo "1" > /dev/stune/rt/schedtune.sched_boost_enabled
echo "5" > /dev/stune/rt/schedtune.boost
echo "1" > /dev/stune/rt/schedtune.sched_boost_no_override
echo "0" > /dev/stune/rt/schedtune.prefer_idle
echo "1" > /dev/stune/rt/cgroup.clone_children
echo "0" > /dev/stune/rt/schedtune.colocate

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

# Entropy
echo "512" > /proc/sys/kernel/random/read_wakeup_threshold
echo "2048" > /proc/sys/kernel/random/write_wakeup_threshold

# Read Flow values from the mode file
flow_value=$(sed -n 's/^flow=\(.*\)/\1/p' "/storage/emulated/0/Lynx/mode")

# Flow
if [ -n "$flow_value" ]; then
    if [ "$flow_value" = "1" ]; then
        flow_mode_value=$(sed -n 's/^flow_mode=\(.*\)/\1/p' "$mode_file")
        # Cek nilai 'flow_mode'
        case "$flow_mode_value" in
            5)
                sh "${Baseflow}/flow5.sh"
                ;;
            3)
                sh "${Baseflow}/flow3.sh"
                ;;
            2)
                sh "${Baseflow}/flow2.sh"
                ;;
            1)
                sh "${Baseflow}/flow.sh"
                ;;
            *)
                echo "Nilai mode tidak dikenali"
                ;;
        esac
    fi
fi

# Kill unused process
sync && echo "3" > /proc/sys/vm/drop_caches
am kill-all

# Set perf
setprop lynx.mode performance
echo " •> Performance mode activated at $(date "+%H:%M:%S")" >> $LOG

# Report
sed -Ei "s/^description=\[.*\]/description=[ 🔥 Pᴇʀꜰᴏʀᴍᴀɴᴄᴇ Mᴏᴅᴇ ]/" "$BASEDIR/module.prop"
am start -a android.intent.action.MAIN -e toasttext "🔥 Pᴇʀꜰᴏʀᴍᴀɴᴄᴇ Mᴏᴅᴇ" -n bellavita.toast/.MainActivity

exit 0