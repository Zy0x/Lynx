#!/system/bin/sh
# ==============================================================================
# Lynx Universal - Unified Intelligent Installer
# Hybrid Architecture: Qualcomm Snapdragon, MediaTek, & Generic Linux
# Clean, high-performance, semi-automatic installation
# ==============================================================================

command -v ui_print >/dev/null 2>&1 || ui_print() { echo "$@"; }
command -v abort >/dev/null 2>&1 || abort() { echo "$@"; exit 1; }
command -v set_perm_recursive >/dev/null 2>&1 || set_perm_recursive() { chmod -R 755 "$1" 2>/dev/null; }

ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  [*] L Y N X   [Codename: Deity]"
ui_print "  Version: 3.0.44  |  Author: ɴᴏɪʀ"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ── 1. Root Implementation Verification ────────────────────────────
check_root() {
    if [ -n "$BOOTMODE" ]; then
        if [ -n "$KSU" ]; then
            ui_print "  [+] Root: KernelSU (${KSU_KERNEL_VER_CODE:-kernel} / ${KSU_VER_CODE:-ksud})"
        elif [ -n "$APATCH" ] || [ -n "$APATCH_VER_CODE" ]; then
            ui_print "  [+] Root: APatch (${APATCH_VER:-unknown})"
        elif [ -n "$MAGISK_VER_CODE" ]; then
            ui_print "  [+] Root: Magisk (${MAGISK_VER:-unknown})"
        else
            ui_print "  [*] Root: Standard Linux Root Environment"
        fi
    fi
}
check_root

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
            ui_print "  [!] WARNING: SoC Spoofer Detected! ($prop_manuf / $prop_plat)"
            ui_print "  [*] Real Hardware: MediaTek Dimensity/Helio"
            ui_print "  [+] Lynx locks installation to MediaTek Engine!"
        fi
    elif [ "$HW_SOC" = "qcom" ]; then
        TARGET_SOC="qcom"
        if echo "$prop_manuf" | grep -q "mediatek" || echo "$prop_plat" | grep -qE "^mt"; then
            IS_SPOOFED=true
            ui_print "  [!] WARNING: SoC Spoofer Detected! ($prop_manuf / $prop_plat)"
            ui_print "  [*] Real Hardware: Qualcomm Snapdragon"
            ui_print "  [+] Lynx locks installation to Qualcomm Engine!"
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
        ui_print "  [*] Architecture: $TARGET_SOC (Auto-confirmed)"
    elif [ -f "$MODPATH/addon/Volume-Key-Selector/install.sh" ]; then
        . "$MODPATH/addon/Volume-Key-Selector/install.sh"
        if [ -n "$VKSEL" ]; then
            ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
            ui_print "  [*] Detected Architecture: $TARGET_SOC"
            ui_print "  [?] Confirm detected chipset?"
            ui_print "     [Vol +]  YES, proceed with $TARGET_SOC"
            ui_print "     [Vol -]  NO, select manually"
            if ! $VKSEL 10; then
                ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                ui_print "  [*] Select SoC Architecture Manually:"
                ui_print "     [1] Qualcomm Snapdragon"
                ui_print "     [2] MediaTek Dimensity / Helio"
                ui_print "     [3] Generic Linux"
                local opt=1
                while true; do
                    ui_print "    -> Option $opt"
                    if $VKSEL; then
                        case $opt in
                            1) TARGET_SOC="qcom" ;;
                            2) TARGET_SOC="mtk" ;;
                            3) TARGET_SOC="generic" ;;
                        esac
                        ui_print "  [+] Selected: $TARGET_SOC"
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
ui_print "  [*] Injecting Hardware Abstraction Layer ($TARGET_SOC)..."
mkdir -p "$MODPATH/system/vendor"

if [ "$TARGET_SOC" = "qcom" ]; then
    if [ -d "$MODPATH/platforms/qcom/system" ]; then
        cp -af "$MODPATH/platforms/qcom/system/"* "$MODPATH/system/" 2>/dev/null
    fi
    if [ -f "$MODPATH/platforms/qcom/system.prop" ]; then
        echo "" >> "$MODPATH/system.prop"
        cat "$MODPATH/platforms/qcom/system.prop" >> "$MODPATH/system.prop"
    fi
    ui_print "  [+] Qualcomm HAL Overlays Injected"
elif [ "$TARGET_SOC" = "mtk" ]; then
    if [ -d "$MODPATH/platforms/mtk/system" ]; then
        cp -af "$MODPATH/platforms/mtk/system/"* "$MODPATH/system/" 2>/dev/null
    fi
    if [ -f "$MODPATH/platforms/mtk/system.prop" ]; then
        echo "" >> "$MODPATH/system.prop"
        cat "$MODPATH/platforms/mtk/system.prop" >> "$MODPATH/system.prop"
    fi
    ui_print "  [+] MediaTek HAL Overlays Injected (Firmware untouched)"
else
    ui_print "  [*] Generic Linux: Preserving stock vendor partition"
fi

# ── 5. Non-Intrusive Selective BusyBox Installation ────────────────
ui_print "  [*] Setting up Selective BusyBox Environment..."
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
        ui_print "  [+] Active BusyBox: $(basename "$chosen_bb")"

        # Selective applet installation with strict system binary shielding
        if [ -d "/system/xbin" ]; then
            mkdir -p "$MODPATH/system/xbin"
            "$target_bb" --install -s "$MODPATH/system/xbin/" 2>/dev/null
            ui_print "  [+] BusyBox applets isolated in /system/xbin"
        else
            "$target_bb" --install -s "$MODPATH/system/bin/" 2>/dev/null
            removed_count=0
            for stock_bin in /system/bin/*; do
                name="${stock_bin##*/}"
                case "$name" in
                    busybox|Lxcore|lynx) continue ;;
                esac
                if [ -e "$MODPATH/system/bin/$name" ] || [ -L "$MODPATH/system/bin/$name" ]; then
                    rm -f "$MODPATH/system/bin/$name" 2>/dev/null
                    removed_count=$((removed_count + 1))
                fi
            done
            ui_print "  [*] Shielded stock system binaries ($removed_count collisions purged)"
        fi
    fi
