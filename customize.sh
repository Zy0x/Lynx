#!/system/bin/sh
# ==============================================================================
# Lynx Universal - Unified Intelligent Installer
# Hybrid Architecture: Qualcomm Snapdragon, MediaTek, & Generic Linux
# Clean, high-performance, semi-automatic installation
# ==============================================================================

command -v ui_print >/dev/null 2>&1 || ui_print() { echo "$@"; }
command -v abort >/dev/null 2>&1 || abort() { echo "$@"; exit 1; }
command -v set_perm_recursive >/dev/null 2>&1 || set_perm_recursive() { chmod -R 755 "$1" 2>/dev/null; }
[ -f "$MODPATH/core/locale.sh" ] && . "$MODPATH/core/locale.sh"

MOD_VER="7.0.0"
if [ -f "$MODPATH/module.prop" ]; then
    MOD_VER=$(grep '^version=' "$MODPATH/module.prop" 2>/dev/null | cut -d= -f2 | tr -d '\r\n')
fi
[ -z "$MOD_VER" ] && MOD_VER="Universal"

ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "$(lynx_t install_title)"
ui_print "$(lynx_t install_meta "$MOD_VER")"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ── 1. Root Implementation Verification ────────────────────────────
check_root() {
    if [ -n "$BOOTMODE" ]; then
        if [ -n "$KSU" ]; then
            ui_print "$(lynx_t root_ksu "${KSU_KERNEL_VER_CODE:-kernel}" "${KSU_VER_CODE:-ksud}")"
        elif [ -n "$APATCH" ] || [ -n "$APATCH_VER_CODE" ]; then
            ui_print "$(lynx_t root_apatch "${APATCH_VER:-unknown}")"
        elif [ -n "$MAGISK_VER_CODE" ]; then
            ui_print "$(lynx_t root_magisk "${MAGISK_VER:-unknown}")"
        else
            ui_print "$(lynx_t root_generic)"
        fi
    fi
}
check_root

