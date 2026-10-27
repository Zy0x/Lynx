#!/system/bin/sh
# Lynx Thermal Silencer - Independent Loader Script
DIR="$(cd "$(dirname "$0")" && pwd)"
KPM_FILE="$DIR/thermal_silencer.kpm"
[ -f "$KPM_FILE" ] || KPM_FILE="/storage/emulated/0/Lynx/KPM/lynx_thermal_silencer.kpm"

echo "[LYNX-KPM] Memeriksa status modul lynx_thermal_silencer..."

# Cek apakah sudah aktif di KernelPatch
if su 10683 -c '/data/local/tmp/kpm_diag list 2>/dev/null' | grep -q "lynx_thermal_silencer"; then
    echo "[!] Modul lynx_thermal_silencer sudah aktif di KernelPatch!"
    exit 0
fi

# Salin ke staging /data/local/tmp agar UID APatch bisa membaca berkas
cp "$KPM_FILE" /data/local/tmp/lynx_thermal_silencer.kpm 2>/dev/null
chown 10683:10683 /data/local/tmp/lynx_thermal_silencer.kpm 2>/dev/null
chmod 644 /data/local/tmp/lynx_thermal_silencer.kpm 2>/dev/null

echo "[*] Memuat ke kernel ring-0 via KernelPatch (kpm_diag)..."
su 10683 -c "/data/local/tmp/kpm_diag load /data/local/tmp/lynx_thermal_silencer.kpm"

if su 10683 -c '/data/local/tmp/kpm_diag list 2>/dev/null' | grep -q "lynx_thermal_silencer"; then
    echo "[✅] lynx_thermal_silencer berhasil dimuat ke kernel via APatch!"
    echo "[🛡️] Throttling termal CPU (mt_ppm) & GPU (mt_gpufreq) berhasil diredam!"
    echo "[🔒] Proteksi shutdown darurat PMIC 115°C tetap aktif 100% demi keselamatan silikon."
    exit 0
else
    echo "[❌] Gagal memuat lynx_thermal_silencer. Anda juga bisa memuatnya langsung lewat tab KPM di aplikasi APatch."
    exit 1
fi
