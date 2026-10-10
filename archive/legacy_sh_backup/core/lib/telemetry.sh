#!/system/bin/sh
# Lynx Kernel Manager (LKM) - High-Frequency Telemetry Fast-Path
# Pure POSIX /system/bin/sh compliance (Runs in <10ms)
# Emits real-time hardware telemetry as single-line JSON

read_val() {
    [ -r "$1" ] && cat "$1" 2>/dev/null || echo "$2"
}

# 1. CPU Frequencies (Universal dynamic topology - strict numeric order for 10-core+ SoCs)
cpu_freqs=""
for idx in 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
    path="/sys/devices/system/cpu/cpu$idx"
    [ -d "$path" ] || continue
    online=1
    [ -f "$path/online" ] && online=$(cat "$path/online" 2>/dev/null || echo 1)
    if [ "$online" = "0" ]; then
        freq=0
    else
        node="$path/cpufreq/scaling_cur_freq"
        freq=$(cat "$node" 2>/dev/null || echo 0)
    fi
    [ -n "$cpu_freqs" ] && cpu_freqs="$cpu_freqs,"
    cpu_freqs="${cpu_freqs}${freq}"
done

# 2. GPU Frequency & Load
gpu_freq=0
gpu_busy=0

# A. Qualcomm Adreno
if [ -d "/sys/class/kgsl/kgsl-3d0" ]; then
    if [ -f "/sys/class/kgsl/kgsl-3d0/gpuclk" ]; then
        gpu_freq=$(cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null || echo 0)
    elif [ -f "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq" ]; then
        gpu_freq=$(cat /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq 2>/dev/null || echo 0)
    fi
    [ "$gpu_freq" -gt 1000000 ] 2>/dev/null && gpu_freq=$(( gpu_freq / 1000000 ))
    [ "$gpu_freq" -gt 10000 ] 2>/dev/null && gpu_freq=$(( gpu_freq / 1000 ))

    if [ -f "/sys/class/kgsl/kgsl-3d0/gpubusy" ]; then
        busy_data=$(cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null)
        busy_time=$(echo "$busy_data" | awk '{print $1}')
        total_time=$(echo "$busy_data" | awk '{print $2}')
        if [ -n "$total_time" ] && [ "$total_time" -gt 0 ] 2>/dev/null; then
            gpu_busy=$(( busy_time * 100 / total_time ))
        fi
    elif [ -f "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage" ]; then
        gpu_busy=$(cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null | tr -dc 0-9 || echo 0)
    fi
# B. MediaTek Mali / GED
else
    if [ -r "/proc/gpufreq/gpufreq_var_dump" ]; then
        rf=$(grep -m1 -oE '\(real\) freq: [0-9]+' /proc/gpufreq/gpufreq_var_dump 2>/dev/null | awk '{print $NF}')
        [ -n "$rf" ] && [ "$rf" -gt 0 ] 2>/dev/null && gpu_freq=$(( rf / 1000 ))
    fi
    if [ -z "$gpu_freq" ] || [ "$gpu_freq" -eq 0 ]; then
        if [ -r "/proc/gpufreq/gpufreq_fixed_freq_volt" ]; then
            ff=$(grep -m1 -oE 'g_fixed_freq = [0-9]+' /proc/gpufreq/gpufreq_fixed_freq_volt 2>/dev/null | awk '{print $NF}')
            [ -n "$ff" ] && [ "$ff" -gt 0 ] 2>/dev/null && gpu_freq=$(( ff / 1000 ))
        fi
    fi
    if [ -z "$gpu_freq" ] || [ "$gpu_freq" -eq 0 ]; then
        if [ -r "/sys/kernel/ged/hal/current_freqency" ]; then
            gpu_freq=$(cat /sys/kernel/ged/hal/current_freqency 2>/dev/null | awk '{if(NF>=2) print int($2/1000); else print int($1/1000)}')
        fi
    fi
    if [ -z "$gpu_freq" ] || [ "$gpu_freq" -eq 0 ]; then
        if [ -r "/proc/gpufreq/gpufreq_opp_freq" ]; then
            gpu_freq=$(grep 'freq =' /proc/gpufreq/gpufreq_opp_freq 2>/dev/null | head -n1 | awk '{print $4}' | tr -dc 0-9)
            [ -n "$gpu_freq" ] && [ "$gpu_freq" -gt 10000 ] 2>/dev/null && gpu_freq=$(( gpu_freq / 1000 ))
        fi
    fi

    if [ -r "/sys/kernel/ged/hal/gpu_utilization" ]; then
        gpu_busy=$(cat /sys/kernel/ged/hal/gpu_utilization 2>/dev/null | awk '{print int($1)}')
    elif [ -f "/sys/module/ged/parameters/gpu_loading" ]; then
        gpu_busy=$(cat /sys/module/ged/parameters/gpu_loading 2>/dev/null | tr -dc 0-9 || echo 0)
    elif [ -f "/sys/class/misc/mali0/device/utilisation" ]; then
        gpu_busy=$(cat /sys/class/misc/mali0/device/utilisation 2>/dev/null | tr -dc 0-9 || echo 0)
    fi