# ── 1.5 Legacy Lynx Module Preservation & Safe Deactivation ────────
preserve_legacy_module() {
    local mod_dir="/data/adb/modules/Lynx"
    local backup_dir="/data/adb/modules/Lynx_legacy"
    if [ -d "$mod_dir" ] && [ -f "$mod_dir/module.prop" ] && [ ! -f "$mod_dir/system/bin/lynxd" ]; then
        ui_print "📦 Terdeteksi modul Lynx versi lama di $mod_dir"
        ui_print "🛡️ Mengamankan ke $backup_dir dan menonaktifkannya..."
        mkdir -p "$backup_dir" 2>/dev/null
        cp -af "$mod_dir"/* "$backup_dir"/ 2>/dev/null
        touch "$backup_dir/disable" 2>/dev/null
        ui_print "✅ Modul Lynx lama berhasil diamankan & dinonaktifkan."
    fi
}
preserve_legacy_module

# ── 2. Hardware Anti-Spoofing Detection Protocol ───────────────────
detect_soc() {
    TARGET_SOC="generic"
    IS_SPOOFED=false
    HW_SOC="unknown"

    local dt_compat=""
    if [ -f "/sys/firmware/devicetree/base/compatible" ]; then
        dt_compat=$(tr -d '\0' < /sys/firmware/devicetree/base/compatible 2>/dev/null | tr '[:upper:]' '[:lower:]')
    elif [ -f "/proc/device-tree/compatible" ]; then
        dt_compat=$(tr -d '\0' < /proc/device-tree/compatible 2>/dev/null | tr '[:upper:]' '[:lower:]')
    fi

    # Level 1: Hardware Ground Truth (Kernel Driver Nodes)
    if echo "$dt_compat" | grep -q "mediatek" || [ -d "/proc/ppm" ] || [ -d "/proc/ged" ] || [ -c "/dev/ged" ]; then
        HW_SOC="mtk"
    elif echo "$dt_compat" | grep -qE "qcom|qualcomm" || [ -d "/sys/class/kgsl" ] || [ -c "/dev/kgsl-3d0" ] || [ -d "/sys/devices/soc0" ]; then
        HW_SOC="qcom"
    fi

    local prop_manuf prop_plat
    prop_manuf=$(getprop ro.soc.manufacturer 2>/dev/null | tr '[:upper:]' '[:lower:]')
    prop_plat=$(getprop ro.board.platform 2>/dev/null | tr '[:upper:]' '[:lower:]')

    # Level 2: Anti-Spoofer Detection
    if [ "$HW_SOC" = "mtk" ]; then
        TARGET_SOC="mtk"
        if echo "$prop_manuf" | grep -q "qualcomm" || echo "$prop_plat" | grep -qE "^(sm|sdm|msm|kona|taro|kalama)"; then
            IS_SPOOFED=true
            ui_print "$(lynx_t spoof_warning "$prop_manuf" "$prop_plat")"
            ui_print "$(lynx_t real_mtk)"
            ui_print "$(lynx_t lock_mtk)"
        fi
    elif [ "$HW_SOC" = "qcom" ]; then
        TARGET_SOC="qcom"
        if echo "$prop_manuf" | grep -q "mediatek" || echo "$prop_plat" | grep -qE "^mt"; then
            IS_SPOOFED=true
            ui_print "$(lynx_t spoof_warning "$prop_manuf" "$prop_plat")"
            ui_print "$(lynx_t real_qcom)"
            ui_print "$(lynx_t lock_qcom)"
        fi
    else
        # Level 3: Fallback to System Properties
        if echo "$prop_manuf" | grep -q "mediatek" || echo "$prop_plat" | grep -qE "^mt"; then
            TARGET_SOC="mtk"
        elif echo "$prop_manuf" | grep -q "qualcomm" || echo "$prop_plat" | grep -qE "^(sm|sdm|msm|kona|taro|kalama)"; then
            TARGET_SOC="qcom"
        else
            TARGET_SOC="generic"
        fi
    fi

    # Level 4: Volume Key Confirmation Protocol (with 10s auto-confirm timeout)
    if [ "$AUTO_INSTALL" = "1" ] || [ "$AUTO_INSTALL" = "true" ] || [ -f "/data/local/tmp/lynx_auto_install" ]; then
        ui_print "$(lynx_t architecture_auto "$TARGET_SOC")"
    elif [ -f "$MODPATH/addon/Volume-Key-Selector/install.sh" ]; then
        . "$MODPATH/addon/Volume-Key-Selector/install.sh"
        if [ -n "$VKSEL" ]; then
            ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            ui_print "$(lynx_t architecture "$TARGET_SOC")"
            ui_print "$(lynx_t confirm_chipset)"
            ui_print "$(lynx_t volume_yes "$TARGET_SOC")"
            ui_print "$(lynx_t volume_no)"
            if ! $VKSEL 10; then
                ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                ui_print "$(lynx_t select_architecture)"
                ui_print "$(lynx_t qcom_option)"
                ui_print "$(lynx_t mtk_option)"
                ui_print "$(lynx_t generic_option)"
                local opt=1
                while true; do
                    ui_print "$(lynx_t selected_option "$opt")"
                    if $VKSEL; then
                        case $opt in
                            1) TARGET_SOC="qcom" ;;
                            2) TARGET_SOC="mtk" ;;
                            3) TARGET_SOC="generic" ;;
                        esac
                        ui_print "$(lynx_t selected_architecture "$TARGET_SOC")"
                        break
                    else
                        opt=$((opt + 1))
                        [ $opt -gt 3 ] && opt=1
                    fi
                done
            fi
        fi
    fi

    echo "$TARGET_SOC" > "$MODPATH/target_soc"
}
detect_soc

# ── 3. Dynamic Metadata Customization ──────────────────────────────
MODPROP="$MODPATH/module.prop"
case "$TARGET_SOC" in
    mtk)
        sed -i 's/^name=.*/name=Lynx - Deity (MediaTek)/' "$MODPROP"
        sed -i 's/^description=.*/description=[ MediaTek Dimensity\/Helio Engine Active ]/' "$MODPROP"
        ;;
    qcom)
        sed -i 's/^name=.*/name=Lynx - Deity (Qualcomm)/' "$MODPROP"
        sed -i 's/^description=.*/description=[ Qualcomm Snapdragon Engine Active ]/' "$MODPROP"
        ;;
    *)
        sed -i 's/^name=.*/name=Lynx - Deity (Universal)/' "$MODPROP"
        sed -i 's/^description=.*/description=[ Generic Linux Engine Active ]/' "$MODPROP"
        ;;
esac

