#!/system/bin/sh
# Lynx Universal - JSON State Management Library
# Pure POSIX /system/bin/sh compatible (Android Toybox/ash)
# Supports top-level keys ("active_profile") and dot-notation ("section.field")

CONFIG_FILE="/data/adb/modules/Lynx/config.json"
DEFAULT_TEMPLATE='{
  "version": 1,
  "module_version": "3.0.0",
  "release_type": "beta",
  "active_profile": "dormant",
  "setup_pending": true,
  "target_soc": "generic",
  "is_spoofed": false,
  "hardware": {
    "soc_type": "generic",
    "soc_name": "Generic Linux",
    "is_overclocked": false
  },
  "overclock": {
    "enabled": false,
    "cpu_floor_ratio": 85,
    "zram_size_mb": 2048,
    "swappiness": 80,
    "custom_scaling_min": 0,
    "custom_scaling_max": 0
  },
  "memory": {
    "zram_enabled": true,
    "zram_size_mb": 2048,
    "comp_algorithm": "auto",
    "swappiness": 80,
    "mglru_enabled": true
  },
  "charging": {
    "bypass_enabled": false,
    "temp_cutoff_c": 45,
    "limit_current_ma": 1500,
    "max_battery_percent": 80,
    "auto_cut_enabled": true
  },
  "uclamp": {
    "game_min_ratio": 70,
    "game_max_ratio": 100,
    "balance_min_ratio": 20,
    "schedutil_up_rate_us": 500,
    "schedutil_down_rate_us": 20000
  },
  "display_touch": {
    "touchboost": true,
    "refresh_rate_lock": 120,
    "surfaceflinger_offset_ns": 2000000,
    "skia_vulkan_enabled": false
  },
  "network": {
    "wifi_ping_stabilizer": true,
    "tcp_congestion": "bbr",
    "fq_codel_enabled": true,
    "wifi_power_save_in_game": false
  },
  "audio": {
    "low_latency_mmap": true,
    "fast_track_enabled": true,
    "bt_crackle_guard": true
  },
  "oem_neutralizer": {
    "joyose_neutralize": true,
    "gos_neutralize": true,
    "freeze_method": "sigstop"
  },
  "thermal": {
    "full_bypass": false,
    "custom_temp_limit_c": 50,
    "trip_point_override_c": 150
  },
  "game_manager": {
    "auto_detect_games": true,
    "custom_apps": []
  },
  "features": {
    "touchboost": true,
    "network_boost": true,
    "audio_mmap": true,
    "oem_neutralizer": true,
    "unity_trick": false,
    "sched_features": true
  },
  "diagnostics": {
    "log_level": "verbose",
    "last_bugreport": ""
  }
}'

init_state() {
    local target_dir
    target_dir=$(dirname "$CONFIG_FILE")
    [ -d "$target_dir" ] || mkdir -p "$target_dir" 2>/dev/null
    if [ ! -f "$CONFIG_FILE" ]; then
        echo "$DEFAULT_TEMPLATE" > "$CONFIG_FILE"
        chmod 644 "$CONFIG_FILE" 2>/dev/null
    fi
}

get_state_val() {
    local key="$1"
    [ -f "$CONFIG_FILE" ] || init_state

    case "$key" in
        *.*)
            local sec="${key%%.*}"
            local fld="${key#*.}"
            awk -v sec="$sec" -v fld="$fld" '
            BEGIN { in_sec = 0 }
            {
                if ($0 ~ ("\"" sec "\"[ \t]*:[ \t]*\\{")) {
                    in_sec = 1
                    next
                }
                if (in_sec) {
                    if ($0 ~ /^[ \t]*\}/) {
                        in_sec = 0
                        next
                    }
                    if ($0 ~ ("\"" fld "\"[ \t]*:")) {
                        sub(/.*:[ \t]*/, "")
                        sub(/^[ \t]*\"/, "")
                        sub(/\"[, \t]*$/, "")
                        sub(/[, \t]*$/, "")
                        print
                        exit
                    }
                }
            }
            ' "$CONFIG_FILE" 2>/dev/null
            ;;
        *)
            awk -v target="$key" '
            {
                if ($0 ~ ("\"" target "\"[ \t]*:")) {
                    sub(/.*:[ \t]*/, "")
                    sub(/^[ \t]*\"/, "")
                    sub(/\"[, \t]*$/, "")
                    sub(/[, \t]*$/, "")
                    print
                    exit
                }
            }
            ' "$CONFIG_FILE" 2>/dev/null
            ;;
    esac
}

set_state_val() {
    local key="$1"
    local val="$2"
    local is_string="$3" # true/false
    [ -f "$CONFIG_FILE" ] || init_state

    local tmp_file="$CONFIG_FILE.tmp"
    if [ "$is_string" = "true" ]; then
        val="\"$val\""
    fi

    case "$key" in
        *.*)
            local sec="${key%%.*}"
            local fld="${key#*.}"
            awk -v sec="$sec" -v fld="$fld" -v newval="$val" '
            BEGIN { in_sec = 0 }
            {
                if ($0 ~ ("\"" sec "\"[ \t]*:[ \t]*\\{")) {
                    in_sec = 1
                    print $0
                    next
                }
                if (in_sec) {
                    if ($0 ~ /^[ \t]*\}/) {
                        in_sec = 0
                        print $0
                        next
                    }
                    if ($0 ~ ("\"" fld "\"[ \t]*:")) {
                        match($0, /^[ \t]*\"[^\"]+\"[ \t]*:[ \t]*/)
                        prefix = substr($0, RSTART, RLENGTH)
                        comma = ($0 ~ /,[ \t]*$/) ? "," : ""
                        print prefix newval comma
                        next
                    }
                }
                print $0
            }
            ' "$CONFIG_FILE" > "$tmp_file" 2>/dev/null
            ;;
        *)
            awk -v target="$key" -v newval="$val" '
            {
                if ($0 ~ ("\"" target "\"[ \t]*:")) {
                    match($0, /^[ \t]*\"[^\"]+\"[ \t]*:[ \t]*/)
                    prefix = substr($0, RSTART, RLENGTH)
                    comma = ($0 ~ /,[ \t]*$/) ? "," : ""
                    print prefix newval comma
                } else {
                    print $0
                }
            }
            ' "$CONFIG_FILE" > "$tmp_file" 2>/dev/null
            ;;
    esac

    if [ -s "$tmp_file" ]; then
        mv "$tmp_file" "$CONFIG_FILE" 2>/dev/null
        chmod 644 "$CONFIG_FILE" 2>/dev/null
        if [ "$key" = "active_profile" ]; then
            mod_p="/data/adb/modules/Lynx"
            [ -d "$mod_p" ] || mod_p="${0%/*/*/*}"
            [ -f "$mod_p/core/lib/state_watcher.sh" ] && nohup sh "$mod_p/core/lib/state_watcher.sh" >/dev/null 2>&1 &
        fi
        return 0
    else
        rm -f "$tmp_file" 2>/dev/null
        return 1
    fi
}
