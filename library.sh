# 🚀 KernelSU / Magisk Script
MIN_KSU_VERSION=10940
MIN_KSUD_VERSION=11425
MAX_KSU_VERSION=20000
MIN_MAGISK_VERSION=24200

check_root() {
    if [ -n "$BOOTMODE" ]; then
      if [ -n "$KSU" ]; then
        ui_print "  ✅ Installing via KernelSU"
        ui_print "  📌 KernelSU Version: ${KSU_KERNEL_VER_CODE:-unknown} (kernel) + ${KSU_VER_CODE:-unknown} (ksud)"
        ROOT_Method=KernelSU
        ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        
        [ -z "$KSU_KERNEL_VER_CODE" ] || [ "$KSU_KERNEL_VER_CODE" -lt "$MIN_KSU_VERSION" ] && {
          ui_print "  ⚠️  ERROR: ❌ KernelSU version too old!"
          ui_print "  🔄 Please update KernelSU to the latest version!"
          abort "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        }
    
        [ "$KSU_KERNEL_VER_CODE" -ge "$MAX_KSU_VERSION" ] && {
          ui_print "  ⚠️  ERROR: ❌ KernelSU version abnormal!"
          ui_print "  🔧 Please integrate KernelSU into your kernel as a submodule!"
          abort "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        }
    
        [ -z "$KSU_VER_CODE" ] || [ "$KSU_VER_CODE" -lt "$MIN_KSUD_VERSION" ] && {
          ui_print "  ⚠️  ERROR: ❌ ksud version too old!"
          ui_print "  🔄 Please update KernelSU Manager to the latest version!"
          abort "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        }
    
        if command -v magisk >/dev/null; then
          ui_print "  ⚠️  ERROR: ❌ Multiple root implementations detected!"
          ui_print "  🛑 Please uninstall Magisk before installing this module!"
          abort "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        fi
    
      elif [ -n "$MAGISK_VER_CODE" ]; then
        ui_print "  ✅ Installing via Magisk"
        ui_print "  📌 Magisk Version: ${MAGISK_VER:-unknown} (App: ${MAGISK_APK_VER:-unknown})"
        ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        [ "$MAGISK_VER_CODE" -lt "$MIN_MAGISK_VERSION" ] && {
          ui_print "  ⚠️  ERROR: ❌ Magisk version too old!"
          ui_print "  🔄 Please update Magisk to the latest version!"
          abort "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        ROOT_Method=MAGISK
        }
      else
        ui_print "  ⚠️  ERROR: ❌ Installation from recovery is not supported!"
        ui_print "  🔄 Please install from KernelSU or Magisk app!"
        abort "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
      fi
    fi
}

wifibonding_enable() {
    # Initialize variables
    local MAGISK_PATH MIRRORPATH CFG SELECTPATH TARGET
    local FOUND_FILE=false

    # Check for Magisk and set paths
    if command -v magisk >/dev/null 2>&1; then
        MAGISK_PATH=$(magisk --path)
        MIRRORPATH="$MAGISK_PATH/.magisk/mirror"
    else
        unset MIRRORPATH
    fi

    # Find WCNSS_qcom_cfg.ini files, only regular files
    array=$(find /system /vendor /product /system_ext -type f -name WCNSS_qcom_cfg.ini 2>/dev/null)
    if [ -z "$array" ]; then
        echo "Error: No valid WCNSS_qcom_cfg.ini file found."
        return 1
    fi

    # Process each found file
    while IFS= read -r CFG; do
        # Double-check that CFG is a regular file and not a symlink
        if [[ -f "$CFG" && ! -L "$CFG" ]]; then
            SELECTPATH="$CFG"
            TARGET="$MODPATH$SELECTPATH"

            # Create target directory
            mkdir -p "$(dirname "$TARGET")" || {
                echo "Error: Failed to create directory $(dirname "$TARGET")"
                continue
            }

            # Copy file from mirror or original path
            if [ -n "$MIRRORPATH" ] && [ -f "$MIRRORPATH$SELECTPATH" ] && [ -r "$MIRRORPATH$SELECTPATH" ]; then
                cp -af "$MIRRORPATH$SELECTPATH" "$TARGET" || {
                    echo "Error: Failed to copy $MIRRORPATH$SELECTPATH to $TARGET"
                    continue
                }
            elif [ -f "$SELECTPATH" ] && [ -r "$SELECTPATH" ]; then
                cp -af "$SELECTPATH" "$TARGET" || {
                    echo "Error: Failed to copy $SELECTPATH to $TARGET"
                    continue
                }
            else
                echo "Error: Source file $SELECTPATH not found or not readable."
                continue
            fi

            # Check if file is writable
            if [ ! -w "$TARGET" ]; then
                echo "Error: Target file $TARGET is not writable."
                continue
            fi

            # Check if END marker exists, append if not
            if ! grep -q '^END$' "$TARGET"; then
                echo "Appending END marker to $TARGET"
                echo "END" >> "$TARGET"
            fi

            # Modify the copied file
            sed -i '
                /gChannelBondingMode24GHz=/d;
                /gChannelBondingMode5GHz=/d;
                /gForce1x1Exception=/d;
                /sae_enabled=/d;
                /gEnablefwlog=/d;
                /gEnablePacketLog=/d;
                /gEnableSNRMonitoring=/d;
                /gEnableNUDTracking=/d;
                /gEnableLogp=/d;
                /nrx_wakelock_timeout=/d;
                /gFwDebugLogLevel=/d;
                /gFwDebugModuleLoglevel=/d;
                s/^END$/gChannelBondingMode24GHz=1\ngChannelBondingMode5GHz=1\ngForce1x1Exception=0\nsae_enabled=1\ngEnablefwlog=0\ngEnablePacketLog=0\ngEnableSNRMonitoring=0\ngEnableNUDTracking=0\ngEnableLogp=0\nnrx_wakelock_timeout=0\nEND/' "$TARGET" || {
                echo "Error: Failed to modify $TARGET"
                continue
            }

            FOUND_FILE=true
            echo "Successfully processed $SELECTPATH"
        else
            echo "Skipping $CFG: Not a valid file or is a symlink."
        fi
    done <<< "$array"

    # Check if any file was successfully processed
    if ! $FOUND_FILE; then
        echo "Error: No valid WCNSS_qcom_cfg.ini files were processed."
        abort "Installation FAILED: No supported Wi-Fi configuration files found."
    fi

    # Move directories to system structure
    move_dir_content() {
        local src="$1"
        local dst="$2"

        if [ -d "$src" ]; then
            mkdir -p "$dst" || {
                echo "Error: Failed to create directory $dst"
                return 1
            }
            cp -af "$src"/. "$dst"/ 2>/dev/null && rm -rf "$src" || {
                echo "Error: Failed to move contents from $src to $dst"
                return 1
            }
            echo "Moved contents from $src to $dst"
        fi
    }

    # Perform directory moves
    mkdir -p "${MODPATH}/system"
    move_dir_content "${MODPATH}/vendor"     "${MODPATH}/system/vendor"
    move_dir_content "${MODPATH}/product"    "${MODPATH}/system/product"
    move_dir_content "${MODPATH}/system_ext" "${MODPATH}/system/system_ext"

    echo "Installation completed successfully."
}

# Universal GMS Doze by gloeyisk
# open source loving GL-DP and all contributors;
# Patches Google Play services app and its background processes to be able using battery optimization
#
# Patch the XML and place the modified one to the original directory
gms_doze_installer() {
ui_print "- Patching XML files"
GMS0="\"com.google.android.gms"\"
STR1="allow-in-power-save package=$GMS0"
STR2="allow-in-data-usage-save package=$GMS0"
NULL="/dev/null"
ui_print "- Finding system XML"
SYS_XML="$(
SXML="$(find /system_ext/* /system/* /product/* \
/vendor/* -type f -iname '*.xml' -print)"
for S in $SXML; do
  if grep -qE "$STR1|$STR2" $ROOT$S 2> $NULL; then
    echo "$S"
  fi
done
)"

PATCH_SX() {
for SX in $SYS_XML; do
  mkdir -p "$(dirname $MODPATH$SX)"
  cp -af $ROOT$SX $MODPATH$SX
  ui_print "  Patching: $SX"
  sed -i "/$STR1/d;/$STR2/d" $MODPATH/$SX
done

# Merge patched files under /system dir
for P in product vendor; do
  if [ -d $MODPATH/$P ]; then
    mkdir -p $MODPATH/system/$P
    mv -f $MODPATH/$P $MODPATH/system/
  fi
done
}

# Search and patch any conflicting modules (if present)
# Search conflicting XML files
MOD_XML="$(
MXML="$(find /data/adb/* -type f -iname "*.xml" -print)"
for M in $MXML; do
  if grep -qE "$STR1|$STR2" $M; then
    echo "$M"
  fi
done
)"

PATCH_MX() {
ui_print "- Finding conflicting XML"
for MX in $MOD_XML; do
  MOD="$(echo "$MX" | awk -F'/' '{print $5}')"
  ui_print "  $MOD: $MX"
  sed -i "/$STR1/d;/$STR2/d" $MX
done
}

# Find and patch conflicting XML
PATCH_SX && PATCH_MX
}

