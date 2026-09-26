# 🗺️ Master Sysfs & Kernel Ground-Truth Translation Mapping
> **Reference Document**: Complete Subsystem Path Dictionary  
> **Target Runtimes**: Standard Android Linux Kernel 4.9 – 6.6 (ARM64)  

---

## 1. Qualcomm Snapdragon Translation Dictionary

| Subsystem | DTS Source Reference | Kernel Runtime Path | Lynx Safe Write Command | Effect & Safety Guard |
| :--- | :--- | :--- | :--- | :--- |
| **GPU Throttle** | `qcom,kgsl-3d0` | `/sys/class/kgsl/kgsl-3d0/throttling` | `write_node 0 <path>` | Disables KGSL driver-level thermal throttling. Safe on all Adreno 5xx/6xx/7xx. |
| **GPU Min Level** | `qcom,kgsl-3d0` | `/sys/class/kgsl/kgsl-3d0/min_pwrlevel` | `write_node 0 <path>` | 0 = Highest GPU frequency. Clamped by kernel if out of bounds. |
| **GPU Idle Timer** | `qcom,kgsl-3d0` | `/sys/class/kgsl/kgsl-3d0/idle_timer` | `write_node 120 <path>` | Extends awake window to 120ms to prevent micro-stutter during frame drops. |
| **GPU Rail Power** | `qcom,kgsl-3d0` | `/sys/class/kgsl/kgsl-3d0/force_rail_on` | `write_node 1 <path>` | Keeps power rail energized. Only touched if node exists. |
| **CPU-DDR Bus** | `soc:qcom,cpubw` | `/sys/class/devfreq/soc:qcom,cpubw/governor` | `write_node performance <path>` | Lifts memory bandwidth limit between CPU cores and LPDDR RAM. |
| **GPU-DDR Bus** | `soc:qcom,gpubw` | `/sys/class/devfreq/soc:qcom,gpubw/governor` | `write_node performance <path>` | Unlocks bandwidth between Adreno GPU and DDR memory. |
| **UFS Controller** | `1d84000.ufshc` | `/sys/class/devfreq/1d84000.ufshc/governor` | `write_node performance <path>` | Switches UFS storage controller devfreq to max throughput. |
| **Last Level Cache**| `soc:qcom,llccbw` | `/sys/class/devfreq/soc:qcom,llccbw/governor` | `write_node performance <path>` | Ensures system cache bus operates at peak clock on SD865/888/8Gen. |

---

## 2. MediaTek Dimensity & Helio Translation Dictionary

| Subsystem | DTS Source Reference | Kernel Runtime Path | Lynx Safe Write Command | Effect & Safety Guard |
| :--- | :--- | :--- | :--- | :--- |
| **PPM Master** | `mediatek,ppm` | `/proc/ppm/enabled` | `write_node 0 <path>` | Disables Processor Power Management frequency capping. |
| **CPU Power Mode**| `mediatek,cpufreq` | `/proc/cpufreq/cpufreq_power_mode` | `write_node 3 <path>` | Mode 3 = Sport / High Performance profile on all MediaTek chips. |
| **CCI Interconnect**| `mediatek,cci` | `/proc/cpufreq/cpufreq_cci_mode` | `write_node 1 <path>` | Maximizes Cache Coherent Interconnect bandwidth between clusters. |
| **GED GPU Boost** | `mediatek,ged` | `/sys/module/ged/parameters/boost_gpu_enable` | `write_node 1 <path>` | Commands MediaTek GPU Extension Driver to prioritize rendering. |
| **GED FB DVFS** | `mediatek,ged` | `/sys/module/ged/parameters/g_fb_dvfs_threshold` | `write_node 100 <path>` | Overrides dynamic voltage scaling threshold to hold GPU clocks. |
| **FPSGO GPU Block**| `mediatek,fpsgo` | `/sys/kernel/fpsgo/common/gpu_block_boost` | `write_node 1 <path>` | Prevents Frame-Rate Optimization Engine from denying boost events. |
| **Mali Power** | `arm,mali-valhall` | `/sys/devices/platform/*mali*/power_policy` | `write_node always_on <path>` | Forces Mali GPU out of coarse_demand sleep. Skipped on PowerVR. |
| **Mali Always On** | `arm,mali-valhall` | `/proc/mali/always_on` | `write_node 1 <path>` | Kernel Mali driver switch. Checked for existence first. |

---

## 3. Universal Power & Storage Translation Dictionary

| Subsystem | Kernel Subsystem | Kernel Runtime Path | Lynx Safe Write Command | Effect & Safety Guard |
| :--- | :--- | :--- | :--- | :--- |
| **Memory Compaction**| Linux VM Subsystem | `/proc/sys/vm/compact_memory` | `echo 1 > <path>` | Gentle memory defragmentation. Replaces destructive drop_caches. |
| **I/O Read-Ahead** | Linux Block Layer | `/sys/block/*/queue/read_ahead_kb` | `write_node 512 <path>` | Optimizes read caching for flash storage (UFS/eMMC). |
| **Battery Temp** | Power Supply Class | `/sys/class/power_supply/battery/temp` | *Read-only* | Monitors battery temperature in decicelsius (e.g. 460 = 46.0°C). |
| **Charge Current**| Power Supply Class | `/sys/class/power_supply/battery/constant_charge_current` | `write_node <ua> <path>` | Regulates battery charging current in microamps. |
| **Bypass Charging**| Power Supply Class | `/sys/class/power_supply/battery/input_suspend` | `write_node 1 <path>` | Suspends battery charging while keeping phone powered. |
| **Emergency Clamp**| Lynx Failsafe Engine| *Triggered at ≥ 46.0°C* | `set_charging_current 1000000` | Mandatory hardware safety protection against thermal runaway. |
