# ⚡ Module 03: MediaTek Dimensity & Helio Hardware Abstraction Layer
> **Subsystem**: MediaTek HAL (`platforms/mtk/`)  
> **Target Chips**: Helio G70/G85/G88/G95/G96/G99, Dimensity 6000, 7000, 8000, 9000 series  
> **Source Lineage**: Adapted and hardened from *Chimera*  

---

## 1. Architectural Blueprint

MediaTek SoCs use a distinctive kernel architecture fundamentally different from Qualcomm:
- **PPM (Processor Power Management)**: MediaTek's proprietary governor that acts above Linux CPUFreq. PPM dynamically limits cluster frequencies, parks cores, and enforces OEM thermal tables.
- **CCI (Cache Coherent Interconnect)**: Interconnect between Little and Big clusters. Operating mode determines cross-cluster data transfer latency.
- **GED (GPU Extension Driver)**: MediaTek's driver extension that monitors frame delivery, interacts with SurfaceFlinger, and overrides Mali DVFS.
- **FPSGO (Frame-Rate Optimization Engine)**: Kernel-level frame-pacing and burst manager that throttles or boosts based on vsync deadlines.
- **Thermal Policy (`.tp`)**: MediaTek thermal management via encrypted policy files (`.thermal_policy_*`, `thermal.conf`).

```
+-------------------------------------------------------------------------+
|                         MediaTek Platform HAL                           |
+--------------------+---------------------+------------------------------+
|    CPU / PPM       |    GPU / GED        |         Thermal / .tp        |
| • /proc/ppm        | • /sys/module/ged   | • /vendor/etc/.tp/           |
| • cpufreq_power_m  | • /sys/kernel/fpsgo | • .thermal_policy_08         |
| • cpufreq_cci_mode | • Dynamic Mali EGL  | • thermal.conf               |
+--------------------+---------------------+------------------------------+
```

---

## 2. Kernel Node & Sysfs Reference Matrix

### 2.1 MediaTek PPM (Processor Power Management)

All nodes reside under `/proc/ppm/`:

| Node Path | Balanced | Performance | Function & Impact |
| :--- | :--- | :--- | :--- |
| `/proc/ppm/enabled` | `1` | `0` | Master PPM switch. Set to `0` to unbind OEM frequency caps. |
| `/proc/ppm/policy_status` | Default table | Tuned table | Toggles specific PPM policies (thermal, userlimit, power-saving). |
| `/proc/ppm/policy/hard_userlimit_max_cpu_freq` | Auto | `<cluster> <freq>` | Locks cluster maximum frequency. |
| `/proc/ppm/policy/hard_userlimit_min_cpu_freq` | Auto | `<cluster> <freq>` | Elevates cluster minimum floor frequency. |
| `/proc/ppm/dump_cluster_0_dvfs_table` | *Read-only* | *Read-only* | Little cluster DVFS table in kHz. |
| `/proc/ppm/dump_cluster_1_dvfs_table` | *Read-only* | *Read-only* | Big cluster DVFS table in kHz. |
| `/proc/ppm/dump_cluster_2_dvfs_table` | *Read-only* | *Read-only* | Prime cluster DVFS table (tri-cluster chips). |

### 2.2 MediaTek CPUFreq Power Modes

| Node Path | Balanced | Performance | Function |
| :--- | :--- | :--- | :--- |
| `/proc/cpufreq/cpufreq_power_mode` | `0` (Default) | `3` (Sport/High) | MediaTek proprietary CPU power mode profile. |
| `/proc/cpufreq/cpufreq_cci_mode` | `0` (Balance) | `1` (High Perf) | CCI interconnect clock and latency mode. |
| `/proc/cpufreq/cpufreq_imax_enable` | `0` (Disabled) | `1` (Enabled) | Forces maximum CPU current capability. |
| `/proc/cpufreq/cpufreq_debug` | `0` | `0` | Disables kernel CPU logging overhead. |

### 2.3 GED (GPU Extension Driver) & Mali Engine