fi

# ── 6. Toast Notification Utility Staging ──────────────────────────
if [ -f "$MODPATH/Toast.apk" ]; then
    cp -af "$MODPATH/Toast.apk" /data/local/tmp/LynxToast.apk 2>/dev/null
    chmod 644 /data/local/tmp/LynxToast.apk 2>/dev/null
    pm install -r /data/local/tmp/LynxToast.apk >/dev/null 2>&1
    rm -f /data/local/tmp/LynxToast.apk 2>/dev/null
fi

# ── 6.5 Lynx Kernel Manager Companion App Staging ──────────────────
if [ -f "$MODPATH/LynxKernelManager.apk" ]; then
    ui_print "  [*] Installing Lynx Kernel Manager Companion App..."
    cp -af "$MODPATH/LynxKernelManager.apk" /data/local/tmp/LynxKernelManager.apk 2>/dev/null
    chmod 644 /data/local/tmp/LynxKernelManager.apk 2>/dev/null
    if pm install -r /data/local/tmp/LynxKernelManager.apk >/dev/null 2>&1; then
        ui_print "  [+] Lynx Kernel Manager App installed successfully!"
    else
        ui_print "  [*] APK staged for manual install if pm install is restricted."
    fi
    rm -f /data/local/tmp/LynxKernelManager.apk 2>/dev/null
fi

# ── 7. Storage Directory & Applist Initialization ──────────────────
mkdir -p /storage/emulated/0/Lynx 2>/dev/null
cp -af "$MODPATH/README.md" /storage/emulated/0/Lynx/ 2>/dev/null
if [ ! -f /storage/emulated/0/Lynx/applist_perf.txt ]; then
    cp -af "$MODPATH/core/applist_perf.txt" /storage/emulated/0/Lynx/ 2>/dev/null
fi

# ── 7. State Engine (Dormant Mode First-Boot) ──────────────────────
ui_print "  [*] Initializing State Engine (Dormant Safe Mode)..."
mkdir -p "$MODPATH/logs" 2>/dev/null

if [ ! -f "$MODPATH/config.json" ] && [ -f "$MODPATH/core/lib/state.sh" ]; then
    . "$MODPATH/core/lib/state.sh"
    init_state
fi

if [ -f "$MODPATH/config.json" ]; then
    sed -i "s/\"target_soc\": \".*\"/\"target_soc\": \"$TARGET_SOC\"/" "$MODPATH/config.json" 2>/dev/null
    sed -i "s/\"soc_type\": \".*\"/\"soc_type\": \"$TARGET_SOC\"/" "$MODPATH/config.json" 2>/dev/null
    sed -i "s/\"soc_name\": \".*\"/\"soc_name\": \"$TARGET_SOC\"/" "$MODPATH/config.json" 2>/dev/null
    sed -i "s/\"is_spoofed\": .*/\"is_spoofed\": $IS_SPOOFED,/" "$MODPATH/config.json" 2>/dev/null
    chmod 644 "$MODPATH/config.json"
fi

# ── 7.5 Create Stock Baseline Restore Point ────────────────────────
ui_print "  [*] Creating Stock Baseline Restore Point..."
mkdir -p "$MODPATH/stock_state" 2>/dev/null
for n in /proc/sys/vm/dirty_ratio /proc/sys/vm/dirty_background_ratio /proc/sys/vm/vfs_cache_pressure /proc/sys/vm/swappiness /proc/sys/kernel/sched_util_clamp_min /proc/sys/kernel/sched_util_clamp_max; do
    if [ -f "$n" ]; then
        bname=$(basename "$n")
        cat "$n" > "$MODPATH/stock_state/$bname" 2>/dev/null
    fi
done


# ── 8. Permissions & Cleanup ───────────────────────────────────────
ui_print "  [*] Setting permissions..."
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm_recursive "$MODPATH/system/bin" 0 0 0755 0755
[ -d "$MODPATH/system/vendor/bin" ] && set_perm_recursive "$MODPATH/system/vendor/bin" 0 0 0755 0755
set_perm_recursive "$MODPATH/core" 0 0 0755 0755
[ -d "$MODPATH/platforms" ] && set_perm_recursive "$MODPATH/platforms" 0 0 0755 0755
set_perm_recursive "$MODPATH/webroot" 0 0 0755 0644
[ -f "$MODPATH/webroot/script.sh" ] && chmod 755 "$MODPATH/webroot/script.sh"

# Enforce clean system_file SELinux context
chcon -R u:object_r:system_file:s0 "$MODPATH/system" 2>/dev/null
chcon -R u:object_r:system_file:s0 "$MODPATH/core" 2>/dev/null
chcon -R u:object_r:system_file:s0 "$MODPATH/platforms" 2>/dev/null

# Clean non-runtime assets
rm -rf "$MODPATH/archive" "$MODPATH/docs" "$MODPATH/references" "$MODPATH/tests" 2>/dev/null

ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  [OK] Lynx Universal v3.0 Installed Successfully!"
ui_print "  [*] Buka 'Lynx Companion' atau WebUI untuk mengatur profil."
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
