#!/system/bin/sh
# Lynx Universal - Master KPM Unloader Script
# Unloads all modular kernel patch components cleanly in reverse order.

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=================================================="
echo "   LYNX UNIVERSAL - KERNEL PATCH MODULE UNLOADER  "
echo "=================================================="

# 4. Memory Cushion
if [ -d "$BASE_DIR/04_mem_cushion" ]; then
    echo "\n[1/4] Melepas Memory Cushion..."
    sh "$BASE_DIR/04_mem_cushion/unload.sh"
fi

# 3. GPU DVFS Lock
if [ -d "$BASE_DIR/03_gpu_dvfs_lock" ]; then
    echo "\n[2/4] Melepas GPU DVFS Lock..."
    sh "$BASE_DIR/03_gpu_dvfs_lock/unload.sh"
fi

# 2. Thermal Silencer
if [ -d "$BASE_DIR/02_thermal_silencer" ]; then
    echo "\n[3/4] Melepas Thermal Silencer..."
    sh "$BASE_DIR/02_thermal_silencer/unload.sh"
fi

# 1. EAS Sentinel
if [ -d "$BASE_DIR/01_eas_sentinel" ]; then
    echo "\n[4/4] Melepas EAS Sentinel..."
    sh "$BASE_DIR/01_eas_sentinel/unload.sh"
fi

echo "\n=================================================="
echo "Status Modul Kernel Setelah Pelepasan:"
lsmod | grep -E "(eas_sentinel|thermal_silencer|gpu_dvfs_lock|mem_cushion)" || echo "Semua modul Lynx KPM telah berhasil dilepas dari kernel!"
echo "=================================================="
