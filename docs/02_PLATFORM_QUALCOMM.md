# ⚡ Module 02: Qualcomm Snapdragon Hardware Abstraction Layer
> **Subsystem**: Qualcomm HAL (`platforms/qcom/`)  
> **Target Chips**: Snapdragon 6xx, 7xx, 8xx, 8 Gen 1/2/3/4 (Adreno 5xx, 6xx, 7xx, 8xx)  

---

## 1. Architectural Blueprint

Qualcomm Snapdragon platforms rely on a tightly integrated hardware fabric managed through specific kernel subsystems:
- **KGSL (Kernel Graphics Support Layer)**: The kernel driver managing Adreno GPU power levels, clock gating, and memory buses.
- **Devfreq Memory Busses**: Dynamic frequency scaling for L3 cache, memory latencies, LLCC (Last Level Cache Controller), and DDR memory controllers (`cpubw`, `gpubw`, `llccbw`).
- **Energy Aware Scheduling (EAS)**: Schedutil governor tunables paired with Qualcomm WALT (Window Assisted Load Tracking) or PELT.
- **Thermal Engine Daemons**: User-space and kernel thermal managers (`thermal-engine`, `mi_thermald`, `thermal_factory`) that aggressively throttle GPU and CPU clocks.

```
+-------------------------------------------------------------------------+
|                        Qualcomm Snapdragon HAL                          |
+--------------------+---------------------+------------------------------+
|    CPU / EAS       |    GPU / KGSL       |     Devfreq Interconnect     |
| • Schedutil Tuners | • Freq Table Parser | • soc:qcom,cpubw             |
| • WALT Boost       | • Bus/Rail Force On | • soc:qcom,gpubw             |
| • Cluster Min/Max  | • Idle Timer 80ms   | • 1d84000.ufshc (UFS 3.1/4.0)|
+--------------------+---------------------+------------------------------+
                                  │
                                  ▼
               +--------------------------------------+
               |    Thermal Mitigation Engine         |
               | • Dummy Thermal Engine (exit 0)      |
               | • mi_thermald / thermal_factory stub |
               | • Thermal Zone Trip Point Overrides  |
               +--------------------------------------+
```

---

## 2. Kernel Node & Sysfs Reference Matrix

### 2.1 Adreno GPU (KGSL) Parameters

All nodes reside under `/sys/class/kgsl/kgsl-3d0/`:

| Sysfs Node | Balanced Value | Performance Value | Function & Engineering Impact |
| :--- | :--- | :--- | :--- |
| `gpu_available_frequencies` | *Read-only* | *Read-only* | Space-separated list of supported frequencies in Hz. |
| `freq_table_mhz` | *Read-only* | *Read-only* | Space-separated list of supported frequencies in MHz. |
| `min_pwrlevel` | 5 (or max index) | 0 | Minimum power level index (0 = maximum GPU frequency). |
| `max_pwrlevel` | 0 | 0 | Maximum power level index. |
| `thermal_pwrlevel` | 0 | 0 | Overrides thermal cap power level index. |
| `default_pwrlevel` | 5 | 0 | Default power level on wake. |
| `idle_timer` | 80 | 120 | Milliseconds before GPU enters idle power collapse. |
| `force_bus_on` | 0 | 1 | Forces GPU memory bus to stay awake, eliminating frame drops. |
| `force_clk_on` | 0 | 1 | Forces GPU clock domain active. |
| `force_rail_on` | 0 | 1 | Prevents GPU power rail voltage collapse during intense rendering. |
| `throttling` | 1 | 0 | Disables KGSL driver-level thermal throttling. |
| `bus_split` | 0 | 0 | Bus splitting control. |

### 2.2 Devfreq Interconnect Busses

Devfreq interfaces reside under `/sys/class/devfreq/`:

| Device Path | Balanced Governor | Performance Governor | Function |
| :--- | :--- | :--- | :--- |
| `1d84000.ufshc` | `simple_ondemand` | `performance` | UFS Host Controller Bus (reduces I/O latency). |
| `soc:qcom,cpubw` | `bw_hwmon` | `performance` / `bw_hwmon` | CPU to DDR memory bandwidth. |
| `soc:qcom,gpubw` | `bw_vbif` | `performance` / `bw_vbif` | GPU to DDR memory bandwidth. |
| `soc:qcom,kgsl-busmon` | `gpubw_mon` | `performance` / `gpubw_mon` | KGSL bus monitor governor. |
| `soc:qcom,llccbw` | `bw_hwmon` | `performance` | Last Level Cache to DDR memory bandwidth. |
| `soc:qcom,l3-cpu0` | `mem_latency` | `performance` | Little cluster L3 cache latency governor. |
| `soc:qcom,l3-cpu4` | `mem_latency` | `performance` | Big/Prime cluster L3 cache latency governor. |

---

## 3. High Performance Implementation (`perf.sh`)

