#!/system/bin/sh
# Powered by AI Controller 5.0

# Sync to data in the rare case a device crashes
sync

# Path
BASEDIR="/data/adb/modules/Chimera"
Base_eterna="${BASEDIR}/script/eterna"
LOG=/storage/emulated/0/Chimera/chimera.log
mode_file="/storage/emulated/0/Chimera/mode"

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

# Run Script
sh "$BASEDIR/script/balance.sh" > /dev/null 2>&1

# Cpu core control 
#disable2core
#downclock_cpu

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

# Set balance
setprop chimera.mode balance
echo " •> Balance mode activated at $(date "+%H:%M:%S")" >> $LOG

# Report
sed -Ei "s/^description=\[.*\]/description=[ ❄️ Bᴀʟᴀɴᴄᴇ Mᴏᴅᴇ (AI) ]/" "$BASEDIR/module.prop"
am start -a android.intent.action.MAIN -e toasttext "❄️ Bᴀʟᴀɴᴄᴇ Mᴏᴅᴇ (AI)" -n bellavita.toast/.MainActivity

exit 0