# ── 4. Conditional Hardware Abstraction Layer Injection ────────────
ui_print "$(lynx_t inject_hal "$TARGET_SOC")"
if [ "$TARGET_SOC" = "qcom" ]; then
    if [ -d "$MODPATH/platforms/qcom/system" ]; then
        cp -af "$MODPATH/platforms/qcom/system/"* "$MODPATH/system/" 2>/dev/null
    fi
    if [ -f "$MODPATH/platforms/qcom/system.prop" ]; then
        echo "" >> "$MODPATH/system.prop"
        cat "$MODPATH/platforms/qcom/system.prop" >> "$MODPATH/system.prop"
    fi
    ui_print "$(lynx_t injected_qcom)"
elif [ "$TARGET_SOC" = "mtk" ]; then
    if [ -d "$MODPATH/platforms/mtk/system" ]; then
        cp -af "$MODPATH/platforms/mtk/system/"* "$MODPATH/system/" 2>/dev/null
    fi
    if [ -f "$MODPATH/platforms/mtk/system.prop" ]; then
        echo "" >> "$MODPATH/system.prop"
        cat "$MODPATH/platforms/mtk/system.prop" >> "$MODPATH/system.prop"
    fi
    ui_print "$(lynx_t injected_mtk)"
else
    ui_print "$(lynx_t generic_vendor)"
fi
[ -d "$MODPATH/system/vendor" ] && rmdir "$MODPATH/system/vendor" 2>/dev/null

# ── 4b. Dynamic RAM-Proportional Dalvik Heap Tuning ──────────────
tune_dalvik_heap() {
    local mem_total_kb
    mem_total_kb=$(awk '/MemTotal/ {print $2}' /proc/meminfo 2>/dev/null)
    [ -z "$mem_total_kb" ] && mem_total_kb=4194304
    local mem_total_mb=$((mem_total_kb / 1024))

    local growth_limit="256m"
    local heap_size="512m"
    local start_size="8m"
    local min_free="4m"
    local max_free="16m"
    local target_util="0.75"
    local tier_label="6GB (Optimal)"

    if [ "$mem_total_mb" -le 4500 ]; then
        # ── Tier 1: 3GB - 4GB RAM (Conservative) ─────────────────────
        # Prevents excessive heap bloat, thrashing, and aggressive LMK background app killing
        tier_label="3GB-4GB (Konservatif)"
        growth_limit="192m"
        heap_size="512m"
        start_size="8m"
        min_free="4m"
        max_free="16m"
    elif [ "$mem_total_mb" -le 6500 ]; then
        # ── Tier 2: 6GB RAM (Balanced Sweet Spot) ────────────────────
        # Optimal multitasking headroom with balanced GC compaction cycles
        tier_label="6GB (Seimbang)"
        growth_limit="256m"
        heap_size="640m"
        start_size="16m"
        min_free="6m"
        max_free="24m"
    elif [ "$mem_total_mb" -le 10500 ]; then
        # ── Tier 3: 8GB - 10GB RAM (High Performance) ────────────────
        # Ample headroom preventing system_server IPC fragmentation & bad_alloc OOM
        tier_label="8GB-10GB (Performa Tinggi)"
        growth_limit="384m"
        heap_size="768m"
        start_size="16m"
        min_free="8m"
        max_free="32m"
    else
        # ── Tier 4: 12GB+ RAM (Extreme Flagship) ─────────────────────
        # Unrestricted heap space for heavy multi-window & high-end emulation
        tier_label="12GB+ (Ekstrem / Unggulan)"
        growth_limit="512m"
        heap_size="1024m"
        start_size="24m"
        min_free="12m"
        max_free="48m"
    fi

    ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    ui_print "  📊 Deteksi Kapasitas RAM: ~${mem_total_mb}MB [Tier: $tier_label]"
    ui_print "  ⚡ Dalvik Heap Otomatis: Growth=$growth_limit | Max=$heap_size | Start=$start_size"
    ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    # Bersihkan nilai statis sebelumnya jika ada di system.prop
    if [ -f "$MODPATH/system.prop" ]; then
        sed -i '/^dalvik\.vm\.heap/d' "$MODPATH/system.prop" 2>/dev/null
    fi

    {
        echo ""
        echo "# ── Dynamic Dalvik VM Heap Tuning (RAM: ${mem_total_mb}MB | $tier_label) ──"
        echo "dalvik.vm.heapgrowthlimit=$growth_limit"
        echo "dalvik.vm.heapsize=$heap_size"
        echo "dalvik.vm.heapstartsize=$start_size"
        echo "dalvik.vm.heapminfree=$min_free"
        echo "dalvik.vm.heapmaxfree=$max_free"
        echo "dalvik.vm.heaptargetutilization=$target_util"
    } >> "$MODPATH/system.prop"
}
tune_dalvik_heap

