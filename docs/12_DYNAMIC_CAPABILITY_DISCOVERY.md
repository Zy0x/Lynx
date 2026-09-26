# 🔬 Module 12: Universal Dynamic Capability Discovery Protocol
> **Core Subsystem**: `core/lib/capabilities.sh`  
> **Philosophy**: Zero Hardcoding — "Kecerdasan Tingkat Tinggi" (High-Level Adaptive Intelligence)  
> **Target Scope**: Linux Kernel Drivers, ZRAM, I/O Schedulers, TCP Congestion, Governors, Dynamic Memory  

---

## 1. Problem Statement: The Hardcoding Anti-Pattern

A widespread design mistake in Android root modules is **blind feature assumption**:
```bash
# FRAGILE ANTIPATTERN (DO NOT USE)
# Blindly writing 'zstd' to ZRAM compression algorithm
echo "zstd" > /sys/block/zram0/comp_algorithm
# Result on older/custom kernels: write error: Invalid argument (Kernel crash or silent failure)

# Blindly writing 'bfq' scheduler
echo "bfq" > /sys/block/sda/queue/scheduler
# Result on modern UFS with multiqueue: 'bfq' does not exist -> write error
```

### The Multi-Generational Android Reality:
- Kernels span from Linux 3.18 / 4.4 / 4.19 / 5.4 / 5.10 to 6.1 GKI.
- Many custom kernels compile out `zstd` to save binary size, leaving only `lzo` or `lz4`.
- Many GKI kernels deprecate legacy schedulers (`cfq`, `deadline`), supporting only `none` and `mq-deadline`.
- Hardcoding ANY configuration without verifying kernel hardware driver support is **strictly forbidden**.

---

## 2. Dynamic Discovery Architecture

Lynx Universal operates on the **Universal Dynamic Capability Discovery Protocol**:
> The running kernel driver itself is the sole ground truth. Lynx queries the kernel driver string, validates availability, and executes a graceful priority fallback chain.

```
                         ┌─────────────────────────────────┐
                         │   Query Active Sysfs Driver     │
                         │   e.g. /sys/block/zram0/...     │
                         └────────────────┬────────────────┘
                                          │
                         ┌────────────────▼────────────────┐
                         │ Parse Supported Capabilities    │
                         │ e.g. "lzo [lz4] lz4hc zstd"     │
                         └────────────────┬────────────────┘
                                          │
                         ┌────────────────▼────────────────┐
                         │   Evaluate Priority Hierarchy   │
                         │   High-Ratio vs Low-Latency     │
                         └────────────────┬────────────────┘
                                          │
               ┌──────────────────────────┴──────────────────────────┐
               ▼                                                     ▼
      [Target: High Ratio]                                  [Target: Low Latency]
 1. Check "zstd" -> Found!                             1. Check "lz4" -> Found!
 2. If missing -> Check "lz4hc"                        2. If missing -> Check "lzo-rle"
 3. If missing -> Check "lz4"                          3. If missing -> Check "lzo"
 4. If missing -> Fallback "lzo"                                     │
               │                                                     │
               └──────────────────────────┬──────────────────────────┘
                                          ▼
                         ┌─────────────────────────────────┐
                         │ Execute Safe write_node         │
                         │ Record to logs/fallback.log     │
                         └─────────────────────────────────┘
```

---

## 3. Subsystem Implementation Specifications

### 3.1 ZRAM Compression Algorithm (`comp_algorithm`)
- **Sysfs Node**: `/sys/block/zram0/comp_algorithm`
- **Output Format**: Space-delimited string with currently selected option in brackets: `lzo [lz4] lz4hc zstd`
- **Priority Chains**:
  - High Ratio (Devices with $\ge$ 8 GB RAM): `zstd` $\to$ `lz4hc` $\to$ `lz4` $\to$ `lzo-rle` $\to$ `lzo`
  - Low Latency (Devices with $\le$ 4 GB RAM / Older CPUs): `lz4` $\to$ `lzo-rle` $\to$ `lzo`
- **Implementation in `core/lib/capabilities.sh`**:
  ```sh
  select_supported_zram_algo() {
      local supported
      supported=$(cat /sys/block/zram0/comp_algorithm 2>/dev/null)
      [ -z "$supported" ] && { echo "lzo"; return 0; }
      for algo in "$@"; do
          case " $supported " in
              *" $algo "*) echo "$algo"; return 0 ;;
          esac
      done
      echo "lzo" # Universal baseline
  }
  ```

### 3.2 Block Storage I/O Schedulers
- **Sysfs Node**: `/sys/block/*/queue/scheduler`
- **Output Format**: `[none] mq-deadline bfq kyber`
- **Priority Chains**:
  - Fast Flash Storage (UFS 2.1 / 3.1 / 4.0): `none` $\to$ `mq-deadline` $\to$ `bfq`
  - eMMC Storage (eMMC 5.1): `mq-deadline` $\to$ `bfq` $\to$ `deadline` $\to$ `cfq` $\to$ `noop`

### 3.3 TCP Congestion Control
- **Sysfs Node**: `/proc/sys/net/ipv4/tcp_available_congestion_control`
- **Priority Chain**: `bbr` $\to$ `cubic` $\to$ `westwood` $\to$ `reno`
- **Rule**: If `bbr` is compiled into kernel, activate it for lower packet latency; otherwise gracefully fall back to standard `cubic`.

### 3.4 CPU & Devfreq Governors
- **Sysfs Node**: `/sys/devices/system/cpu/cpufreq/policy*/scaling_available_governors`
- **Rule**: If a custom kernel governor is active (e.g. `blu_schedutil`, `sched_pixel`, `electroutil`), Lynx **preserves** the governor name and tunes rate limits rather than forcing a destructive switch to `performance` or `schedutil`.

### 3.5 Dynamic Physical RAM Sizing
- **Source**: `/proc/meminfo` (`MemTotal`)
- **Formula**:
  $$\text{Target ZRAM Size} = \min\left(\frac{\text{MemTotal}}{2}, 4194304\text{ KB}\right)$$
  On devices with $\le$ 3 GB RAM, limit to 40% of `MemTotal` to prevent decompression thrashing on low-end CPUs.

---

## 4. Fallback Audit & Telemetry
Every time a fallback occurs (e.g. `zstd` requested but kernel lacks support), Lynx writes a structured entry to `/data/adb/modules/Lynx/logs/fallback.log`:
```
[2026-09-23 21:30:15] [CAPABILITY] Subsystem: ZRAM | Requested: zstd | Unsupported by kernel | Graceful fallback: lz4 | Status: OK
```
This guarantees complete auditability without losing execution context or showing empty logs.
