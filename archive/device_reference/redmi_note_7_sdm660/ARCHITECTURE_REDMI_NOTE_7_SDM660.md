# 🏛️ Cetak Biru Arsitektur & Analisis Mendalam Perangkat: Xiaomi Redmi Note 7 (`lavender` — Qualcomm Snapdragon 660)

> **Dokumen Otoritas**: Master Hardware & Software Architecture Blueprint  
> **Target Perangkat**: Xiaomi Redmi Note 7 (`lavender` / `M1901F7G`)  
> **Platform SoC**: Qualcomm Snapdragon 660 (SDM660 — Octa-core Kryo 260 @ 14nm FinFET)  
> **Basis Audit**: 100% Real Physical Hardware Ground Truth (Passive Non-Invasive ADB & Native Lynx Daemon Probes)  
> **Tujuan Strategis**: Landasan ekspansi arsitektur universal Lynx untuk mendukung penuh lini SoC Qualcomm Snapdragon lintas generasi.

---

## 📑 Daftar Isi
1. [Ringkasan Eksekutif & Identitas Hardware](#1-ringkasan-eksekutif--identitas-hardware)
2. [Arsitektur SoC & Topologi CPU Cluster (Kryo 260 & EAS)](#2-arsitektur-soc--topologi-cpu-cluster-kryo-260--eas)
3. [Subsistem GPU & Grafis (Adreno 512 & KGSL 3D0)](#3-subsistem-gpu--grafis-adreno-512--kgsl-3d0)
4. [Subsistem Layar, Panel & Kalibrasi Warna (KCAL v2 & SurfaceFlinger)](#4-subsistem-layar-panel--kalibrasi-warna-kcal-v2--surfaceflinger)
5. [Subsistem Memori & Penyimpanan (LPDDR4, ZRAM, F2FS & Dynamic Partitions)](#5-subsistem-memori--penyimpanan-lpddr4-zram-f2fs--dynamic-partitions)
6. [Subsistem Termal & Mesin Throttling OEM (Thermal-Engine & 31 Zones)](#6-subsistem-termal--mesin-throttling-oem-thermal-engine--31-zones)
7. [Subsistem Sensor (InvenSense, AKM, Sensortek, FPC & Qualcomm SLPI DSP)](#7-subsistem-sensor-invensense-akm-sensortek-fpc--qualcomm-slpi-dsp)
8. [Subsistem Pengisian Daya & PMIC (PM660, PM660L, SMB2 & FG)](#8-subsistem-pengisian-daya--pmic-pm660-pm660l-smb2--fg)
9. [Audio, Jaringan & Periferal (QDSP6, IPA Offload, WCNSS & DWC3)](#9-audio-jaringan--periferal-qdsp6-ipa-offload-wcnss--dwc3)
10. [Rekomendasi Strategis & Integrasi Lynx Universal (Qualcomm Engine Roadmap)](#10-rekomendasi-strategis--integrasi-lynx-universal-qualcomm-engine-roadmap)

---

## 1. Ringkasan Eksekutif & Identitas Hardware

Berdasarkan hasil audit mendalam langsung dari perangkat fisik Redmi Note 7 (`192.168.1.19:5555`) yang diverifikasi melalui sistem deteksi hardware anti-spoofing kernel ground truth, berikut adalah matriks identitas perangkat:

| Parameter | Nilai Ground Truth Fisik | Catatan Teknis / Dampak Arsitektur |
| :--- | :--- | :--- |
| **Model Perangkat** | `Redmi Note 7` | Xiaomi M1901F7G |
| **Codename Perangkat** | `lavender` | `ro.product.device=lavender`, `ro.product.name=lineage_lavender` |
| **SoC Model / ID** | Qualcomm Snapdragon 660 (`SDM660` / `soc_id: 317`) | Revision 1.0, Foundry ID 3 (Samsung 14nm FinFET), MTP Platform |
| **Arsitektur CPU** | Octa-Core (4x Little Kryo 260 Silver + 4x Big Kryo 260 Gold) | Heterogeneous Multi-Processing (HMP/EAS), 64-bit ARMv8-A (AArch64) |
| **GPU / Driver** | Qualcomm Adreno 512 @ 5000000.qcom,kgsl-3d0 | Driver KGSL (Kernel Graphics Support Layer), Devfreq Bus `soc:qcom,gpubw` |
| **Sistem Operasi (ROM)**| Android 15 VanillaIceCream (`BP1A.250505.005 release-keys`) | Mod Version 11.7.0 (crDroid / LineageOS 22 base), SDK API 35 |
| **Versi Kernel** | `Linux 4.19.325-st19-San-Kernel-Aegis-R1.1.108` | Neutron Clang 24.0.0git + LLD 24.0.0, SMP PREEMPT 64-bit |
| **SELinux & Keamanan** | `Enforcing` (AVB 1.3, vbmeta locked) | Strict SELinux Policy, Page Size 4096 (4KB) |
| **Tata Letak Partisi** | Dynamic Partitions Retrofit (`super` on `system`) | `/data` & `/cache` berformat `f2fs`, sistem berformat `ext4` |
| **Primary & Sub PMIC** | Qualcomm PM660 & PM660L | SPMI bus slave interface (`spmi0-00`) |
| **Kapasitas RAM / ZRAM**| 4 GB LPDDR4 (3751688 kB) / 4 GB ZRAM Swap | ZRAM Swappiness 80, Dirty Ratio 20/10 |

```mermaid
graph TD
    subgraph SoC["Qualcomm Snapdragon 660 (14nm FinFET - soc_id: 317)"]
        subgraph CPU["Kryo 260 Octa-Core CPU Complex"]
            C0["Core 0: Silver (A53) 1.84 GHz"]
            C1["Core 1: Silver (A53) 1.84 GHz"]
            C2["Core 2: Silver (A53) 1.84 GHz"]
            C3["Core 3: Silver (A53) 1.84 GHz"]
            C4["Core 4: Gold (A73) 2.46 GHz"]
            C5["Core 5: Gold (A73) 2.46 GHz"]
            C6["Core 6: Gold (A73) 2.46 GHz"]
            C7["Core 7: Gold (A73) 2.46 GHz"]
        end
        GPU["Adreno 512 GPU (/dev/kgsl-3d0)"]
        DSP["Hexagon 680 QDSP6 (Audio + SLPI Sensor Core)"]
        MODEM["Snapdragon X12 LTE Modem (rmnet_ipa0)"]
        ISP["Spectra 160 ISP (Dual 14-bit)"]
        MEMCTRL["LPDDR4 Memory Controller (Dual-Channel 1866 MHz)"]
    end
    PM660["Qualcomm PM660 PMIC (Charger SMB2 + FG Gen3)"]
    PM660L["Qualcomm PM660L Sub-PMIC (Display Backlight + Regulators)"]
    DRAM["4GB LPDDR4 RAM"]
    EMMC["64GB eMMC 5.1 Storage (c0c4000.sdhci)"]
    PANEL["6.3\" 1080x2340 FHD+ IPS Panel (KCAL v2)"]
    
    SoC <--> DRAM
    SoC <--> EMMC
    SoC --> PANEL
    SoC <--> PM660
    SoC <--> PM660L
```

---

## 2. Arsitektur SoC & Topologi CPU Cluster (Kryo 260 & EAS)

### 2.1 Spesifikasi Inti & Topologi Cluster
Snapdragon 660 menggunakan arsitektur semi-kustom Qualcomm **Kryo 260** yang terbagi dalam dua cluster fisik dengan asimetri efisiensi dan performa (big.LITTLE):

```
========================================================================================
CLUSTER 0 (Little / Efisiensi) — policy0
========================================================================================
• Cores Fisik          : CPU0, CPU1, CPU2, CPU3 (Mask: 0x0f)
• Microarchitecture    : Semi-custom ARM Cortex-A53 (Implementer: 0x51 QTI, Part: 0x801, Var: 0xa, Rev: 4)
• Kapasitas Energi     : cpu_capacity = 476 (46.5% dari kapasitas puncak)
• Frekuensi Operasional: 633.6 MHz – 1843.2 MHz (7 OPP Steps)
  [OPP 0: 633.6 MHz] [OPP 1: 902.4 MHz] [OPP 2: 1113.6 MHz] [OPP 3: 1401.6 MHz]
  [OPP 4: 1536.0 MHz] [OPP 5: 1747.2 MHz] [OPP 6: 1843.2 MHz]
• CPUFreq Driver       : msm (Qualcomm MSM CPUFreq)
• Default Governor     : schedutil (Energy Aware Scheduling)
• Schedutil Tunables   : up_rate_limit_us = 500, down_rate_limit_us = 10000

========================================================================================
CLUSTER 1 (Big / Performa) — policy4
========================================================================================
• Cores Fisik          : CPU4, CPU5, CPU6, CPU7 (Mask: 0xf0)
• Microarchitecture    : Semi-custom ARM Cortex-A73 (Implementer: 0x51 QTI, Part: 0x800, Var: 0xa, Rev: 2)
• Kapasitas Energi     : cpu_capacity = 1024 (100% kapasitas puncak)
• Frekuensi Operasional: 1113.6 MHz – 2457.6 MHz (6 OPP Steps — Overclocked Aegis Binning!)
  [OPP 0: 1113.6 MHz] [OPP 1: 1401.6 MHz] [OPP 2: 1747.2 MHz]
  [OPP 3: 1958.4 MHz] [OPP 4: 2150.4 MHz] [OPP 5: 2457.6 MHz (OC)]
• CPUFreq Driver       : msm (Qualcomm MSM CPUFreq)
• Default Governor     : schedutil (Energy Aware Scheduling)
• Schedutil Tunables   : up_rate_limit_us = 500, down_rate_limit_us = 10000
========================================================================================
```

### 2.2 Analisis Schedutil & Energy Aware Scheduling (EAS)
1. **Rasio Rate Limit Asimetris**:
   - `up_rate_limit_us = 500 μs`: Responsivitas ekstrem terhadap beban mendadak (*spike*). Hanya butuh 0.5 ms bagi scheduler untuk menaikkan frekuensi clock saat thread UI mendeteksi event sentuhan atau frame render.
   - `down_rate_limit_us = 10000 μs (10 ms)`: Jendela pendinginan (*hysteresis*) yang cukup lebar untuk mencegah *frequency oscillation* (turun lalu naik lagi secara berulang), sehingga menjaga kelancaran 60 FPS pada beban berfluktuasi tanpa micro-stutter.
2. **Karakteristik Overclock Aegis Kernel**:
   - Frekuensi standar pabrikan Qualcomm SDM660 pada Big cluster adalah 2.20 GHz (`2208000 kHz`).
   - Kernel Aegis R1.1.108 yang terpasang pada perangkat ini telah di-overclock hingga **2.457 GHz (`2457600 kHz`)** (+11.7% raw compute power).
   - Pengaturan frekuensi minimum Big cluster dikunci pada **1113.6 MHz** (`1113600 kHz`), yang secara signifikan mengurangi latensi saat thread performa dipromosikan ke Big cores.

### 2.3 Pembagian Afinitas Cgroup Cpuset
Berdasarkan data langsung dari `lynxd cpuset info`:
- `top-app` (Aplikasi aktif layar utama): `0-7` (Akses ke seluruh 8 core).
- `foreground` (Layanan latar depan aktif): `0-7` (Akses ke seluruh 8 core).
- `background` (Layanan latar belakang / sync): `0-3` (Secara ketat diisolasi **HANYA** pada Little cluster demi penghematan daya).
- `system-background` (Daemon sistem internal): `0-3` (Diisolasi pada Little cluster).
- `restricted` (Proses dengan pembatasan ketat): `0-1` (Hanya 2 core Little).
- `camera-daemon` (ISP & Frame Pipeline): `0-3, 5-7` (Core 4 sengaja dikosongkan untuk mencegah interferensi latensi).

---

## 3. Subsistem GPU & Grafis (Adreno 512 & KGSL 3D0)

### 3.1 Arsitektur Driver Adreno & KGSL
Qualcomm mengimplementasikan subsistem grafis melalui driver **KGSL (Kernel Graphics Support Layer)** yang terpasang pada node fisik `/dev/kgsl-3d0` (platform bus `5000000.qcom,kgsl-3d0`):
- **Arsitektur GPU**: Qualcomm Adreno 512 (DirectX 12, Vulkan 1.1, OpenGL ES 3.2, OpenCL 2.0).
- **GPU Governor Aktif**: `msm-adreno-tz` (Qualcomm TrustZone Co-processor Dynamic Frequency Scaling).
- **Governor yang Didukung**: `msm-adreno-tz`, `performance`, `powersave`, `simple_ondemand`.
- **Bus Devfreq Terkait**:
  - `soc:qcom,gpubw` (Devfreq bus bandwidth GPU ke DRAM BIMC).
  - Mengatur throughput memori secara dinamis seiring peningkatan rendering workload GPU.

### 3.2 Parameter Kontrol Sysfs KGSL untuk Lynx
Pada kernel Linux 4.19 Android 15 dengan SELinux Enforcing, direktori `/sys/class/kgsl/kgsl-3d0/` memiliki proteksi izin `0660` / `0600` yang hanya dapat diakses oleh daemon root (`lynxd`). Parameter penting yang tersedia:
- `gpuclk` / `devfreq/cur_freq`: Membaca frekuensi clock fisik GPU saat ini.
- `gpu_available_frequencies`: Daftar tabel frekuensi GPU (OPP levels).
- `pwrscale`: Pengontrol penskalaan daya GPU.
- `thermal_pwrlevel` & `throttling`: Indikator throttling termal GPU dari thermal engine.
- `min_pwrlevel` & `max_pwrlevel`: Pengunci batas level daya minimum dan maksimum (inversi level: pwrlevel 0 = frekuensi tertinggi, pwrlevel 5/6 = frekuensi terendah).
- `idle_timer`: Waktu idle sebelum GPU memasuki status low-power sleep (standar ~64ms).
- `bus_split`, `force_bus_on`, `force_clk_on`, `force_rail_on`: Kontrol pemaksaan rail daya dan clock saat profil Extreme aktif.

---

## 4. Subsistem Layar, Panel & Kalibrasi Warna (KCAL v2 & SurfaceFlinger)

### 4.1 Spesifikasi Panel Fisik & Sinyal Vsync
Berdasarkan telemetri langsung dari `dumpsys SurfaceFlinger`:
- **Dimensi Resolusi**: `1080 x 2340` piksel (FHD+, rasio aspek 19.5:9).
- **Kerapatan Piksel (DPI)**: `409.43 x 409.90` (409 PPI).
- **Laju Penyegaran Fisik**: `60.00 Hz` (Vsync interval ~16.66 ms).
- **Display Layer Mixer**: `LM_Split: false` (Single Layer Mixer, menyederhanakan composition pass).
- **Color Mode Aktif**: `hal_srgb` (DisplayColorSetting: Managed).
- **Kompositor Render Engine**: SkiaGL Threaded (`skiaglthreaded`), HWUI Renderer `skiagl`.

### 4.2 Integrasi Kalibrasi Warna KCAL v2
Kernel Aegis pada perangkat ini mengompilasi driver **KCAL v2 (Qualcomm MDP Color Calibration Controller)** yang berlokasi di:
`/sys/devices/platform/kcal_ctrl.0/`

Node kontrol yang diverifikasi ada secara fisik:
- `kcal`: Matriks kalibrasi warna RGB (Format: `R G B`, contoh: `256 256 256`).
- `kcal_cont`: Kontras layar (Rentang: `128 - 383`, default `255`).
- `kcal_val`: Brightness / Value (Rentang: `128 - 383`, default `255`).
- `kcal_sat`: Saturasi warna (Rentang: `224 - 383`, default `255` untuk sRGB natural, `275+` untuk vivid OLED-like).
- `kcal_hue`: Hue shift (Rentang: `0 - 1536`).
- `kcal_min`: Nilai batas kecerahan minimum RGB.
- `kcal_enable`: Toggle master driver KCAL (`1` = aktif, `0` = bypass).

> [!TIP]
> **Peluang Lynx**: LynxCompanion (dan lynxd) dapat mengekspos kartu kontrol warna mendalam di Tab Display menggunakan antarmuka KCAL ini untuk tuning layar IPS Redmi Note 7 agar menghasilkan warna pekat setara panel OLED.

---

## 5. Subsistem Memori & Penyimpanan (LPDDR4, ZRAM, F2FS & Dynamic Partitions)

### 5.1 Arsitektur Memori Utama (RAM & ZRAM)
- **RAM Fisik**: 4 GB LPDDR4 Dual-Channel (Total terdaftar: `3,751,688 kB` / ~3.66 GB usable, sisanya dialokasikan untuk modem carveout, Hexagon DSP, dan CMA).
- **CMA (Contiguous Memory Allocator)**: `278,528 kB` (272 MB) dialokasikan khusus untuk buffer kamera ganda 48MP Samsung GM1 dan decoder video hardware.
- **ZRAM Swap**: `4,194,300 kB` (~4 GB ZRAM Swap Space), terpasang aktif di `/dev/block/zram0`.
- **Pengaturan Virtual Memory (VM) Terverifikasi**:
  - `swappiness = 80`: Keseimbangan agresif antara retensi cache halaman file dan swapping anonim ke ZRAM berkecepatan tinggi.
  - `dirty_ratio = 20` & `dirty_background_ratio = 10`: Mencegah penumpukan buffer dirty page yang dapat memicu I/O freeze.
  - `vfs_cache_pressure = 100`: Reclaim dentry dan inode seimbang dengan page cache.
  - `dirty_expire_centisecs = 500` (5 detik) & `dirty_writeback_centisecs = 200` (2 detik).

### 5.2 Topologi Penyimpanan eMMC & Filesystem F2FS
- **Media Penyimpanan**: eMMC 5.1 (Pengontrol: Qualcomm SDHCI @ `c0c4000.sdhci`).
- **Partisi Dinamis (Retrofit Dynamic Partitions)**:
  - Super partition di-retrofit ke partisi `system` lama.
  - Virtual logical partitions dipetakan via Device Mapper:
    - `/` (System): `/dev/block/dm-0` (`ext4`, read-only, noatime)
    - `/vendor`: `/dev/block/dm-1` (`ext4`, read-only, noatime)
    - `/product`: `/dev/block/dm-2` (`ext4`, read-only, noatime)
    - `/odm`: `/dev/block/dm-3` (`ext4`, read-only, noatime)
    - `/system_ext`: `/dev/block/dm-4` (`ext4`, read-only, noatime)
- **Partisi Pengguna `/data`**:
  - Terpasang pada node fisik `/dev/block/mmcblk0p66`.
  - Menggunakan format modern **F2FS (Flash-Friendly File System)** dengan flag performa tinggi:
    `rw,lazytime,noatime,background_gc=off,gc_merge,discard,discard_unit=block,inlinecrypt,mode=adaptive,active_logs=6,checkpoint_merge,fsync_mode=nobarrier,atgc`
  - Partisi `/cache` (`mmcblk0p62`) juga berformat **F2FS**.

---

## 6. Subsistem Termal & Mesin Throttling OEM (Thermal-Engine & 31 Zones)

### 6.1 Peta 31 Thermal Zones Terverifikasi
Qualcomm SDM660 mengintegrasikan sensor termal onboard (TSENS) dan ADC thermistor eksternal yang dibagi ke dalam 31 thermal zone:

| Zone ID | Nama Node (`type`) | Perangkat Fisik yang Dipantau | Kebijakan Kernel |
| :--- | :--- | :--- | :--- |
| **`thermal_zone0`** | `pm660-tz` | Die PMIC Primer Qualcomm PM660 | `step_wise` |
| **`thermal_zone1`** | `pm660l-tz` | Die Sub-PMIC Qualcomm PM660L | `step_wise` |
| **`thermal_zone2`** | `xo-therm-adc` | Crystal Oscillator Thermistor | `step_wise` |
| **`thermal_zone3`** | `msm-therm-adc` | Motherboard / SoC Thermistor Utama | `step_wise` |
| **`thermal_zone4`** | `quiet-therm-adc` | Skin / Surface Thermistor (Sensor Throttling Utama) | `step_wise` |
| **`thermal_zone5`** | `mpm-usr` | Multi-Processing Power Manager | `step_wise` |
| **`thermal_zone6-7`** | `cpuss-0-usr`, `cpuss-1-usr` | CPU Subsystem Little Cluster Sensors | `step_wise` |
| **`thermal_zone8-11`**| `cpu-1-0-usr` s/d `cpu-1-3-usr`| Sensor Per-Core Big Cluster (Core 4, 5, 6, 7) | `step_wise` |
| **`thermal_zone12`** | `cpuss-2-usr` | CPU Subsystem Logic Sensor | `step_wise` |
| **`thermal_zone13`** | `gpu-usr` | Core Adreno 512 GPU Sensor | `step_wise` |
| **`thermal_zone14`** | `video-usr` | Hardware Video Encoder/Decoder (VPU) | `step_wise` |
| **`thermal_zone15`** | `mdm-core-usr` | Modem Baseband Processor | `step_wise` |
| **`thermal_zone16`** | `camera-usr` | Spectra ISP Camera Processor | `step_wise` |
| **`thermal_zone17`** | `cpuss-3-usr` | CPU Core Subsystem Logic | `step_wise` |
| **`thermal_zone18`** | `cdsp-usr` | Hexagon Compute DSP Sensor | `step_wise` |
| **`thermal_zone19`** | `gpu-step` | GPU Stepping Evaluation Zone | `step_wise` |
| **`thermal_zone20`** | `quiet-therm-step` | Skin Thermistor Stepping Evaluation | `step_wise` |
| **`thermal_zone21-25`**| `cpuss-0-step`, `cpu-1-X-step`| CPU Cores Stepping Triggers | `step_wise` |
| **`thermal_zone26-27`**| `mpm-lowf`, `camera-lowf` | Low Frequency Protection Zones | `low_limits_floor` |
| **`thermal_zone28`** | `backlight_therm` | Display Backlight LED Thermistor | `step_wise` |
| **`thermal_zone29`** | `battery` | Battery Pack NTC Thermistor | `step_wise` |
| **`thermal_zone30`** | `bms` | Battery Management System Sensor | `step_wise` |

### 6.2 Cooling Devices & Mekanisme Throttling
Terdapat 13 perangkat pendingin (*cooling devices*):
- `cooling_device0`: `thermal-cpufreq-0` (Throttling cluster Little, max 6 level).
- `cooling_device1`: `thermal-cpufreq-4` (Throttling cluster Big, max 5 level).
- `cooling_device2`: `thermal-devfreq-0` (Throttling bus bandwidth & GPU, max 6 level).
- `cooling_device3`: `cx-cdev` (Voltage rail restriction).
- `cooling_device4`: `battery` (Throttling arus pengisian daya, max 7 level).
- `cooling_device5 - 12`: `cpu-isolate0` s/d `cpu-isolate7` (Hotplug isolation per core).

### 6.3 Analisis Kritis Konfigurasi OEM (`/vendor/etc/thermal-engine.conf`)
Audit isi file konfigurasi thermal engine Xiaomi mengungkap aturan throttling bawaan yang sangat agresif:
1. **Throttling Arus Pengisian (`BATTERY_CHARGING_CTL`)**:
   - Memantau `quiet-therm-adc` (skin temp).
   - Ambang batas: **37°C, 38°C, 41°C, 42°C, 43°C**.
   - Pada suhu skin hanya 37°C, pengisian daya langsung diturunkan dari Fast Charging ke level 1. Pada 43°C, arus dicekik ke level terendah.
2. **Throttling CPU Big Cluster (`SS-MSM-THERM-GOLD`)**:
   - Memantau `msm-therm-adc`.
   - Setpoint 1: **44°C** ➔ Frekuensi Big cluster dibatasi maksimal **1.958 GHz** (`1958400 kHz`).
   - Setpoint 2: **46°C** ➔ Frekuensi Big cluster dicekik ke **1.401 GHz** (`1401600 kHz`).
   - Setpoint 3: **48°C** ➔ Frekuensi Big cluster dicekik parah ke **1.113 GHz** (`1113600 kHz`)!
3. **Throttling CPU Little Cluster (`SS-MSM-THERM-SILVER`)**:
   - Setpoint 1: **45°C** ➔ Dibatasi ke **1.536 GHz**.
   - Setpoint 2: **47°C** ➔ Dibatasi ke **1.113 GHz**.
4. **Shutdown Darurat (`SKIN-SHUTDOWN`)**:
   - Memantau `msm-therm-adc` pada ambang **76°C** untuk mematikan perangkat.

> [!WARNING]
> Throttling OEM Xiaomi SDM660 sangat membatasi potensi hardware. Pada suhu 48°C (suhu wajar saat bermain game berat), CPU Big cluster dicekik hingga 55% kemampuannya (dari 2.45 GHz ke 1.11 GHz). Inilah penyebab utama frame-drop pada Redmi Note 7. Lynx Extreme Mode dapat menimpa konfigurasi ini via `platforms/qcom/system/vendor/etc/thermal-engine.conf`.

---

## 7. Subsistem Sensor (InvenSense, AKM, Sensortek, FPC & Qualcomm SLPI DSP)

Berdasarkan dump lengkap `dumpsys sensorservice`, Redmi Note 7 memiliki **44 sensor virtual & fisik** yang diproses oleh **Qualcomm Snapdragon Sensor Core (SSC / SLPI DSP)** berdaya sangat rendah:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        DAFTAR SENSOR FISIK UTAMA (HARDWARE)                            │
├────────────────────────────────┬─────────────────┬──────────┬──────────────┬───────────┤
│ Nama Sensor                    │ Manufaktur / IC │ Tipe     │ Frekuensi    │ Ukuran    │
│                                │                 │ Android  │ Maksimal     │ FIFO      │
├────────────────────────────────┼─────────────────┼──────────┼──────────────┼───────────┤
│ ICM20607 Accelerometer         │ InvenSense      │ TYPE_1   │ 250.00 Hz    │ 40,960 evt│
│ ICM20607 Gyroscope             │ InvenSense      │ TYPE_4   │ 250.00 Hz    │ 40,960 evt│
│ AK09918 3-Axis Magnetometer    │ AKM             │ TYPE_2   │ 50.00 Hz     │ 40,960 evt│
│ STK3X3X Ambient Light (alsprx) │ Sensortek       │ TYPE_5   │ On-change    │ 40,960 evt│
│ STK3X3X Proximity Sensor       │ Sensortek       │ TYPE_8   │ On-change    │ 41,912 evt│
│ Fingerprint Scanner (Rear SPI) │ FingerprintCards│ FPC1020  │ Interrupt    │ Dedicated │
└────────────────────────────────┴─────────────────┴──────────┴──────────────┴───────────┘
```

### Sensor Fusi & DSP Offload (Qualcomm SLPI):
Seluruh sensor komposit dijalankan langsung di dalam firmware Hexagon DSP (bukan di CPU inti):
- `Gravity`, `Linear Acceleration`, `Rotation Vector`, `Game Rotation Vector`, `GeoMagnetic Rotation Vector`, `Orientation`.
- `Step Detector`, `Step Counter`, `Significant Motion Detector`, `Tilt Detector`.
- `FIH PickUp Gesture`, `QTI Stationary Detector`, `QTI Motion Detector`.
- Sensor proprietari QTI: `AMD` (Absolute Motion Detector), `RMD` (Relative Motion Detector), `Pedometer`, `Motion Accel`, `PMD` (Persistent Motion Detector).

---

## 8. Subsistem Pengisian Daya & PMIC (PM660, PM660L, SMB2 & FG)

Audit telemetri baterai dan driver pengisian daya mengonfirmasi topologi berikut:
- **Kapasitas Baterai**: Baterai Li-Polymer 4000 mAh (Model Xiaomi: `BN4A`).
- **Kondisi Kesehatan Saat Audit**:
  - Tegangan Saat Ini: `4060 mV` (4.06 V pada 81% level).
  - Suhu Baterai: `39.5°C`.
  - Kapasitas Desain: `4000 mAh`.
  - Kapasitas Maksimal Saat Ini: `3191 mAh` (~80% retensi kapasitas / baterai telah terpakai wajar).
- **Pengontrol Pengisian Daya**: Qualcomm QPNP-SMB2 (Switch-Mode Battery Charger).
  - Mendukung Quick Charge 3.0 / HVDCP 3.0 (tegangan dinamis 5V–12V via modulasi D+/D-).
  - Driver kernel: `drivers/power/supply/qcom/smb-lib.c`.
- **Topologi Jalur Daya**:
  - Arsitektur **Single-Path BATTFET**: Rel sistem (`VSYS`) terhubung langsung ke sel baterai (`VBAT`) melalui sakelar FET internal PMIC.
  - **TIDAK TERDAPAT sakelar bypass fisik terpisah** (sama seperti karakteristik kebanyakan ponsel di kelasnya).
  - Bypass pengisian daya Lynx diimplementasikan melalui modulasi `CHG_DISABLE` dan pembatasan `USB_ICL` / `FCC` ke 0-100mA saat baterai mencapai target limit.

---

## 9. Audio, Jaringan & Periferal (QDSP6, IPA Offload, WCNSS & DWC3)

### 9.1 Audio Subsystem (Qualcomm Hexagon QDSP6 & APR)
- **Sound Card**: `card0` terhubung ke `soc:qcom,msm-audio-apr:qcom,q6core-audio:sound/sound/card0`.
- Memanfaatkan **Asynchronous Packet Router (APR)** untuk merutekan aliran audio langsung ke Hexagon QDSP6 DSP tanpa membebani CPU Kryo.
- Mendukung pemutaran compressed audio offload (`comprC0D8`, `comprC0D15`, `comprC0D28-45`) untuk konsumsi daya minimal saat memutar musik di latar belakang.

### 9.2 Jaringan & Offload Data (Qualcomm IPA & WCNSS)
- **Cellular Data Offload**: Qualcomm **IPA (IP Accelerator)** hardware offload melalui interface `rmnet_ipa0` dan sub-channel `rmnet_data0` / `rmnet_data1`.
- **Wi-Fi Driver**: Qualcomm WCNSS / Atheros driver pada interface `wlan0` (qdisc `fq_codel`, MTU 1500, queue length 3000).
- **USB Controller**: Synopsys DesignWare Core 3 (`a800000.dwc3`) USB 2.0 / OTG controller.

---

## 10. Rekomendasi Strategis & Integrasi Lynx Universal (Qualcomm Engine Roadmap)

Temuan audit fisik pada Redmi Note 7 ini memberikan fondasi konkrit untuk memperluas arsitektur **Lynx Universal Engine** agar mendukung seluruh keluarga Snapdragon secara native:

### 10.1 Manajemen Izin Node KGSL Adreno
- **Masalah**: Pada Android 15 Enforcing, sysfs `/sys/class/kgsl/kgsl-3d0/` berizin ketat sehingga aplikasi UI unprivileged tidak dapat membaca frekuensi secara langsung.
- **Solusi Lynx**: `lynxd` (yang berjalan sebagai root) wajib bertindak sebagai penyedia telemetri GPU tunggal melalui perintah `lynxd gpu info` atau UNIX domain socket, lalu mengalirkan status frekuensi, load, dan governor ke LynxCompanion.

### 10.2 Penyesuaian Profil Schedutil EAS untuk Snapdragon
- **Profil Performa / Gaming**:
  - Turunkan `down_rate_limit_us` dari `10000` ke `2000` μs saat game aktif agar frekuensi bertahan di clock tinggi.
  - Naikkan `up_rate_limit_us` ke `250` μs (ultra-fast ramp-up).
- **Profil Balance / Efisiensi**:
  - Pertahankan default `up_rate_limit_us = 500` dan `down_rate_limit_us = 10000`.

### 10.3 Pemanfaatan KCAL v2 di LynxCompanion
- Menambahkan kartu kalibrasi warna KCAL pada domain `ColorManagementCard` di aplikasi Android Compose untuk mengontrol `/sys/devices/platform/kcal_ctrl.0/` (RGB gain, saturasi, kontras, dan value).

### 10.4 Mitigasi Throttling Termal Agresif
- Memanfaatkan berkas overlay vendor `platforms/qcom/system/vendor/etc/thermal-engine.conf` untuk menaikkan trip point Big cluster dari **44°C/46°C/48°C** ke **55°C/60°C/65°C** pada Mode Extreme, sehingga pengguna dengan pendingin eksternal (*phone cooler*) dapat menikmati performa stabil 60 FPS tanpa penurunan clock ke 1.1 GHz.

---

> **Status Arsip**: Terverifikasi Valid & Tersimpan di `archive/device_reference/redmi_note_7_sdm660/ARCHITECTURE_REDMI_NOTE_7_SDM660.md`  
> **Lynx Universal Development Team** — *Engineering Excellence for High-Performance Mobile Root Ecosystems*