| Node Path | Balanced | Performance | Function |
| :--- | :--- | :--- | :--- |
| `/sys/module/ged/parameters/boost_gpu_enable` | `0` | `1` | Enables GED GPU boost requests. |
| `/sys/module/ged/parameters/ged_boost_enable` | `0` | `1` | Master GED boost switch. |
| `/sys/module/ged/parameters/enable_gpu_boost` | `0` | `1` | Forces GPU clocks up during heavy touch/render. |
| `/sys/module/ged/parameters/enable_cpu_boost` | `0` | `1` | Coordinates CPU boost when GPU load peaks. |
| `/sys/module/ged/parameters/boost_amp` | `0` | `1` | Boost amplification factor. |
| `/sys/module/ged/parameters/g_fb_dvfs_threshold` | `30` | `100` | Framebuffer DVFS threshold percentage. |
| `/sys/kernel/fpsgo/common/gpu_block_boost` | `0` | `1` | Prevents FPSGO from blocking GPU boost events. |
| `/sys/devices/platform/*mali*/power_policy` | `coarse_demand` | `always_on` | Mali GPU kernel power policy. |

---

## 3. High Performance Implementation (`perf.sh`)

```bash
#!/system/bin/sh
# Lynx Universal - MediaTek Dimensity & Helio Performance Profile
# Hardened POSIX compliance for /system/bin/sh

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# 1. Switch MediaTek CPUFreq Power Modes to High Performance / Sport
write_node "3" "/proc/cpufreq/cpufreq_power_mode"
write_node "1" "/proc/cpufreq/cpufreq_cci_mode"
write_node "1" "/proc/cpufreq/cpufreq_imax_enable"
write_node "0" "/proc/cpufreq/cpufreq_debug"
write_node "N" "/sys/module/workqueue/parameters/power_efficient"

# 2. Disable PPM Throttling & Lock Frequencies to Highest
write_node "0" "/proc/ppm/enabled"
write_node "0 0" "/proc/ppm/policy_status"
write_node "1 1" "/proc/ppm/policy_status"
write_node "2 0" "/proc/ppm/policy_status"
write_node "3 0" "/proc/ppm/policy_status"
write_node "4 0" "/proc/ppm/policy_status"
write_node "5 0" "/proc/ppm/policy_status"
write_node "6 1" "/proc/ppm/policy_status"
write_node "7 1" "/proc/ppm/policy_status"
write_node "8 0" "/proc/ppm/policy_status"
write_node "9 1" "/proc/ppm/policy_status"

# Parse Cluster Tables using cached frequencies if available
lock_cluster_freq() {
    local cluster="$1"
    local table="/proc/ppm/dump_cluster_${cluster}_dvfs_table"
    if [ -f "$table" ]; then
        local max_freq
        max_freq=$(awk '{print $1}' "$table" 2>/dev/null | head -n 1)
        if [ -n "$max_freq" ]; then
            write_node "$cluster $max_freq" "/proc/ppm/policy/hard_userlimit_max_cpu_freq"
            write_node "$cluster $max_freq" "/proc/ppm/policy/hard_userlimit_min_cpu_freq"
        fi
    fi
}
lock_cluster_freq 0
lock_cluster_freq 1
lock_cluster_freq 2

# 3. CPUFreq Policy Scaling
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    if [ -n "$max_freq" ]; then
        write_node "$max_freq" "$policy/scaling_max_freq"
        write_node "$max_freq" "$policy/scaling_min_freq"
    fi
done

# 4. MediaTek GED GPU & FPSGO Boost
write_node "1" "/sys/module/ged/parameters/boost_gpu_enable"
write_node "1" "/sys/module/ged/parameters/ged_boost_enable"
write_node "1" "/sys/module/ged/parameters/enable_gpu_boost"
write_node "1" "/sys/module/ged/parameters/enable_cpu_boost"
write_node "1" "/sys/module/ged/parameters/boost_amp"
write_node "1" "/sys/module/ged/parameters/boost_extra"
write_node "100" "/sys/module/ged/parameters/g_fb_dvfs_threshold"
write_node "1" "/sys/module/ged/parameters/ged_force_mdp_enable"
write_node "1" "/sys/kernel/fpsgo/common/gpu_block_boost"

# 5. ARM Mali GPU Driver (with PowerVR / non-Mali safe guard)
for mali in /sys/devices/platform/*mali*; do
    [ -d "$mali" ] || continue
    write_node "always_on" "$mali/power_policy"
done
write_node "1" "/proc/mali/always_on"
write_node "0" "/proc/mali/dvfs_enable"
```

