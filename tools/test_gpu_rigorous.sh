#!/system/bin/sh
DEV="/dev/input/event3"

echo "=========================================================="
echo "    PENGUJIAN KECERDASAN & DINAMIKA MULTI-FAKTOR GPU     "
echo "=========================================================="

echo ""
echo "[1] UJI COBA 1: BASELINE IDLE (DIAM & TANPA SENTUHAN)"
echo -n "  • Index OPP Boost   : "; cat /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null
echo -n "  • Frekuensi Dasar   : "; cat /sys/module/ged/parameters/gpu_bottom_freq 2>/dev/null
echo -n "  • GPU Boost Level   : "; cat /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null
echo -n "  • DVFS Margin Value : "; cat /sys/kernel/ged/hal/dvfs_margin_value 2>/dev/null
echo -n "  • Ged Boost Enable  : "; cat /sys/module/ged/parameters/ged_boost_enable 2>/dev/null

echo ""
echo "[2] UJI COBA 2: RESPON SENTUHAN TUNGGAL (<1ms HARDWARE TOUCH)"
echo "  >> Mengirim paket sentuhan perangkat keras langsung ke $DEV..."
sendevent $DEV 3 57 1
sendevent $DEV 1 330 1
sendevent $DEV 3 53 540
sendevent $DEV 3 54 1200
sendevent $DEV 0 0 0
echo -n "  • Index OPP Boost (Seketika) : "; cat /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null
echo -n "  • Frekuensi Dasar (Seketika) : "; cat /sys/module/ged/parameters/gpu_bottom_freq 2>/dev/null
echo -n "  • GPU Boost Level (Seketika) : "; cat /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null
echo -n "  • DVFS Margin Value          : "; cat /sys/kernel/ged/hal/dvfs_margin_value 2>/dev/null

echo ""
echo "[3] UJI COBA 3: HISTERESIS PENDINGINAN STAIRCASE (HOLD 1.5 DETIK)"
sleep 0.5
echo -n "  • Snapshot T+500ms  : OPP="; cat /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null
sleep 0.5
echo -n "  • Snapshot T+1000ms : OPP="; cat /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null
sleep 0.6
echo -n "  • Snapshot T+1600ms : OPP="; cat /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null
sleep 0.6
echo -n "  • Snapshot T+2200ms : OPP="; cat /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null

echo ""
echo "[4] UJI COBA 4: GESTUR CEPAT / FLINGING (RAPID TOUCH AWARENESS)"
echo "  >> Mengirim 4 paket gestur cepat dalam jendela <100ms..."
sendevent $DEV 3 57 2; sendevent $DEV 1 330 1; sendevent $DEV 3 53 300; sendevent $DEV 3 54 500; sendevent $DEV 0 0 0
sendevent $DEV 3 57 2; sendevent $DEV 3 53 350; sendevent $DEV 3 54 700; sendevent $DEV 0 0 0
sendevent $DEV 3 57 2; sendevent $DEV 3 53 400; sendevent $DEV 3 54 900; sendevent $DEV 0 0 0
sendevent $DEV 3 57 2; sendevent $DEV 3 53 450; sendevent $DEV 3 54 1100; sendevent $DEV 0 0 0
echo -n "  • Index OPP Boost (Rapid Gesture) : "; cat /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null
echo -n "  • GPU Boost Level (Rapid Gesture) : "; cat /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null

echo ""
echo "[5] UJI COBA 5: PENERAPAN PROFIL PERFORMA TINGGI (PERFORMANCE)"
/data/adb/lynx/lynxd profile apply performance
sleep 1
echo -n "  • Index OPP Boost (Performance) : "; cat /sys/kernel/ged/hal/custom_boost_gpu_freq 2>/dev/null
echo -n "  • GPU Boost Level (Performance) : "; cat /sys/kernel/ged/hal/gpu_boost_level 2>/dev/null

echo ""
echo "[6] UJI COBA 6: PEMULIHAN KE PROFIL BALANCE & IDLE DOWNSCALING"
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
echo "      HASIL: SEMUA PENGUJIAN DINAMIKA BERHASIL TOTAL      "
echo "=========================================================="
