#!/system/bin/sh
# Lynx EAS Sentinel - Independent Loader Script
DIR="$(cd "$(dirname "$0")" && pwd)"
KPM_FILE="$DIR/eas_sentinel.kpm"
[ -f "$KPM_FILE" ] || KPM_FILE="/storage/emulated/0/Lynx/KPM/lynx_eas_sentinel.kpm"

echo "[LYNX-KPM] Memeriksa status modul lynx_eas_sentinel..."

# Cek apakah sudah aktif di KernelPatch
if su 10683 -c '/data/local/tmp/kpm_diag list 2>/dev/null' | grep -q "lynx_eas_sentinel"; then
    echo "[!] Modul lynx_eas_sentinel sudah aktif di KernelPatch!"
    exit 0
fi

# Salin ke staging /data/local/tmp agar UID APatch bisa membaca berkas
cp "$KPM_FILE" /data/local/tmp/lynx_eas_sentinel.kpm 2>/dev/null
chown 10683:10683 /data/local/tmp/lynx_eas_sentinel.kpm 2>/dev/null
chmod 644 /data/local/tmp/lynx_eas_sentinel.kpm 2>/dev/null

echo "[*] Memuat ke kernel ring-0 via KernelPatch (kpm_diag)..."
su 10683 -c "/data/local/tmp/kpm_diag load /data/local/tmp/lynx_eas_sentinel.kpm"

if su 10683 -c '/data/local/tmp/kpm_diag list 2>/dev/null' | grep -q "lynx_eas_sentinel"; then
    echo "[✅] lynx_eas_sentinel berhasil dimuat ke kernel via APatch!"
    echo "[⚡] Prioritas EAS penjadwal game terlindungi (Dynamic Balance Aware)!"
    echo "[💤] Mode Balance tetap hemat baterai dengan deep sleep aktif."
    exit 0
else
    echo "[❌] Gagal memuat lynx_eas_sentinel. Anda juga bisa memuatnya langsung lewat tab KPM di aplikasi APatch."
    exit 1
fi
