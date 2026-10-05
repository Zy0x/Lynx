#!/system/bin/sh
# Lynx Universal - OTA Update Checker
# Pure POSIX /system/bin/sh compatible

MODPATH="/data/adb/modules/Lynx"
[ -d "$MODPATH" ] || MODPATH="${0%/*/*/*}"
MODPROP="$MODPATH/module.prop"
LOG_FILE="/storage/emulated/0/Lynx/Lynx.log"

log_msg() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOG_FILE" 2>/dev/null
}

read_prop() {
    prop_name="$1"
    [ -f "$MODPROP" ] && sed -nE "s/^$prop_name=(.*)/\1/p" "$MODPROP"
}

update_url="$(read_prop 'updateJson')"
[ -z "$update_url" ] && exit 0

notify_user() {
    local title="Lynx - Updater"
    local message="$1"
    su -lp 2000 -c "cmd notification post -S bigtext -t '$title' 'LynxUpdaterTag' '$message'" >/dev/null 2>&1
}

check_internet() {
    curl --silent --head --fail "$update_url" >/dev/null 2>&1
}

# Wait for internet connection (max 60 seconds)
attempts=0
while ! check_internet; do
    attempts=$((attempts + 1))
    [ $attempts -ge 6 ] && exit 0
    sleep 10
done

ori_ver=$(read_prop 'version')
ori_ver_code=$(read_prop 'versionCode')
ori_release_type=$(read_prop 'releaseType')

# Fetch latest version info from GitHub update JSON
raw_json=$(curl -s --connect-timeout 10 "$update_url" 2>/dev/null)
[ -z "$raw_json" ] && exit 0

base_ver=$(echo "$raw_json" | grep '"version":' | awk '{print $2}' | sed 's/[" ,]//g')
base_ver_code=$(echo "$raw_json" | grep '"versionCode":' | awk '{print $2}' | sed 's/[ ,]//g')
base_release_type=$(echo "$raw_json" | grep '"releaseType":' | awk '{print $2}' | sed 's/[" ,]//g')

[ -z "$ori_ver_code" ] || [ -z "$base_ver_code" ] && exit 0

if [ "$base_ver_code" -gt "$ori_ver_code" ] 2>/dev/null; then
    notify_user "Update available: $base_ver ($base_ver_code) [$base_release_type]"
    log_msg "Update available to $base_ver ($base_ver_code) [$base_release_type]."
else
    log_msg "Version is up to date: $ori_ver ($ori_ver_code)."
fi