---

## 4. Balanced Implementation (`balance.sh`)

```bash
#!/system/bin/sh
# Lynx Universal - MediaTek Dimensity & Helio Balanced Profile

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# 1. Reset CPUFreq Power Modes to Default
write_node "0" "/proc/cpufreq/cpufreq_power_mode"
write_node "0" "/proc/cpufreq/cpufreq_cci_mode"
write_node "0" "/proc/cpufreq/cpufreq_imax_enable"
write_node "Y" "/sys/module/workqueue/parameters/power_efficient"

# 2. Re-enable PPM Governor Table
write_node "1" "/proc/ppm/enabled"
write_node "0 0" "/proc/ppm/policy_status"
write_node "1 1" "/proc/ppm/policy_status"
write_node "6 1" "/proc/ppm/policy_status"
write_node "7 1" "/proc/ppm/policy_status"

# 3. Restore CPU Cluster Frequency Bounds
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    min_freq=$(cat "$policy/cpuinfo_min_freq" 2>/dev/null)
    max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    [ -n "$min_freq" ] && write_node "$min_freq" "$policy/scaling_min_freq"
    [ -n "$max_freq" ] && write_node "$max_freq" "$policy/scaling_max_freq"
done

# 4. Reset MediaTek GED Parameters
write_node "0" "/sys/module/ged/parameters/boost_gpu_enable"
write_node "0" "/sys/module/ged/parameters/ged_boost_enable"
write_node "0" "/sys/module/ged/parameters/enable_gpu_boost"
write_node "0" "/sys/module/ged/parameters/enable_cpu_boost"
write_node "30" "/sys/module/ged/parameters/g_fb_dvfs_threshold"
write_node "0" "/sys/kernel/fpsgo/common/gpu_block_boost"

# 5. Restore Mali GPU Power Policy
for mali in /sys/devices/platform/*mali*; do
    [ -d "$mali" ] || continue
    write_node "coarse_demand" "$mali/power_policy"
done
write_node "0" "/proc/mali/always_on"
write_node "1" "/proc/mali/dvfs_enable"
```

---

## 5. Dynamic Early-Boot Mali Configuration (`post-fs-data.sh`)

Rather than shipping static `egl.cfg` files, Lynx dynamically detects the exact Mali GPU ID and generates the config at early boot:

```bash
# Executed during post-fs-data on MediaTek platforms
setup_mali_egl() {
    local gpu_path
    gpu_path=$(find /sys/devices/platform/*mali*/gpuinfo -type f 2>/dev/null | head -n 1)
    if [ -n "$gpu_path" ]; then
        local gpu_id
        gpu_id=$(awk '{print $1}' "$gpu_path" 2>/dev/null)
        if [ -n "$gpu_id" ]; then
            local correct_cfg="0 0 $gpu_id"
            for lib_dir in "$MODDIR/system/lib/egl" "$MODDIR/system/lib64/egl" "$MODDIR/system/vendor/lib/egl" "$MODDIR/system/vendor/lib64/egl"; do
                mkdir -p "$lib_dir"
                echo "$correct_cfg" > "$lib_dir/egl.cfg"
            done
        fi
    fi
}
```

> [!NOTE]
> For MediaTek devices equipped with **PowerVR (IMG B-Series)** GPUs (e.g. Helio G35 / Dimensity 930), `gpu_path` will be empty, and this script gracefully exits without creating invalid Mali EGL configurations.
