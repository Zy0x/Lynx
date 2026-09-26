#!/system/bin/sh
# Lynx Universal - WebUI Dispatcher Bridge v3.0
# Hardened input validation for ksu.exec() calls
# Routes: clean_cache | set_mode | get_status | maintenance | export_log | set_state

MODULE_DIR="/data/adb/modules/Lynx"
LXCORE="$MODULE_DIR/system/bin/Lxcore"
ACTION="$1"
PARAM="$2"
PARAM2="$3"
PARAM3="$4"

case "$ACTION" in
    # ── Cache & Memory Cleanup ─────────────────────────────────────
    clean_cache|"")
        if [ -f "$MODULE_DIR/core/CCleaner.sh" ]; then
            sh "$MODULE_DIR/core/CCleaner.sh"
        else
            echo "1" > /proc/sys/vm/compact_memory 2>/dev/null
            am start -a android.intent.action.MAIN -e toasttext "🧹 Cᴀᴄʜᴇ Cʟᴇᴀɴᴇᴅ" \
                -n bellavita.toast/.MainActivity > /dev/null 2>&1
        fi
        echo "Cache and memory optimization executed."
        ;;

    # ── Performance Mode Setter ────────────────────────────────────
    set_mode)
        case "$PARAM" in
            high|balance|aggressive|performance|extreme|auto|dormant|powersave)
                sh "$LXCORE" state set "active_profile" "$PARAM" str 2>/dev/null
                setprop lynx.mode "$PARAM" 2>/dev/null
                echo "Success: active_profile set to $PARAM"
                ;;
            *)
                echo "Error: Invalid mode '$PARAM'"
                exit 1
                ;;
        esac
        ;;

    # ── Generic State Key Setter (dot notation) ─────────────────────
    set_state)
        # PARAM = key (e.g. "uclamp.game_min_ratio")
        # PARAM2 = value
        # PARAM3 = type (str|val|bool)
        if [ -z "$PARAM" ] || [ -z "$PARAM2" ]; then
            echo "Error: Missing key or value for set_state"
            exit 1
        fi
        # Sanitize key: allow only a-z A-Z 0-9 _ . -
        SAFE_KEY=$(echo "$PARAM" | sed 's/[^a-zA-Z0-9._-]//g')
        TYPE="${PARAM3:-str}"
        sh "$LXCORE" state set "$SAFE_KEY" "$PARAM2" "$TYPE" 2>/dev/null
        echo "Success: $SAFE_KEY = $PARAM2"
        ;;

    # ── Quick Status ───────────────────────────────────────────────
    get_status)
        soc=$(cat "$MODULE_DIR/target_soc" 2>/dev/null | tr -d '[:space:]')
        if [ -z "$soc" ] || [ "$soc" = "generic" ] || [ "$soc" = "unknown" ]; then
            if [ -f "$MODULE_DIR/core/lib/hw_probe.sh" ]; then
                . "$MODULE_DIR/core/lib/hw_probe.sh"
                real_soc=$(detect_real_soc)
                [ "$real_soc" != "generic" ] && soc="$real_soc"
            fi
        fi
        [ -z "$soc" ] && soc="generic"
        echo "SOC: $soc"
        echo "MODE: $(sh "$LXCORE" state get active_profile 2>/dev/null || getprop lynx.mode 2>/dev/null || echo balance)"
        echo "TEMP: $(cat /sys/class/power_supply/battery/temp 2>/dev/null || echo 0)"
        echo "VERSION: $(grep '^version=' "$MODULE_DIR/module.prop" 2>/dev/null | cut -d= -f2 || echo 3.0.0)"
        echo "SPOOFED: $(grep '"is_spoofed"' "$MODULE_DIR/config.json" 2>/dev/null | grep -o 'true\|false' || echo false)"
        ;;

    # ── Storage Maintenance (SQLite VACUUM + fstrim) ───────────────
    maintenance)
        if [ -f "$MODULE_DIR/core/lib/maintenance.sh" ]; then
            sh "$MODULE_DIR/core/lib/maintenance.sh" manual 2>&1
        else
            echo "Error: maintenance.sh not found"
            exit 1
        fi
        ;;

    # ── 1-Click Bug Report Export ──────────────────────────────────
    export_log)
        if [ -f "$LXCORE" ]; then
            sh "$LXCORE" log export 2>&1
        else
            echo "Error: Lxcore not found at $LXCORE"
            exit 1
        fi
        ;;

    # ── Lynx Kernel Manager: Telemetry & Hardware Control ──────────
    telemetry)
        if [ -f "$MODULE_DIR/core/lib/telemetry.sh" ]; then
            sh "$MODULE_DIR/core/lib/telemetry.sh"
        else
            echo "{}"
        fi
        ;;

    cluster_topology)
        if [ -f "$MODULE_DIR/core/lib/cluster_manager.sh" ]; then
            sh "$MODULE_DIR/core/lib/cluster_manager.sh" topology
        else
            echo '{"clusters":[]}'
        fi
        ;;

    set_cluster_freq)
        if [ -f "$MODULE_DIR/core/lib/cluster_manager.sh" ]; then
            sh "$MODULE_DIR/core/lib/cluster_manager.sh" set_freq "$PARAM" "$PARAM2" "$PARAM3"
        fi
        ;;

    set_cluster_gov)
        if [ -f "$MODULE_DIR/core/lib/cluster_manager.sh" ]; then
            sh "$MODULE_DIR/core/lib/cluster_manager.sh" set_gov "$PARAM" "$PARAM2"
        fi
        ;;

    gpu_info)
        if [ -f "$MODULE_DIR/core/lib/gpu_manager.sh" ]; then
            sh "$MODULE_DIR/core/lib/gpu_manager.sh" info
        else
            echo '{"vendor":"unknown"}'
        fi
        ;;

    set_gpu_freq)
        if [ -f "$MODULE_DIR/core/lib/gpu_manager.sh" ]; then
            sh "$MODULE_DIR/core/lib/gpu_manager.sh" set_freq "$PARAM"
        fi
        ;;

    # ── Kernel Flasher & Partition Tools ───────────────────────────
    backup_boot)
        if [ -f "$MODULE_DIR/core/lib/flasher.sh" ]; then
            sh "$MODULE_DIR/core/lib/flasher.sh" backup 2>&1
        fi
        ;;

    list_backups)
        if [ -f "$MODULE_DIR/core/lib/flasher.sh" ]; then
            sh "$MODULE_DIR/core/lib/flasher.sh" list_backups
        else
            echo '{"backups":[]}'
        fi
        ;;

    restore_boot)
        if [ -f "$MODULE_DIR/core/lib/flasher.sh" ]; then
            sh "$MODULE_DIR/core/lib/flasher.sh" restore "$PARAM" 2>&1
        fi
        ;;

    flash_kernel)
        if [ -f "$MODULE_DIR/core/lib/flasher.sh" ]; then
            sh "$MODULE_DIR/core/lib/flasher.sh" flash "$PARAM" 2>&1
        fi
        ;;

    # ── Zero-Hardcoding Dynamic Hardware & VM Handlers ────────────
    drop_caches)
        echo 3 > /proc/sys/vm/drop_caches 2>/dev/null
        echo "Bebaskan Cache RAM berhasil."
        ;;

    set_dirty_ratio)
        echo "$PARAM" > /proc/sys/vm/dirty_ratio 2>/dev/null
        echo "VM Dirty Ratio disetel ke $PARAM%"
        ;;

    set_vfs_pressure)
        echo "$PARAM" > /proc/sys/vm/vfs_cache_pressure 2>/dev/null
        echo "VFS Cache Pressure disetel ke $PARAM"
        ;;

    set_refresh_rate)
        settings put system min_refresh_rate "$PARAM.0" 2>/dev/null
        settings put system peak_refresh_rate "$PARAM.0" 2>/dev/null
        settings put secure user_refresh_rate "$PARAM" 2>/dev/null
        echo "Display Refresh Rate disetel ke $PARAM Hz"
        ;;

    set_selinux)
        setenforce "$PARAM" 2>/dev/null
        echo "SELinux mode: $(getenforce 2>/dev/null || echo unknown)"
        ;;

    set_printk)
        if [ "$PARAM" = "silent" ] || [ "$PARAM" = "1" ]; then
            echo '0 0 0 0' > /proc/sys/kernel/printk 2>/dev/null
            echo "Kernel Printk dimatikan (Zero Overhead)"
        else
            echo '7 4 1 7' > /proc/sys/kernel/printk 2>/dev/null
            echo "Kernel Printk diaktifkan (Verbose)"
        fi
        ;;

    set_tcp)
        echo "$PARAM" > /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null
        echo "TCP Congestion Control disetel ke $PARAM"
        ;;

    applist_read)
        APPPATH="$MODULE_DIR/core/applist_perf.txt"
        [ -f "$APPPATH" ] && cat "$APPPATH" | grep -v '^#' | grep -v '^$'
        ;;

    applist_add)
        APPPATH="$MODULE_DIR/core/applist_perf.txt"
        if [ -n "$PARAM" ] && [ -f "$APPPATH" ]; then
            grep -qx "$PARAM" "$APPPATH" || echo "$PARAM" >> "$APPPATH"
            echo "Added $PARAM"
        fi
        ;;

    applist_remove)
        APPPATH="$MODULE_DIR/core/applist_perf.txt"
        if [ -n "$PARAM" ] && [ -f "$APPPATH" ]; then
            grep -vFx "$PARAM" "$APPPATH" > "${APPPATH}.tmp" && mv "${APPPATH}.tmp" "$APPPATH"
            echo "Removed $PARAM"
        fi
        ;;

    # ── Universal I/O Scheduler Handlers ──────────────────────────
    io_devices)
        echo "{\"devices\":["
        first=1
        for dev in mmcblk0 sda sdb nvme0n1 vda; do
            q="/sys/block/$dev/queue"
            [ -f "$q/scheduler" ] || continue
            sched=$(cat "$q/scheduler" 2>/dev/null)
            ra=$(cat "$q/read_ahead_kb" 2>/dev/null || echo 128)
            cur=$(echo "$sched" | grep -o '\[[^]]*\]' | tr -d '[]' || echo "none")
            avail=$(echo "$sched" | tr -d '[]')
            [ $first -eq 0 ] && echo ","
            first=0
            echo "{\"device\":\"$dev\",\"cur\":\"$cur\",\"avail\":\"$avail\",\"ra_kb\":${ra:-128}}"
        done
        echo "]}"
        ;;

    set_io_sched)
        dev=$(echo "$PARAM" | cut -d '|' -f 1 | tr -cd 'a-zA-Z0-9')
        sched=$(echo "$PARAM" | cut -d '|' -f 2 | tr -cd 'a-zA-Z0-9-')
        if [ -f "/sys/block/$dev/queue/scheduler" ]; then
            echo "$sched" > "/sys/block/$dev/queue/scheduler" 2>/dev/null
            echo "Scheduler for $dev set to $sched"
        else
            echo "Error: device /sys/block/$dev/queue/scheduler not found"
        fi
        ;;

    # ── Universal GPU & Thermal Telemetry Handlers ─────────────────
    gpu_info)
        if [ -d /sys/class/kgsl/kgsl-3d0 ]; then
            plat="adreno"
            D="/sys/class/kgsl/kgsl-3d0/devfreq"
            cur=$(cat "$D/cur_freq" 2>/dev/null)
            max=$(cat "$D/max_freq" 2>/dev/null)
            boost=$(cat "$D/adrenoboost" 2>/dev/null)
            busy=$(cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null | tr -d ' %')
            [ -z "$busy" ] && busy=$(cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null | awk '{if ($2>0) printf "%d", ($1*100)/$2; else print 0}')
        elif [ -d /proc/gpufreq ] || [ -d /sys/module/ged ] || [ -c /dev/mali0 ] || [ -d /sys/devices/platform/13000000.mali ]; then
            plat="mali_ged"
            cur=$(cat /proc/gpufreq/gpufreq_opp_freq 2>/dev/null | cut -d '=' -f 2 | cut -d ',' -f 1 | tr -d ' ' | grep '^[0-9]' | head -n1)
            [ -z "$cur" ] && cur=$(cat /proc/gpufreq/gpufreq_var_dump 2>/dev/null | grep 'freq:' | head -n1 | tr -d ' ' | cut -d ':' -f 2 | cut -d ',' -f 1)
            max=$(cat /proc/gpufreq/gpufreq_opp_dump 2>/dev/null | cut -d '=' -f 2 | cut -d ',' -f 1 | tr -d ' ' | grep '^[0-9]' | sort -nu | tail -n1)
            boost=$(cat /sys/module/ged/parameters/boost_amp 2>/dev/null | tr -d ' \n')
            [ -z "$boost" ] && boost=$(cat /sys/module/ged/parameters/ged_boost_enable 2>/dev/null | tr -d ' \n')
            busy=$(cat /proc/gpufreq/gpufreq_var_dump 2>/dev/null | grep 'gpu_loading' | cut -d '=' -f 2 | tr -d ' \n')
        else
            plat="generic"
            cur=0; max=0; boost=0; busy=0
        fi
        [ "$cur" -gt 1000000 ] 2>/dev/null && cur=$(( cur / 1000000 ))
        [ "$cur" -gt 1000 ] 2>/dev/null && cur=$(( cur / 1000 ))
        [ "$max" -gt 1000000 ] 2>/dev/null && max=$(( max / 1000000 ))
        [ "$max" -gt 1000 ] 2>/dev/null && max=$(( max / 1000 ))
        echo "{\"platform\":\"$plat\",\"cur_mhz\":${cur:-0},\"max_mhz\":${max:-0},\"load\":${busy:-0},\"boost\":${boost:-0}}"
        ;;

    set_gpu_boost)
        if [ -f /sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost ]; then
            chmod 644 /sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost 2>/dev/null
            echo "$PARAM" > /sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost 2>/dev/null
        elif [ -d /sys/module/ged/parameters ]; then
            if [ "$PARAM" = "0" ]; then
                echo 0 > /sys/module/ged/parameters/ged_boost_enable 2>/dev/null
                echo 0 > /sys/module/ged/parameters/gx_game_mode 2>/dev/null
                echo 0 > /sys/module/ged/parameters/boost_amp 2>/dev/null
            elif [ "$PARAM" = "1" ]; then
                echo 1 > /sys/module/ged/parameters/ged_boost_enable 2>/dev/null
                echo 1 > /sys/module/ged/parameters/gx_game_mode 2>/dev/null
                echo 1 > /sys/module/ged/parameters/boost_amp 2>/dev/null
            elif [ "$PARAM" = "2" ]; then
                echo 1 > /sys/module/ged/parameters/ged_boost_enable 2>/dev/null
                echo 1 > /sys/module/ged/parameters/gx_game_mode 2>/dev/null
                echo 2 > /sys/module/ged/parameters/boost_amp 2>/dev/null
                echo 1 > /sys/module/ged/parameters/gx_boost_on 2>/dev/null
            fi
        fi
        echo "GPU boost set to $PARAM"
        ;;

    thermal_zones)
        for tz in /sys/class/thermal/thermal_zone*; do
            [ -d "$tz" ] || continue
            type=$(cat "$tz/type" 2>/dev/null)
            temp=$(cat "$tz/temp" 2>/dev/null)
            if [ -n "$type" ] && [ -n "$temp" ]; then
                [ "$temp" -gt 1000 ] 2>/dev/null && temp=$(( temp / 1000 ))
                [ "$temp" -ge 10 ] 2>/dev/null && [ "$temp" -le 110 ] 2>/dev/null && echo "$type|$temp"
            fi
        done | head -n 8
        ;;

    top_wakelocks)
        for w in /sys/class/wakeup/wakeup*; do
            [ -d "$w" ] || continue
            n=$(cat "$w/name" 2>/dev/null)
            c=$(cat "$w/active_count" 2>/dev/null)
            t=$(cat "$w/prevent_suspend_time_ms" 2>/dev/null)
            if [ -n "$n" ] && [ "$c" -gt 0 ] 2>/dev/null; then
                echo "$n|$c|${t:-0}"
            fi
        done | sort -t'|' -k2 -nr | head -n 8
        ;;

    set_gov_preset)
        case "$PARAM" in
            responsive)
                for d in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/schedutil; do
                    [ -d "$d" ] || continue
                    chmod 644 "$d"/* 2>/dev/null
                    echo 0 > "$d/up_rate_limit_us" 2>/dev/null
                    echo 5000 > "$d/down_rate_limit_us" 2>/dev/null
                    echo 0 > "$d/rate_limit_us" 2>/dev/null
                done
                echo "Governor preset set to Ultra-Responsive (0us ramp-up)"
                ;;
            powersave)
                for d in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/schedutil; do
                    [ -d "$d" ] || continue
                    chmod 644 "$d"/* 2>/dev/null
                    echo 4000 > "$d/up_rate_limit_us" 2>/dev/null
                    echo 20000 > "$d/down_rate_limit_us" 2>/dev/null
                done
                echo "Governor preset set to Powersave (4000us)"
                ;;
            *)
                for d in /sys/devices/system/cpu/cpufreq/policy*/schedutil /sys/devices/system/cpu/cpufreq/schedutil; do
                    [ -d "$d" ] || continue
                    chmod 644 "$d"/* 2>/dev/null
                    echo 1000 > "$d/up_rate_limit_us" 2>/dev/null
                    echo 10000 > "$d/down_rate_limit_us" 2>/dev/null
                done
                echo "Governor preset set to Balanced (1000us)"
                ;;
        esac
        ;;

    # ── Universal Deep Sysfs Inspector & Smart Comment Interpreter ─
    deep_scan)
        if [ -f "$MODULE_DIR/core/lib/deep_inspector.sh" ]; then
            sh "$MODULE_DIR/core/lib/deep_inspector.sh" scan
        else
            echo "[]"
        fi
        ;;

    deep_inspect)
        if [ -f "$MODULE_DIR/core/lib/deep_inspector.sh" ] && [ -n "$PARAM" ]; then
            sh "$MODULE_DIR/core/lib/deep_inspector.sh" inspect "$PARAM"
        else
            echo "{}"
        fi
        ;;

    deep_set)
        if [ -f "$MODULE_DIR/core/lib/deep_inspector.sh" ] && [ -n "$PARAM" ] && [ -n "$PARAM2" ]; then
            sh "$MODULE_DIR/core/lib/deep_inspector.sh" set "$PARAM" "$PARAM2"
        fi
        ;;

    custom_rules_get)
        RULES="$MODULE_DIR/custom_rules.sh"
        [ -f "$RULES" ] && cat "$RULES" || echo "# Lynx Custom Boot Script"
        ;;

    custom_rules_set)
        RULES="$MODULE_DIR/custom_rules.sh"
        if [ -n "$PARAM" ]; then
            echo "$PARAM" | base64 -d > "$RULES" 2>/dev/null
            chmod 755 "$RULES" 2>/dev/null
            echo "Custom boot rules saved."
        fi
        ;;

    # ── Fallback ───────────────────────────────────────────────────
    *)
        echo "Error: Unknown action '$ACTION'"
        echo "Available actions: clean_cache | set_mode | set_state | get_status | maintenance | export_log | backup_boot | list_backups | restore_boot | flash_kernel"
        exit 1
        ;;
esac