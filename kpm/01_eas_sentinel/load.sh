#!/system/bin/sh
# Lynx EAS Sentinel - Independent Loader Script
DIR="$(cd "$(dirname "$0")" && pwd)"
MOD_NAME="eas_sentinel"
KO_PATH="$DIR/${MOD_NAME}.ko"

echo "[LYNX-KPM] Memeriksa modul $MOD_NAME..."

# Cek apakah modul sudah berjalan
if lsmod | grep -q "^$MOD_NAME "; then
    echo "[!] Modul $MOD_NAME sudah aktif!"
    exit 0
fi

# Prioritas 1: APatch Jailbreak insmod (bypass vermagic)
if [ -x /data/adb/ap/bin/apd ]; then
    echo "[*] Memuat via APatch jailbreak engine (apd insmod)..."
    /data/adb/ap/bin/apd insmod "$KO_PATH" 2>/dev/null
    RC=$?
    if [ $RC -eq 0 ]; then
        echo "[✅] $MOD_NAME berhasil dimuat via APatch!"
        [ -f /proc/lynx/eas_sentinel ] && cat /proc/lynx/eas_sentinel
        exit 0
    fi
fi

# Prioritas 2: KernelPatch kpatch kpm load
if [ -x /data/adb/ap/bin/kpatch ]; then
    echo "[*] Mencoba memuat via KernelPatch kpm load..."
    /data/adb/ap/bin/kpatch dummy kpm load "$KO_PATH" 2>/dev/null
    if lsmod | grep -q "^$MOD_NAME "; then
        echo "[✅] $MOD_NAME berhasil dimuat via KernelPatch KPM!"
        [ -f /proc/lynx/eas_sentinel ] && cat /proc/lynx/eas_sentinel
        exit 0
    fi
fi

# Prioritas 3: Standar Linux insmod
echo "[*] Memuat via insmod standar..."
insmod "$KO_PATH" 2>/dev/null
if [ $? -eq 0 ]; then
    echo "[✅] $MOD_NAME berhasil dimuat!"
    [ -f /proc/lynx/eas_sentinel ] && cat /proc/lynx/eas_sentinel
    exit 0
else
    echo "[❌] Gagal memuat $MOD_NAME. Pastikan file .ko telah dikompilasi untuk kernel Anda."
    exit 1
fi
