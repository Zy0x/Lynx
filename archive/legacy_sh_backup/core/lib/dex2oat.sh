#!/system/bin/sh
# Lynx Universal - Android ART & Dex2oat Compilation Optimizer
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)

help_dex2oat() {
    cat <<EOF
Usage: Lxcore -dex2oat [command]

Available commands:
  apply       Run background Android ART Dex2oat compilation.
  help        Show this help message.

Description:
  Compiles installed user & system application packages with speed-profile
  to improve startup latency, reduce JIT compilation overhead, and optimize execution.

Example:
  Lxcore -dex2oat apply
EOF
}

dex2oat_opt_enable() {
    local MODPROP="/data/adb/modules/Lynx/module.prop"

    log_msg "Starting Android ART Dex2oat Optimization..."

    if [ -f "$MODPROP" ]; then
        sed -Ei "s/^description=\[.*\]/description=[ Dex2oat Compilation Active ]/" "$MODPROP" 2>/dev/null
    fi

    su -lp 2000 -c "cmd notification post -S bigtext -t 'Lynx - Deity' 'Lynx' 'Mengoptimalkan ART Dex2oat paket aplikasi...'" >/dev/null 2>&1

    # 1. Preferred modern Android ART compilation method
    if command -v cmd >/dev/null 2>&1; then
        log_msg "Executing: cmd package bg-dexopt-job & speed-profile"
        cmd package compile -m speed-profile -a >/dev/null 2>&1
    # 2. Fallback for older Android versions
    elif command -v pm >/dev/null 2>&1; then
        log_msg "Executing: pm compile -m speed-profile -a"
        pm compile -m speed-profile -a >/dev/null 2>&1
    else
        log_msg "Error: Neither 'cmd' nor 'pm' found on device."
        return 1
    fi

    log_msg "Dex2oat ART Compilation completed successfully."
    su -lp 2000 -c "cmd notification post -S bigtext -t 'Lynx - Deity' 'Lynx' 'Dex2oat Selesai Dioptimalkan'" >/dev/null 2>&1
}