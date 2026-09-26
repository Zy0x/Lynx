# 🚀 Module 11: Custom Kernel & Overclocking (OC) Adaptation Engine
> **Subsystem**: Dynamic Frequency Scaler (`platforms/qcom/perf.sh`, `platforms/mtk/perf.sh`, `core/Smart-AI.sh`)  
> **Target Environments**: Stock Kernels, Overclocked (OC) Kernels, Undervolted (UV) Kernels, Custom EAS Kernels  

---

## 1. Problem Statement: The Hardcoded Frequency Anti-Pattern

A critical flaw in amateur performance scripts is **hardcoding frequency values**:
```bash
# BROKEN ANTIPATTERN (DO NOT USE)
# On Snapdragon 660, hardcoding stock maximum:
echo "2208000" > /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq
echo "650000000" > /sys/class/kgsl/kgsl-3d0/max_gpuclk
```

### The Fatal Flaw When Overclocking:
When an enthusiast flashes a custom overclocked kernel (e.g. *ElectroPerf, NoGravity, SilverCore, Ryzen*):
- The custom kernel patches the OPP tables, overclocking the CPU to **2.45 GHz** (2457600 kHz) and the GPU to **750 MHz** (750000000 Hz).
- If Lynx hardcodes stock `2208000`, Lynx will **forcefully downclock** the user's overclocked kernel, completely defeating the purpose of the custom kernel.
- Conversely, on underclocked/undervolted battery-saving kernels, writing a non-existent higher frequency either gets ignored or causes kernel warning traps.

---

## 2. Dynamic Frequency Scaling Architecture

Lynx Universal operates on a principle of **Dynamic Hardware Discovery**:
> Lynx never assumes stock maximum frequencies. It queries the kernel's active runtime limits at execution time.

```
                      ┌─────────────────────────────────┐
                      │    Kernel Cold Boot / Flash     │
                      └────────────────┬────────────────┘
                                       │
                        [Custom Kernel Overclock Active?]
                                       │
                      ┌────────────────┴────────────────┐
                      ▼                                 ▼
             [Overclocked Kernel]                 [Stock Kernel]
           cpuinfo_max_freq = 2457600           cpuinfo_max_freq = 2208000
           pwrlevel 0 = 750 MHz                 pwrlevel 0 = 650 MHz
                      │                                 │
                      └────────────────┬────────────────┘
                                       │
                                       ▼
                     [Lynx Dynamic Discovery Protocol]
            max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
            write_node "$max_freq" "$policy/scaling_max_freq"
            write_node "0" "$KGSL/min_pwrlevel"
                                       │
                                       ▼
                     ┌───────────────────────────────────┐
                     │  Full Overclock Capacity Utilized │
                     │       Zero Downclock Penalty      │
                     └───────────────────────────────────┘
```

---

## 3. Subsystem Breakdown: How Overclocking is Handled

### 3.1 CPU Overclocking (Qualcomm & MediaTek)
- **Linux CPUFreq Driver Contract**:
  - `cpuinfo_max_freq`: Hardware absolute maximum frequency supported by the currently running kernel.
  - `cpuinfo_min_freq`: Hardware absolute minimum frequency supported.
  - `scaling_available_frequencies`: List of all discrete frequency steps registered by the kernel driver.
- **Lynx Policy**:
  ```bash
  for policy in /sys/devices/system/cpu/cpufreq/policy*; do
      [ -d "$policy" ] || continue
      max_freq=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
      min_freq=$(cat "$policy/cpuinfo_min_freq" 2>/dev/null)
      
      # In Performance Mode: Lock to kernel's true ceiling (including OC)
      write_node "$max_freq" "$policy/scaling_max_freq"
      write_node "$max_freq" "$policy/scaling_min_freq"
      
      # In Balanced Mode: Restore full dynamic scaling window
      # scaling_min_freq = min_freq, scaling_max_freq = max_freq
  done
  ```
  *Result*: If the kernel is overclocked to 2.45 GHz, Lynx locks to 2.45 GHz. If it is stock 2.2 GHz, Lynx locks to 2.2 GHz. 100% adaptive.

### 3.2 Qualcomm Adreno GPU Overclocking (KGSL)
- **Adreno Powerlevel Mechanics**:
  - In Qualcomm KGSL drivers, power levels are indexed from `0` to `N-1`.
  - **Level `0` is ALWAYS the highest frequency in the kernel's frequency table**, regardless of whether that frequency is stock 650 MHz or overclocked 820 MHz.
  - When custom kernel developers overclock Adreno GPUs, they inject new clock frequencies at the top of `freq_table` and assign them to `pwrlevel 0`.
- **Lynx Policy**:
  ```bash
  KGSL="/sys/class/kgsl/kgsl-3d0"
  if [ -d "$KGSL" ]; then
      # Writing 0 to min_pwrlevel dynamically engages the overclocked ceiling!
      write_node "0" "$KGSL/min_pwrlevel"
      write_node "0" "$KGSL/default_pwrlevel"
      write_node "0" "$KGSL/thermal_pwrlevel"
  fi
  ```

### 3.3 MediaTek CPU & GPU Overclocking (PPM & GED)
- **PPM Cluster DVFS Tables**:
  - Custom MediaTek kernels update `/proc/ppm/dump_cluster_*_dvfs_table`.
  - The first entry (row 0, column 1) is dynamically assigned to the highest frequency step.
- **Lynx Policy**:
  ```bash
  lock_cluster_freq() {
      local cluster="$1"
      local table="/proc/ppm/dump_cluster_${cluster}_dvfs_table"
      if [ -f "$table" ]; then
          # head -n 1 extracts the true peak frequency, even if overclocked!
          local max_freq=$(awk '{print $1}' "$table" 2>/dev/null | head -n 1)
          write_node "$cluster $max_freq" "/proc/ppm/policy/hard_userlimit_max_cpu_freq"
          write_node "$cluster $max_freq" "/proc/ppm/policy/hard_userlimit_min_cpu_freq"
      fi
  }
  ```

---

## 4. Thermal & Voltage Safety with Overclocked Kernels

Overclocked kernels operate at elevated voltage steps (VDD), leading to quadratic increases in dynamic power dissipation:
$$P = C \cdot V^2 \cdot f$$

When overclocked kernels are detected:
1. **Mandatory Battery Failsafe Active**:
   The 46.0°C charging current clamp (`core/Charging-Controller.sh`) protects the battery from thermal runaway even if an aggressive OC kernel heats up the SoC.
2. **Asymmetric Debounce Protection**:
   The 3-second hysteresis filter prevents the CPU from thrashing between overclocked peak states and idle states during rapid app switches, stabilizing VRM voltages.
3. **No Voltage Manipulation**:
   Lynx never touches raw regulator voltages (`vdd_dig`, `vdd_mem`), leaving voltage stability entirely under the control of the custom kernel's calibrated OPP tables.