# Dex2oat opt
dex2oat_enable() {
[[ "$IS64BIT" == "true" ]] && mv -f "$MODPATH/system/bin/dex2oat_opt64" "$MODPATH/system/bin/dex2oat_opt" && rm -f $MODPATH/system/bin/dex2oat_opt32 || mv -f "$MODPATH/system/bin/dex2oat_opt32" "$MODPATH/system/bin/dex2oat_opt" && rm -f $MODPATH/system/bin/dex2oat_opt64
}

# Touch tweak
touchtweak_enable() {
mkdir -p $MODPATH/vendor/usr/idc
cp -r $MODPATH/extra/touch/idc $MODPATH/vendor/usr
}

# Make mode file
mode_conf()
{
cat <<EOL > /storage/emulated/0/Lynx/mode
# Set flow ram ~ (1)enable; (0)disable
flow=0

# Set mode flow ~ (1)basic; (2)advance; (3)high; (5)extreme
flow_mode=0
EOL
}

# Dark GPU
dark_gpu() {
    ui_print "  🔍 Detecting GPU Model..."
    sleep 5
    model=$(dumpsys SurfaceFlinger | grep 'GLES:' | head -n 1 | awk '{print $5}' | sed 's/,//')
    opengl_renderer=$(dumpsys SurfaceFlinger | grep 'GLES:' | awk '{print $3, $4}' | sed 's/,//' | tr -d '[:space:]')
    source="dumpsys"
    if [ -z "$model" ]; then
        model=$(cat /sys/class/kgsl/kgsl-3d0/gpu_model 2>/dev/null | tr -d '[:space:]')
        source="kgsl"
    fi
    if [ -z "$model" ]; then
        ui_print "  ⚠️ GPU Model Not Found!"
        return 0
    fi
    if [ "$source" = "dumpsys" ]; then
        detected_gpu="$opengl_renderer - $model"
    else
        detected_gpu="$model"
    fi
    ui_print "  ✅ GPU Detected: $detected_gpu (based: $source)"

    for arch in lib lib64; do
        for base in "$MODPATH/system/$arch/egl" "$MODPATH/system/vendor/$arch/egl"; do
            file="$base/egl.cfg"
            mkdir -p "$base"
            [ ! -f "$file" ] && touch "$file"
            if [ "$source" = "dumpsys" ]; then
                echo "1 1 adreno$model" > "$file"
            else
                echo "1 1 $model" > "$file"
            fi
            ui_print "  🔧 Configuration applied to: $file"
        done
    done
    ui_print "  ☠️ Dark GPU Has Been Applied!"
}

