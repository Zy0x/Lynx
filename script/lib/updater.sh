#!/bin/bash

# Read Prop Function
read_prop() {
    prop_name="$1"
    sed -nE "s/^$prop_name=(.*)/\1/p" "$MODPROP"
}

# URL Update
update_url="$(read_prop 'updateJson')"

# Notify function with release type color coding
notify_user() {
    local title="𝗟𝘆𝗻𝘅 - 𝗨𝗽𝗱𝗮𝘁𝗲𝗿"
    local message="$1"
    local icon="$2"
    su -lp 2000 -c "cmd notification post -S bigtext -t '$title' 'LynxUpdaterTag' '$icon $message'" >/dev/null 2>&1
}

# Check Internet Connection
check_internet() {
    curl --silent --head --fail "$update_url" > /dev/null
}

# Notify checking internet
notify_user "♻️ 𝘾𝙝𝙚𝙘𝙠𝙞𝙣𝙜 𝙞𝙣𝙩𝙚𝙧𝙣𝙚𝙩 𝙘𝙤𝙣𝙣𝙚𝙘𝙩𝙞𝙤𝙣..." "📶"

# Wait for internet connection
while ! check_internet; do
    log_msg "No internet connection. Waiting..."
    sleep 10
done

notify_user "🟢 𝘾𝙤𝙣𝙣𝙚𝙘𝙩𝙚𝙙 𝙩𝙤 𝙨𝙚𝙧𝙫𝙚𝙧" "📶"

# Get current module version and release type
ori_ver=$(read_prop 'version')
ori_ver_code=$(read_prop 'versionCode')
ori_release_type=$(read_prop 'releaseType')

# Fetch latest module version, version code, and release type from the server
base_ver=$(curl -s "$update_url" | grep '"version":' | awk '{print $2}' | sed 's/[" ,]//g')
base_ver_code=$(curl -s "$update_url" | grep '"versionCode":' | awk '{print $2}' | sed 's/[ ,]//g')
base_release_type=$(curl -s "$update_url" | grep '"releaseType":' | awk '{print $2}' | sed 's/[" ,]//g')

# Convert versions to numeric format for comparison
ori_ver_num=$(echo "$ori_ver" | sed 's/[^0-9.]//g' | awk -F. '{print $1"."$2$3$4$5$6}')
base_ver_num=$(echo "$base_ver" | sed 's/[^0-9.]//g' | awk -F. '{print $1"."$2$3$4$5$6}')

# Validate versions
if [ -z "$ori_ver" ] || [ -z "$ori_ver_code" ] || [ -z "$ori_release_type" ]; then
    notify_user "🔴 𝙁𝙖𝙞𝙡𝙚𝙙 𝙩𝙤 𝙧𝙚𝙩𝙧𝙞𝙚𝙫𝙚 𝙘𝙪𝙧𝙧𝙚𝙣𝙩 𝙢𝙤𝙙𝙪𝙡𝙚 𝙫𝙚𝙧𝙨𝙞𝙤𝙣 𝙤𝙧 𝙧𝙚𝙡𝙚𝙖𝙨𝙚 𝙩𝙮𝙥𝙚" "❌"
    log_msg "Failed to retrieve current module version or release type."
    exit 1
fi

if [ -z "$base_ver" ] || [ -z "$base_ver_code" ] || [ -z "$base_release_type" ]; then
    notify_user "🔴 𝙁𝙖𝙞𝙡𝙚𝙙 𝙩𝙤 𝙛𝙚𝙩𝙘𝙝 𝙡𝙖𝙩𝙚𝙨𝙩 𝙫𝙚𝙧𝙨𝙞𝙤𝙣 𝙤𝙧 𝙧𝙚𝙡𝙚𝙖𝙨𝙚 𝙩𝙮𝙥𝙚 𝙛𝙧𝙤𝙢 𝙨𝙚𝙧𝙫𝙚𝙧" "❌"
    log_msg "Failed to fetch latest version or release type from server."
    exit 1
fi

# Determine emoji based on release type
get_release_icon() {
    case "$1" in
        stable) echo "🟢";;
        beta) echo "🔵";;
        dev) echo "🔴";;
        alpha) echo "🟡";;
        canary) echo "🟣";;
        *) echo "⚪";;
    esac
}

base_icon=$(get_release_icon "$base_release_type")
current_icon=$(get_release_icon "$ori_release_type")

# Compare versions
if (( $(echo "$ori_ver_num < $base_ver_num" | bc -l) )); then
    # Notify user about available update and release type
    notify_user "𝙐𝙥𝙙𝙖𝙩𝙚 𝙖𝙫𝙖𝙞𝙡𝙖𝙗𝙡𝙚: $base_ver ($base_ver_code) [$base_release_type]" "$base_icon"
    log_msg "Update available to $base_ver ($base_ver_code) [$base_release_type]."
else
    # Check release type mismatch even if no update is available
    if [ "$ori_release_type" != "$base_release_type" ]; then
        notify_user "⚠️ 𝙉𝙤 𝙪𝙥𝙙𝙖𝙩𝙚, 𝙗𝙪𝙩 𝙧𝙚𝙡𝙚𝙖𝙨𝙚 𝙩𝙮𝙥𝙚 𝙢𝙞𝙨𝙢𝙖𝙩𝙘𝙝. 𝘾𝙪𝙧𝙧𝙚𝙣𝙩: $ori_release_type $current_icon, 𝘼𝙫𝙖𝙞𝙡𝙖𝙗𝙡𝙚: $base_release_type $base_icon"
        log_msg "No update, but release type mismatch. Current: $ori_release_type, Available: $base_release_type."
    else
        notify_user "𝙑𝙚𝙧𝙨𝙞𝙤𝙣 𝙞𝙨 𝙐𝙥 𝙩𝙤 𝘿𝙖𝙩𝙚 [$base_release_type]" "$base_icon"
        log_msg "Version is up to date [$base_release_type]."
    fi
fi