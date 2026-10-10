#!/system/bin/sh
for n in \
    /sys/kernel/ged/hal/* \
    /sys/kernel/fpsgo/common/* \
    /sys/kernel/fpsgo/fbt/* \
    /sys/kernel/fpsgo/fstb/* \
    /proc/ppm/enabled \
    /proc/ppm/mode \
    /sys/devices/system/cpu/eas/*; do

    [ -f "$n" ] || continue

    case "$n" in
        *kpi*|*utilization*|*previous_freqency*|*current_freqency*|*BQid*|*table*|*fpsgo_status*|*/info|*systrace_mask*) continue ;;
    esac

    # Writable check with zero fork
    if [ ! -w "$n" ]; then
        chmod 644 "$n" 2>/dev/null
        [ -w "$n" ] || continue
    fi
    [ -r "$n" ] || continue

    val=""
    read -r val < "$n" 2>/dev/null
    [ -z "$val" ] && continue

    echo "$n = $val"
done