```bash
#!/system/bin/sh
# Lynx Universal - Qualcomm Snapdragon Performance Profile
# Optimized for pure POSIX /system/bin/sh

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# 1. Devfreq Memory Bus Boost
for dev in /sys/class/devfreq/*; do
    [ -d "$dev" ] || continue
    governor_node="$dev/governor"
    if [ -f "$governor_node" ]; then
        case "$dev" in
            *ufshc*|*cpubw*|*gpubw*|*llccbw*|*l3-cpu*)
                write_node "performance" "$governor_node"
                ;;
        esac
    fi
    # Set to highest available devfreq if table exists
    freq_table="$dev/available_frequencies"
    if [ -s "$freq_table" ]; then
        highest_freq=$(tr -s ' ' '\n' < "$freq_table" | sort -n | tail -n 1)
        [ -n "$highest_freq" ] && write_node "$highest_freq" "$dev/min_freq"
    fi
done

# 2. Adreno KGSL GPU Engine Optimization
KGSL="/sys/class/kgsl/kgsl-3d0"
if [ -d "$KGSL" ]; then
    write_node "0" "$KGSL/throttling"
    write_node "0" "$KGSL/thermal_pwrlevel"
    write_node "0" "$KGSL/min_pwrlevel"
    write_node "1" "$KGSL/force_bus_on"
    write_node "1" "$KGSL/force_clk_on"
    write_node "1" "$KGSL/force_rail_on"
    write_node "120" "$KGSL/idle_timer"
fi

# 3. CPU Cluster Scaling & Schedutil Tuning
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    [ -n "$max_freq" ] && write_node "$max_freq" "$policy/scaling_max_freq"
    
    # Schedutil tunables
    schedutil="$policy/schedutil"
    if [ -d "$schedutil" ]; then
        write_node "0" "$schedutil/up_rate_limit_us"
        write_node "5000" "$schedutil/down_rate_limit_us"
        write_node "$max_freq" "$schedutil/hispeed_freq"
        write_node "85" "$schedutil/hispeed_load"
        write_node "1" "$schedutil/pl"
    fi
done

# 4. Disable Power-Efficient Workqueues
write_node "N" "/sys/module/workqueue/parameters/power_efficient"
```

---

## 4. Balanced Implementation (`balance.sh`)

```bash
#!/system/bin/sh
# Lynx Universal - Qualcomm Snapdragon Balanced Profile

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# 1. Reset Devfreq Governors
for dev in /sys/class/devfreq/*; do
    [ -d "$dev" ] || continue
    governor_node="$dev/governor"
    case "$dev" in
        *ufshc*)       write_node "simple_ondemand" "$governor_node" ;;
        *cpubw*)       write_node "bw_hwmon" "$governor_node" ;;
        *gpubw*)       write_node "bw_vbif" "$governor_node" ;;
        *kgsl-busmon*) write_node "gpubw_mon" "$governor_node" ;;
        *llccbw*)      write_node "bw_hwmon" "$governor_node" ;;
        *l3-cpu*)      write_node "mem_latency" "$governor_node" ;;
    esac
    # Reset min_freq to lowest
    freq_table="$dev/available_frequencies"
    if [ -s "$freq_table" ]; then
        lowest_freq=$(tr -s ' ' '\n' < "$freq_table" | sort -n | head -n 1)
        [ -n "$lowest_freq" ] && write_node "$lowest_freq" "$dev/min_freq"
    fi
done

# 2. Reset Adreno KGSL Parameters
KGSL="/sys/class/kgsl/kgsl-3d0"
if [ -d "$KGSL" ]; then
    write_node "1" "$KGSL/throttling"
    write_node "0" "$KGSL/force_bus_on"
    write_node "0" "$KGSL/force_clk_on"
    write_node "0" "$KGSL/force_rail_on"
    write_node "80" "$KGSL/idle_timer"
    # Allow full powerlevel scale down
    max_idx=$(cat "$KGSL/num_pwrlevels" 2>/dev/null)
    [ -n "$max_idx" ] && write_node "$((max_idx - 1))" "$KGSL/min_pwrlevel"
fi

# 3. CPU Frequencies to Hardware Range
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    min_freq=$(cat "$policy/cpuinfo_min_freq" 2>/dev/null)
    max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    [ -n "$min_freq" ] && write_node "$min_freq" "$policy/scaling_min_freq"
    [ -n "$max_freq" ] && write_node "$max_freq" "$policy/scaling_max_freq"
    
    schedutil="$policy/schedutil"
    if [ -d "$schedutil" ]; then
        write_node "500" "$schedutil/up_rate_limit_us"
        write_node "20000" "$schedutil/down_rate_limit_us"
        write_node "99" "$schedutil/hispeed_load"
    fi
done

# 4. Enable Power-Efficient Workqueues
write_node "Y" "/sys/module/workqueue/parameters/power_efficient"
```

---

## 5. Thermal Daemon Overlays (`platforms/qcom/system/vendor/`)

Qualcomm OEM ROMs (MIUI/HyperOS, OxygenOS, Motorola, ROG) ship background thermal binaries that poll sysfs and throttle clocks.

In `platforms/qcom/system/vendor/`:
- `bin/thermal-engine` -> Replaced with executable script: `#!/system/bin/sh\nexit 0`
- `bin/mi_thermald` -> Replaced with dummy executable `exit 0`
- `bin/thermal_factory` -> Replaced with dummy executable `exit 0`
- `etc/thermal-engine.conf` -> Overridden with empty or high-threshold profiles.

> [!CAUTION]
> These dummy binaries are strictly contained inside `platforms/qcom/` and are copied to `$MODPATH/system/` **ONLY** when `detect_soc` confirms genuine Qualcomm hardware.
