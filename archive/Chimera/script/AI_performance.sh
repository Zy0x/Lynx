#!/system/bin/sh
# Powered by AI Controller 5.0

# Sync to data in the rare case a device crashes
sync

# Path
BASEDIR="/data/adb/modules/Chimera"
LOG=/storage/emulated/0/Chimera/chimera.log

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

# High Performance
sh "$BASEDIR/script/high_perf.sh" > /dev/null 2>&1
# Cpu core control
#enableallcore
#restore_cpu_clock

#internal storage
for int in /sys/block/sd*/queue
do
   echo "noop" > $int/scheduler
   echo "512" > $int/read_ahead_kb
   echo "128" > $int/nr_requests
done

# Kill unused process
sync && echo "3" > /proc/sys/vm/drop_caches
am kill-all

# Set perf
setprop chimera.mode performance
echo " •> Performance mode activated at $(date "+%H:%M:%S")" >> $LOG

# Report
sed -Ei "s/^description=\[.*\]/description=[ 🔥 Pᴇʀꜰᴏʀᴍᴀɴᴄᴇ Mᴏᴅᴇ (AI) ]/" "$BASEDIR/module.prop"
am start -a android.intent.action.MAIN -e toasttext "🔥 Pᴇʀꜰᴏʀᴍᴀɴᴄᴇ Mᴏᴅᴇ (AI)" -n bellavita.toast/.MainActivity

exit 0