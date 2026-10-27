#!/system/bin/sh
# Lynx Universal - KPM Status Inspector Script
echo "=================================================="
echo "    LYNX KERNEL PATCH MODULE STATUS INSPECTOR     "
echo "=================================================="

echo "\n[1] Kernel Modules List (lsmod):"
lsmod | grep -E "(Module|eas_sentinel|thermal_silencer|gpu_dvfs_lock|mem_cushion)"

echo "\n[2] Ring-0 Procfs Telemetry (/proc/lynx):"
if [ -d /proc/lynx ]; then
    for node in /proc/lynx/*; do
        if [ -f "$node" ]; then
            echo "\n--- Node: $node ---"
            cat "$node"
        fi
    done
else
    echo "Direktori /proc/lynx belum aktif (belum ada modul yang dimuat)."
fi
echo "\n=================================================="
