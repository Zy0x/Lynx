#!/system/bin/sh
# Lynx EAS Sentinel - Independent Unloader Script
MOD_NAME="eas_sentinel"

echo "[LYNX-KPM] Melepas modul $MOD_NAME..."

if ! lsmod | grep -q "^$MOD_NAME "; then
    echo "[!] Modul $MOD_NAME memang sedang tidak aktif."
    exit 0
fi

# Prioritas 1: KernelPatch kpm unload
if [ -x /data/adb/ap/bin/kpatch ]; then
    /data/adb/ap/bin/kpatch dummy kpm unload "$MOD_NAME" 2>/dev/null
fi

# Prioritas 2: Standar Linux rmmod
rmmod "$MOD_NAME" 2>/dev/null
if [ $? -eq 0 ] || ! lsmod | grep -q "^$MOD_NAME "; then
    echo "[✅] $MOD_NAME berhasil dilepas dari kernel."
    exit 0
else
    echo "[❌] Gagal melepas $MOD_NAME. Mungkin masih ada proses yang mengaitkan modul ini."
    exit 1
fi
