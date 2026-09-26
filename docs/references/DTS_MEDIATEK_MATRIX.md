# 🔬 MediaTek Dimensity & Helio Device Tree Matrix & Hardware Evolution
> **Reference Document**: DTS/DTSI Ground Truth Analysis  
> **Spectrum**: 17 MediaTek SoCs: MT6761 (Helio A22) -> MT6789 (Helio G99/G100) -> MT6855 (Dimensity 7020) -> MT6877 (Dimensity 920/7050) -> MT6878 (Dimensity 7300) -> MT6989 (Dimensity 9300 Apex)  

---

## 1. Architectural Evolution Across Generations

MediaTek's architecture has transitioned from early big.LITTLE Helio chips with PowerVR graphics to sophisticated multi-cluster Dimensity SoCs featuring integrated APUs, DVFSRC interconnects, and modern Valhall/Immortalis GPUs:

```
Era 1: Helio P & Entry Series (2018-2020)
  • Examples: Helio A22 (MT6761), Helio P22 / G35 (MT6762 / MT6765)
  • CPU: 4x or 8x Cortex-A53 (up to 2.3 GHz)
  • GPU: PowerVR GE8320 (IMG Rogue architecture) -- CRITICAL: Not Mali!
  • Thermal: Early .tp policy engine with sysfs thermal zones

Era 2: Helio G-Series Gaming & 2024 Revisions (2020-2025)
  • Examples: Helio G80/G85 (MT6769), Helio G90T/G95 (MT6785), Helio G96 (MT6781), Helio G99 / Helio G100 (MT6789)
  • CPU: 2x Cortex-A75/A76 (up to 2.2 GHz) + 6x Cortex-A55 (up to 2.0 GHz)
  • GPU: ARM Mali-G52 / G76 / G57 (Bifrost & Valhall architectures)
  • Memory & Bus: PPM (Processor Power Management) + CCI Interconnect + GED

Era 3: Dimensity 7000 Series & 5G Evolution (2023-2025)
  • Dimensity 7020 (MT6855): 2x A78 + 6x A55 with IMG PowerVR BXM-8-256 GPU (No Mali!)
  • Dimensity 7050 / 920 (MT6877 / MT6877V): 2x A78 + 6x A55 with ARM Mali-G68 MC4
  • Dimensity 7200 / 7200-Ultra (MT6886): 4nm TSMC 2x A715 (2.8 GHz) + 6x A510 with Mali-G610 MC4
  • Dimensity 7300 / 7300-Energy / 7350 Pro (MT6878): 4nm TSMC 4x A78 (2.5 GHz) + 4x A55 with Mali-G615 MC2

Era 4: 4nm Dimensity Apex & All-Big-Core (2023-Present)
  • Examples: Dimensity 8100/8200 (MT6895), Dimensity 8300 (MT6897), Dimensity 9200 (MT6985), Dimensity 9300 (MT6989)
  • CPU: ARMv9 Tri-Cluster (Cortex-A715 / A720) up to All-Big-Core (4x X4 + 4x A720 on MT6989)
  • GPU: ARM Mali-G610, Mali-G615, Immortalis-G715, Immortalis-G720 (Hardware Ray Tracing)
  • Bus: DVFSRC 4nm, LPDDR5X (up to 8533 Mbps), UFS 4.0 host controller
```

---

## 2. In-Depth SoC Hardware Comparative Breakdown

### 2.1 Baseline Minimum: Helio A22 (MT6761) & Helio P22 / G35 (MT6762 / MT6765)
*Kernel Reference: `arch/arm64/boot/dts/mediatek/mt6761.dtsi`, `mt6765.dtsi`*

- **CPU**: Quad/Octa Cortex-A53 up to 2.3 GHz.
- **GPU**: PowerVR Rogue GE8320 (IMG Rogue).
- **Safety Fact**: Devices have **no `/dev/mali0`** and **no `/proc/mali`**. Lynx guards all operations with `[ -e "$node" ]`.

---

### 2.2 Global 4G Kings: Helio G99 & Helio G100 (MT6789 / "Hermes")
*Kernel Reference: `arch/arm64/boot/dts/mediatek/mt6789.dtsi`*

- **Hardware Ground Truth on Helio G100**:
  - The **Helio G100** (found in late 2024 devices like Infinix Hot 50 Pro+ and Tecno Camon 30S Pro) is built upon the identical **MT6789 silicon platform** (6nm TSMC).
  - CPU: 2x Cortex-A76 @ 2.20 GHz + 6x Cortex-A55 @ 2.0 GHz.
  - GPU: ARM Mali-G57 MC2 up to 1068 MHz.
  - Driver paths: Uses the exact same `/proc/ppm/`, `/proc/ged/`, and `/sys/module/ged/` nodes as MT6789.
  - Lynx recognizes both Helio G99 and Helio G100 natively under the unified MT6789 engine profile.

---

### 2.3 The Dimensity 7000 Series Spectrum (Ground Truth Comparison)
The "Dimensity 7000" name encompasses three completely different hardware silicon designs:

