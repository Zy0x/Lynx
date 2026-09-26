#!/system/bin/sh
# Lynx Kernel Manager (LKM) - High-Frequency Telemetry Fast-Path
# Pure POSIX /system/bin/sh compliance (Runs in <10ms)
# Emits real-time hardware telemetry as single-line JSON

read_val() {
    [ -r "$1" ] && cat "$1" 2>/dev/null || echo "$2"
}

# 1. CPU Frequencies (cpu0 - cpu7)
cpu_freqs=""
for i in 0 1 2 3 4 5 6 7; do
    node="/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq"
    freq=$(cat "$node" 2>/dev/null || echo 0)
    [ -n "$cpu_freqs" ] && cpu_freqs="$cpu_freqs,"
    cpu_freqs="${cpu_freqs}${freq}"
done

# 2. GPU Frequency & Load
gpu_freq=0
gpu_busy=0

# A. Qualcomm Adreno
if [ -f "/sys/class/kgsl/kgsl-3d0/gpuclk" ]; then
    gpu_freq=$(cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null || echo 0)
    # Convert Hz to MHz if necessary (> 1,000,000)
    [ "$gpu_freq" -gt 1000000 ] 2>/dev/null && gpu_freq=$(( gpu_freq / 1000000 ))
    if [ -f "/sys/class/kgsl/kgsl-3d0/gpubusy" ]; then
        busy_data=$(cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null)
        busy_time=$(echo "$busy_data" | awk '{print $1}')
        total_time=$(echo "$busy_data" | awk '{print $2}')
        if [ -n "$total_time" ] && [ "$total_time" -gt 0 ] 2>/dev/null; then
            gpu_busy=$(( busy_time * 100 / total_time ))
        fi
    fi
# B. MediaTek Mali
elif [ -f "/proc/gpufreq/gpufreq_opp_freq" ]; then
    gpu_freq=$(grep 'freq =' /proc/gpufreq/gpufreq_opp_freq 2>/dev/null | head -n1 | awk '{print $4}' | tr -d ',')
    [ -z "$gpu_freq" ] && gpu_freq=0
    [ "$gpu_freq" -gt 10000 ] 2>/dev/null && gpu_freq=$(( gpu_freq / 1000 ))
    if [ -f "/sys/module/ged/parameters/gpu_loading" ]; then
        gpu_busy=$(cat /sys/module/ged/parameters/gpu_loading 2>/dev/null || echo 0)
    elif [ -f "/sys/class/misc/mali0/device/utilisation" ]; then
        gpu_busy=$(cat /sys/class/misc/mali0/device/utilisation 2>/dev/null || echo 0)
    fi
fi

# 3. Battery Statistics
batt_temp_raw=$(read_val "/sys/class/power_supply/battery/temp" "300")
batt_temp=$(awk -v t="$batt_temp_raw" 'BEGIN {printf "%.1f", t / 10}')
batt_level=$(read_val "/sys/class/power_supply/battery/capacity" "50")

batt_cur_raw=$(read_val "/sys/class/power_supply/battery/current_now" "0")
batt_cur_ma=$(( batt_cur_raw / 1000 ))

batt_volt_raw=$(read_val "/sys/class/power_supply/battery/voltage_now" "4000000")
batt_volt_mv=$(( batt_volt_raw / 1000 ))

# 4. RAM Utilization (MB)
ram_total_kb=$(awk '/MemTotal/ {print $2}' /proc/meminfo 2>/dev/null || echo 4194304)
ram_avail_kb=$(awk '/MemAvailable/ {print $2}' /proc/meminfo 2>/dev/null || echo 2097152)
ram_total_mb=$(( ram_total_kb / 1024 ))
ram_used_mb=$(( (ram_total_kb - ram_avail_kb) / 1024 ))

# Integer sanity checks to prevent printf format errors
echo "$gpu_freq" | grep -qE '^[0-9]+$' || gpu_freq=0
echo "$gpu_busy" | grep -qE '^[0-9]+$' || gpu_busy=0
echo "$batt_level" | grep -qE '^[0-9]+$' || batt_level=50
echo "$batt_cur_ma" | grep -qE '^-?[0-9]+$' || batt_cur_ma=0
echo "$batt_volt_mv" | grep -qE '^[0-9]+$' || batt_volt_mv=4000
echo "$ram_used_mb" | grep -qE '^[0-9]+$' || ram_used_mb=0
echo "$ram_total_mb" | grep -qE '^[0-9]+$' || ram_total_mb=4096

# 5. Output Unified JSON
printf '{"cpu":[%s],"gpu_freq":%d,"gpu_busy":%d,"temp":%s,"batt_level":%d,"batt_current_ma":%d,"batt_volt_mv":%d,"ram_used_mb":%d,"ram_total_mb":%d}\n' \
    "$cpu_freqs" "$gpu_freq" "$gpu_busy" "$batt_temp" "$batt_level" "$batt_cur_ma" "$batt_volt_mv" "$ram_used_mb" "$ram_total_mb"

