#!/system/bin/sh
# ==============================================================================
# Lynx Universal - Late Start Service Router
# Hybrid Architecture: Qualcomm Snapdragon, MediaTek, & Generic Linux
# Clean, high-performance, non-redundant system boot manager
# ==============================================================================

# Wait for boot completion (max 120s timeout)
boot_count=0
while [ "$(getprop sys.boot_completed | tr -d '\r')" != "1" ]; do
    sleep 2
    boot_count=$((boot_count + 1))
    [ $boot_count -ge 60 ] && break
done

# Wait for decrypted user storage (max 50s timeout for lockscreen FBE)
fbe_count=0
while [ ! -d "/sdcard/Android" ]; do
    sleep 2
    fbe_count=$((fbe_count + 1))
    [ $fbe_count -ge 25 ] && break
done

MODPATH=${0%/*}
[ -d "$MODPATH" ] || MODPATH="/data/adb/modules/Lynx"
MODPROP="$MODPATH/module.prop"
CORE="$MODPATH/core"
LIB="$MODPATH/core/lib"
CONFIG_JSON="$MODPATH/config.json"
TARGET_SOC_FILE="$MODPATH/target_soc"

# ── 1. Target Architecture & Environment ────────────────────────────
TARGET_SOC="generic"
if [ -f "$TARGET_SOC_FILE" ]; then
    TARGET_SOC=$(cat "$TARGET_SOC_FILE" 2>/dev/null | tr -d '[:space:]')
fi
if [ -z "$TARGET_SOC" ] || [ "$TARGET_SOC" = "generic" ]; then
    if [ -f "$CONFIG_JSON" ]; then
        TARGET_SOC=$(awk -F'"' '/"soc_type"[ \t]*:/ {print $4}' "$CONFIG_JSON" 2>/dev/null)
    fi
fi
# Kernel ground-truth fallback (Immune to prop spoofing)
if [ -z "$TARGET_SOC" ] || [ "$TARGET_SOC" = "generic" ]; then
    if [ -f "$LIB/hw_probe.sh" ]; then
        . "$LIB/hw_probe.sh"
        detected_soc=$(detect_real_soc)
        [ "$detected_soc" != "generic" ] && TARGET_SOC="$detected_soc"
    fi
fi
[ -z "$TARGET_SOC" ] && TARGET_SOC="generic"


# Ensure log and storage directories exist
mkdir -p "/storage/emulated/0/Lynx" "$MODPATH/logs" 2>/dev/null
LOG_FILE="/storage/emulated/0/Lynx/Lynx.log"

# Log rotation if file exceeds 512 KB
if [ -f "$LOG_FILE" ]; then
    log_size=$(wc -c < "$LOG_FILE" 2>/dev/null || echo 0)
    if [ "$log_size" -gt 524288 ] 2>/dev/null; then
        tail -n 300 "$LOG_FILE" > "$LOG_FILE.tmp" 2>/dev/null
        mv "$LOG_FILE.tmp" "$LOG_FILE" 2>/dev/null
    fi
fi

log_msg() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOG_FILE" 2>/dev/null
}

log_msg "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_msg "Lynx Universal Service Initializing..."
log_msg "• Platform: $TARGET_SOC"
log_msg "• Device: $(getprop ro.product.brand) $(getprop ro.product.model)"
log_msg "• Kernel: $(uname -r)"

# ── 2. Platform-Specific Hardware Initialization (Strictly SoC Isolated) ─
if [ -f "$MODPATH/platforms/$TARGET_SOC/sysfs.sh" ]; then
    sh "$MODPATH/platforms/$TARGET_SOC/sysfs.sh" >/dev/null 2>&1
    log_msg "⚡ Platform HAL ($TARGET_SOC) sysfs initialized"
fi

# ── 3. Universal Subsystem Optimization (Lxcore Modular Categories) ─────
if [ -x "$MODPATH/system/bin/Lxcore" ]; then
    "$MODPATH/system/bin/Lxcore" -system apply >/dev/null 2>&1
    "$MODPATH/system/bin/Lxcore" -cpu apply >/dev/null 2>&1
    "$MODPATH/system/bin/Lxcore" -gpu apply >/dev/null 2>&1
    "$MODPATH/system/bin/Lxcore" -task apply >/dev/null 2>&1
    "$MODPATH/system/bin/Lxcore" -io apply >/dev/null 2>&1
    "$MODPATH/system/bin/Lxcore" -ram apply >/dev/null 2>&1
    "$MODPATH/system/bin/Lxcore" -network apply >/dev/null 2>&1
    log_msg "✅ Core modular subsystems optimized via Lxcore"
fi

# Touch & Compositor Phase Latency Offsets
settings put secure long_press_timeout 280 2>/dev/null
settings put secure multi_press_timeout 80 2>/dev/null

# ── 5. Start Background Daemons ────────────────────────────────────
if command -v crond >/dev/null && [ -d "$CORE/cron" ]; then
    crond -f -c "$CORE/cron" -l 5 -L "$MODPATH/logs/cron.log" &
fi

if [ -d "$MODPATH/webroot" ]; then
    pkill -f "httpd.*127.0.0.1:8080" 2>/dev/null
    if command -v busybox >/dev/null; then
        busybox httpd -p 127.0.0.1:8080 -h "$MODPATH/webroot" 2>/dev/null
    elif command -v toybox >/dev/null; then
        toybox httpd -p 127.0.0.1:8080 -h "$MODPATH/webroot" 2>/dev/null
    fi
    log_msg "🌐 WebUI HTTP Server active on 127.0.0.1:8080"
fi

if command -v inotifyd >/dev/null && [ -f "$CONFIG_JSON" ] && [ -f "$LIB/state_watcher.sh" ]; then
    pkill -f "inotifyd.*state_watcher.sh" 2>/dev/null
    nohup inotifyd "$LIB/state_watcher.sh" "$CONFIG_JSON:w" >/dev/null 2>&1 &
    log_msg "🔄 Inotifyd state watcher active"
fi

# ── 6. Profile Enforcement & First-Boot Dormant Safety ──────────────
active_profile="dormant"
if [ -f "$CONFIG_JSON" ]; then
    active_profile=$(awk -F'"' '/"active_profile"[ \t]*:/ {print $4}' "$CONFIG_JSON" 2>/dev/null)
fi

log_msg "Active profile: $active_profile"

if [ "$active_profile" = "dormant" ]; then
    sed -Ei "s/^description=\[.*\]/description=[ 💤 Dormant (Pending Setup) ]/" "$MODPROP" 2>/dev/null
    su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʏ' 'Lʏɴx' '💤 Modul terpasang aman (Standby). Buka WebUI atau Aplikasi Lynx untuk konfigurasi awal.'" >/dev/null 2>&1
    log_msg "Lynx initialized in dormant standby mode."
    exit 0
fi

if [ -f "$CORE/Charging-Controller.sh" ]; then
    nohup sh "$CORE/Charging-Controller.sh" >/dev/null 2>&1 &
fi

[ -f "$CORE/CCleaner.sh" ] && sh "$CORE/CCleaner.sh" >/dev/null 2>&1

if [ -f "$LIB/lowend_shield.sh" ]; then
    . "$LIB/lowend_shield.sh"
    apply_lowend_shield
fi

case "$active_profile" in
    auto)
        sed -Ei "s/^description=\[.*\]/description=[ ⚡ Auto (AI) Mode Active ]/" "$MODPROP" 2>/dev/null
        nohup sh "$CORE/Smart-AI.sh" >/dev/null 2>&1 &
        ;;
    powersave)
        sed -Ei "s/^description=\[.*\]/description=[ 🔋 Powersave Mode Active ]/" "$MODPROP" 2>/dev/null
        [ -f "$MODPATH/platforms/$TARGET_SOC/powersave.sh" ] && sh "$MODPATH/platforms/$TARGET_SOC/powersave.sh" >/dev/null 2>&1
        ;;
    balance)
        sed -Ei "s/^description=\[.*\]/description=[ ⚖️ Balance Mode Active ]/" "$MODPROP" 2>/dev/null
        [ -f "$MODPATH/platforms/$TARGET_SOC/balance.sh" ] && sh "$MODPATH/platforms/$TARGET_SOC/balance.sh" >/dev/null 2>&1
        ;;
    performance)
        sed -Ei "s/^description=\[.*\]/description=[ 🚀 Performance Mode Active ]/" "$MODPROP" 2>/dev/null
        [ -f "$MODPATH/platforms/$TARGET_SOC/perf.sh" ] && sh "$MODPATH/platforms/$TARGET_SOC/perf.sh" >/dev/null 2>&1
        ;;
    extreme)
        sed -Ei "s/^description=\[.*\]/description=[ 🔥 Extreme Mode Active ]/" "$MODPROP" 2>/dev/null
        [ -f "$MODPATH/platforms/$TARGET_SOC/perf.sh" ] && sh "$MODPATH/platforms/$TARGET_SOC/perf.sh" "extreme" >/dev/null 2>&1
        ;;
esac

[ -f "$LIB/updater.sh" ] && nohup sh "$LIB/updater.sh" >/dev/null 2>&1 &

# ── 7. Execute Custom User Rules & Deep Tunables ──────────────────
if [ -f "$CORE/custom_tunables.sh" ]; then
    log_msg "⚡ Applying custom deep kernel tunables ($CORE/custom_tunables.sh)..."
    sh "$CORE/custom_tunables.sh" >> "$MODPATH/logs/custom_tunables.log" 2>&1
fi
if [ -f "$MODPATH/custom_rules.sh" ]; then
    log_msg "⚡ Executing custom user boot rules ($MODPATH/custom_rules.sh)..."
    sh "$MODPATH/custom_rules.sh" >> "$MODPATH/logs/custom_rules.log" 2>&1
    log_msg "Custom rules execution finished."
fi


su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʏ' 'Lʏɴx' '✅  $active_profile Mode Applied...'" >/dev/null 2>&1
log_msg "Lynx service completed successfully."