# ── 5. Non-Intrusive Selective BusyBox Installation ────────────────
ui_print "$(lynx_t setup_busybox)"
feravolt_dir="$MODPATH/system/bin/feravolt"
target_bb="$MODPATH/system/bin/busybox"

if [ -d "$feravolt_dir" ]; then
    abi=$(getprop ro.product.cpu.abi 2>/dev/null | tr '[:upper:]' '[:lower:]')
    chosen_bb=""

    case "$abi" in
        *arm64*|*aarch64*)
            [ -f "$feravolt_dir/busybox64" ] && chosen_bb="$feravolt_dir/busybox64"
            ;;
        *arm*|*v7a*)
            [ -f "$feravolt_dir/busybox7" ] && chosen_bb="$feravolt_dir/busybox7"
            ;;
        *x86_64*|*x86*)
            [ -f "$feravolt_dir/busybox86" ] && chosen_bb="$feravolt_dir/busybox86"
            ;;
    esac

    if [ -z "$chosen_bb" ]; then
        if [ -f "$feravolt_dir/busybox64" ]; then
            chosen_bb="$feravolt_dir/busybox64"
        elif [ -f "$feravolt_dir/busybox8" ]; then
            chosen_bb="$feravolt_dir/busybox8"
        elif [ -f "$feravolt_dir/busybox_brutal" ]; then
            chosen_bb="$feravolt_dir/busybox_brutal"
        fi
    fi

    if [ -n "$chosen_bb" ] && [ -f "$chosen_bb" ]; then
        cp -af "$chosen_bb" "$target_bb"
        chmod 755 "$target_bb"
        ui_print "$(lynx_t active_busybox "$(basename "$chosen_bb")")"

        # Selective applet installation with strict system binary shielding
        if [ -d "/system/xbin" ]; then
            mkdir -p "$MODPATH/system/xbin"
            "$target_bb" --install -s "$MODPATH/system/xbin/" 2>/dev/null
            ui_print "$(lynx_t busybox_isolated)"
        else
            "$target_bb" --install -s "$MODPATH/system/bin/" 2>/dev/null
            removed_count=0
            for stock_bin in /system/bin/*; do
                name="${stock_bin##*/}"
                case "$name" in
                    busybox|lynx|lynxd) continue ;;
                esac
                if [ -e "$MODPATH/system/bin/$name" ] || [ -L "$MODPATH/system/bin/$name" ]; then
                    rm -f "$MODPATH/system/bin/$name" 2>/dev/null
                    removed_count=$((removed_count + 1))
                fi
            done
            ui_print "$(lynx_t collisions_purged "$removed_count")"
        fi
    fi
fi

# ── 6. Notification Sanitization ──────────────────────────────────
# Purge legacy third-party toast popups for distraction-free gaming performance
if pm path bellavita.toast >/dev/null 2>&1; then
    (pm uninstall bellavita.toast >/dev/null 2>&1 &)
fi

# ── 6.5 Lynx Kernel Manager Companion App Staging ──────────────────
if [ -f "$MODPATH/LynxKernelManager.apk" ]; then
    ui_print "$(lynx_t install_companion)"
    cp -af "$MODPATH/LynxKernelManager.apk" /data/local/tmp/LynxKernelManager.apk 2>/dev/null
    chmod 644 /data/local/tmp/LynxKernelManager.apk 2>/dev/null
    # Run install in background subshell to prevent PackageInstaller IPC hangs
    (
        pm install -r /data/local/tmp/LynxKernelManager.apk >/dev/null 2>&1
        rm -f /data/local/tmp/LynxKernelManager.apk 2>/dev/null
    ) &
    sleep 2
    if pm path com.noir.lynx >/dev/null 2>&1; then
        ui_print "$(lynx_t companion_installed)"
    else
        ui_print "$(lynx_t companion_staged)"
    fi
fi

# Grant seamless overlay and background execution privileges for Lynx Visual Feedback HUD
appops set com.noir.lynx SYSTEM_ALERT_WINDOW allow 2>/dev/null
dumpsys deviceidle whitelist +com.noir.lynx 2>/dev/null

mkdir -p /data/adb/lynx 2>/dev/null
if [ ! -f /data/adb/lynx/feedback_style ]; then
    echo "jewel_top" > /data/adb/lynx/feedback_style
fi

