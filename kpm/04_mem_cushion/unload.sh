#!/system/bin/sh
# Lynx Memory Cushion - Independent Unloader Script

echo "[*] Melepas modul lynx_mem_cushion dari kernel ring-0..."
if [ -x /data/local/tmp/kpm_diag ]; then
    su 10683 -c "/data/local/tmp/kpm_diag unload lynx_mem_cushion"
fi

if su 10683 -c '/data/local/tmp/kpm_diag list 2>/dev/null' | grep -q "lynx_mem_cushion"; then
    echo "[❌] Modul masih terdeteksi aktif."
else
    echo "[✅] Modul lynx_mem_cushion berhasil dilepas! Manajemen memori standar Linux kembali aktif 100%."
fi
