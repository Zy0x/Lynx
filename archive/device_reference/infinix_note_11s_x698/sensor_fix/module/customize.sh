#!/system/bin/sh
# Infinix Note 11S (X698 MT6781) - Sensor Fix Installer
# Author: ɴᴏɪʀ

ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print " 📱 Infinix Note 11S (X698) GSI Sensor Fix v1.0.0"
ui_print " ⚡ MediaTek Helio G96 (MT6781) Hardware Sensor Engine"
ui_print " 👤 Author: ɴᴏɪʀ"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# 1. Verification of hardware
ui_print "- Memeriksa kompatibilitas perangkat..."
IS_X698=false
if [ -f /sys/firmware/devicetree/base/compatible ] && grep -qi "mt6781" /sys/firmware/devicetree/base/compatible 2>/dev/null; then
    IS_X698=true
elif [ -d /proc/ppm ] || [ -d /proc/ged ]; then
    IS_X698=true
fi

if [ "$IS_X698" = "true" ]; then
    ui_print "  [✓] Target SoC MT6781 / MediaTek terverifikasi!"
else
    ui_print "  [!] Peringatan: SoC bukan MT6781, instalasi tetap dilanjutkan sebagai fallback."
fi

# 2. Stage KPM for APatch / KernelPatch if present
if [ -f "$MODPATH/kpm/infinix_sensor_hub.kpm" ]; then
    ui_print "- Memasang KernelPatch Module (KPM)..."
    mkdir -p /data/adb/kpm 2>/dev/null
    cp -af "$MODPATH/kpm/infinix_sensor_hub.kpm" /data/adb/kpm/infinix_sensor_hub.kpm 2>/dev/null
    chmod 644 /data/adb/kpm/infinix_sensor_hub.kpm 2>/dev/null
    ui_print "  [✓] infinix_sensor_hub.kpm berhasil dipasang ke /data/adb/kpm/"
fi

# 3. Stage Early Post-FS Hook into /data/adb/post-fs-data.d/
ui_print "- Memasang Early Boot Pre-Zygote Hook..."
mkdir -p /data/adb/post-fs-data.d 2>/dev/null
cat << 'EOF' > /data/adb/post-fs-data.d/00_infinix_sensor.sh
#!/system/bin/sh
# Early post-fs sensor permission hook
chmod 0660 /dev/m_*_misc /dev/sensorlist 2>/dev/null
chmod 0660 /sys/class/sensor/*/*active /sys/class/sensor/*/*batch \
           /sys/class/sensor/*/*flush /sys/class/sensor/*/*cali \
           /sys/class/sensor/*/*devnum /sys/class/sensor/*/*enablenodata \
           /sys/class/sensor/*/*delay 2>/dev/null
chmod 755 /sys/class/sensor /sys/devices/virtual/sensor 2>/dev/null
chmod 755 /sys/devices/virtual/sensor/* 2>/dev/null
chown -R system:system /dev/m_*_misc /dev/sensorlist /sys/class/sensor /sys/devices/virtual/sensor 2>/dev/null
EOF
chmod 755 /data/adb/post-fs-data.d/00_infinix_sensor.sh 2>/dev/null
ui_print "  [✓] Hook 00_infinix_sensor.sh aktif di /data/adb/post-fs-data.d/"

# 4. Set module file permissions
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/service.sh" 0 0 0755

ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print " ✅ Instalasi Selesai! Silakan reboot perangkat."
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
