#!/system/bin/sh
# Lynx GPU DVFS Lock - Independent Loader Script
DIR="$(cd "$(dirname "$0")" && pwd)"
KPM_FILE="$DIR/gpu_dvfs_lock.kpm"
[ -f "$KPM_FILE" ] || KPM_FILE="/storage/emulated/0/Lynx/KPM/lynx_gpu_lock.kpm"

echo "[LYNX-KPM] Memeriksa status modul lynx_gpu_lock..."

# Cek apakah sudah aktif di KernelPatch
if su 10683 -c '/data/local/tmp/kpm_diag list 2>/dev/null' | grep -q "lynx_gpu_lock"; then
    echo "[!] Modul lynx_gpu_lock sudah aktif di KernelPatch!"
    exit 0
fi

# Salin ke staging /data/local/tmp agar UID APatch bisa membaca berkas
cp "$KPM_FILE" /data/local/tmp/lynx_gpu_lock.kpm 2>/dev/null
chown 10683:10683 /data/local/tmp/lynx_gpu_lock.kpm 2>/dev/null
chmod 644 /data/local/tmp/lynx_gpu_lock.kpm 2>/dev/null

echo "[*] Memuat ke kernel ring-0 via KernelPatch (kpm_diag)..."
su 10683 -c "/data/local/tmp/kpm_diag load /data/local/tmp/lynx_gpu_lock.kpm"

if su 10683 -c '/data/local/tmp/kpm_diag list 2>/dev/null' | grep -q "lynx_gpu_lock"; then
    echo "[✅] lynx_gpu_lock berhasil dimuat ke kernel via APatch!"
    echo "[⚡] Lantai frekuensi GPU Mali-G57 MC2 terkunci di 950 MHz (OPP Puncak)!"
    exit 0
else
    echo "[❌] Gagal memuat lynx_gpu_lock. Anda juga bisa memuatnya langsung lewat tab KPM di aplikasi APatch."
    exit 1
fi
