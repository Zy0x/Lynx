#!/system/bin/sh
# ==============================================================================
# Lynx Universal - Anti-Spoofing Hardware Fingerprinting & Profile Loader
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)
# 
# Resolves Kernel Ground Truth (DTB, SoC drivers, GPU registers).
# Completely immune to user-space prop spoofers (GLTools, Device Faker, MagiskHide).
# ==============================================================================

LOG_FILE="/storage/emulated/0/Lynx/Lynx.log"
log_probe() {
    [ -n "$1" ] && echo "[HW-Probe] $1" >> "$LOG_FILE" 2>/dev/null
}

# ── 1. Unspoofable Kernel Device Tree & Driver Probers ───────────────────────

detect_real_soc() {
    local dt_compat=""
    if [ -f "/sys/firmware/devicetree/base/compatible" ]; then
        dt_compat=$(tr -d '\0' < /sys/firmware/devicetree/base/compatible 2>/dev/null | tr '[:upper:]' '[:lower:]')
    elif [ -f "/proc/device-tree/compatible" ]; then
        dt_compat=$(tr -d '\0' < /proc/device-tree/compatible 2>/dev/null | tr '[:upper:]' '[:lower:]')
    fi

    # Hardware Ground Truth: Kernel Drivers & Nodes (Impossible to spoof from userspace)
    if echo "$dt_compat" | grep -q "mediatek" || [ -d "/proc/ppm" ] || [ -d "/proc/ged" ] || [ -c "/dev/ged" ]; then
        echo "mtk"
        return 0
    elif echo "$dt_compat" | grep -qE "qcom|qualcomm" || [ -d "/sys/class/kgsl" ] || [ -c "/dev/kgsl-3d0" ] || [ -d "/sys/devices/soc0" ]; then
        echo "qcom"
        return 0
    fi

    if [ -f "/sys/class/kgsl/kgsl-3d0/gpu_model" ]; then
        echo "qcom"
        return 0
    fi
    if [ -f "/proc/mali/version" ] || [ -d "/sys/module/ged" ]; then
        echo "mtk"
        return 0
    fi

    echo "generic"
}

get_kernel_board_model() {
    local m=""
    if [ -f "/sys/firmware/devicetree/base/model" ]; then
        m=$(tr -d '\0' < /sys/firmware/devicetree/base/model 2>/dev/null)
    elif [ -f "/proc/device-tree/model" ]; then
        m=$(tr -d '\0' < /proc/device-tree/model 2>/dev/null)
    fi
    echo "$m"
}

get_kernel_compatible() {
    local c=""
    if [ -f "/sys/firmware/devicetree/base/compatible" ]; then
        c=$(tr -d '\0' < /sys/firmware/devicetree/base/compatible 2>/dev/null)
    elif [ -f "/proc/device-tree/compatible" ]; then
        c=$(tr -d '\0' < /proc/device-tree/compatible 2>/dev/null)
    fi
    echo "$c"
}

get_kernel_soc_chip() {
    local soc=""
    # 1. Qualcomm machine node
    if [ -f "/sys/devices/soc0/machine" ]; then
        soc=$(cat /sys/devices/soc0/machine 2>/dev/null)
    fi
    # 2. Kernel cpuinfo hardware line
    if [ -z "$soc" ]; then
        soc=$(awk -F': ' '/Hardware/ {print $2}' /proc/cpuinfo 2>/dev/null | head -n 1)
    fi
    # 3. MediaTek chip ID
    if [ -z "$soc" ] && [ -f "/sys/devices/system/chip-id/chip_id" ]; then
        soc=$(cat /sys/devices/system/chip-id/chip_id 2>/dev/null)
    fi
    echo "$soc"
}

