#!/system/bin/sh
# Lynx EAS Sentinel - Independent Unloader Script

echo "[*] Melepas modul lynx_eas_sentinel dari kernel ring-0..."
if [ -x /data/local/tmp/kpm_diag ]; then
    su 10683 -c "/data/local/tmp/kpm_diag unload lynx_eas_sentinel"
fi

if su 10683 -c '/data/local/tmp/kpm_diag list 2>/dev/null' | grep -q "lynx_eas_sentinel"; then
    echo "[❌] Modul masih terdeteksi aktif."
else
    echo "[✅] Modul lynx_eas_sentinel berhasil dilepas! Penjadwal standar kembali aktif 100%."
fi
