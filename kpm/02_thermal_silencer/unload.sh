#!/system/bin/sh
# Lynx Thermal Silencer - Independent Unloader Script

echo "[*] Melepas modul lynx_thermal_silencer dari kernel ring-0..."
if [ -x /data/local/tmp/kpm_diag ]; then
    su 10683 -c "/data/local/tmp/kpm_diag unload lynx_thermal_silencer"
fi

if su 10683 -c '/data/local/tmp/kpm_diag list 2>/dev/null' | grep -q "lynx_thermal_silencer"; then
    echo "[❌] Modul masih terdeteksi aktif."
else
    echo "[✅] Modul lynx_thermal_silencer berhasil dilepas! Proteksi termal standar OEM kembali aktif 100%."
fi