# ── 7. Storage Directory & Applist Initialization ──────────────────
mkdir -p /storage/emulated/0/Lynx 2>/dev/null
cp -af "$MODPATH/README.md" /storage/emulated/0/Lynx/ 2>/dev/null
if [ ! -f /storage/emulated/0/Lynx/applist_perf.txt ]; then
    cp -af "$MODPATH/core/applist_perf.txt" /storage/emulated/0/Lynx/ 2>/dev/null
fi

# ── 7. State Engine (Dormant Mode First-Boot) ──────────────────────
ui_print "$(lynx_t initialize_engine)"
mkdir -p "$MODPATH/logs" 2>/dev/null

if [ -f "$MODPATH/config.json" ]; then
    sed -i "s/\"target_soc\": \".*\"/\"target_soc\": \"$TARGET_SOC\"/" "$MODPATH/config.json" 2>/dev/null
    sed -i "s/\"soc_type\": \".*\"/\"soc_type\": \"$TARGET_SOC\"/" "$MODPATH/config.json" 2>/dev/null
    sed -i "s/\"soc_name\": \".*\"/\"soc_name\": \"$TARGET_SOC\"/" "$MODPATH/config.json" 2>/dev/null
    sed -i "s/\"is_spoofed\": .*/\"is_spoofed\": $IS_SPOOFED,/" "$MODPATH/config.json" 2>/dev/null
    chmod 644 "$MODPATH/config.json"
fi

# ── 7.5 Create Stock Baseline Restore Point ────────────────────────
ui_print "$(lynx_t baseline_restore)"
mkdir -p "$MODPATH/stock_state" 2>/dev/null
for n in /proc/sys/vm/dirty_ratio /proc/sys/vm/dirty_background_ratio /proc/sys/vm/vfs_cache_pressure /proc/sys/vm/swappiness /proc/sys/kernel/sched_util_clamp_min /proc/sys/kernel/sched_util_clamp_max; do
    if [ -f "$n" ]; then
        bname=$(basename "$n")
        cat "$n" > "$MODPATH/stock_state/$bname" 2>/dev/null
    fi
done


# ── 8. Permissions & Cleanup ───────────────────────────────────────
ui_print "$(lynx_t set_permissions)"
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm_recursive "$MODPATH/system/bin" 0 0 0755 0755
[ -d "$MODPATH/system/vendor/bin" ] && set_perm_recursive "$MODPATH/system/vendor/bin" 0 0 0755 0755
set_perm_recursive "$MODPATH/core" 0 0 0755 0644
[ -d "$MODPATH/platforms" ] && set_perm_recursive "$MODPATH/platforms" 0 0 0755 0644
set_perm_recursive "$MODPATH/webroot" 0 0 0755 0644
chmod 755 "$MODPATH/post-fs-data.sh" "$MODPATH/service.sh" "$MODPATH/uninstall.sh" "$MODPATH/action.sh" 2>/dev/null

# Enforce clean system_file SELinux context
chcon -R u:object_r:system_file:s0 "$MODPATH/system" 2>/dev/null
chcon -R u:object_r:system_file:s0 "$MODPATH/core" 2>/dev/null
chcon -R u:object_r:system_file:s0 "$MODPATH/platforms" 2>/dev/null

# Clean non-runtime assets
rm -rf "$MODPATH/archive" "$MODPATH/docs" "$MODPATH/references" "$MODPATH/tests" 2>/dev/null

# Purge install-only payloads that no runtime component (lynxd, APK, WebUI) reads.
# - addon/ (volume-key selector) and core/locale.sh are only sourced by this installer.
# - changelog.md / UserGuide-*.html / README.md are documentation (README is copied to /sdcard/Lynx).
# - core/Charging-Controller.sh is a legacy shell fallback fully superseded by native lynxd charging.
rm -rf "$MODPATH/addon" 2>/dev/null
rm -f "$MODPATH/core/locale.sh" "$MODPATH/core/Charging-Controller.sh" 2>/dev/null
rm -f "$MODPATH/changelog.md" "$MODPATH/UserGuide-EN.html" "$MODPATH/UserGuide-ID.html" "$MODPATH/README.md" 2>/dev/null
# Keep the bundled APK only when installation did not complete (user can sideload it manually).
if pm path com.noir.lynx >/dev/null 2>&1; then
    rm -f "$MODPATH/LynxKernelManager.apk" 2>/dev/null
fi

ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "$(lynx_t installed "$MOD_VER")"
ui_print "$(lynx_t open_app)"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
