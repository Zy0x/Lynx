#!/system/bin/sh
echo "=== CPU CORES ONLINE ==="
cat /sys/devices/system/cpu/online

echo "=== CPU FREQUENCIES (KHz) ==="
for c in /sys/devices/system/cpu/cpu[0-7]; do
    name=$(basename "$c")
    cur=$(cat "$c/cpufreq/scaling_cur_freq" 2>/dev/null)
    max=$(cat "$c/cpufreq/scaling_max_freq" 2>/dev/null)
    gov=$(cat "$c/cpufreq/scaling_governor" 2>/dev/null)
    echo "$name: cur=$cur | max=$max | gov=$gov"
done

echo "=== CPU COOLING DEVICES & ISOLATION ==="
for cd in /sys/class/thermal/cooling_device*; do
    type=$(cat "$cd/type" 2>/dev/null)
    case "$type" in
        *cpu*|*isolate*|*devfreq*)
            name=$(basename "$cd")
            cur=$(cat "$cd/cur_state" 2>/dev/null)
            max=$(cat "$cd/max_state" 2>/dev/null)
            echo "$name ($type): cur=$cur / max=$max"
            ;;
    esac
done

echo "=== TOP CPU CONSUMING PROCESSES ==="
top -b -n 1 | head -n 18