choose_busybox() {
    mkdir -p $MODPATH/system/xbin
    set_perm_recursive $MODPATH/system/xbin 0 0 0755 0777

    for path in \
        "/data/adb/modules/busybox-brutal" \
        "/data/adb/modules/busybox-ndk" \
        "/system/xbin/busybox" \
        "/system/bin/busybox" \
        "/vendor/bin/busybox"; do
        [ -e "$path" ] && {
            ui_print "    ⚠️ Another BusyBox detected!"
            sleep 2
        }
    done

    case "$busybox_type" in
        "builtin")
            if [ -n "$BOOTMODE" ]; then
                if [ -n "$KSU" ]; then
                    ui_print "    ✅ Installing Built-in BusyBox for KSU!"
                    cp -f /data/adb/ksu/bin/busybox $MODPATH/system/xbin
                elif [ -n "$MAGISK_VER_CODE" ]; then
                    ui_print "    ✅ Installing Built-in BusyBox for Magisk!"
                    cp -f /data/adb/magisk/busybox $MODPATH/system/xbin
                else
                    ui_print "    ⚠️  Installation from recovery is not supported!"
                    ui_print "    🔄 Please install from KernelSU or Magisk app!"
                    return 1
                fi
                sed -i '/#install_busybox/s/.*/install_busybox/' $MODPATH/post-fs-data.sh
                rm -rf $MODPATH/system/bin/feravolt
            fi
            ;;
        
        "brutal")
            # 🔍 Deteksi arsitektur
            arch=$(getprop ro.product.cpu.abi)
            rm -f $MODPATH/busybox_installed
            case "$arch" in
                *arm64*) ARCH="busybox8"; ARCH_NAME="64-bit ARM" ;;
                *armeabi*) ARCH="busybox7"; ARCH_NAME="32-bit ARM" ;;
                *x86_64*) ARCH="busybox64"; ARCH_NAME="x86_64" ;;
                *x86*) ARCH="busybox86"; ARCH_NAME="x86" ;;
                *) abort "    ❌ Can't detect device architecture!" ;;
            esac

            mv -f $MODPATH/system/bin/feravolt/$ARCH $MODPATH/system/xbin/busybox
            rm -rf $MODPATH/system/bin/feravolt
            ui_print "    ✅ $ARCH_NAME architecture detected."
            sed -i '/#install_busybox/s/.*/install_busybox/' $MODPATH/post-fs-data.sh
            ;;
        
        *)
            abort "    ❌ Unknown BusyBox type!"
            ;;
    esac

    if [ ! -d /system/xbin ]; then
        mv -f $MODPATH/system/xbin/busybox $MODPATH/system/bin/busybox
        rm -rf $MODPATH/system/xbin
        ui_print "    📁 Installing BusyBox to /system/bin/"
    fi
}

# Load common functions
LOG_FILE="/storage/emulated/0/Lynx/Lynx.log"
log_msg() {
    local msg="[$(date '+%Y-%m-%d %H:%M:%S')] $1"
    echo "$msg" | tee -a "$LOG_FILE"
}