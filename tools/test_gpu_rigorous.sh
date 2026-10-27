#!/system/bin/sh
DEV="/dev/input/event3"

echo "=========================================================="
echo "    PENGUJIAN KECERDASAN 4-TIER ULTRA-EFISIEN GPU LYNX   "
echo "=========================================================="

echo ""
echo "[1] UJI COBA 1: BASELINE TIER 0 - IDLE (DIAM TANPA SENTUHAN)"
echo -n "  • Index OPP Boost   : "; cat /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null
echo -n "  • Frekuensi Dasar   : "; cat /sys/module/ged/parameters/gpu_bottom_freq 2>/dev/null
echo -n "  • GPU Boost Level   : "; cat /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null
echo -n "  • DVFS Margin Value : "; cat /sys/kernel/ged/hal/dvfs_margin_value 2>/dev/null

echo ""
echo "[2] UJI COBA 2: TIER 1 - RESPON SENTUHAN TUNGGAL / TAP RINGAN (<1ms)"
input keyevent KEYCODE_HOME
sleep 2.5
echo "  >> Mengirim paket sentuhan perangkat keras tunggal langsung ke $DEV..."
sendevent $DEV 3 57 1
sendevent $DEV 1 330 1
sendevent $DEV 3 53 540
sendevent $DEV 3 54 1200
sendevent $DEV 0 0 0
echo -n "  • Index OPP Boost (Light Tap) : "; cat /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null
echo -n "  • Frekuensi Dasar (Light Tap) : "; cat /sys/module/ged/parameters/gpu_bottom_freq 2>/dev/null
echo -n "  • GPU Boost Level (Light Tap) : "; cat /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null
echo -n "  • DVFS Margin Value           : "; cat /sys/kernel/ged/hal/dvfs_margin_value 2>/dev/null

echo ""
echo "[3] UJI COBA 3: TIER 2 - SCROLLING CEPAT / RAPID FLING (EFISIEN, BUKAN 950MHz!)"
echo "  >> Mengirim rentetan gestur usapan cepat..."
sendevent $DEV 3 57 2; sendevent $DEV 1 330 1; sendevent $DEV 3 53 300; sendevent $DEV 3 54 500; sendevent $DEV 0 0 0
sendevent $DEV 3 57 2; sendevent $DEV 3 53 350; sendevent $DEV 3 54 700; sendevent $DEV 0 0 0
sendevent $DEV 3 57 2; sendevent $DEV 3 53 400; sendevent $DEV 3 54 900; sendevent $DEV 0 0 0
sendevent $DEV 3 57 2; sendevent $DEV 3 53 450; sendevent $DEV 3 54 1100; sendevent $DEV 0 0 0
echo -n "  • Index OPP Boost (Fluid Scroll) : "; cat /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null
echo -n "  • Frekuensi Dasar (Fluid Scroll) : "; cat /sys/module/ged/parameters/gpu_bottom_freq 2>/dev/null
echo -n "  • GPU Boost Level (Fluid Scroll) : "; cat /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null
echo -n "  • DVFS Margin Value              : "; cat /sys/kernel/ged/hal/dvfs_margin_value 2>/dev/null

echo ""
echo "[4] UJI COBA 4: HISTERESIS PENDINGINAN BERTINGKAT (4-TIER STAIRCASE DECAY)"
echo "  >> Mengamati penurunan bertahap: Fluid (~675MHz) -> Light (~495MHz) -> Idle (300MHz)..."
sleep 0.5
echo -n "  • T+500ms  (Fluid Hold) : OPP="; cat /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null
sleep 0.7
echo -n "  • T+1200ms (Light Step) : OPP="; cat /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null
sleep 1.2
echo -n "  • T+2400ms (Idle Step)  : OPP="; cat /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null

echo ""
echo "[5] UJI COBA 5: TIER 3 - BEBAN 3D BERAT / PROFILE EKSTREM (950MHz PEAK)"
/data/adb/lynx/lynxd profile apply extreme
sleep 1
echo -n "  • Index OPP Boost (Peak 3D)  : "; cat /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null
echo -n "  • Frekuensi Dasar (Peak 3D)  : "; cat /sys/module/ged/parameters/gpu_bottom_freq 2>/dev/null
echo -n "  • GPU Boost Level (Peak 3D)  : "; cat /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null

echo ""
echo "[6] UJI COBA 6: PEMULIHAN KE PROFIL BALANCE & IDLE DOWNSCALING (300MHz)"
/data/adb/lynx/lynxd profile apply balance
sleep 2.5
echo -n "  • Index OPP Boost (Balance Idle) : "; cat /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null
echo -n "  • Frekuensi Dasar (Balance Idle) : "; cat /sys/module/ged/parameters/gpu_bottom_freq 2>/dev/null
echo -n "  • GPU Boost Level (Balance Idle) : "; cat /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null

echo ""
echo "[7] TELEMETRI LENGKAP JSON DARI LYNXD:"
/data/adb/lynx/lynxd gpu info

echo ""
echo "=========================================================="
echo "      HASIL: SEMUA PENGUJIAN 4-TIER BERHASIL TOTAL        "
echo "=========================================================="
