#!/system/bin/sh
# AI Controller 5.0
# By Noir

# Sync all
sync

# Paths
BASEDIR="/data/adb/modules/Chimera"
RWD="/storage/emulated/0/Chimera"
LOG="$RWD/Chimera.log"
MODPROP="$BASEDIR/module.prop"
MSC="$BASEDIR/script"
BAL="$MSC/AI_balance.sh"
PERF="$MSC/AI_performance.sh"
HIGH="$MSC/high_perf.sh"
PSAVE="$MSC/powersave.sh"
STD="$MSC/balance.sh"
APPLIST="$RWD/applist_perf.txt"
FLOW="$MSC/flow"
ETERNA="$MSC/eterna"

# Source File
source $MSC/enable_thermal.sh
source $MSC/disable_thermal.sh

# Prop Control
setprop eterna.control notset
setprop eterna.control.d notset
setprop chimera.thermal.control notset
setprop flow.control notset

# Read Prop Function
read_prop() {
    prop_name="$1"
    sed -nE "s/^$prop_name=(.*)/\1/p" "$MODPROP"
}

# Check and create Chimera directory
mkdir -p "$RWD" || { echo "Failed to create Chimera directory" >> "$LOG"; exit 1; }

# Log info and module
{
printf '\n%s\n' " -------------------- "
printf ' Module info:\n'
printf ' • Name             : %s\n' "$(read_prop 'name')"
printf ' • Version          : %s\n' "$(read_prop 'version')"
printf ' • Owner            : %s\n' "$(read_prop 'author')"
printf ' • Release Date     : %s\n' "$(read_prop 'versionCode' | sed 's/\(....\)\(..\)\(..\)/\3-\2-\1/')"
printf '\n%s\n' ''
printf ' Device info:\n'
printf ' • Brand            : %s\n' "$(getprop ro.product.system.brand)"
printf ' • Device           : %s\n' "$(getprop ro.product.system.model)"
printf ' • Processor        : %s\n' "$(getprop ro.product.board)"
printf ' • Android Version  : %s\n' "$(getprop ro.system.build.version.release)"
printf ' • SDK Version      : %s\n' "$(getprop ro.build.version.sdk)"
printf ' • Architecture     : %s\n' "$(getprop ro.product.cpu.abi)"
printf ' • Kernel Version   : %s\n' "$(uname -r)"
printf '\n%s\n' ''
printf ' Profile Mode:\n'
} >> "$LOG" || { echo "Failed to write module info and device info to log"; exit 1; }

# Check and create applist file
if [ ! -e $RWD/applist_perf.txt ]; then
  cp -f $MSC/applist_perf.txt $RWD
fi

# AI Interface
sed -Ei "s/^description=\[.*\]/description=[ 🤖 Aɪ ɪꜱ ꜱᴛᴀʀᴛᴇᴅ ]/" "$BASEDIR/module.prop"
am start -a android.intent.action.MAIN -e toasttext "🤖 Aɪ ɪꜱ ꜱᴛᴀʀᴛᴇᴅ..." -n bellavita.toast/.MainActivity

