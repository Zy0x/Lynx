#!/system/bin/sh
for n in \
    /sys/kernel/ged/hal/* \
    /sys/kernel/fpsgo/common/* \
    /sys/kernel/fpsgo/fbt/* \
    /sys/kernel/fpsgo/fstb/* \
    /proc/ppm/enabled \
    /proc/ppm/mode \
    /sys/devices/system/cpu/eas/* \
    /proc/hps/* \
    /sys/devices/system/cpu/cpu*/core_ctl/* \
    /sys/module/lowmemorykiller/parameters/* \
    /sys/module/process_reclaim/parameters/* \
    /sys/class/power_supply/main/* \
    /sys/class/power_supply/bms/* \
    /sys/kernel/sound_control/* \
    /sys/class/misc/soundcontrol/*; do
    [ -f "$n" ] || continue
    chmod 644 "$n" 2>/dev/null
    if [ -w "$n" ] && [ -r "$n" ]; then
        v=$(head -n1 "$n" 2>/dev/null | tr -d '\r\n')
        [ -n "$v" ] && echo "FOUND: $n = $v"
    fi
done
