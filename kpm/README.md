# ⚡ Lynx Universal — Kernel Patch Module (KPM) & Loadable Kernel Modules (LKM)
> **Platform Target**: Infinix Note 11 Pro (`X698`) — MediaTek Helio G96 (`mt6781`)  
> **Kernel Target**: Linux 4.14.186 (aarch64)  
> **Root Engine**: APatch (`apd` / `kpatch`) & Magisk / KernelSU Compatible  
> **Status**: Modul Ringan Terpisah (Modular Independent Load/Unload)  

---

## 📌 1. Ikhtisar Arsitektur Modular

KPM (Kernel Patch Module) Lynx dirancang secara **terpisah per-fitur**. Setiap fitur memiliki modul `.c`, `Makefile`, serta skrip `load.sh` dan `unload.sh` mandiri sehingga pengguna dapat memuat (*load*), menguji, atau melepas (*unload*) bagian tertentu tanpa memengaruhi modul lainnya.

```
/storage/emulated/0/Lynx/KPM/
├── README.md                      # Panduan lengkap ini
├── load_all.sh                    # Muat seluruh modul sekaligus
├── unload_all.sh                  # Lepas seluruh modul sekaligus
├── status.sh                      # Periksa modul yang aktif & baca telemetri ring-0
├── Makefile                       # Top-level Makefile
├── build_all.sh                   # Script kompilasi NDK/Clang
│
├── 01_eas_sentinel/               # [Fitur 1] Penjaga Margin EAS & Schedtune
│   ├── eas_sentinel.c             # Source code kernel module C
│   ├── Makefile                   # Build configuration
│   ├── load.sh                    # Script load mandiri
│   └── unload.sh                  # Script unload mandiri
│
├── 02_thermal_silencer/           # [Fitur 2] Peredam Throttling Termal Agresif
│   ├── thermal_silencer.c
│   ├── Makefile
│   ├── load.sh
│   └── unload.sh
│
├── 03_gpu_dvfs_lock/              # [Fitur 3] Pengunci Lantai Frekuensi GPU Mali G57
│   ├── gpu_dvfs_lock.c
│   ├── Makefile
│   ├── load.sh
│   └── unload.sh
│
└── 04_mem_cushion/                # [Fitur 4] Penyangga Memori Anti-Allocstall
    ├── mem_cushion.c
    ├── Makefile
    ├── load.sh
    └── unload.sh
```

---

## 🛠️ 2. Rincian Fitur Per-Modul

### 1️⃣ `01_eas_sentinel` (EAS Schedtune & Priority Guardian)
- **Tujuan**: Mencegah Android framework (`OomAdjuster`) dan daemon vendor MediaTek (`perfservice`) menghapus boost prioritas game saat ada notifikasi heads-up atau pesan masuk.
- **Mekanisme**: Bekerja di kernel space memantau status `top-app` schedtune boost. Jika terdeteksi penurunan nilai boost di bawah batas gaming (< 35%), penjaga seketika memulihkan EAS boost kembali ke target profil.
- **Node Procfs**: `/proc/lynx/eas_sentinel`
- **Kontrol Dinamis**:
  ```bash
  # Ubah target boost (0 - 100%)
  echo "boost=50" > /proc/lynx/eas_sentinel
  # Matikan sementara / aktifkan kembali
  echo 0 > /proc/lynx/eas_sentinel
  echo 1 > /proc/lynx/eas_sentinel
  ```

---

### 2️⃣ `02_thermal_silencer` (Thermal Throttling Trip-Point Silencer)
- **Tujuan**: Meredam downclock agresif dan penonaktifan core CPU/GPU oleh OEM thermal policy saat bermain game berat.
- **Mekanisme**: Memanipulasi soft thermal trip-points pada zona termal MediaTek (`mtkts_*`), memberikan margin pendinginan +15°C tanpa mematikan proteksi kritis PMIC (shutdown darurat hardware pada suhu 115°C tetap aktif 100% demi keamanan fisik baterai dan SoC).
- **Node Procfs**: `/proc/lynx/thermal_silencer`
- **Kontrol Dinamis**:
  ```bash
  # Ubah margin kompensasi suhu (0 - 30°C)
  echo "offset=20" > /proc/lynx/thermal_silencer
  # Toggle aktivasi
  echo 0 > /proc/lynx/thermal_silencer
  echo 1 > /proc/lynx/thermal_silencer
  ```

---

### 3️⃣ `03_gpu_dvfs_lock` (ARM Mali G57 MC2 DVFS Floor Lock)
- **Tujuan**: Mengunci lantai frekuensi GPU ARM Mali-G57 MC2 pada MediaTek Helio G96.
- **Mekanisme**: Mencegah driver GPU dan MediaTek GED menurunkan frekuensi ke 300 MHz saat terjadi transisi scene, loading assets, atau popup UI yang kerap memicu drop FPS sesaat (*1% low frame dip*).
- **Node Procfs**: `/proc/lynx/gpu_dvfs_lock`
- **Kontrol Dinamis**:
  ```bash
  # Kunci frekuensi lantai GPU ke 850 MHz atau 950 MHz puncak
  echo "freq=950" > /proc/lynx/gpu_dvfs_lock
  echo "freq=850" > /proc/lynx/gpu_dvfs_lock
  # Kembalikan ke auto DVFS bawaan
  echo 0 > /proc/lynx/gpu_dvfs_lock
  ```

