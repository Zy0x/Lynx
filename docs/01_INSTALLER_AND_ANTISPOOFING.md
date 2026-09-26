# 🛠️ Module 01: Installer Architecture & Anti-Spoofing Engine
> **Subsystem**: Installer Engine (`customize.sh`, `library.sh`, `module.prop`)  
> **Target Runtimes**: KernelSU, Magisk, APatch on Android 10 – 15  

---

## 1. Problem Statement & Threat Model

### 1.1 The SoC Spoofing Epidemic
Mobile gamers frequently install third-party root modules (e.g., *GLTools, MagiskHide Props Config, Device Faker, Game Unlocker 90/120 FPS*) that overwrite Android system properties:
- `ro.soc.manufacturer` -> spoofed to `qti` or `qualcomm`
- `ro.board.platform` -> spoofed to `sm8450`, `sm8550`, `kona`, or `taro`
- `ro.product.board` -> spoofed to `taro` or `kalama`

**Fatal Vulnerability**: If a module installer queries `getprop ro.soc.manufacturer` on a MediaTek Dimensity device spoofed as a Snapdragon 8 Gen 2, it would inject Qualcomm dummy thermal daemons (`thermal-engine`) and Qualcomm `system.prop` settings onto the MediaTek device. This causes **immediate GPU driver initialization crashes, thermal daemon fatal panics, or unrecoverable bootloops**.

### 1.2 Root Manager Fragmentation
The rooting ecosystem is split across three major implementations:
1. **Magisk**: Sets `MAGISK_VER_CODE`, uses `/data/adb/magisk/busybox`, provides Magic Mount overlay.
2. **KernelSU**: Sets `KSU=true`, `KSU_KERNEL_VER_CODE`, `KSU_VER_CODE`, provides overlayfs.
3. **APatch**: Sets `APATCH=true`, `APATCH_VER_CODE`, uses kernel patching via SuperCall.

Old installer scripts checking only `[ -n "$KSU" ]` and `[ -n "$MAGISK_VER_CODE" ]` cause **immediate aborts on APatch**, alienating a large segment of power users.

### 1.3 Dirty Update / In-Place Upgrade Hazards
When updating an existing module installation, Magisk and KernelSU create a staging directory `$MODPATH`. If previous installations left obsolete vendor files or if a user switched modules without a clean wipe, old vendor overlays can leak into the new installation, causing magic mount collisions.

---

## 2. Multi-Level Anti-Spoofing Detection Matrix

The detection engine establishes **Ground Truth** using kernel hardware nodes that user-space modules cannot spoof.

```
                    ┌───────────────────────────────┐
                    │   Start Detection Protocol    │
                    └───────────────┬───────────────┘
                                    │
                     [1. Check Kernel Device Tree]
              /sys/firmware/devicetree/base/compatible
                                    │
            ┌───────────────────────┴───────────────────────┐
            ▼                                               ▼
    Contains "mediatek"?                            Contains "qcom"?
            │                                               │
    ┌───────┴───────┐                               ┌───────┴───────┐
   YES              NO                             YES              NO
    │               │                               │               │
TARGET=mtk   [2. Check MTK Nodes]              TARGET=qcom  [2. Check QCOM Nodes]
             /proc/ppm, /proc/ged                           /dev/kgsl-3d0, soc0
                    │                                               │
             ┌──────┴──────┐                                 ┌──────┴──────┐
            YES            NO                               YES            NO
             │             │                                 │             │
        TARGET=mtk         └────────────────┬────────────────┘        TARGET=qcom
                                            │
                                            ▼
                           [3. Cross-Check System Properties]
                                  Check for Spoofer
                                            │
                               ┌────────────┴────────────┐
                               ▼                         ▼
                       Hardware != Prop          Hardware == None
                               │                         │
                       SPOOF_DETECTED=true        TARGET=generic
                       Lock to Hardware!        (Safe Universal Mode)
```

### Hardware Verification Matrix

| Target SoC | Kernel Device Tree String | Physical Character Node | Kernel Sysfs Subsystem | Driver Node |
| :--- | :--- | :--- | :--- | :--- |
| **MediaTek** | `*mediatek*` | `/dev/ged`, `/dev/mali0` | `/proc/ppm`, `/proc/ged` | `/sys/module/ged` |
| **Qualcomm** | `*qcom*`, `*qualcomm*` | `/dev/kgsl-3d0` | `/sys/class/kgsl` | `/sys/devices/soc0` |
| **Generic** | *None of above* (Exynos, Unisoc, Tensor) | *Standard DRM* | `/sys/devices/system/cpu/cpufreq` | `/sys/class/devfreq` |

