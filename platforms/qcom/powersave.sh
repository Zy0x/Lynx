#!/system/bin/sh
# Lynx Universal - Qualcomm Snapdragon Powersave Profile
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)

MODDIR="/data/adb/modules/Lynx"

write_node() {
    [ -e "$2" ] || return 0
    echo "$1" > "$2" 2>/dev/null && return 0
    chmod 666 "$2" 2>/dev/null
    echo "$1" > "$2" 2>/dev/null
}

# ── 1. Cap CPU Frequencies to 55% of Max Clock ───────────────────────
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    min_freq=$(cat "$policy/cpuinfo_min_freq" 2>/dev/null)
    max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    [ -n "$min_freq" ] && write_node "$min_freq" "$policy/scaling_min_freq"

    if [ -n "$max_freq" ]; then
        power_cap=$(( max_freq * 55 / 100 ))
        [ "$power_cap" -gt "$min_freq" ] && write_node "$power_cap" "$policy/scaling_max_freq"
    fi

    pol_id=$(basename "$policy" | tr -dc '0-9')
    # Slow ramp-up and quick drop (asymmetric per-cluster)
    schedutil="$policy/schedutil"
    if [ -d "$schedutil" ]; then
        if [ "$pol_id" = "0" ]; then
            # Little Cluster (Silver): Jeda evaluasi naik 10ms, cepat turun 1ms
            write_node "10000" "$schedutil/up_rate_limit_us"
            write_node "1000" "$schedutil/down_rate_limit_us"
        else
            # Big/Prime Cluster (Gold/Kryo): Sangat enggan naik (20ms), langsung turun (500µs)
            write_node "20000" "$schedutil/up_rate_limit_us"
            write_node "500" "$schedutil/down_rate_limit_us"
        fi
        write_node "-6" "$schedutil/sched_load_boost"
        write_node "99" "$schedutil/hispeed_load"
        write_node "0" "$schedutil/pl"
        write_node "0" "$schedutil/iowait_boost_enable"
    fi
    write_node "-6" "$policy/sched_load_boost"
done

# ── 2. Devfreq Memory Bus Lowest Clocks / Powersave ──────────────────
for dev in /sys/class/devfreq/*; do
    [ -d "$dev" ] || continue
    gov_node="$dev/governor"
    case "$dev" in
        *latfloor*)
            write_node "compute" "$gov_node"
            ;;
        *ufshc*|*cpubw*|*gpubw*|*llccbw*|*l3-cpu*|*bus_ddr*|*cpu-ddr-bw*|*ddr-bw*|*cpu-ddr-lat*|*memlat-cpu*)
            write_node "powersave" "$gov_node"
            ;;
    esac
    freq_table="$dev/available_frequencies"
    if [ -s "$freq_table" ]; then
        lowest_freq=$(tr -s ' ' '\n' < "$freq_table" 2>/dev/null | sort -n | head -n 1)
        [ -n "$lowest_freq" ] && write_node "$lowest_freq" "$dev/min_freq"
    fi
done

# ── 3. Adreno KGSL Power Saver ───────────────────────────────────────
KGSL="/sys/class/kgsl/kgsl-3d0"
if [ -d "$KGSL" ]; then
    num_pwr=$(cat "$KGSL/num_pwrlevels" 2>/dev/null)
    [ -n "$num_pwr" ] && [ "$num_pwr" -gt 1 ] && write_node "$((num_pwr - 1))" "$KGSL/min_pwrlevel"
    write_node "0" "$KGSL/devfreq/adreno_boost"
    write_node "1" "$KGSL/throttling"
    write_node "0" "$KGSL/force_bus_on"
    write_node "0" "$KGSL/force_clk_on"
    write_node "0" "$KGSL/force_rail_on"
    write_node "20" "$KGSL/idle_timer"

    avail_govs=$(cat "$KGSL/devfreq/available_governors" 2>/dev/null)
    if echo "$avail_govs" | grep -q "powersave"; then
        write_node "powersave" "$KGSL/devfreq/governor"
    elif echo "$avail_govs" | grep -q "msm-adreno-tz"; then
        write_node "msm-adreno-tz" "$KGSL/devfreq/governor"
    fi
fi

# ── 4. Workqueue & Thermal Reset ─────────────────────────────────────
write_node "Y" "/sys/module/workqueue/parameters/power_efficient"

if [ -f "$MODDIR/platforms/qcom/thermal.sh" ]; then
    sh "$MODDIR/platforms/qcom/thermal.sh" enable >/dev/null 2>&1
fi

write_node "0" "/dev/stune/schedtune.boost"
write_node "0" "/dev/stune/schedtune.prefer_high_cap"
write_node "0" "/dev/stune/foreground/schedtune.boost"
write_node "0" "/dev/stune/foreground/schedtune.prefer_high_cap"
write_node "0" "/dev/stune/top-app/schedtune.boost"
write_node "0" "/dev/stune/top-app/schedtune.prefer_high_cap"

# Enforce 60Hz Screen Refresh Rate for Power Conservation
cur_min_rr=$(settings get system min_refresh_rate 2>/dev/null)
cur_peak_rr=$(settings get system peak_refresh_rate 2>/dev/null)
if [ ! -f "/dev/lynx_orig_min_rr" ] && [ -n "$cur_min_rr" ] && [ "$cur_min_rr" != "null" ]; then
    echo "$cur_min_rr" > /dev/lynx_orig_min_rr
fi
if [ ! -f "/dev/lynx_orig_peak_rr" ] && [ -n "$cur_peak_rr" ] && [ "$cur_peak_rr" != "null" ]; then
    echo "$cur_peak_rr" > /dev/lynx_orig_peak_rr
fi
settings put system min_refresh_rate 60.0 2>/dev/null
settings put system peak_refresh_rate 60.0 2>/dev/null

# Revert Unity Trick if applied
for cpu in 0 1 2 3 4 5 6 7; do
    path="/sys/devices/system/cpu/cpu${cpu}"
    [ -e "$path/cpufreq/cpuinfo_max_freq" ] && chmod 444 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
    [ -e "$path/cpu_capacity" ] && chmod 444 "$path/cpu_capacity" 2>/dev/null
done
