#!/system/bin/sh
# Lynx Universal - Master KPM Loader Script
# Loads all 4 modular kernel patch components in proper initialization order.

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=================================================="
echo "   LYNX UNIVERSAL - KERNEL PATCH MODULE LOADER    "
echo "   Target: Infinix Note 11 Pro (MT6781 Helio G96) "
echo "=================================================="

# 1. EAS Sentinel
if [ -d "$BASE_DIR/01_eas_sentinel" ]; then
    echo "\n[1/4] Memuat EAS Sentinel..."
    sh "$BASE_DIR/01_eas_sentinel/load.sh"
fi

# 2. Thermal Silencer
if [ -d "$BASE_DIR/02_thermal_silencer" ]; then
    echo "\n[2/4] Memuat Thermal Silencer..."
    sh "$BASE_DIR/02_thermal_silencer/load.sh"
fi

# 3. GPU DVFS Lock
if [ -d "$BASE_DIR/03_gpu_dvfs_lock" ]; then
    echo "\n[3/4] Memuat GPU DVFS Lock..."
    sh "$BASE_DIR/03_gpu_dvfs_lock/load.sh"
fi

# 4. Memory Cushion
if [ -d "$BASE_DIR/04_mem_cushion" ]; then
    echo "\n[4/4] Memuat Memory Cushion..."
    sh "$BASE_DIR/04_mem_cushion/load.sh"
fi

echo "\n=================================================="
echo "Status Modul Kernel Aktif:"
lsmod | grep -E "(eas_sentinel|thermal_silencer|gpu_dvfs_lock|mem_cushion)" || echo "Tidak ada modul Lynx yang terdeteksi di lsmod."
echo "=================================================="
