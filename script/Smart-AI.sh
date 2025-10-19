#!/system/bin/sh
# AI Controller 5.0
# By Noir

# Sync all
sync

# Paths
BASEDIR="/data/adb/modules/Lynx"
RWD="/storage/emulated/0/Lynx"
LOG_FILE="$RWD/Lynx.log"
MSC="$BASEDIR/script"
BAL="$MSC/AI_balance.sh"
PERF="$MSC/AI_performance.sh"
HIGH="$MSC/high_perf.sh"
PSAVE="$MSC/powersave.sh"
OPT="$MSC/aggresive.sh"
APPLIST="$RWD/applist_perf.txt"
FLOW="$MSC/flow"
MODPROP="$BASEDIR/module.prop"

# Source Files
source "$MSC/enable_thermal.sh"
source "$MSC/disable_thermal.sh"

# Logging Function
log_msg() {
    local msg="[$(date '+%Y-%m-%d %H:%M:%S')] $1"
    echo "$msg" | tee -a "$LOG_FILE"
}

# Prop Control
setprop lynx.thermal.control notset
setprop flow.control notset

# Check and create Lynx directory
mkdir -p "$RWD" || { log_msg "Failed to create Lynx directory"; exit 1; }

# Check and create applist file
[ ! -e "$APPLIST" ] && cp -f "$MSC/applist_perf.txt" "$RWD"

# AI Interface
sed -Ei "s/^description=\[.*\]/description=[ 🤖 Aɪ ɪꜱ ꜱᴛᴀʀᴛᴇᴅ ]/" "$MODPROP"
am start -a android.intent.action.MAIN -e toasttext "🤖 Aɪ ɪꜱ ꜱᴛᴀʀᴛᴇᴅ..." -n bellavita.toast/.MainActivity

# Applist Function
update_app_list_filter() {
    local app_list_filter="grep -o -e applist.app.add"
    while IFS= read -r applist || [[ -n "$applist" ]]; do
        filter=$(echo "$applist" | awk '!/ /')
        [ -n "$filter" ] && app_list_filter+=" -e $filter"
    done < "$APPLIST"
    APP_LIST_FILTER="$app_list_filter"
}

# Start AI
update_timer=0
update_interval=60
while true; do
    # Update Applist with timer
    [ "$update_timer" -ge "$update_interval" ] && { update_app_list_filter; update_timer=0; }

    # AI Function
    window=$(dumpsys window | grep package | $APP_LIST_FILTER)
    case "$(getprop lynx.mode)" in
        high)
            [ "$(getprop lynx.control)" != "1" ] && { sh "$HIGH" && setprop lynx.control 1; }
            ;;
        aggresive)
            [ "$(getprop lynx.control)" != "1" ] && { sh "$OPT" && setprop lynx.control 1; }
            ;;
        powersave)
            [ "$(getprop lynx.control)" != "1" ] && { sh "$PSAVE" && setprop lynx.control 1; }
            ;;
        *)
            if [ -n "$window" ]; then
                [ "$(getprop lynx.mode)" != "performance" ] && { sh "$PERF" /dev/null 2>&1 && setprop lynx.mode performance; }
            else
                [ "$(getprop lynx.mode)" != "balance" ] && { sh "$BAL" /dev/null 2>&1 && setprop lynx.mode balance; }
            fi
            setprop lynx.control 0
            ;;
    esac

    # Thermal Control
    if [ "$(getprop lynx.thermal)" = "0" ]; then
        [ "$(getprop lynx.thermal.control)" != "1" ] && { disable_thermal && setprop lynx.thermal.control 1; }
    elif [ "$(getprop lynx.thermal)" = "1" ]; then
        [ "$(getprop lynx.thermal.control)" != "1" ] && { enable_thermal && setprop lynx.thermal.control 1; }
    fi

    # Flow Control
    if [ -n "$window" ] && [ "$(getprop lynx.flow)" = "1" ]; then
        case "$(getprop flow.mode)" in
            1) [ "$(getprop flow.control)" != "1" ] && { sh "$FLOW/flow.sh" && setprop flow.control 1; } ;;
            2) [ "$(getprop flow.control)" != "1" ] && { sh "$FLOW/flow2.sh" && setprop flow.control 1; } ;;
            3) [ "$(getprop flow.control)" != "1" ] && { sh "$FLOW/flow3.sh" && setprop flow.control 1; } ;;
            5) [ "$(getprop flow.control)" != "1" ] && { sh "$FLOW/flow5.sh" && setprop flow.control 1; } ;;
            *) setprop flow.control 0 ;;
        esac
    else
        setprop flow.control 0
    fi

    # Time Setter
    ((update_timer++))
    sleep 0.5
done || { am start -a android.intent.action.MAIN -e toasttext "🛠️ ᴀɪ'ꜱ ʙʀᴏᴋᴇɴ, ɴᴇᴇᴅ ᴛᴏ ꜰɪxɪɴɢ!" -n bellavita.toast/.MainActivity; exit 1; }