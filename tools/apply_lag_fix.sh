#!/system/bin/sh
# Lynx Universal — Interactive UI & Memory Lag Fix for Charging Window
LOG="/data/local/tmp/lag_fix.log"
echo "=== LYNX LAG FIX APPLIED AT $(date) ===" > "$LOG"

# ==========================================
# SOLUSI 1: JINAKKAN BACKGROUND & CPUMASK
# ==========================================
echo "[1] Mengatur Cpuset & Isolasi Background..." >> "$LOG"
for root in /dev/cpuset /sys/fs/cgroup/cpuset; do
    [ -d "$root" ] || continue
    # Foreground dan Top-App bebas memakai seluruh core (0-7)
    echo "0-7" > "$root/top-app/cpus" 2>/dev/null
    echo "0-7" > "$root/foreground/cpus" 2>/dev/null
    echo "0-7" > "$root/foreground_window/cpus" 2>/dev/null
    
    # Background, Dex2oat, dan Restricted DIKUNCI di Little Cores (0-1)
    echo "0-1" > "$root/background/cpus" 2>/dev/null
    echo "0-2" > "$root/system-background/cpus" 2>/dev/null
    echo "0-1" > "$root/restricted/cpus" 2>/dev/null
    echo "0-1" > "$root/dex2oat/cpus" 2>/dev/null
done

# Responsivitas Interaktif CPU Schedutil
echo "[1b] Tuning CPU Schedutil & Touch Boost..." >> "$LOG"
for s in /sys/devices/system/cpu/cpu*/cpufreq/schedutil; do
    [ -d "$s" ] || continue
    echo 0 > "$s/up_rate_limit_us" 2>/dev/null
    echo 15000 > "$s/down_rate_limit_us" 2>/dev/null
done

# Qualcomm CPU Input Boost
if [ -d /sys/module/cpu_boost/parameters ]; then
    echo "0:1113600 4:1401600" > /sys/module/cpu_boost/parameters/input_boost_freq 2>/dev/null
    echo 64 > /sys/module/cpu_boost/parameters/input_boost_ms 2>/dev/null
fi

# Turunkan prioritas GMS dan background sync ke nice 19 (SCHED_IDLE / low priority)
for p in $(pgrep -f "com.google.android.gms"); do
    renice -n 15 -p "$p" 2>/dev/null
done
for p in $(pgrep -f "com.facebook.katana"); do
    renice -n 19 -p "$p" 2>/dev/null
done

# ==========================================
# SOLUSI 2: PEMBERSIHAN MEMORI & ZRAM COMPACT
# ==========================================
echo "[2] Memulai Pembersihan Memori & ZRAM..." >> "$LOG"

# Evict proses cached yang idle (oom_score_adj >= 900)
EVICTED=0
for proc in /proc/[0-9]*; do
    pid=${proc##*/}
    [ "$pid" -le 1000 ] && continue
    if [ -f "$proc/oom_score_adj" ]; then
        score=$(cat "$proc/oom_score_adj" 2>/dev/null)
        if [ -n "$score" ] && [ "$score" -ge 900 ]; then
            cmdline=$(cat "$proc/cmdline" 2>/dev/null | tr '\0' ' ')
            case "$cmdline" in
                *lynx*|*system*|*launcher*|*inputmethod*|*keyboard*) continue ;;
                *)
                    kill -9 "$pid" 2>/dev/null && EVICTED=$((EVICTED + 1))
                    ;;
            esac
        fi
    fi
done
echo "Proses cached dievakuasi: $EVICTED" >> "$LOG"

# Drop caches dan compact memory
sync
echo 3 > /proc/sys/vm/drop_caches 2>/dev/null
echo 1 > /proc/sys/vm/compact_memory 2>/dev/null

# Compact ZRAM jika ada
if [ -f /sys/block/zram0/compact ]; then
    echo 1 > /sys/block/zram0/compact 2>/dev/null
    echo "ZRAM Compact selesai" >> "$LOG"
fi

# Tuning Parameter Kernel VM agar bebas dari thrashing ZRAM
echo 50 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null
echo 60 > /proc/sys/vm/swappiness 2>/dev/null
echo 100 > /proc/sys/vm/watermark_scale_factor 2>/dev/null
echo 16384 > /proc/sys/vm/min_free_kbytes 2>/dev/null
echo 524288 > /proc/sys/vm/max_map_count 2>/dev/null

echo "=== SELESAI DENGAN SUKSES ===" >> "$LOG"
cat "$LOG"
