#!/system/bin/sh
echo "=== GPU TELEMETRY ==="
if [ -d /sys/class/kgsl/kgsl-3d0 ]; then
    echo "PLATFORM: adreno"
    cat /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq 2>/dev/null
elif [ -d /proc/gpufreq ] || [ -d /sys/module/ged ]; then
    echo "PLATFORM: mali_ged"
    cat /proc/gpufreq/gpufreq_var_dump 2>/dev/null | grep -E 'freq|gpu_loading' | head -n 3
fi

echo "=== TOP WAKELOCKS ==="
for w in /sys/class/wakeup/wakeup*; do
    [ -d "$w" ] || continue
    n=$(cat "$w/name" 2>/dev/null)
    c=$(cat "$w/active_count" 2>/dev/null)
    t=$(cat "$w/prevent_suspend_time_ms" 2>/dev/null)
    if [ -n "$n" ] && [ "$c" -gt 0 ] 2>/dev/null; then
        echo "$n|$c|${t:-0}ms"
    fi
done | sort -t"|" -k3 -nr | head -n 8

echo "=== THERMAL ZONES ==="
for tz in /sys/class/thermal/thermal_zone*; do
    [ -d "$tz" ] || continue
    type=$(cat "$tz/type" 2>/dev/null)
    temp=$(cat "$tz/temp" 2>/dev/null)
    [ -n "$type" ] && [ -n "$temp" ] && echo "$type: $temp"
done | head -n 8

echo "=== GOVERNOR TUNABLES ==="
for p in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$p" ] || continue
    gov=$(cat "$p/scaling_governor" 2>/dev/null)
    echo "Policy $(basename $p): $gov"
    if [ -d "$p/schedutil" ]; then
        ls "$p/schedutil"
    fi
done
