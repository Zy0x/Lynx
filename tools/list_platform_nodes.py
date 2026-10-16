import subprocess

target = "192.168.1.13:5555"
script = '''
echo "=== CHARGER NODES ==="
ls /sys/devices/platform/charger/

echo "=== ODM TRAN BATTERY ==="
ls /sys/devices/platform/odm/odm:tran_battery/

echo "=== TRAN BATTERY ==="
ls /sys/devices/platform/tran_battery/ 2>/dev/null

echo "=== PCA DV2 ALGO ==="
ls /sys/devices/platform/pca_dv2_algo/
'''
p = subprocess.run(["adb", "-s", target, "shell", f"su -c '{script}'"], capture_output=True, text=True)
print(p.stdout)
