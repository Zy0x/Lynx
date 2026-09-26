# 🗺️ Master Index: Ground-Truth Device Tree & Kernel Architecture References
> **Purpose**: Empirical Hardware Ground Truth for Lynx Universal Optimization  
> **Source Base**: Official Open-Source Kernel Trees (Xiaomi OSS, LineageOS, CAF/CLO, Transsion)  
> **Target Spectrum**: 34 Chipsets — From Minimum Baseline (Redmi 4X / Helio A22) to Current 2024-2025 Generation (SD 8s Gen 3 / Dimensity 7300 / Dimensity 9300 / Helio G100)  

---

## 📌 1. Device Tree Foundation Philosophy

Performance modules often fail or cause micro-stutters because developers guess sysfs paths from internet threads or copy scripts across incompatible SoCs. 

In Lynx Universal, **every single sysfs node, devfreq governor, clock frequency, voltage step, and thermal policy is mathematically and physically grounded in genuine kernel Device Tree Blob sources (DTS/DTSI)** across 34 distinct platforms spanning 2016 to 2025.

---

## 🏛️ 2. Comprehensive Chipset Spectrum & Device Mapping (34 SoCs)

### 2.1 Qualcomm Snapdragon Spectrum (17 SoCs)

| Index | Generation Tier | SoC Model | Code Name | Reference Device | Kernel Source Basis |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 01 | **Legacy Baseline** | **Snapdragon 435** (MSM8940 / MSM8937) | Santoni | Redmi 4X | `MiCode/Xiaomi_Kernel_OpenSource:santoni-n-oss` |
| 02 | **Efficiency Legend** | **Snapdragon 625** (MSM8953) | Mido | Redmi Note 4X | `MiCode/Xiaomi_Kernel_OpenSource:mido-n-oss` |
| 03 | **User Target** | **Snapdragon 660** (SDM660 / MSM8976Plus) | Lavender / Whyred | Redmi Note 7 / Note 5 | `MiCode/Xiaomi_Kernel_OpenSource:lavender-q-oss` |
| 04 | **Flagship Classic** | **Snapdragon 845** (SDM845) | Beryllium / Dipper | POCO F1 / Mi 8 | `MiCode/Xiaomi_Kernel_OpenSource:beryllium-q-oss` |
| 05 | **Budget 4G Ubiquity**| **Snapdragon 680 / 662** (SM6225 / SM6115) | Spes / Lime | Redmi Note 11 / Redmi 9T | `MiCode/Xiaomi_Kernel_OpenSource:spes-s-oss` |
| 06 | **Midrange Gaming** | **Snapdragon 720G** (SM7125) | Curtana / Joyeuse | Redmi Note 9S / Note 9 Pro | `MiCode/Xiaomi_Kernel_OpenSource:curtana-q-oss` |
| 07 | **Gamer Legend** | **Snapdragon 732G / 730G** (SM7150) | Surya / Sweet | POCO X3 NFC / Redmi Note 10 Pro | `MiCode/Xiaomi_Kernel_OpenSource:surya-r-oss` |
| 08 | **5G Mass Midrange**| **Snapdragon 695 5G** (SM6375) | Veux / Peux | POCO X4 Pro 5G / Redmi Note 11 Pro 5G | `MiCode/Xiaomi_Kernel_OpenSource:veux-s-oss` |
| 09 | **Upper Midrange** | **Snapdragon 778G / 778G+** (SM7325) | Lisa / Taoyao | Xiaomi 11 Lite 5G NE | `MiCode/Xiaomi_Kernel_OpenSource:lisa-r-oss` |
| 10 | **New-Gen Mid 4nm** | **Snapdragon 7s Gen 2** (SM7435) | Garnet | Redmi Note 13 Pro 5G / Realme 12 Pro+ | `MiCode/Xiaomi_Kernel_OpenSource:garnet-t-oss` |
| 11 | **Midrange Apex** | **Snapdragon 7+ Gen 2** (SM7475) | Marble | POCO F5 / Redmi Note 12 Turbo | `MiCode/Xiaomi_Kernel_OpenSource:marble-t-oss` |
| 12 | **Flagship Legend** | **Snapdragon 865 / 870** (SM8250) | Kona / Alioth | Mi 10 / POCO F3 | `MiCode/Xiaomi_Kernel_OpenSource:alioth-r-oss` |
| 13 | **Modern Flagship** | **Snapdragon 8 Gen 1** (SM8450) | Taro / Zeus | Xiaomi 12 Pro | `MiCode/Xiaomi_Kernel_OpenSource:zeus-s-oss` |
| 14 | **TSMC Refinement** | **Snapdragon 8+ Gen 1** (SM8475) | Mondrian / Diting | POCO F5 Pro / Redmi K60 / 12T Pro | `MiCode/Xiaomi_Kernel_OpenSource:mondrian-s-oss` |
| 15 | **Flagship Killer 2024**| **Snapdragon 8s Gen 3** (SM8635) | Peridot | POCO F6 / Redmi Turbo 3 | `MiCode/Xiaomi_Kernel_OpenSource:peridot-u-oss` |
| 16 | **Efficiency Apex** | **Snapdragon 8 Gen 2** (SM8550) | Kalama / Ishtar | Xiaomi 13 / 13 Ultra | `MiCode/Xiaomi_Kernel_OpenSource:ishtar-t-oss` |
| 17 | **Current Apex** | **Snapdragon 8 Gen 3** (SM8650) | Pineapple / Clover | Xiaomi 14 / 14 Pro | `MiCode/Xiaomi_Kernel_OpenSource:houji-u-oss` |