---

## 3. Specification & Implementation

### 3.1 Hardened `detect_soc` Function

```bash
detect_soc() {
    TARGET_SOC="generic"
    IS_SPOOFED=false
    HW_SOC="unknown"

    # Step 1: Read Kernel Device Tree Blob (DTB) Ground Truth
    local dt_compat=""
    if [ -f "/sys/firmware/devicetree/base/compatible" ]; then
        dt_compat=$(tr -d '\0' < /sys/firmware/devicetree/base/compatible 2>/dev/null | tr '[:upper:]' '[:lower:]')
    elif [ -f "/proc/device-tree/compatible" ]; then
        dt_compat=$(tr -d '\0' < /proc/device-tree/compatible 2>/dev/null | tr '[:upper:]' '[:lower:]')
    fi

    # Step 2: Hardware Driver Node Validation
    if echo "$dt_compat" | grep -q "mediatek" || [ -d "/proc/ppm" ] || [ -d "/proc/ged" ] || [ -c "/dev/ged" ]; then
        HW_SOC="mtk"
    elif echo "$dt_compat" | grep -qE "qcom|qualcomm" || [ -d "/sys/class/kgsl" ] || [ -c "/dev/kgsl-3d0" ] || [ -d "/sys/devices/soc0" ]; then
        HW_SOC="qcom"
    fi

    # Step 3: Spoof-Awareness Cross Check with System Properties
    local prop_manuf prop_plat
    prop_manuf=$(getprop ro.soc.manufacturer | tr '[:upper:]' '[:lower:]')
    prop_plat=$(getprop ro.board.platform | tr '[:upper:]' '[:lower:]')

    if [ "$HW_SOC" = "mtk" ]; then
        TARGET_SOC="mtk"
        if echo "$prop_manuf" | grep -q "qualcomm" || echo "$prop_plat" | grep -qE "^(sm|sdm|msm|kona|taro|kalama)"; then
            IS_SPOOFED=true
            ui_print "⚠️  WARNING: SoC Spoofer Detected!"
            ui_print "    • Fake Identity : $prop_manuf ($prop_plat)"
            ui_print "    • Hardware Truth: MediaTek Dimensity/Helio"
            ui_print "    🛡️ Locking engine to MediaTek HAL for safety!"
        fi
    elif [ "$HW_SOC" = "qcom" ]; then
        TARGET_SOC="qcom"
        if echo "$prop_manuf" | grep -q "mediatek" || echo "$prop_plat" | grep -qE "^mt"; then
            IS_SPOOFED=true
            ui_print "⚠️  WARNING: SoC Spoofer Detected!"
            ui_print "    • Fake Identity : $prop_manuf ($prop_plat)"
            ui_print "    • Hardware Truth: Qualcomm Snapdragon"
            ui_print "    🛡️ Locking engine to Qualcomm HAL for safety!"
        fi
    else
        # Step 4: Fallback to System Properties only if DTB was blocked/unreadable
        if echo "$prop_manuf" | grep -q "mediatek" || echo "$prop_plat" | grep -qE "^mt"; then
            TARGET_SOC="mtk"
        elif echo "$prop_manuf" | grep -q "qualcomm" || echo "$prop_plat" | grep -qE "^(sm|sdm|msm|kona|taro|kalama)"; then
            TARGET_SOC="qcom"
        else
            TARGET_SOC="generic"
            ui_print "ℹ️  Non-Qualcomm / Non-MediaTek Hardware Detected."
            ui_print "    🛡️ Engaging Generic Linux CPUFreq & RAM Engine."
        fi
    fi

    # Save target architecture for late-service runtime
    echo "$TARGET_SOC" > "$MODPATH/target_soc"
}
```

### 3.2 Unified Root Manager Detection (`check_root`)

