#!/system/bin/sh
# Waiting for boot completed
while [ "$(getprop sys.boot_completed | tr -d '\r')" != "1" ]; do sleep 3; done

# Detect temproot

# Path
MODPATH=${0%/*}
MODPROP="$MODPATH/module.prop"
SCRIPT="$MODPATH/script"
LIB="$MODPATH/script/lib"

# Variables
ZRAMSIZE=0
SWAPSIZE=0

# Load common functions
LOG_FILE="/storage/emulated/0/Lynx/Lynx.log"
log_msg() {
    local msg="[$(date '+%Y-%m-%d %H:%M:%S')] $1"
    echo "$msg" | tee -a "$LOG_FILE"
}

# Read Prop Function
read_prop() {
    sed -nE "s/^$1=(.*)/\1/p" "$MODPROP"
}

# Check and create Lynx directory
mkdir -p "/storage/emulated/0/Lynx" || { log_msg "Failed to create Lynx directory"; exit 1; }

# Log module and device info
{
    log_msg "--------------------"
    log_msg "Module info:"
    log_msg "• Name             : $(read_prop 'name')"
    log_msg "• Version          : $(read_prop 'version')"
    log_msg "• Owner            : $(read_prop 'author')"
    log_msg "• Release Date     : $(read_prop 'versionCode' | sed 's/\(....\)\(..\)\(..\)/\3-\2-\1/')"
    log_msg ""
    log_msg "Device info:"
    log_msg "• Brand            : $(getprop ro.product.system.brand)"
    log_msg "• Device           : $(getprop ro.product.system.model)"
    log_msg "• Processor        : $(getprop ro.product.board)"
    log_msg "• Android Version  : $(getprop ro.system.build.version.release)"
    log_msg "• SDK Version      : $(getprop ro.build.version.sdk)"
    log_msg "• Architecture     : $(getprop ro.product.cpu.abi)"
    log_msg "• Kernel Version   : $(uname -r)"
    log_msg ""
    log_msg "Profile Mode:"
} || { log_msg "Failed to write module and device info to log"; exit 1; }

# Device online functions
wait_until_login()
{
    # whether in lock screen, tested on Android 7.1 & 10.0
    # in case of other magisk module remounting /data as RW
    while [ "$(dumpsys window policy | grep mInputRestricted=true)" != "" ]; do
        sleep 2
    done
    # we doesn't have the permission to rw "/sdcard" before the user unlocks the screen
    while [ ! -d "/sdcard/Android" ]; do
        sleep 2
    done
}

# Unlock Screen fullroot
screen_unlock()
{
su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʏ' 'Lʏɴx' '⚠️ 𝙋𝙡𝙚𝙖𝙨𝙚 𝙐𝙣𝙡𝙤𝙘𝙠 𝙔𝙤𝙪𝙧 𝙇𝙤𝙘𝙠𝙨𝙘𝙧𝙚𝙚𝙣!'" >/dev/null 2>&1
}
#screen_unlock

# Device online
#wait_login_temproot

# Sync to data in the rare case a device crashes
sync

# Change zram
#change_zram

# Swap ram
#change_swap

# Device online
#wait_login_fullroot

# Load Config File
. "$MODPATH/script/lib/lynx.conf"
CONFIG_FILE="$MODPATH/script/lib/lynx.conf"
if [ -f "$CONFIG_FILE" ]; then
    while IFS= read -r line || [ -n "$line" ]; do
        echo "$line" | grep -q '^#' && continue
        echo "$line" | grep -q '^$' && continue
        NAME=$(echo "$line" | cut -d= -f1)
        VALUE=$(echo "$line" | cut -d= -f2)
        if [[ "$NAME" == *widow* || "$NAME" == *trans* || "$NAME" == *anim* ]]; then
            log_msg "⏩ Skipping prop: $NAME"
            continue
        fi
        resetprop "$NAME" "$VALUE"
        log_msg "resetprop $NAME $VALUE"
    done < "$CONFIG_FILE"
    log_msg "✅ All props applied (excluding skipped patterns)."
else
    log_msg "❌ Configuration file not found: $CONFIG_FILE"
fi

# Enable all tweak
sed -Ei "s/^description=\[.*\]/description=[ ⚙️ Aᴘᴘʟʏ ᴛᴡᴇᴀᴋꜱ ᴘʟᴇᴀꜱᴇ ᴡᴀɪᴛ... ]/" "$MODPROP"
su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʏ' 'Lʏɴx' '⚙️ 𝘼𝙥𝙥𝙡𝙮 𝙏𝙬𝙚𝙖𝙠𝙨 𝙋𝙡𝙚𝙖𝙨𝙚 𝙒𝙖𝙞𝙩...'" >/dev/null 2>&1

# Thermal Service
cmd thermalservice override-status 0

# DNS Routing
dns_provider=""
if [ -n "$dns_provider" ]; then
    Lxcore -dns "$dns_provider" || log_msg "Lxcore is not available right now!"
else
    log_msg "No changes to DNS Provider, skipping configuration."
fi

# Internet Tweak
#Internet_Tweak

# Unity Big.Little trick by lybxlpsv 
#unitytrick_enable

# Animation scales
animation_system() {
    if [ "$window" != "" ]; then
        settings put global window_animation_scale $window
    fi
    if [ "$trans" != "" ]; then
        settings put global transition_animation_scale $trans
    fi
    if [ "$anim" != "" ]; then
        settings put global animator_duration_scale $anim
    fi
}

# Animation Tweak
animation_system
settings put secure long_press_timeout 280
settings put secure multi_press_timeout 80

# Run Script
Lxcore -io apply
Lxcore -task apply
Lxcore -cpu apply
Lxcore -gpu apply
Lxcore -ram apply
Lxcore -system apply

# Run add-on
# for script in $(grep -E '^[a-zA-Z_][a-zA-Z0-9_]*\(\)' $LIB/add_on.sh | awk -F '(' '{print $1}'); do
#     "$script" >/dev/null 2>&1
# done

# Cron Job
crond -f -c $MODPATH/cron -l 5 -L $MODPATH/cron/cron.log &

# Dexoat Optimizer
#dex2oat_opt_enable

# Doze mode
#dozemode

# Disable Ramdumps
if [ -d "/sys/module/subsystem_restart/parameters" ]
then
    echo "0" > /sys/module/subsystem_restart/parameters/enable_ramdumps
    echo "0" > /sys/module/subsystem_restart/parameters/enable_mini_ramdumps
fi

# Disable all Log and Debug Mask
find /sys/ -type f \( \
    -name 'debug_mask' -or \
    -name 'debug_level' -or \
    -name 'enable_event_log' -or \
    -name 'log_level*' -or \
    -name '*debug_mode' -or \
    -name 'edac_mc_log*' -or \
    -name '*log_ue*' -or \
    -name '*log_ce*' -or \
    -name 'log_ecn_error' -or \
    -name 'seclog*' -or \
    -name 'compat-log' -or \
    -name '*log_enabled' -or \
    -name 'tracing_on' -or \
    -name 'mballoc_debug' \
\) -exec sh -c 'echo 0 > "$1"' _ {} \;

# Disable debuggers bluetooth
for bl in /sys/module/bluetooth/parameters/disable_ertm /sys/module/bluetooth/parameters/disable_esco
do
if [[ -e "$bl" ]]; then
    echo "Y" > "$bl"
fi
done

# Disable System Log
pm uninstall --user 0 com.android.traceur

# Disable Some Service Log
stop logcat logcatd logd tcpdump cnss_diag statsd traced idd-logreader idd-logreadermain stats dumpstate aplogd vendor_tcpdump vendor.tcpdump vendor.cnss_diag

# Low Latency Wi-Fi
cmd wifi force-low-latency-mode enabled

# Clear Wi-Fi Logs
rm -rf /data/vendor/wlan_logs
touch /data/vendor/wlan_logs
chmod 000 /data/vendor/wlan_logs

# Cache Cleaner
#cache_cleaner
 
# Fstrim
fstrim /system
fstrim /vendor
fstrim /metadata
fstrim /odm
fstrim /system_ext
fstrim /product
fstrim /data
fstrim /cache
for sd in /storage/*; do
  fstrim -v ${sd}
done

# Dark GPU
nohup sh "$LIB/dark_gpu.sh" > /dev/null 2>&1 &

# Charging Control
charging_control() {
  nohup sh $SCRIPT/Charging-Controller.sh > /dev/null 2>&1 &
}
#charging_control

# Kill unused process
sync && echo "3" > /proc/sys/vm/drop_caches
am kill-all

# Run Ai
sleep 3
nohup sh "$SCRIPT/Smart-AI.sh" > /dev/null 2>&1 &

# Beta Test
nohup sh $LIB/beta.sh

# Done
. "$LIB/updater.sh" > /dev/null 2>&1 &
sed -Ei "s/^description=\[.*\]/description=[ ✅ Aʟʟ ᴛᴡᴇᴀᴋꜱ ɪꜱ ᴀᴘᴘʟɪᴇᴅ ]/" "$MODPROP"
su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʏ' 'Lʏɴx' '✅  𝘼𝙡𝙡 𝙏𝙬𝙚𝙖𝙠𝙨 𝙞𝙨 𝘼𝙥𝙥𝙡𝙞𝙚𝙙...'" >/dev/null 2>&1