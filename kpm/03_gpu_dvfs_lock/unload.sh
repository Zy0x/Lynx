#!/system/bin/sh
# Lynx GPU DVFS Lock - Independent Unloader Script

echo "[*] Melepas modul lynx_gpu_lock dari kernel ring-0..."
if [ -x /data/local/tmp/kpm_diag ]; then
    su 10683 -c "/data/local/tmp/kpm_diag unload lynx_gpu_lock"
fi

if su 10683 -c '/data/local/tmp/kpm_diag list 2>/dev/null' | grep -q "lynx_gpu_lock"; then
    echo "[❌] Modul masih terdeteksi aktif."
else
    echo "[✅] Modul lynx_gpu_lock berhasil dilepas! Dynamic DVFS standar kembali aktif."
fi
