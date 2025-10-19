#!/bin/sh

help_cache() {
    log_msg "Displaying help for Cache cleaning..."
    echo "Usage: Lxcore -cache [apply|help]"
    echo ""
    echo "Options:"
    echo "  apply     Clean all unnecessary cache and junk files."
    echo "  help      Show this help message."
    echo ""
    echo "Description of applied actions:"
    echo "  - Removes directories with names containing 'cache' in the following locations:"
    echo "    - /data/data"
    echo "    - /data/media/0/Android/data"
    echo "    - /data_mirror/data_ce/null/0"
    echo "    - /data_mirror/data_de/null/0"
    echo "    - /data/user_de/0"
    echo "    - /data/user/0"
    echo "  - Excludes specific directories like '*hkrpgoversea*' and '*genshin*' to prevent accidental deletion."
    echo "  - Deletes junk files from various system directories:"
    echo "    - /cache/ (excluding 'magisk.log')"
    echo "    - /data/vendor/thermal/"
    echo "    - /data/vendor/wlan_logs/"
    echo "    - /data/anr/"
    echo "    - /data/log_other_mode/"
    echo "    - /data/log/"
    echo "    - /sys/kernel/debug/"
    echo "    - /dev/log/main"
    echo "    - /data/system/dropbox/"
    echo "  - Displays a notification confirming that the cache has been cleaned."
}

main_cache() {
    log_msg "Starting cache cleaning process..."
    clean_cache() {
        find "$1" -type d -iname '*cache*' \
        -not \( -ipath "*/*hkrpgoversea*/*" -o -ipath "*/*genshin*/*" \) \
        -exec rm -rf {} + >/dev/null 2>&1
    }
    clean_cache "/data/data"
    clean_cache "/data/media/0/Android/data"
    clean_cache "/data_mirror/data_ce/null/0"
    clean_cache "/data_mirror/data_de/null/0"
    clean_cache "/data/user_de/0"
    clean_cache "/data/user/0"
    rm -rf /cache/* > /dev/null 2>&1
    echo "⚠️ Keeping magisk.log" > /dev/null 2>&1
    rm -rf /data/vendor/thermal/* > /dev/null 2>&1
    rm -rf /data/vendor/wlan_logs/* > /dev/null 2>&1
    rm -rf /data/anr/* > /dev/null 2>&1
    rm -rf /data/log_other_mode/* > /dev/null 2>&1
    rm -rf /data/log/* > /dev/null 2>&1
    rm -rf /sys/kernel/debug/* > /dev/null 2>&1
    rm -rf /dev/log/main > /dev/null 2>&1
    rm -rf /data/system/dropbox/* > /dev/null 2>&1
    log_msg "Cache cleaned successfully."
    if command -v am > /dev/null 2>&1; then
        am start -a android.intent.action.MAIN -e toasttext "🧹 Cᴀᴄʜᴇ Cʟᴇᴀɴᴇᴅ" -n bellavita.toast/.MainActivity > /dev/null 2>&1
    else
        log_msg "Command 'am' not found. Skipping toast notification."
    fi
}