#### A. Dimensity 7020 (MT6855 / Infinix Note 40 Pro 5G / Motorola Moto G54)
*Kernel Reference: `arch/arm64/boot/dts/mediatek/mt6855.dtsi`*
- **CPU**: 2x Cortex-A78 (2.20 GHz) + 6x Cortex-A55 (2.0 GHz).
- **GPU: IMG BXM-8-256 (PowerVR B-Series)**:
  - ⚠️ **CRITICAL ARCHITECTURAL DISTINCTION**: Dimensity 7020 **DOES NOT USE ARM MALI**. It uses an **Imagination PowerVR B-Series GPU**.
  - There is no `/dev/mali0`, no `/proc/mali/`, and no GED Mali parameters.
  - Lynx's safe verification automatically protects MT6855 devices from erroneous Mali tweaks.

#### B. Dimensity 7050 (MT6877V / Realme 11 Pro 5G / Realme 12+ 5G)
*Kernel Reference: `arch/arm64/boot/dts/mediatek/mt6877.dtsi`*
- Rebrand of the acclaimed **Dimensity 920 / 1080 (MT6877)**.
- CPU: 2x Cortex-A78 (2.60 GHz) + 6x Cortex-A55 (2.0 GHz).
- GPU: ARM Mali-G68 MC4 @ 950 MHz.
- Shares 100% sysfs register parity with our live Infinix X698 testbed.

#### C. Dimensity 7200 / 7200-Ultra (MT6886 / Redmi Note 13 Pro+ 5G)
*Kernel Reference: `arch/arm64/boot/dts/mediatek/mt6886.dtsi`*
- TSMC 4nm 2nd Gen architecture.
- CPU: 2x Cortex-A715 (2.80 GHz) + 6x Cortex-A510 (2.0 GHz).
- GPU: ARM Mali-G610 MC4 up to 1130 MHz.

#### D. Dimensity 7300 / 7300-Energy / 7350 Pro (MT6878 / CMF Phone 1 / Nothing Phone 2a Plus)
*Kernel Reference: `arch/arm64/boot/dts/mediatek/mt6878.dtsi`*
- TSMC 4nm architecture with Quad Big Cores.
- CPU: 4x Cortex-A78 (2.50 GHz) + 4x Cortex-A55 (2.0 GHz).
- GPU: ARM Mali-G615 MC2 (Valhall 4th Gen) with MediaTek HyperEngine.

---

### 2.4 Flagship Dimensity & All-Big-Core Generation:
*Kernel Reference: `mt6895.dtsi`, `mt6897.dtsi`, `mt6985.dtsi`, `mt6989.dtsi`*

- **MT6895 (Dimensity 8100/8200 / POCO X4 GT)**: 4x Cortex-A78 (2.85 GHz) + 4x Cortex-A55 (2.0 GHz) + Mali-G610 MC6.
- **MT6897 (Dimensity 8300-Ultra / POCO X6 Pro)**: 1x A715 (3.35 GHz) + 3x A715 (3.20 GHz) + 4x A510 (2.20 GHz) + Mali-G615 MC6.
- **MT6985 (Dimensity 9200 / Xiaomi 13T Pro)**: 1x Cortex-X3 (3.05 GHz) + Immortalis-G715 with Ray Tracing.
- **MT6989 (Dimensity 9300)**: All-Big-Core design (4x Cortex-X4 + 4x Cortex-A720) + Immortalis-G720 MC12.

---

## 3. Sysfs Mapping Translation for MediaTek

| DTS Property / Subsystem | Runtime Node Path | Lynx Production Setting | Purpose |
| :--- | :--- | :--- | :--- |
| `ppm/enabled` | `/proc/ppm/enabled` | `0` (Perf/Extreme) / `1` (Balance) | Disables OEM thermal clock throttler |
| `cpufreq/power_mode` | `/proc/cpufreq/cpufreq_power_mode` | `3` (Sport/Extreme) / `0` (Balance) | Locks kernel CPU power mode to highest tier |
| `cpufreq/cci_mode` | `/proc/cpufreq/cpufreq_cci_mode` | `1` (Perf) / `0` (Balance) | Unlocks Cache Coherent Interconnect bandwidth |
| `ged/boost` | `/sys/module/ged/parameters/boost_gpu_enable` | `1` | Forces GED driver to dispatch GPU boost interrupts |
| `ged/fb_dvfs` | `/sys/module/ged/parameters/g_fb_dvfs_threshold` | `100` | Holds GPU clock high across vsync boundaries |
| `fpsgo/boost` | `/sys/kernel/fpsgo/common/gpu_block_boost` | `1` | Prevents MediaTek FPSGO from dropping target frame rates |
| `mali/power_policy` | `/sys/devices/platform/*mali*/power_policy` | `always_on` (Perf) / `coarse_demand` | Keeps Mali GPU cores awake (Skipped safely on PowerVR) |
