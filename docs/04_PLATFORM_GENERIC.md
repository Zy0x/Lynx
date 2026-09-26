# 🛡️ Module 04: Generic Linux Platform & Fallback Engine
> **Subsystem**: Generic Fallback HAL (`platforms/generic/`)  
> **Target Chips**: Samsung Exynos, Google Tensor (GS101/201/301), Unisoc/Spreadtrum, Allwinner, Rockchip  

---

## 1. Architectural Blueprint & Safety Model

When Lynx detects an Android device that is neither Qualcomm Snapdragon nor MediaTek Dimensity/Helio, it engages the **Generic Linux Fallback Engine**.

### 1.1 Non-Invasive Safety Rule (Zero Vendor Overlays)
Unlike Qualcomm and MediaTek architectures which require vendor file replacements (dummy thermal engines, `.tp` configs), the Generic Engine enforces a strict rule:
> **Zero Vendor File Injection**: `$MODPATH/system/vendor/` remains completely empty. No proprietary libraries, init `.rc` files, or thermal configs are touched.

This guarantees that Samsung Knox/Exynos devices, Pixel Tensor devices, and budget Unisoc phones **never experience bootloops, display flickering, or camera/Wi-Fi breakage** caused by alien vendor files.

```
+-------------------------------------------------------------------------+
|                        Generic Linux Engine                             |
+--------------------+---------------------+------------------------------+
|  CPUFreq Scaling   |  Virtual Memory     |      Block I/O & Storage     |
| • Policy Freq Bounds| • Memory Compaction | • Elevators (mq-deadline)    |
| • Governor Tunables| • Swappiness Tuning | • Read-ahead Buffering       |
| • WQ Power Eff     | • Dirty Page Ratios | • Ext4 / F2FS Fstrim         |
+--------------------+---------------------+------------------------------+
```

---

## 2. Kernel Node & Sysfs Reference Matrix

| Kernel Node | Balanced Value | Performance Value | Function |
| :--- | :--- | :--- | :--- |
| `/sys/devices/system/cpu/cpufreq/policy*/scaling_governor` | `schedutil` | `performance` / `schedutil` | CPU governor selection. |
| `/sys/devices/system/cpu/cpufreq/policy*/scaling_max_freq` | Hardware Max | Hardware Max | Maximum cluster frequency in kHz. |
| `/sys/devices/system/cpu/cpufreq/policy*/scaling_min_freq` | Hardware Min | 70% Max Freq | Minimum cluster floor frequency. |
| `/sys/module/workqueue/parameters/power_efficient` | `Y` | `N` | Kernel workqueue power saving mode. |
| `/proc/sys/vm/swappiness` | `60` | `30` | Tendency to swap anonymous memory pages. |
| `/proc/sys/vm/vfs_cache_pressure` | `100` | `80` | Tendency to reclaim directory/inode cache. |
| `/proc/sys/vm/dirty_ratio` | `20` | `10` | Percentage of RAM for dirty memory pages. |
| `/proc/sys/vm/dirty_background_ratio` | `10` | `5` | Background writeback trigger ratio. |

---

## 3. High Performance Implementation (`perf.sh`)

```bash
#!/system/bin/sh
# Lynx Universal - Generic Linux Performance Profile

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# 1. Elevate CPU Minimum Frequencies (Eliminate Latency Spikes)
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    [ -z "$max_freq" ] && continue

    write_node "$max_freq" "$policy/scaling_max_freq"
    
    # Set minimum floor to ~60-70% of maximum for instant touch response
    elevated_min=$((max_freq * 60 / 100))
    write_node "$elevated_min" "$policy/scaling_min_freq"

    # Boost schedutil if active
    if [ -d "$policy/schedutil" ]; then
        write_node "0" "$policy/schedutil/up_rate_limit_us"
        write_node "5000" "$policy/schedutil/down_rate_limit_us"
        write_node "$max_freq" "$policy/schedutil/hispeed_freq"
        write_node "80" "$policy/schedutil/hispeed_load"
    fi
done

# 2. Kernel Workqueues & Schedulers
write_node "N" "/sys/module/workqueue/parameters/power_efficient"
write_node "0" "/proc/sys/kernel/sched_tunable_scaling"
write_node "1" "/proc/sys/kernel/sched_autogroup_enabled"

# 3. Virtual Memory Optimization for Gaming
write_node "30" "/proc/sys/vm/swappiness"
write_node "80" "/proc/sys/vm/vfs_cache_pressure"
write_node "10" "/proc/sys/vm/dirty_ratio"
write_node "5" "/proc/sys/vm/dirty_background_ratio"
```

---

## 4. Balanced Implementation (`balance.sh`)

```bash
#!/system/bin/sh
# Lynx Universal - Generic Linux Balanced Profile

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# 1. Restore Hardware Frequency Bounds
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    min_freq=$(cat "$policy/cpuinfo_min_freq" 2>/dev/null)
    max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    [ -n "$min_freq" ] && write_node "$min_freq" "$policy/scaling_min_freq"
    [ -n "$max_freq" ] && write_node "$max_freq" "$policy/scaling_max_freq"

    if [ -d "$policy/schedutil" ]; then
        write_node "500" "$policy/schedutil/up_rate_limit_us"
        write_node "20000" "$policy/schedutil/down_rate_limit_us"
        write_node "99" "$policy/schedutil/hispeed_load"
    fi
done

# 2. Restore Energy Efficiency
write_node "Y" "/sys/module/workqueue/parameters/power_efficient"

# 3. Standard Linux VM Parameters
write_node "60" "/proc/sys/vm/swappiness"
write_node "100" "/proc/sys/vm/vfs_cache_pressure"
write_node "20" "/proc/sys/vm/dirty_ratio"
write_node "10" "/proc/sys/vm/dirty_background_ratio"
```
