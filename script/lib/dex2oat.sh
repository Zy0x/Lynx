#!/bin/sh

# === HELP FUNCTION ===
help_dex2oat() {
    cat <<EOF
Usage: Lxcore -dex2oat [command]

Available commands:
  apply       Run Dex2oat optimization process.
  help        Show this help message.

Description:
  This module optimizes Android's dex2oat compilation process,
  improving app install and update performance.

Example:
  Lxcore -dex2oat apply
EOF
}

# === DEX2OAT OPTIMIZATION FUNCTION ===
dex2oat_opt_enable()
{
    local MODPROP="/data/adb/modules/Lynx/module.prop"
    local BIN_PATH="/system/bin/dexoat_opt"

    log_msg "Starting Dex2oat Optimization..."

    # Update status di module.prop
    if [ -f "$MODPROP" ]; then
        sed -Ei "s/^description=$.*/description=[ ⛔ 𝘿𝙚𝙭2𝙤𝙖𝙩 𝙊𝙥𝙩𝙞𝙢𝙞𝙯𝙚𝙧 𝙞𝙨 𝙍𝙪𝙣𝙣𝙞𝙣𝙜... ]/" "$MODPROP"
    else
        log_msg "Error: module.prop not found at $MODPROP"
        echo "Error: module.prop not found."
        exit 1
    fi

    # Kirim notifikasi
    su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʏ' 'Lʏɴx' '⛔ 𝘿𝙚𝙭2𝙤𝙖𝙩 𝙊𝙥𝙩𝙞𝙢𝙞𝙯𝙚𝙧 𝙞𝙨 𝙍𝙪𝙣𝙣𝙞𝙣𝙜...'" >/dev/null 2>&1

    # Cek apakah binary tersedia
    if [ -x "$BIN_PATH" ]; then
        log_msg "Running binary: $BIN_PATH"
        "$BIN_PATH"
    elif command -v dexoat_opt >/dev/null 2>&1; then
        log_msg "Running function: dexoat_opt"
        dexoat_opt
    else
        log_msg "Error: dexoat_opt binary or function not found!"
        echo "Error: dexoat_opt binary or function not found!"
        exit 1
    fi

    log_msg "Dex2oat Optimization completed."
}