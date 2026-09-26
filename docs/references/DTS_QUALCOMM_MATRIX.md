# 🔬 Qualcomm Snapdragon Device Tree Matrix & Hardware Evolution
> **Reference Document**: DTS/DTSI Ground Truth Analysis  
> **Spectrum**: 17 Qualcomm SoCs: MSM8940 (SD435 Baseline) -> SDM660 (User Device) -> SM7435 (7s Gen 2) -> SM8635 (8s Gen 3) -> SM8650 (Flagship Apex)  

---

## 1. Architectural Evolution Across Generations

Qualcomm Snapdragon platforms have evolved through four distinct eras of memory interconnects, GPU power managers, and CPU cluster topologies:

```
Era 1: Legacy 32/64-bit Big.LITTLE (2016-2017)
  • Examples: Snapdragon 435 (MSM8940), Snapdragon 625 (MSM8953)
  • CPU: 8x Cortex-A53 (Octa-core up to 2.0 GHz)
  • GPU: Adreno 505 / 506 (KGSL v1 software power levels)
  • Bus: BIMC (Bus Interface & Memory Controller) devfreq

Era 2: Kryo Semi-Custom & Devfreq Busmon (2018-2020)
  • Examples: Snapdragon 660 (SDM660), Snapdragon 845 (SDM845)
  • CPU: 4x Kryo Gold (Cortex-A73/A75) + 4x Kryo Silver (Cortex-A53/A55)
  • GPU: Adreno 512 / 630 (KGSL v2 with dynamic frequency table, pwrlevels 0-5)
  • Bus: msm-cpubw, msm-gpubw, kgsl-busmon, snoc_cnoc_keepalive

Era 3: Tri-Cluster, GMU Co-Processor & NoC (2020-2022)
  • Examples: SM7125 (SD720G), SM7150 (SD732G), SM6225 (SD680), SM6375 (SD695), SM7325 (SD778G), SM8250 (SD865)
  • CPU: 1-2x Prime/Gold + 6x Silver (or 1x Prime + 3x Gold + 4x Silver)
  • GPU: Adreno 618 / 619 / 642L / 650 with dedicated GMU (Graphics Management Unit)
  • Bus: Network-on-Chip (NoC), LLCC (Last Level Cache Controller), UFS 2.2 / 3.1 devfreq

Era 4: ARMv9 Tri/Quad-Cluster & Modern 4nm Generation (2022-2025)
  • Examples: SM7435 (7s Gen 2), SM7475 (7+ Gen 2), SM8450 (8 Gen 1), SM8475 (8+ Gen 1), SM8635 (8s Gen 3), SM8550 (8 Gen 2), SM8650 (8 Gen 3)
  • CPU: Cortex-X Prime + Cortex-A7xx Performance + Cortex-A5xx Efficiency (up to 1+5+2 on SM8650, 1+4+3 on SM8635)
  • GPU: Adreno 710 / 725 / 730 / 735 / 740 / 750 (KGSL v3, hardware Ray Tracing, GMU v3)
  • Bus: Dual-Channel UFS 4.0, LPDDR5X (up to 8533 Mbps), system-level cache partitions
```

---

## 2. In-Depth SoC Hardware Comparative Breakdown

### 2.1 Baseline Minimum: Snapdragon 435 (MSM8940 / Redmi 4X) & Snapdragon 625 (MSM8953)
*Kernel Reference: `arch/arm64/boot/dts/qcom/msm8940.dtsi`, `msm8953.dtsi`*

- **CPU Topology**: Octa-Core Cortex-A53 in 2 clusters.
  - MSM8940: Little cluster up to 1094 MHz, Performance cluster up to 1401 MHz.
  - MSM8953: 8x Cortex-A53 up to 2016 MHz on 14nm FinFET.
- **GPU**: Adreno 505 (MSM8940) / Adreno 506 (MSM8953) via `qcom,kgsl-3d0`.
  - Frequencies: 133 MHz (idle) to 450 MHz / 650 MHz.
  - `pwrlevel 0` locks maximum frequency; software interrupt handling.
- **Bus Devfreq**: `bimc-ddr` / `qcom,cpubw` adjusts between 200 MHz and 800/933 MHz.

---

### 2.2 User Target: Snapdragon 660 (SDM660 / Redmi Note 7) & Snapdragon 845 (SDM845 / POCO F1)
*Kernel Reference: `arch/arm64/boot/dts/qcom/sdm660.dtsi`, `sdm845.dtsi`*

- **SDM660 CPU**: 4x Kryo 260 Gold (Cortex-A73 @ 2.2 GHz) + 4x Kryo 260 Silver (Cortex-A53 @ 1.84 GHz).
  - Gold OPP Table: `633, 1113, 1401, 1747, 1958, 2208 MHz`.
  - Silver OPP Table: `633, 902, 1113, 1401, 1536, 1747, 1843 MHz`.
- **SDM660 GPU**: Adreno 512 (`qcom,kgsl-3d0@5000000`).
  - Freq: `160, 266, 370, 465, 588, 650 MHz` (pwrlevels 0-5).
  - Busmon: `soc:qcom,kgsl-busmon` adjusts DDR bus from 400 MHz to 1333 MHz.
- **SDM845 CPU & GPU**: 4x Kryo 385 Gold (Cortex-A75 @ 2.8 GHz) + 4x Kryo 385 Silver (Cortex-A55 @ 1.8 GHz) + Adreno 630 (710 MHz).

---

### 2.3 Budget 4G & 5G Ubiquity: Snapdragon 680 (SM6225) & Snapdragon 695 5G (SM6375)
*Kernel Reference: `arch/arm64/boot/dts/qcom/sm6225.dtsi`, `sm6375.dtsi`*