---

### 4️⃣ `04_mem_cushion` (Anti-Allocstall Kernel Memory Buffer)
- **Tujuan**: Menghilangkan fenomena micro-stutter akibat direct page compaction dan `allocstall_movable` saat RAM menipis (ZRAM 90%+).
- **Mekanisme**: Menyediakan bantalan memori asinkron (default 64MB) di level kernel memory allocator, memastikan thread rendering game tidak pernah tertahan (*blocked*) oleh proses pembersihan memori sinkron.
- **Node Procfs**: `/proc/lynx/mem_cushion`
- **Kontrol Dinamis**:
  ```bash
  # Atur ukuran buffer bantalan (16 - 256 MB)
  echo "cushion=128" > /proc/lynx/mem_cushion
  # Toggle proteksi
  echo 0 > /proc/lynx/mem_cushion
  echo 1 > /proc/lynx/mem_cushion
  ```

---

## 🚀 3. Cara Memuat & Melepas Modul (Load / Unload)

Seluruh operasi wajib dijalankan dengan hak akses **Root (`su`)** via Termux, MT Manager Terminal, atau ADB Shell:

### A. Operasi Mandiri Per-Fitur
```bash
# Masuk ke direktori KPM
cd /storage/emulated/0/Lynx/KPM

# [Fitur 1: EAS Sentinel]
sh 01_eas_sentinel/load.sh       # Muat EAS Sentinel
sh 01_eas_sentinel/unload.sh     # Lepas EAS Sentinel

# [Fitur 2: Thermal Silencer]
sh 02_thermal_silencer/load.sh   # Muat Thermal Silencer
sh 02_thermal_silencer/unload.sh # Lepas Thermal Silencer

# [Fitur 3: GPU DVFS Lock]
sh 03_gpu_dvfs_lock/load.sh      # Muat GPU DVFS Lock
sh 03_gpu_dvfs_lock/unload.sh    # Lepas GPU DVFS Lock

# [Fitur 4: Memory Cushion]
sh 04_mem_cushion/load.sh        # Muat Memory Cushion
sh 04_mem_cushion/unload.sh      # Lepas Memory Cushion
```

### B. Operasi Global (Semua Fitur)
```bash
cd /storage/emulated/0/Lynx/KPM

# Muat semua modul sekaligus:
sh load_all.sh

# Periksa status telemetri modul aktif:
sh status.sh

# Lepas semua modul sekaligus:
sh unload_all.sh
```

---

## 🔍 4. Metode Pemuatan yang Didukung (Multi-Loader)

Skrip `load.sh` pada setiap modul telah dilengkapi dengan hirarki pemuat otomatis bertingkat:
1. **APatch Jailbreak Engine (`apd insmod <file.ko>`)**:
   - Melewati pemeriksaan string `vermagic` kernel Linux, memungkinkan pemuatan modul tanpa harus mencocokkan hash versi kernel persis karakter per karakter.
2. **KernelPatch Module Manager (`kpatch <key> kpm load <file.ko>`)**:
   - Antarmuka KernelPatch resmi yang berjalan langsung di memori boot.
3. **Standar Linux In-Tree (`insmod <file.ko>`)**:
   - Menggunakan syscall bawaan Linux `init_module()` standar.

---

## 🌐 5. Panduan Lanjutan: Custom Kernel Build Penuh (Full Source)

Bagi pengembang yang ingin melangkah lebih jauh melakukan **Kompilasi Kernel Penuh (Full Custom Kernel Build)** untuk Infinix Note 11 Pro (Helio G96 / MT6781):

### 1. Toolchain & Compiler
- Disarankan menggunakan **LLVM / Clang 18+** dengan target `aarch64-linux-android-`.
- Aktifkan flag optimasi:
  ```make
  KBUILD_CFLAGS += -O3 -fvectorize -fslp-vectorize
  CONFIG_THINLTO=y
  ```

### 2. Fitur Kernel yang Direkomendasikan untuk Diaktifkan (`defconfig`):
- **TCP BBRv2**:
  ```kconfig
  CONFIG_TCP_CONG_BBR=y
  CONFIG_DEFAULT_TCP_CONG="bbr"
  ```
- **ZRAM Kompresi ZSTD**:
  ```kconfig
  CONFIG_CRYPTO_ZSTD=y
  CONFIG_ZRAM_DEF_COMP_ZSTD=y
  ```
- **Tickless Full Scheduler (`NO_HZ_FULL`)**:
  ```kconfig
  CONFIG_NO_HZ_FULL=y
  CONFIG_RCU_NOCB_CPU=y
  ```
- **Debloating Logging Overhead Vendor**:
  ```kconfig
  # CONFIG_MTK_AEE_FEATURE is not set
  # CONFIG_MTK_MET is not set
  # CONFIG_TRANSACTION_LOG is not set
  ```

---
*Dikelola oleh Lynx Universal Optimization Core — ɴᴏɪʀ.*