# Start AI
    # Applist Function
    update_app_list_filter() {
        local app_list_filter="grep -o -e applist.app.add"
        while IFS= read -r applist || [[ -n "$applist" ]]; do
            filter=$(echo "$applist" | awk '!/ /')
            if [[ -n "$filter" ]]; then
            app_list_filter+=" -e "$filter
            fi
        done < "$RWD/applist_perf.txt"
        APP_LIST_FILTER="$app_list_filter"
    }
    # Applist variable
    APP_LIST_FILTER=""
    update_timer=0
    update_interval=60
    while true; do
        # Update Applist with timer
        if [ "$update_timer" -ge "$update_interval" ]; then
            update_app_list_filter
            update_timer=0  # Reset Timer
        fi
        # AI Function 
        window=$(dumpsys window | grep package | eval "$APP_LIST_FILTER" | tail -1)
        if [ "$(getprop chimera.mode)" == "high" ]; then
            [ "$(getprop chimera.control)" == "1" ] || sh $HIGH | setprop chimera.control 1
            if [ "$(getprop chimera.thermal)" == "0" ]; then
                [ "$(getprop chimera.thermal.control)" == "1" ] || disable_thermal | setprop chimera.thermal.control 1
            elif [ "$(getprop chimera.thermal)" == "1" ]; then
                [ "$(getprop chimera.thermal.control)" == "1" ] || enable_thermal | setprop chimera.thermal.control 1
            elif [ "$(getprop chimera.thermal)" == "notset" ]; then
                :
            fi
        elif [ "$(getprop chimera.mode)" == "std" ] && [ "$(getprop chimera.control)" == 1 ]; then
            [ "$(getprop chimera.control)" == "1" ] || sh $STD | setprop chimera.control 1
            if [ "$(getprop chimera.thermal)" == "0" ]; then
                [ "$(getprop chimera.thermal.control)" == "1" ] || disable_thermal | setprop chimera.thermal.control 1
            elif [ "$(getprop chimera.thermal)" == "1" ]; then
                [ "$(getprop chimera.thermal.control)" == "1" ] || enable_thermal | setprop chimera.thermal.control 1
            elif [ "$(getprop chimera.thermal)" == "notset" ]; then
                :
            fi
        elif [ "$(getprop chimera.mode)" == "powersave" ] && [ "$(getprop chimera.control)" == 1 ]; then
            [ "$(getprop chimera.control)" == "1" ] || sh $PSAVE | setprop chimera.control 1
            if [ "$(getprop chimera.thermal)" == "0" ]; then
                [ "$(getprop chimera.thermal.control)" == "1" ] || disable_thermal | setprop chimera.thermal.control 1
            elif [ "$(getprop chimera.thermal)" == "1" ]; then
                [ "$(getprop chimera.thermal.control)" == "1" ] || enable_thermal | setprop chimera.thermal.control 1
            elif [ "$(getprop chimera.thermal)" == "notset" ]; then
                :
            fi
        else
            setprop chimera.control 0
            if [ -n "$window" ]; then
                [ "$(getprop chimera.mode)" = "performance" ] || sh "$PERF" /dev/null 2>&1 && wait
                if [ "$(getprop chimera.thermal)" == "0" ]; then
                    [ "$(getprop chimera.thermal.control)" == "1" ] || disable_thermal | setprop chimera.thermal.control 1
                elif [ "$(getprop chimera.thermal)" == "1" ]; then
                    [ "$(getprop chimera.thermal.control)" == "1" ] || enable_thermal | setprop chimera.thermal.control 1
                elif [ "$(getprop chimera.thermal)" == "notset" ]; then
                    :
                fi  
            else
                [ "$(getprop chimera.mode)" = "balance" ] || sh "$BAL" /dev/null 2>&1 && wait
                if [ "$(getprop chimera.thermal)" == "0" ]; then
                    [ "$(getprop chimera.thermal.control)" == "1" ] || disable_thermal | setprop chimera.thermal.control 1
                elif [ "$(getprop chimera.thermal)" == "1" ]; then
                    [ "$(getprop chimera.thermal.control)" == "1" ] || enable_thermal | setprop chimera.thermal.control 1
                elif [ "$(getprop chimera.thermal)" == "notset" ]; then
                    :
                fi
            fi
        fi
        # FLow Control
        if [ -n "$window" ]; then
            if [ "$(getprop chimera.flow)" == "1" ]; then
                if [ "$(getprop flow.mode)" == "1" ]; then
                    [ "$(getprop flow.control)" == "1" ] || sh $FLOW/flow.sh | setprop flow.control 1
                elif [ "$(getprop flow.mode)" == "2" ]; then
                    [ "$(getprop flow.control)" == "1" ] || sh $FLOW/flow2.sh | setprop flow.control 1
                elif [ "$(getprop flow.mode)" == "3" ]; then
                    [ "$(getprop flow.control)" == "1" ] || sh $FLOW/flow3.sh | setprop flow.control 1
                elif [ "$(getprop flow.mode)" == "5" ]; then
                    [ "$(getprop flow.control)" == "1" ] || sh $FLOW/flow5.sh | setprop flow.control 1
                elif [ "$(getprop flow.mode)" == "0" ]; then
                    [ "$(getprop flow.control)" == "1" ] || :
                fi
            fi
        else
            [ "$(getprop flow.control)" == "0" ] || setprop flow.control 0
        fi

        # Eterna Control
        if [ -n "$window" ]; then
            if [ "$(getprop chimera.eterna)" == "1" ]; then
                [ "$(getprop eterna.control)" == "1" ] || sh $ETERNA/enable_eterna.sh | setprop eterna.control 1
            fi
        else
            setprop eterna.control 0
            if [ "$(getprop chimera.eterna)" == "1" ]; then
                if [ "$(getprop eterna.control.d)" != "1" ]; then
                    sh $ETERNA/disable_eterna.sh
                else
                    :
                fi
            else
                setprop eterna.control.d 0
            fi
        fi
        # Time Setter
        ((update_timer++))

        sleep 3
    done || { am start -a android.intent.action.MAIN -e toasttext "🛠️ ᴀɪ'ꜱ ʙʀᴏᴋᴇɴ, ɴᴇᴇᴅ ᴛᴏ ꜰɪxɪɴɢ!" -n bellavita.toast/.MainActivity; exit 1; }