fi

# 3. Battery Statistics
batt_temp_raw=$(read_val "/sys/class/power_supply/battery/temp" "")
[ -z "$batt_temp_raw" ] || [ "$batt_temp_raw" = "0" ] && batt_temp_raw=$(read_val "/sys/class/power_supply/bms/temp" "350")
batt_temp_val=$(echo "$batt_temp_raw" | tr -dc 0-9)
if [ -n "$batt_temp_val" ] && [ "$batt_temp_val" -gt 1000 ] 2>/dev/null; then
    batt_temp=$(awk -v t="$batt_temp_val" 'BEGIN {printf "%.1f", t / 1000}')
elif [ -n "$batt_temp_val" ] && [ "$batt_temp_val" -gt 100 ] 2>/dev/null; then
    batt_temp=$(awk -v t="$batt_temp_val" 'BEGIN {printf "%.1f", t / 10}')
else
    batt_temp="35.0"
fi

batt_level=$(read_val "/sys/class/power_supply/battery/capacity" "")
[ -z "$batt_level" ] && batt_level=$(read_val "/sys/class/power_supply/bms/capacity" "50")

batt_status=$(read_val "/sys/class/power_supply/battery/status" "")
[ -z "$batt_status" ] && batt_status=$(read_val "/sys/class/power_supply/bms/status" "Discharging")
[ -z "$batt_status" ] && batt_status="Discharging"