```bash
check_root() {
    if [ -n "$BOOTMODE" ]; then
        if [ -n "$KSU" ]; then
            ROOT_Method="KernelSU"
            ui_print "  ✅ Root Manager: KernelSU (ksud: ${KSU_VER_CODE:-unknown})"
        elif [ -n "$APATCH" ] || [ -n "$APATCH_VER_CODE" ]; then
            ROOT_Method="APatch"
            ui_print "  ✅ Root Manager: APatch (version: ${APATCH_VER:-unknown})"
        elif [ -n "$MAGISK_VER_CODE" ]; then
            ROOT_Method="Magisk"
            ui_print "  ✅ Root Manager: Magisk (version: ${MAGISK_VER:-unknown})"
        else
            ui_print "  ⚠️  ERROR: Unsupported recovery installation environment!"
            ui_print "  🛑 Please flash this module via KernelSU, APatch, or Magisk Manager app."
            abort "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        fi
    fi
}
```

### 3.3 Dynamic Module Identity & Metadata Update

During installation, `module.prop` is updated dynamically to reflect the verified hardware platform:

```bash
update_module_prop() {
    local modprop="$MODPATH/module.prop"
    [ -f "$modprop" ] || return 0

    case "$TARGET_SOC" in
        mtk)
            sed -i 's/^name=.*/name=Lʏɴx - Deity (MediaTek)/' "$modprop"
            sed -i 's/^description=.*/description=[ ⚡ Hybrid Engine: MediaTek Dimensity\/Helio Active ]/' "$modprop"
            ;;
        qcom)
            sed -i 's/^name=.*/name=Lʏɴx - Deity (Qualcomm)/' "$modprop"
            sed -i 's/^description=.*/description=[ ⚡ Hybrid Engine: Qualcomm Snapdragon Active ]/' "$modprop"
            ;;
        *)
            sed -i 's/^name=.*/name=Lʏɴx - Deity (Universal)/' "$modprop"
            sed -i 's/^description=.*/description=[ ⚡ Hybrid Engine: Generic Linux Active ]/' "$modprop"
            ;;
    esac
}
```

### 3.4 Conditional File Injection & Dirty Update Mitigation

```bash
inject_platform_files() {
    ui_print "📦 Injecting Hardware Abstraction Layer for: $TARGET_SOC"

    # Step 1: Mitigate dirty updates by wiping staging vendor directory
    rm -rf "$MODPATH/system/vendor"
    mkdir -p "$MODPATH/system/vendor"

    # Step 2: Inject vendor overlay files if platform has hardware-specific files
    if [ "$TARGET_SOC" != "generic" ] && [ -d "$MODPATH/platforms/$TARGET_SOC/system" ]; then
        cp -af "$MODPATH/platforms/$TARGET_SOC/system/"* "$MODPATH/system/" 2>/dev/null || true
    fi

    # Step 3: Merge platform-specific system.prop into main system.prop
    if [ -f "$MODPATH/platforms/$TARGET_SOC/system.prop" ]; then
        echo "" >> "$MODPATH/system.prop"
        echo "# --- Platform Specific Properties ($TARGET_SOC) ---" >> "$MODPATH/system.prop"
        cat "$MODPATH/platforms/$TARGET_SOC/system.prop" >> "$MODPATH/system.prop"
    fi

    # Step 4: Cleanup inactive platform assets to save disk space
    rm -rf "$MODPATH/platforms"
    
    # Step 5: Ensure Core and WebUI directories are preserved
    chmod -R 755 "$MODPATH/core" "$MODPATH/webroot"
}
```

---

## 4. Failure Modes & Mitigations

| Failure Scenario | Root Cause | Impact | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| **Corrupted DTB Node** | OEM custom kernel with non-standard permissions on `/sys/firmware/devicetree` | Reading DTB produces empty string | Fallback immediately to Level 2 (GPU character nodes `/dev/kgsl-3d0` vs `/dev/mali0`) |
| **New SoC Architecture** | Device uses Google Tensor (GS101/201/301) or Exynos 2400 | Neither QCOM nor MTK | `TARGET_SOC=generic`, skip vendor file injection, run universal CPUFreq governor & I/O compaction |
| **APatch Installation Crash** | Missing APatch environment variables in check_root | Installer aborts installation | Comprehensive check supporting `$APATCH`, `$APATCH_VER_CODE`, and `$APATCH_VER` |
| **Dirty Re-Flash Leaks** | Previous version had dummy Qualcomm `thermal-engine` in `$MODPATH/system/vendor/bin` | Stale Qualcomm binary mounted on MediaTek | Explicit `rm -rf $MODPATH/system/vendor` before copying new platform tree |