---

### 2.2 MediaTek Dimensity & Helio Spectrum (17 SoCs)

| Index | Generation Tier | SoC Model | Code Name | Reference Device | Kernel Source Basis |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 01 | **Ultra Budget Entry**| **Helio A22** (MT6761) | Cactus | Redmi 6A | `MiCode/Xiaomi_Kernel_OpenSource:cactus-o-oss` |
| 02 | **Budget Baseline** | **Helio P22 / G35 / G25** (MT6762 / MT6765) | Cereus / Angelica | Redmi 6 / 9A / 9C | `MiCode/Xiaomi_Kernel_OpenSource:cereus-o-oss` |
| 03 | **Mass Budget Gaming**| **Helio G80 / G85** (MT6769) | Merlin / Lancelot | Redmi Note 9 / Redmi 9 | `MiCode/Xiaomi_Kernel_OpenSource:merlin-q-oss` |
| 04 | **Performance Heavy**| **Helio G90T / G95** (MT6785) | Begonia | Redmi Note 8 Pro / Realme 6 | `MiCode/Xiaomi_Kernel_OpenSource:begonia-q-oss` |
| 05 | **User Target** | **Helio G96** (MT6781) | Fleur / Miel | Redmi Note 11S / POCO M4 Pro 4G | `MiCode/Xiaomi_Kernel_OpenSource:fleur-s-oss` |
| 06 | **Global 4G King** | **Helio G99 / G100** (MT6789) | Hermes / Rock | POCO M5 / Infinix Hot 50 Pro+ / Tecno Camon 30S Pro | `MiCode/Xiaomi_Kernel_OpenSource:rock-s-oss` |
| 07 | **5G Entry Legend** | **Dimensity 700 / 810** (MT6833) | Camellian / Evergreen | POCO M3 Pro 5G / Redmi Note 10 5G| `MiCode/Xiaomi_Kernel_OpenSource:camellian-r-oss` |
| 08 | **Dimensity 7000 Series (PowerVR)** | **Dimensity 7020 / 930** (MT6855) | Cancun | Infinix Note 40 Pro 5G / Moto G54 | Transsion MT6855 OSS / Moto OSS |
| 09 | **User Device (Live)**| **Dimensity 920 / 7050** (MT6877 / MT6877V) | Infinix X698 / Realme | Infinix Zero 5G / Realme 11/12 Pro 5G | Transsion MT6877 OSS / Live ADB Device |
| 10 | **Dimensity 7000 Series (4nm)** | **Dimensity 7200-Ultra** (MT6886) | Zircon | Redmi Note 13 Pro+ 5G | `MiCode/Xiaomi_Kernel_OpenSource:zircon-t-oss` |
| 11 | **Dimensity 7000 Series (4nm Apex)** | **Dimensity 7300 / 7350** (MT6878) | Tonga | CMF Phone 1 / Nothing Phone (2a) Plus | MediaTek MT6878 OSS Architecture |
| 12 | **Midrange Powerhouse**| **Dimensity 1100** (MT6891) | Chopin | POCO X3 GT / Redmi Note 10 Pro 5G | `MiCode/Xiaomi_Kernel_OpenSource:chopin-r-oss` |
| 13 | **Upper Midrange** | **Dimensity 1200 / 1300** (MT6893) | Agate | Xiaomi 11T | `MiCode/Xiaomi_Kernel_OpenSource:agate-r-oss` |
| 14 | **High-Perf Legend** | **Dimensity 8100 / 8200** (MT6895) | Matisse / Rubens | POCO X4 GT / Redmi K50 | `MiCode/Xiaomi_Kernel_OpenSource:matisse-s-oss` |
| 15 | **Mid-Apex Gaming** | **Dimensity 8300-Ultra** (MT6897) | Duchamp | POCO X6 Pro / Redmi K70E | `MiCode/Xiaomi_Kernel_OpenSource:duchamp-u-oss` |
| 16 | **Modern Flagship** | **Dimensity 9200 / 9200+** (MT6985) | Corot | Xiaomi 13T Pro / Redmi K60 Ultra | `MiCode/Xiaomi_Kernel_OpenSource:corot-t-oss` |
| 17 | **Flagship Apex** | **Dimensity 9300** (MT6989) | Corot Apex | All-Big-Core Flagship Generation | `MiCode/Xiaomi_Kernel_OpenSource:corot-u-oss` |

---

## 📂 3. Repository Reference Index

Detailed node-by-node analyses and sysfs translation tables are organized into dedicated specification documents:

1. [`DTS_QUALCOMM_MATRIX.md`](./DTS_QUALCOMM_MATRIX.md): Complete comparative analysis of Qualcomm DTS nodes (Kryo cluster OPP, Adreno KGSL 3D, Devfreq interconnect buses, and thermal trip points).
2. [`DTS_MEDIATEK_MATRIX.md`](./DTS_MEDIATEK_MATRIX.md): Complete comparative analysis of MediaTek DTS nodes (Cortex OPP tables, PPM DVFS, ARM Mali vs PowerVR GPUs, GED, and `.tp` thermal zones).
3. [`SYSFS_GROUND_TRUTH_MAPPING.md`](./SYSFS_GROUND_TRUTH_MAPPING.md): Direct mapping table from kernel device tree registers to runtime Linux `/sys` and `/proc` paths manipulated by Lynx.