- **SM6225 (SD680 / Redmi Note 11)**:
  - 4x Kryo 265 Gold (2.4 GHz) + 4x Kryo 265 Silver (1.9 GHz) + Adreno 610.
- **SM6375 (SD695 5G / POCO X4 Pro 5G)**:
  - 2x Kryo 660 Gold (Cortex-A78 @ 2.2 GHz) + 6x Kryo 660 Silver (Cortex-A55 @ 1.8 GHz).
  - GPU: Adreno 619 @ 840 MHz with GMU.
  - Storage: UFS 2.2 host controller `1d84000.ufshc`.

---

### 2.4 Midrange Gaming Kings: Snapdragon 720G (SM7125) & Snapdragon 732G/730G (SM7150 / POCO X3 NFC)
*Kernel Reference: `arch/arm64/boot/dts/qcom/sm7125.dtsi`, `sm7150.dtsi`*

- **CPU**: 2x Kryo 465/470 Gold (Cortex-A76 @ 2.3 GHz) + 6x Kryo Silver (Cortex-A55 @ 1.8 GHz).
- **GPU**: Adreno 618 (`qcom,kgsl-3d0@5000000`) with integrated GMU (750 MHz – 800 MHz).
- **Storage & Interconnect**: UFS 2.1 / 2.2 `1d84000.ufshc` devfreq governor switches to `performance`.

---

### 2.5 New-Gen Midrange 4nm: Snapdragon 7s Gen 2 (SM7435 / Redmi Note 13 Pro 5G) & Snapdragon 778G (SM7325)
*Kernel Reference: `arch/arm64/boot/dts/qcom/sm7435.dtsi`, `sm7325.dtsi`*

- **SM7435 (Snapdragon 7s Gen 2 / Garnet)**:
  - CPU: 4x Cortex-A78 @ 2.40 GHz + 4x Cortex-A55 @ 1.95 GHz (Samsung 4nm).
  - GPU: Adreno 710 with GMU.
  - Exceptionally popular mid-tier device family powering Redmi Note 13 Pro 5G and Realme 12 Pro+ 5G.
- **SM7325 (Snapdragon 778G / Lisa)**:
  - 1x Kryo 670 Prime (2.4 GHz) + 3x Gold (2.2 GHz) + 4x Silver (1.9 GHz) + Adreno 642L.

---

### 2.6 Midrange Apex: Snapdragon 7+ Gen 2 (SM7475 / POCO F5 "Marble")
*Kernel Reference: `arch/arm64/boot/dts/qcom/sm7475.dtsi`*

- **CPU**: 1x Cortex-X2 Prime (2.91 GHz) + 3x Cortex-A710 Gold (2.49 GHz) + 4x Cortex-A510 Silver (1.80 GHz).
- **GPU**: Adreno 725 with GMU v3 (frequencies: 300 MHz – 800 MHz).
- **Storage**: UFS 3.1 dual-lane host controller `1d84000.ufshc`.

---

### 2.7 Flagship Tier & 2024 Flagship Killers:
*Kernel Reference: `sm8250.dtsi`, `sm8450.dtsi`, `sm8475.dtsi`, `sm8635.dtsi`, `sm8550.dtsi`, `sm8650.dtsi`*

- **SM8635 (Snapdragon 8s Gen 3 / POCO F6 "Peridot")**:
  - TSMC 4nm Flagship Killer.
  - CPU: 1x Cortex-X4 (3.0 GHz) + 4x Cortex-A720 (2.8 GHz) + 3x Cortex-A520 (2.0 GHz).
  - GPU: Adreno 735 with GMU v3 up to 1100 MHz.
  - UFS 4.0 storage controller `1d84000.ufshc`.
- **SM8250 (SD865/870 / POCO F3)**: 1x Prime (3.2 GHz) + 3x Gold (2.42 GHz) + 4x Silver (1.8 GHz) + Adreno 650.
- **SM8475 (8+ Gen 1 / POCO F5 Pro)**: TSMC 4nm refinement, Prime @ 3.2 GHz + Adreno 730 @ 900 MHz.
- **SM8550 (8 Gen 2 / Xiaomi 13)**: 1+2+2+3 topology + Adreno 740 with Hardware Ray Tracing.
- **SM8650 (8 Gen 3 / Xiaomi 14)**: 1+5+2 topology: 1x Cortex-X4 (3.3 GHz) + 5x Cortex-A720 (3.2 GHz) + 2x Cortex-A520 (2.3 GHz) + Adreno 750.

---

## 3. Sysfs Mapping Translation for Qualcomm

| DTS Hardware Property | Runtime Sysfs Node | Lynx Production Setting | Purpose |
| :--- | :--- | :--- | :--- |
| `qcom,kgsl-3d0/default-pwrlevel` | `/sys/class/kgsl/kgsl-3d0/default_pwrlevel` | `0` | Forces maximum clock immediately upon GPU wake |
| `qcom,kgsl-3d0/thermal-pwrlevel` | `/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel` | `0` (Perf/Extreme) | Overrides OEM thermal frequency clamp |
| `qcom,kgsl-3d0/idle-timeout` | `/sys/class/kgsl/kgsl-3d0/idle_timer` | `120` | Prolongs GPU active clock state during frame rendering |
| `qcom,cpubw/governor` | `/sys/class/devfreq/soc:qcom,cpubw/governor` | `performance` | Unlocks full DDR memory bandwidth |
| `1d84000.ufshc/governor` | `/sys/class/devfreq/1d84000.ufshc/governor` | `performance` | Maximizes UFS transfer rate and minimizes read latency |
| `kgsl-busmon/governor` | `/sys/class/devfreq/soc:qcom,kgsl-busmon/governor` | `performance` | Locks GPU-to-memory interconnect bus to top tier |
