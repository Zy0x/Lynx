#!/system/bin/sh
echo "=== CURRENT FOCUSED WINDOW ==="
dumpsys window | grep -E "mCurrentFocus|mFocusedApp"

echo "=== CPU FREQUENCIES AND GOVERNORS ==="
for i in 0 1 2 3 4 5 6 7; do
    cur=$(cat /sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq 2>/dev/null)
    gov=$(cat /sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor 2>/dev/null)
    min=$(cat /sys/devices/system/cpu/cpu$i/cpufreq/scaling_min_freq 2>/dev/null)
    max=$(cat /sys/devices/system/cpu/cpu$i/cpufreq/scaling_max_freq 2>/dev/null)
    echo "CPU$i: cur=${cur} kHz (min=${min}, max=${max}) gov=${gov}"
done

echo "=== MEDIATEK PPM STATUS ==="
if [ -d /proc/ppm ]; then
    cat /proc/ppm/policy_status 2>/dev/null
    echo "ppm root_cluster: $(cat /proc/ppm/root_cluster 2>/dev/null)"
fi

echo "=== GPU FREQUENCY AND GED ==="
if [ -d /proc/ged ]; then
    echo "GED hal current freq: $(cat /proc/ged/hal/current_freqency 2>/dev/null)"
    echo "GED gpu boost: $(cat /proc/ged/hal/custom_upbound_gpu_freq 2>/dev/null)"
fi
for f in /sys/class/misc/mali0/device/cur_freq /sys/kernel/debug/mali/gpu_utilization; do
    [ -f "$f" ] && echo "$f: $(cat $f 2>/dev/null)"
done

echo "=== DISPLAY REFRESH RATE ==="
dumpsys SurfaceFlinger | grep -E "fps|refresh-rate|displayModes|activeMode" | head -n 10
dumpsys display | grep -E "mDisplayModes|mActiveModeId|mRefreshRate|fps" | head -n 10

echo "=== THERMAL STATUS ==="
for z in /sys/class/thermal/thermal_zone*; do
    type=$(cat $z/type 2>/dev/null)
    temp=$(cat $z/temp 2>/dev/null)
    if [ ! -z "$temp" ]; then
        echo "$z ($type): $temp"
    fi
done | head -n 20

echo "=== TOP PROCESSES (CPU USAGE) ==="
top -n 1 -m 15 -s 9

echo "=== GFXINFO COM.NOIR.LYNX ==="
dumpsys gfxinfo com.noir.lynx | grep -A 25 "Total frames rendered"

echo "=== CHOREOGRAPHER SKIPPED FRAMES (LAST 50 LOGS) ==="
logcat -d -t 1500 | grep -E "Choreographer.*Skipped|lynx|recomposition|dropped" | tail -n 25