batt_cur_raw=$(read_val "/sys/class/power_supply/battery/current_now" "")
[ -z "$batt_cur_raw" ] || [ "$batt_cur_raw" = "0" ] && batt_cur_raw=$(read_val "/sys/class/power_supply/battery/BatteryAverageCurrent" "")
[ -z "$batt_cur_raw" ] || [ "$batt_cur_raw" = "0" ] && batt_cur_raw=$(read_val "/sys/class/power_supply/bms/current_now" "")
[ -z "$batt_cur_raw" ] || [ "$batt_cur_raw" = "0" ] && batt_cur_raw=$(read_val "/sys/class/power_supply/battery/current_avg" "0")
batt_cur_ma=$batt_cur_raw
if [ -n "$batt_cur_raw" ]; then
    abs_cur=${batt_cur_raw#-}
    if [ "$abs_cur" -gt 10000 ] 2>/dev/null; then
        batt_cur_ma=$(( batt_cur_raw / 1000 ))
    fi
fi

batt_volt_raw=$(read_val "/sys/class/power_supply/battery/voltage_now" "")
[ -z "$batt_volt_raw" ] || [ "$batt_volt_raw" = "0" ] && batt_volt_raw=$(read_val "/sys/class/power_supply/bms/voltage_now" "4000000")
abs_volt=${batt_volt_raw#-}
if [ "$abs_volt" -gt 100000 ] 2>/dev/null; then
    batt_volt_mv=$(( batt_volt_raw / 1000 ))
else
    batt_volt_mv=$batt_volt_raw
fi

# 4. RAM & ZRAM Utilization (MB)
ram_total_kb=$(awk '/MemTotal:/ {print $2}' /proc/meminfo 2>/dev/null)
[ -z "$ram_total_kb" ] && ram_total_kb=4194304

ram_avail_kb=$(awk '/MemAvailable:/ {print $2}' /proc/meminfo 2>/dev/null)
if [ -z "$ram_avail_kb" ]; then
    mf=$(awk '/MemFree:/ {print $2}' /proc/meminfo 2>/dev/null)
    mb=$(awk '/Buffers:/ {print $2}' /proc/meminfo 2>/dev/null)
    mc=$(awk '/^Cached:/ {print $2}' /proc/meminfo 2>/dev/null)
    [ -z "$mf" ] && mf=0
    [ -z "$mb" ] && mb=0
    [ -z "$mc" ] && mc=0
    ram_avail_kb=$(( mf + mb + mc ))
fi
[ -z "$ram_avail_kb" ] || [ "$ram_avail_kb" -le 0 ] 2>/dev/null && ram_avail_kb=2097152

ram_total_mb=$(( ram_total_kb / 1024 ))
ram_used_mb=$(( (ram_total_kb - ram_avail_kb) / 1024 ))
[ "$ram_used_mb" -lt 0 ] 2>/dev/null && ram_used_mb=0

swap_total_kb=$(awk '/SwapTotal:/ {print $2}' /proc/meminfo 2>/dev/null)
[ -z "$swap_total_kb" ] && swap_total_kb=0
swap_free_kb=$(awk '/SwapFree:/ {print $2}' /proc/meminfo 2>/dev/null)
[ -z "$swap_free_kb" ] && swap_free_kb=0

swap_total_mb=$(( swap_total_kb / 1024 ))
swap_used_mb=$(( (swap_total_kb - swap_free_kb) / 1024 ))
[ "$swap_used_mb" -lt 0 ] 2>/dev/null && swap_used_mb=0

zram_total_mb=$swap_total_mb
zram_used_mb=$swap_used_mb
if [ -r "/proc/swaps" ]; then
    zram_info=$(grep -m1 'zram' /proc/swaps 2>/dev/null | awk '{print $3, $4}')
    if [ -n "$zram_info" ]; then
        z_tot=$(echo "$zram_info" | awk '{print $1}')
        z_usd=$(echo "$zram_info" | awk '{print $2}')
        [ -n "$z_tot" ] && [ "$z_tot" -gt 0 ] 2>/dev/null && zram_total_mb=$(( z_tot / 1024 ))
        [ -n "$z_usd" ] && [ "$z_usd" -ge 0 ] 2>/dev/null && zram_used_mb=$(( z_usd / 1024 ))
    fi
fi
if [ "$zram_total_mb" -eq 0 ] 2>/dev/null && [ -f "/sys/block/zram0/disksize" ]; then
    z_bytes=$(cat /sys/block/zram0/disksize 2>/dev/null | tr -dc 0-9)
    if [ -n "$z_bytes" ] && [ "$z_bytes" -gt 0 ] 2>/dev/null; then
        zram_total_mb=$(( z_bytes / 1048576 ))
        if [ -f "/sys/block/zram0/mem_used_total" ]; then
            u_bytes=$(cat /sys/block/zram0/mem_used_total 2>/dev/null | tr -dc 0-9)
            [ -n "$u_bytes" ] && zram_used_mb=$(( u_bytes / 1048576 ))
        fi
    fi
fi

# Integer sanity checks to prevent printf format errors
echo "$gpu_freq" | grep -qE '^[0-9]+$' || gpu_freq=0
echo "$gpu_busy" | grep -qE '^[0-9]+$' || gpu_busy=0
echo "$batt_level" | grep -qE '^[0-9]+$' || batt_level=50
echo "$batt_cur_ma" | grep -qE '^-?[0-9]+$' || batt_cur_ma=0
echo "$batt_volt_mv" | grep -qE '^[0-9]+$' || batt_volt_mv=4000
echo "$ram_used_mb" | grep -qE '^[0-9]+$' || ram_used_mb=0
echo "$ram_total_mb" | grep -qE '^[0-9]+$' || ram_total_mb=4096
echo "$zram_used_mb" | grep -qE '^[0-9]+$' || zram_used_mb=0
echo "$zram_total_mb" | grep -qE '^[0-9]+$' || zram_total_mb=0
echo "$swap_used_mb" | grep -qE '^[0-9]+$' || swap_used_mb=0
echo "$swap_total_mb" | grep -qE '^[0-9]+$' || swap_total_mb=0

# 5. Output Unified JSON
printf '{"cpu":[%s],"gpu_freq":%d,"gpu_busy":%d,"temp":"%s","batt_level":%d,"batt_current_ma":%d,"batt_volt_mv":%d,"batt_status":"%s","ram_used_mb":%d,"ram_total_mb":%d,"zram_used_mb":%d,"zram_total_mb":%d,"swap_used_mb":%d,"swap_total_mb":%d}\n' \
    "$cpu_freqs" "$gpu_freq" "$gpu_busy" "$batt_temp" "$batt_level" "$batt_cur_ma" "$batt_volt_mv" "$batt_status" "$ram_used_mb" "$ram_total_mb" "$zram_used_mb" "$zram_total_mb" "$swap_used_mb" "$swap_total_mb"
