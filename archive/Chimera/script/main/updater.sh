#!/bin/bash
MODPROP="/data/adb/modules/Chimera/module.prop"
# Read Prop Function
read_prop() {
    prop_name="$1"
    sed -nE "s/^$prop_name=(.*)/\1/p" "$MODPROP"
}

# Ori Path
file_path="$MODPROP"

# URL Update
update_url="$(read_prop 'updateJson')"

# Check Internet
check_internet() {
    curl --silent --head --fail $update_url > /dev/null
}
su -lp 2000 -c "cmd notification post -S bigtext -t 'Chimera - Updater' 'Chimera - Updater' '♻️ Check internet connection...'" >/dev/null 2>&1
while ! check_internet; do
    echo "No internet connection. Wait..."
    sleep 10
done
su -lp 2000 -c "cmd notification post -S bigtext -t 'Chimera - Updater' 'Chimera - Updater' '🟢 Connected to server'" >/dev/null 2>&1

# Get module version
ori_ver=$(cat "$file_path" | grep "version=" | awk -F= '{print $2}')
ori_ver_code=$(cat "$file_path" | grep "versionCode=" | awk -F= '{print $2}' | sed 's/ //g')

# Get latest module version
base_ver=$(curl -s "$update_url" | grep '"version":' | awk '{print $2}' | sed 's/[" ,]//g')
base_ver_code=$(curl -s "$update_url" | grep '"versionCode":' | awk '{print $2}' | sed 's/[ ,]//g')
ori_ver_num=$(echo "$ori_ver" | sed 's/[^0-9.]//g' | awk -F. '{print $1"."$2$3$4$5$6}')
base_ver_num=$(echo "$base_ver" | sed 's/[^0-9.]//g' | awk -F. '{print $1"."$2$3$4$5$6}')

# Check version
if [ -z $ori_ver ] | [ -z $ori_ver_code ]; then
    su -lp 2000 -c "cmd notification post -S bigtext -t 'Chimera - Updater' 'Chimera - Updater' '🔴 Failed to retrieve current module version'" >/dev/null 2>&1
    exit 1
else
    if [ -z $base_ver ] | [ -z $base_ver_code ]; then
        su -lp 2000 -c "cmd notification post -S bigtext -t 'Chimera - Updater' 'Chimera - Updater' '🔴 Failed to fetch latest version from server'" >/dev/null 2>&1
        exit 1
    fi
fi

# Compare version
if [ $ori_ver == $base_ver ] && [ $ori_ver_code -lt $base_ver_code ]; then
    su -lp 2000 -c "cmd notification post -S bigtext -t 'Chimera - Updater' 'Chimera - Updater' '🟡 Update available to $base_ver ($base_ver_code)'" >/dev/null 2>&1
elif [ $ori_ver == $base_ver ] && [ $ori_ver_code -ge $base_ver_code ]; then
    su -lp 2000 -c "cmd notification post -S bigtext -t 'Chimera - Updater' 'Chimera - Updater' '🟢 Version is Up to Date'" >/dev/null 2>&1
else
    if (( $(echo "$ori_ver_num < $base_ver_num" | bc -l) )); then
        su -lp 2000 -c "cmd notification post -S bigtext -t 'Chimera - Updater' 'Chimera - Updater' '🟡 Update available to $base_ver ($base_ver_code)'" >/dev/null 2>&1
    elif (( $(echo "$ori_ver_num > $base_ver_num" | bc -l) )); then
        su -lp 2000 -c "cmd notification post -S bigtext -t 'Chimera - Updater' 'Chimera - Updater' '🟢 Version is Up to Date'" >/dev/null 2>&1
    fi
fi