get_kernel_gpu_id() {
    # A. Qualcomm Adreno hardware node
    if [ -f "/sys/class/kgsl/kgsl-3d0/gpu_model" ]; then
        cat /sys/class/kgsl/kgsl-3d0/gpu_model 2>/dev/null | tr -d '[:space:]'
        return 0
    fi

    # B. MediaTek / ARM Mali hardware node
    local mali_node
    mali_node=$(find /sys/devices/platform/*mali* -name "gpuinfo" 2>/dev/null | head -n 1)
    if [ -n "$mali_node" ] && [ -f "$mali_node" ]; then
        awk '{print $1}' "$mali_node" 2>/dev/null | tr -d '[:space:]'
        return 0
    fi
    if [ -f "/proc/mali/version" ]; then
        awk '{print $1}' /proc/mali/version 2>/dev/null
        return 0
    fi

    # C. Fallback: SurfaceFlinger GLES string (only if sysfs is locked by SELinux)
    dumpsys SurfaceFlinger 2>/dev/null | grep -i "GLES:" | awk '{print $5}' | tr -d ',[:space:]'
}

# ── 2. Anti-Spoofing Verification & Hardware Identification ──────────────────

probe_hardware() {
    REAL_BOARD=$(get_kernel_board_model)
    REAL_COMPAT=$(get_kernel_compatible)
    REAL_SOC=$(get_kernel_soc_chip)
    REAL_GPU=$(get_kernel_gpu_id)
    REAL_PLATFORM=$(detect_real_soc)

    PROP_MODEL=$(getprop ro.product.model 2>/dev/null)
    PROP_MANUF=$(getprop ro.soc.manufacturer 2>/dev/null)

    # Normalize to lower-case for pattern matching
    BOARD_LOWER=$(echo "$REAL_BOARD $REAL_COMPAT" | tr '[:upper:]' '[:lower:]')
    GPU_LOWER=$(echo "$REAL_GPU" | tr '[:upper:]' '[:lower:]')
    SOC_LOWER=$(echo "$REAL_SOC" | tr '[:upper:]' '[:lower:]')
    PROP_LOWER=$(echo "$PROP_MODEL $PROP_MANUF" | tr '[:upper:]' '[:lower:]')

    SPOOF_ACTIVE=false
    # Detect if user-space prop differs significantly from kernel hardware
    if [ -n "$REAL_BOARD" ] && [ -n "$PROP_MODEL" ]; then
        case "$BOARD_LOWER" in
            *infinix*|*x698*|*transsion*|*tecno*)
                if echo "$PROP_LOWER" | grep -qvE "infinix|tecno|transsion|x698"; then
                    SPOOF_ACTIVE=true
                fi
                ;;
            *xiaomi*|*redmi*|*poco*)
                if echo "$PROP_LOWER" | grep -qvE "xiaomi|redmi|poco"; then
                    SPOOF_ACTIVE=true
                fi
                ;;
            *samsung*)
                if echo "$PROP_LOWER" | grep -qvE "samsung|galaxy"; then
                    SPOOF_ACTIVE=true
                fi
                ;;
        esac
    fi

    # Check for cross-SoC spoofing (e.g. MediaTek device spoofed as Snapdragon)
    if [ "$REAL_PLATFORM" = "mtk" ] && echo "$PROP_LOWER" | grep -qE "qualcomm|snapdragon|sm8|sdm|msm"; then
        SPOOF_ACTIVE=true
    elif [ "$REAL_PLATFORM" = "qcom" ] && echo "$PROP_LOWER" | grep -qE "mediatek|dimensity|helio|mt6|mt8"; then
        SPOOF_ACTIVE=true
    fi

    if [ "$SPOOF_ACTIVE" = "true" ]; then
        log_probe "[!] SoC/Device Spoofer Detected! Props: '$PROP_MODEL', Hardware Ground Truth: '$REAL_BOARD' (SoC: $REAL_PLATFORM, GPU: $REAL_GPU)"
    else
        log_probe "[OK] Hardware Verified: '$REAL_BOARD' (SoC: $REAL_SOC, GPU: $REAL_GPU)"
    fi
}

# ── 3. Dynamic Sub-Profile Loader ─────────────────────────────────────────────

load_hardware_subprofiles() {
    local platform="$1" # mtk | qcom | generic
    local moddir="/data/adb/modules/Lynx"
    [ -d "$moddir" ] || moddir="${0%/*/*/*}"

    probe_hardware

    local profiles_dir="$moddir/platforms/$platform/profiles"
    [ -d "$profiles_dir" ] || return 0

    log_probe "Evaluating targeted profiles for $platform (GPU: $REAL_GPU, Board: $REAL_BOARD)..."

    # ── MediaTek Targeted Profiles ──
    if [ "$platform" = "mtk" ]; then
        # 1. GPU Specific Architecture Profiles
        case "$GPU_LOWER" in
            *g57*|*mali-g57*|*g-57*)
                if [ -f "$profiles_dir/gpu_mali_g57.sh" ]; then
                    log_probe "[*] Loading targeted profile: gpu_mali_g57.sh"
                    . "$profiles_dir/gpu_mali_g57.sh"
                fi
                ;;
            *g68*|*g77*|*g78*)
                if [ -f "$profiles_dir/gpu_mali_valhall2.sh" ]; then
                    log_probe "[*] Loading targeted profile: gpu_mali_valhall2.sh"
                    . "$profiles_dir/gpu_mali_valhall2.sh"
                fi
                ;;
            *g710*|*g715*|*g720*|*immortalis*)
                if [ -f "$profiles_dir/gpu_immortalis.sh" ]; then
                    log_probe "[*] Loading targeted profile: gpu_immortalis.sh"
                    . "$profiles_dir/gpu_immortalis.sh"
                fi
                ;;
        esac

        # 2. Board / Model Specific Profiles (Infinix X698 / MT6781 test device)
        case "$BOARD_LOWER" in
            *x698*|*infinix*|*transsion*|*mt6781*)
                if [ -f "$profiles_dir/infinix_x698.sh" ]; then
                    log_probe "[*] Loading test device profile: infinix_x698.sh (Ground truth match)"
                    . "$profiles_dir/infinix_x698.sh"
                fi
                ;;
        esac
    fi

    # ── Qualcomm Targeted Profiles ──
    if [ "$platform" = "qcom" ]; then
        # 1. Adreno Architecture Profiles
        case "$GPU_LOWER" in
            *610*|*612*|*615*|*616*|*618*|*619*|*620*|*630*|*640*|*642*|*650*|*660*|*adreno*6*)
                if [ -f "$profiles_dir/gpu_adreno_600.sh" ]; then
                    log_probe "[*] Loading targeted profile: gpu_adreno_600.sh"
                    . "$profiles_dir/gpu_adreno_600.sh"
                fi
                ;;
            *710*|*720*|*725*|*730*|*732*|*740*|*750*|*adreno*7*)
                if [ -f "$profiles_dir/gpu_adreno_700.sh" ]; then
                    log_probe "[*] Loading targeted profile: gpu_adreno_700.sh"
                    . "$profiles_dir/gpu_adreno_700.sh"
                fi
                ;;
        esac

        # 2. Xiaomi / Poco Specific Profile
        case "$BOARD_LOWER" in
            *xiaomi*|*redmi*|*poco*)
                if [ -f "$profiles_dir/xiaomi_qcom.sh" ]; then
                    log_probe "[*] Loading targeted profile: xiaomi_qcom.sh"
                    . "$profiles_dir/xiaomi_qcom.sh"
                fi
                ;;
        esac
    fi
}
