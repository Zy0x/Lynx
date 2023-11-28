#!/system/bin/sh
# By Noir

sleep 1

# Path
BASEDIR=/data/adb/modules/Lynx
INT=/storage/emulated/0
RWD=$INT/Lynx
LOG=$RWD/lynx.log
MSC=$BASEDIR/script
BAL=$MSC/lynx_balance.sh
PERF=$MSC/lynx_performance.sh

# Check rewrite directory
if [ ! -e $RWD ]; then
  mkdir $RWD
fi

echo " " > $LOG
echo " Module info: " >> $LOG
echo " • Name            : Lynx " >> $LOG
echo " • Codename        : Quiet " >> $LOG
echo " • Version         : v1.9" >> $LOG
echo " • Status          : Stable " >> $LOG
echo " • Owner           : Noir " >> $LOG
echo " • Release Date    : 24-11-2023 " >> $LOG
echo " " >> $LOG
echo " Device info: " >> $LOG
echo " • Brand           : $(getprop ro.product.system.brand) " >> $LOG
echo " • Device          : $(getprop ro.product.system.model) " >> $LOG
echo " • Processor       : $(getprop ro.product.board) " >> $LOG
echo " • Android Version : $(getprop ro.system.build.version.release)" >> $LOG
echo " • SDK Version     : $(getprop ro.build.version.sdk) " >> $LOG
echo " • Architecture    : $(getprop ro.product.cpu.abi) " >> $LOG
echo " • Kernel Version  : $(uname -r)" >> $LOG
echo " " >> $LOG

echo " Profile Mode:" >> $LOG

# Check applist file
if [ ! -e $RWD/applist_perf.txt ]; then
  cp -f $MSC/applist_perf.txt $RWD
fi

# Begin of AI
sed -Ei 's/^description=(\[.*][[:space:]]*)?/description=[ 🤖 Ai is started... ] /g' "$BASEDIR/module.prop"
am start -a android.intent.action.MAIN -e toasttext "🤖 Ai is started..." -n bellavita.toast/.MainActivity

# Start AI
setprop lynx.mode notset
while true; do
     sleep 20
     app_list_filter="grep -o -e applist.app.add"
     while IFS= read -r applist || [[ -n "$applist" ]]; do
          filter=$(echo "$applist" | awk '!/ /')
          if [[ -n "$filter" ]]; then
            app_list_filter+=" -e "$filter
          fi
     done < "$RWD/applist_perf.txt"
     window=$(dumpsys window | grep package | $app_list_filter | tail -1)
     if [[ "$window" ]]; then
       if [[ $(getprop lynx.mode) == "performance" ]]; then
         echo " "
       else
         sh $PERF
       fi
       sleep 1
     else
       if [[ $(getprop lynx.mode) == "balance" ]]; then
         echo " "
       else
         sh $BAL
       fi
       sleep 1
     fi
done
