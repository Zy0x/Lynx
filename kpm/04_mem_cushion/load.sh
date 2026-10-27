#!/system/bin/sh
# Lynx Memory Cushion - Independent Loader Script
DIR="$(cd "$(dirname "$0")" && pwd)"
KPM_FILE="$DIR/mem_cushion.kpm"
[ -f "$KPM_FILE" ] || KPM_FILE="/storage/emulated/0/Lynx/KPM/lynx_mem_cushion.kpm"

echo "[LYNX-KPM] Memeriksa status modul lynx_mem_cushion..."

# Cek apakah sudah aktif di KernelPatch
if su 10683 -c '/data/local/tmp/kpm_diag list 2>/dev/null' | grep -q "lynx_mem_cushion"; then
    echo "[!] Modul lynx_mem_cushion sudah aktif di KernelPatch!"
    exit 0
fi

# Salin ke staging /data/local/tmp agar UID APatch bisa membaca berkas
cp "$KPM_FILE" /data/local/tmp/lynx_mem_cushion.kpm 2>/dev/null
chown 10683:10683 /data/local/tmp/lynx_mem_cushion.kpm 2>/dev/null
chmod 644 /data/local/tmp/lynx_mem_cushion.kpm 2>/dev/null

echo "[*] Memuat ke kernel ring-0 via KernelPatch (kpm_diag)..."
su 10683 -c "/data/local/tmp/kpm_diag load /data/local/tmp/lynx_mem_cushion.kpm"

if su 10683 -c '/data/local/tmp/kpm_diag list 2>/dev/null' | grep -q "lynx_mem_cushion"; then
    echo "[✅] lynx_mem_cushion berhasil dimuat ke kernel via APatch!"
    echo "[🛡️] Pelindung allocstall memori aktif untuk game!"
    echo "[💤] Mode Balance tetap hemat baterai dengan manajemen memori standar."
    exit 0
else
    echo "[❌] Gagal memuat lynx_mem_cushion. Anda juga bisa memuatnya langsung lewat tab KPM di aplikasi APatch."
    exit 1
fi
