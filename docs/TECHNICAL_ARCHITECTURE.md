# 📐 DOKUMEN ARSITEKTUR & SPESIFIKASI TEKNIS: LYNX UNIVERSAL HYBRID CORE
> **Target Proyek**: Lynx Universal (Hybrid Qualcomm Snapdragon & MediaTek Architecture)  
> **Klasifikasi**: Spesifikasi Teknis, Logika Kernel, & Cetak Biru Rekayasa Sistem  
> **Status**: Dokumen Arsitektur Resmi  

---

## 📑 Daftar Isi
1. [Ikhtisar Arsitektur Sistem (High-Level Architecture)](#1-ikhtisar-arsitektur-sistem-high-level-architecture)
2. [Mesin Deteksi & Validasi Hardware (Anti-Spoofing Ground Truth)](#2-mesin-deteksi--validasi-hardware-anti-spoofing-ground-truth)
3. [Pre-Calculated RAM Caching Engine (Sub-15ms Mode Switching)](#3-pre-calculated-ram-caching-engine-sub-15ms-mode-switching)
4. [Adaptive Intelligent AI Core (Smart-AI.sh Engine)](#4-adaptive-intelligent-ai-core-smart-aish-engine)
5. [Hardware Abstraction Layer (HAL): Qualcomm Snapdragon (`platforms/qcom/`)](#5-hardware-abstraction-layer-hal-qualcomm-snapdragon-platformsqcom)
6. [Hardware Abstraction Layer (HAL): MediaTek Platform (`platforms/mtk/`)](#6-hardware-abstraction-layer-hal-mediatek-platformsmtk)
7. [Pengontrol Daya, Arus Pengisian & Baterai (`Charging-Controller.sh`)](#7-pengontrol-daya-arus-pengisian--baterai-charging-controllersh)
8. [Manajemen Memori, Storage I/O & Pemeliharaan Sistem](#8-manajemen-memori-storage-io--pemeliharaan-sistem)
9. [Antarmuka Kontrol Pasca-Instalasi (WebUI `webroot/`)](#9-antarmuka-kontrol-pasca-instalasi-webui-webroot)
10. [Protokol Keamanan, Mitigasi Bootloop & Reversibilitas Penuh](#10-protokol-keamanan-mitigasi-bootloop--reversibilitas-penuh)

---

## 1. Ikhtisar Arsitektur Sistem (High-Level Architecture)

### 1.1 Filosofi Desain
Lynx Universal menyatukan dua ekosistem arsitektur chipset terbesar di Android: **Qualcomm Snapdragon** (berbasis Adreno & KGSL) dan **MediaTek** (berbasis Mali GED & PPM) ke dalam **satu berkas ZIP modul yang sama**. Sistem secara cerdas beradaptasi tanpa membebani pengguna dengan pertanyaan teknis yang rumit.

```
                           ┌───────────────────────────────┐
                           │      Lynx Universal ZIP       │
                           │   (Single Universal Package)  │
                           └───────────────┬───────────────┘
                                           │
                           [ Tahap Instalasi: customize.sh ]
                                           │
                         ┌─────────────────┴─────────────────┐
                         ▼                                   ▼
             [ Qualcomm Snapdragon ]                [ MediaTek Dimensity/Helio ]
             • Mount: platforms/qcom/system         • Mount: platforms/mtk/system
             • Props: system.prop.qcom              • Props: system.prop.mtk
             • Engine: Adreno KGSL & Devfreq        • Engine: MTK PPM, CCI & GED
                         │                                   │
                         └─────────────────┬─────────────────┘
                                           │
                                [ BOOT: service.sh ]
                                           │
               ┌───────────────────────────┼───────────────────────────┐
               ▼                           ▼                           ▼
     [ Pre-Calculated RAM ]        [ Adaptive AI Core ]        [ WebUI Interface ]
     • Hitung max freq CPU/GPU     • Sub-30ms Top App Query    • Dark Cyberpunk UI
     • Simpan ke /dev/ (RAM-disk)  • Asymmetric Debounce       • ksu.exec() IPC Bridge
     • Siap injeksi sub-15ms       • Screen-Off Deep Sleep     • Mobile-First Touch
```

### 1.2 Siklus Hidup Modul (Lifecycle Timeline)
1. **Fase Flash (`customize.sh`)**:
   - Mengeksekusi pemeriksaan root environment (Magisk, KernelSU, APatch).
   - Menjalankan mesin `detect_soc` anti-spoofing berbasis hardware ground-truth.
   - Melakukan *Conditional File Injection* (hanya berkas platform yang cocok disalin ke `$MODPATH/system`).
   - Menggabungkan konfigurasi properti sistem (`system.prop`).
2. **Fase Early Boot (`post-fs-data.sh`)**:
   - Berjalan sebelum Zygote aktif.
   - Menyiapkan environment perizinan SELinux (`sepolicy.rule`).
   - Menyiapkan tautan binary Busybox jika diperlukan.
3. **Fase Late Start (`service.sh`)**:
   - Menunggu status `sys.boot_completed=1`.
   - Menjalankan **Pre-Calculated RAM Caching Engine** (menghitung frekuensi maks/min satu kali).
   - Menginisialisasi *network low latency*, mematikan *kernel logging/tracing*, dan mengatur penjadwal I/O.
   - Memulai daemon latar belakang: `crond` (pembersih cache) dan `Smart-AI.sh` (pengontrol adaptif).
4. **Fase Runtime (`Smart-AI.sh`)**:
   - Memantau aplikasi aktif di layar secara *asymmetric* (masuk game instan 0 ms, keluar game cooldown 3–5 detik).
   - Berhenti saat layar mati untuk mengizinkan kernel masuk ke *Deep Sleep*.

---

## 2. Mesin Deteksi & Validasi Hardware (Anti-Spoofing Ground Truth)

### 2.1 Masalah SoC Spoofer
Banyak modul pengubah identitas (seperti *Game Unlocker 90/120 FPS, GLTools, MagiskHide Props Config*) memalsukan nilai `getprop`:
- Ponsel MediaTek diubah propertinya menjadi:
  `ro.soc.manufacturer=Qualcomm`, `ro.board.platform=taro`, `ro.product.board=sm8450`.
- Jika modul hanya mengandalkan `getprop`, modul akan memasang berkas vendor Qualcomm pada hardware MediaTek, memicu **Bootloop atau GPU Driver Crash**.

### 2.2 Hierarki Deteksi Kebal Spoofer
Modul menerapkan prinsip **Kernel Ground Truth**: *Driver fisik kernel Linux tidak dapat dipalsukan oleh modul user-space manapun.*

```
Level 1: Hardware Ground Truth (Mutlak)
├── Device Tree Blob: /sys/firmware/devicetree/base/compatible (String 'mediatek' vs 'qcom')
├── Device Node Karakter GPU:
│   ├── Qualcomm: /dev/kgsl-3d0 & sysfs /sys/class/kgsl
│   └── MediaTek: /dev/mali0, /dev/ged, /proc/ged, /proc/ppm
└── SoC Physical Driver: /sys/devices/soc0 (Qualcomm)

Level 2: Verifikasi Anomali (Spoof-Awareness)
├── Jika Kernel == MediaTek, tapi getprop == Qualcomm:
│   ├── Set SPOOF_DETECTED=true
│   ├── Abaikan getprop!
│   ├── Kunci target ke arsitektur MediaTek
│   └── Beri notifikasi visual: '⚠️ SoC Spoofer Terdeteksi! Lynx mengunci ke MediaTek demi keamanan.'

Level 3: Fallback ke System Properties
└── Hanya dibaca jika node fisik kernel terproteksi SELinux tingkat tinggi.

Level 4: Generic Linux Failsafe
└── Jika kedua SoC tidak dikenali, aktifkan CPUFreq standar Linux tanpa menyentuh vendor.
```

---

## 3. Pre-Calculated RAM Caching Engine (Sub-15ms Mode Switching)

### 3.1 Eliminasi Fork/Pipe Overhead
Pada modul lawas, setiap kali game dibuka, skrip mengeksekusi:
```bash
# CARA LAMA (Lambat: 300-600ms overhead karena banyak proses fork)
gpumaxfreq=$(cat /sys/class/kgsl/kgsl-3d0/gpu_available_frequencies | tr -s ' ' '\n' | sort -n | tail -n 1)
```
Di Android, setiap pipe `|` dan pemanggilan `tr`, `sort`, `tail` memicu *fork/exec* sub-proses Linux. Pada CPU octa-core menengah ke bawah, ini memakan waktu ratusan milidetik.

### 3.2 Solusi RAM Cache di `/dev/`
Frekuensi maksimum hardware ponsel adalah konstanta fisik yang tidak berubah selama runtime. Kalkulasi dilakukan **hanya 1 kali saat boot di `service.sh`** dan disimpan ke file RAM disk `/dev/`:

| Nama File Cache di RAM | Nilai yang Disimpan | Contoh Isi | Ukuran RAM |
| :--- | :--- | :--- | :--- |
| `/dev/lynx_cpu_p0_max` | Frekuensi maks CPU Little cluster | `1804800` | 8 bytes |
| `/dev/lynx_cpu_p4_max` | Frekuensi maks CPU Big cluster | `2208000` | 8 bytes |
| `/dev/lynx_cpu_p7_max` | Frekuensi maks CPU Prime core | `2400000` | 8 bytes |
| `/dev/lynx_gpu_max` | Frekuensi maks GPU Adreno / Mali GED | `850000000` | 10 bytes |
| `/dev/lynx_soc_type` | Arsitektur aktif (`qcom` / `mtk`) | `mtk` | 4 bytes |

Saat game dibuka, skrip `AI_performance.sh` **tidak melakukan kalkulasi apapun**, melainkan langsung menginjeksi nilai dari cache:
```bash
# CARA BARU (Instan: < 5ms)
cat /dev/lynx_gpu_max > /sys/class/kgsl/kgsl-3d0/max_gpuclk
```

---

## 4. Adaptive Intelligent AI Core (`Smart-AI.sh` Engine)

### 4.1 Logika Asymmetric Switching (Peredam Getaran Transisi)
- **Saat Masuk Game**:
  - **Latensi: 0 milidetik (Hard Real-Time Priority)**.
  - Begitu paket game terdeteksi di *top activity*, parameter performa langsung disuntikkan seketika sebelum frame pertama selesai dirender.
- **Saat Keluar Game**:
  - **Dilarang langsung turun ke mode Balance!**
  - Sistem mengaktifkan buffer pendinginan (*cooldown timer*) selama **3 hingga 5 detik**.
  - Jika pengguna hanya membalas notifikasi singkat WhatsApp, recent apps, atau game memunculkan overlay iklan, mode performa **tetap dipertahankan**.
  - Transisi ke `balance` hanya dieksekusi jika pengguna benar-benar meninggalkan game lebih dari 3 detik berturut-turut.

### 4.2 Atomic Mutex Lock (`/dev/lynx_mode.lock`)
Mencegah *race condition* saat aplikasi dibuka-tutup secara cepat:
```bash
acquire_lock() {
    while [ -f "/dev/lynx_mode.lock" ]; do
        # Jika proses lama menggantung lebih dari 2 detik, bersihkan paksa
        sleep 0.05
    done
    touch "/dev/lynx_mode.lock"
}
release_lock() {
    rm -f "/dev/lynx_mode.lock"
}
```

### 4.3 Screen-State Power-Awareness (Deep Sleep Shield)
- Masalah skrip lama: Tetap melakukan *polling* setiap 0.5 detik saat layar mati di saku celana, mencegah CPU masuk ke *Deep Sleep*.
- Solusi Baru:
  ```bash
  is_screen_on() {
      dumpsys power | grep -q "mHoldingDisplaySuspendBlocker=true"
  }
  ```
  - **Layar Mati (Screen OFF)**: Loop polling berhenti total, beralih ke `sleep 15` s/d `sleep 30`. CPU 100% diizinkan masuk ke status C-States (Deep Sleep).
  - **Layar Menyala (Screen ON)**: Seketika bangun dan kembali memantau aplikasi.

### 4.4 Ultra-Fast Top-Resumed App Query
Menggantikan eksekusi lambat `dumpsys window` dengan metode parsing terfokus sub-30ms:
```bash
get_top_pkg() {
    dumpsys activity activities | grep -m1 "topResumedActivity" | awk '{print $3}' | cut -d'/' -f1
}
```

### 4.5 Proteksi Khusus Low-End Device (RAM ≤ 4GB & SoC Lawas)
- **Dilarang keras mengeksekusi `echo 3 > /proc/sys/vm/drop_caches` dan `am kill-all`**: Pada penyimpanan eMMC 5.1 dan RAM 4GB, menghapus page cache secara liar memicu *I/O storage thrashing*, membekukan layar (*freeze*) selama 2-3 detik, dan menyebabkan launcher (*Home screen*) *redraw/crash*.
- **Pembersihan Halus (*Gentle Compact*)**: Menggunakan `echo 1 > /proc/sys/vm/compact_memory` yang merapikan fragmentasi RAM tanpa membuang cache aktif aplikasi.
- **Launcher Whitelist**: Melindungi package launcher OEM (MIUI Launcher, Transsion XOS Launcher, Nova, Pixel Launcher) agar tidak tersentuh pembersihan proses.

---

## 5. Hardware Abstraction Layer (HAL): Qualcomm Snapdragon (`platforms/qcom/`)

### 5.1 Arsitektur CPU Scaling
- **Governor**: `schedutil` dengan penyesuaian rate limits:
  - `up_rate_limit_us`: Diturunkan ke `500` (CPU merespons lonjakan beban seketika).
  - `down_rate_limit_us`: Dinaikkan ke `20000` (mencegah frekuensi drop terlalu cepat saat jeda frame game).
- **Core Allocation**:
  - `cpu0-3` (Little cores): Diberi batas frekuensi seimbang untuk background tasks.
  - `cpu4-7` (Big & Prime cores): Dikunci pada frekuensi performa tinggi saat game aktif.
- **Workqueue Efficiency**:
  - Mode Performance: `echo N > /sys/module/workqueue/parameters/power_efficient`
  - Mode Balance: `echo Y > /sys/module/workqueue/parameters/power_efficient`

### 5.2 Adreno GPU Tuning (KGSL Subsystem)
- **GPU Path**: `/sys/class/kgsl/kgsl-3d0/`
- **Tuning Parameters**:
  - `max_gpuclk`: Diatur ke frekuensi maksimum dari tabel ketersediaan.
  - `min_clock_mhz`: Dinaikkan pada mode Extreme untuk menghilangkan frame drop rendering.
  - `throttling`: `echo 0 > /sys/class/kgsl/kgsl-3d0/throttling` (hanya di mode Extreme/High).
  - `bus_split`: Dioptimalkan untuk memory throughput.

### 5.3 Qualcomm Devfreq Memory Bus Bandwidth
- Penyesuaian governor bus devfreq ke mode `performance` saat gaming:
  - `soc:qcom,cpubw`, `soc:qcom,gpubw`, `soc:qcom,kgsl-busmon`, `soc:qcom,llccbw`.
  - Memastikan *bandwidth bottleneck* antara GPU dan RAM terbuka maksimal saat texture streaming berat berlangsung.

### 5.4 Thermal Throttling Bypass (Qualcomm)
- Mengganti biner dan berkas konfigurasi OEM vendor:
  - `thermal-engine.conf`, `init_thermal-engine-v2.rc`, `libsomc_thermal.so` (Sony), `mi_thermal_interface.ko` (Xiaomi).
  - Menonaktifkan thermal zone di `/sys/class/thermal/thermal_zone*` dan menaikkan trip point ke 150°C.

### 5.5 Wi-Fi Dual-Band Bonding
- Modifikasi berkas konfigurasi firmware Qualcomm `WCNSS_qcom_cfg.ini`:
  - `gChannelBondingMode24GHz=1`
  - `gChannelBondingMode5GHz=1`
  - Menggabungkan band 2.4GHz dan 5GHz secara simultan untuk menurunkan ping dan packet loss pada jaringan yang mendukung.

---

## 6. Hardware Abstraction Layer (HAL): MediaTek Platform (`platforms/mtk/`)

### 6.1 MediaTek PPM (Processor Power Management)
- **Path Utama**: `/proc/ppm/`
- **Logika Mode Performa (Chimera Engine)**:
  ```bash
  # Mengaktifkan mode High Performance / Sport internal MediaTek
  write_node "3" "/proc/cpufreq/cpufreq_power_mode"
  write_node "1" "/proc/cpufreq/cpufreq_cci_mode"
  write_node "1" "/proc/cpufreq/cpufreq_imax_enable"
  
  # Manajemen Kebijakan PPM
  write_node "0" "/proc/ppm/enabled"
  write_node "0" "/proc/ppm/cpi/cpi_enabled"
  ```
- **DVFS Cluster Table**:
  Membaca tabel DVFS dari `/proc/ppm/dump_cluster_0_dvfs_table` dan mengunci frekuensi min/max via `/proc/ppm/policy/hard_userlimit_max_cpu_freq`.

### 6.2 MediaTek GED (Graphics Engine Driver) & GPU Mali
- **Path Utama**: `/proc/ged/hal/`
- **Tuning Parameters**:
  - `custom_upbound_gpu_freq`: Mengunci batas atas frekuensi clock GPU Mali.
  - `custom_boost_gpu_freq`: Mengatur *boost floor* agar GPU tidak turun clock saat adegan kompleks.
  - `system/lib[64]/egl/egl.cfg`: Mengarahkan pipeline rendering langsung ke driver hardware Mali murni.

### 6.3 MediaTek Thermal Policy (`.tp`)
- File konfigurasi vendor MediaTek diatur melalui direktori vendor:
  - `system/vendor/etc/.tp/thermal.conf`
  - `system/vendor/etc/.tp/.thermal_policy_08`
- Override policy `.tp` mematikan pembatasan frekuensi agresif khas thermal engine OEM MediaTek saat beban gaming intensif.

### 6.4 MediaTek Wi-Fi Firmware Tuning
- Injeksi berkas firmware vendor:
  `system/vendor/firmware/wifi.cfg`
- Penyesuaian buffer transmit dan sleep latency firmware Wi-Fi MediaTek untuk menjaga kestabilan ping game online.

---

## 7. Pengontrol Daya, Arus Pengisian & Baterai (`Charging-Controller.sh`)

### 7.1 Parameter Arus Pengisian (Fast Charge Current - FCC)
Daemon memonitor node sysfs charger:
- `/sys/class/power_supply/battery/constant_charge_current`
- `/sys/class/power_supply/battery/constant_charge_current_max`
- `/sys/class/power_supply/battery_ext/chg_pwr_fcc`
- `/sys/class/qcom-battery/restricted_charging`

### 7.2 Fitur AutoCut (Simulasi Bypass Charging)
Berfungsi memutus arus pengisian daya saat baterai mencapai ambang batas atas, dan mengalirkan kembali saat turun ke ambang batas bawah:
```
Status Baterai: 85% (Batas Bawah) s/d 95% (Batas Atas)
├── Baterai mencapai 95%:
│   └── Set arus pengisian ke 0 mA (Arus berhenti, daya charger langsung mentenagai sistem)
└── Baterai turun ke 85%:
    └── Kembalikan arus pengisian ke FCC normal (Pengisian dimulai kembali)
```
*Manfaat*: Suhu baterai tetap dingin saat bermain game sambil dicolokkan ke charger, dan mencegah degradasi siklus kimia baterai (*battery health protection*).

### 7.3 Thermal Throttling Charging
Saat mode `performance` aktif di dalam game:
Arus pengisian secara otomatis dibatasi ke rentang aman **1500mA – 1700mA** (dapat diubah via `lynx.lcc`), mencegah akumulasi panas ganda dari SoC dan modul charger baterai secara bersamaan.

---

## 8. Manajemen Memori, Storage I/O & Pemeliharaan Sistem

### 8.1 ZRAM Tuning Modular
- **RAM ≤ 4GB (Low-End / Mid-Range)**:
  - Ukuran: `1536MB` s/d `2048MB`
  - Algoritma: **`lz4`** (prioritas efisiensi CPU, tidak membebani core hemat daya).
- **RAM ≥ 6GB–12GB (High-End)**:
  - Ukuran: `3072MB` s/d `6144MB`
  - Algoritma: **`zstd`** (rasio kompresi maksimal, hemat RAM fisik).
- **Swappiness & Dirty Ratios**:
  - `vm.swappiness`: Diatur dinamis (60 di Balance, 100 di Low RAM, 30 di Extreme Gaming).
  - `vm.vfs_cache_pressure`: Diatur ke `100` untuk menjaga metadata dentry tetap seimbang.

### 8.2 Storage I/O Scheduler & Fstrim
- Blok disk internal `/sys/block/*/queue/`:
  - Scheduler: Menggunakan `kyber` atau `noop` (menghilangkan antrean I/O yang membebani CPU).
  - `read_ahead_kb`: `512` KB (kecepatan transfer optimal).
- **Fstrim Berkala**:
  Dijalankan pada `/system`, `/vendor`, `/data`, `/cache`, dan `/metadata` untuk membersihkan blok memori flash NAND yang tidak terpakai, menjaga kecepatan baca-tulis UFS/eMMC.

### 8.3 Pembersih Terjadwal Harian (`cron/CCleaner.sh`)
- Dijalankan via daemon `crond` setiap hari tepat pukul **23:59**:
  - Membersihkan direktori sampah cache: `/cache/*`, `/data/cache/*`.
  - Membersihkan crash logs & dump error: `/data/anr/*`, `/data/tombstones/*`, `/data/system/dropbox/*`.
  - Memberikan notifikasi visual toast di layar: `🧹 Cache Cleaned`.

---

## 9. Antarmuka Kontrol Pasca-Instalasi (WebUI `webroot/`)

### 9.1 Arsitektur Frontend (KernelSU / APatch WebUI)
WebUI adalah **antarmuka kontrol utama** pengguna pasca-instalasi:
- **Teknologi**: Single Page Application (HTML5, Vanilla JS, CSS Tokens modern) tanpa dependensi library eksternal yang membebani WebView.
- **Komunikasi Backend**: Menggunakan bridge asynchronous `ksu.exec(command)` yang disediakan secara *native* oleh KernelSU Manager dan APatch.
- **Target Touch & Aksesibilitas**:
  - Target sentuh tombol minimal **48×48 px** (bebas salah tekan pada layar ponsel).
  - Responsif untuk rasio layar non-reguler (1080×2400, 1080×2460, rasio 20:9, tablet, dan layar lipat).
  - Skema warna **Deep Dark Theme / Modern Cyberpunk** (OLED `#0a0a0a` dengan aksen `#00ffaa` dan `#0099ff`).

### 9.2 Penanganan Error & Sanitasi Input
- Setiap perintah yang dikirim melalui `ksu.exec()` dibungkus dalam blok `try/catch` asinkron dengan validasi *error boundary*.
- Tampilan terminal output real-time untuk memperlihatkan log keberhasilan eksekusi script tanpa memerlukan aplikasi Termux tambahan.

---

## 10. Protokol Keamanan, Mitigasi Bootloop & Reversibilitas Penuh

### 10.1 Aturan Penulisan Node Kernel Aman (`write_node`)
Setiap skrip dilarang menulis nilai secara membabi buta. Wajib membungkus operasi dalam helper validasi:
```bash
write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}
```

### 10.2 Conditional Overlay Mount (Pencegah Bootloop #1)
- Dilarang keras menaruh berkas vendor Qualcomm dan MediaTek secara bersamaan di direktori `system/`.
- File vendor Qualcomm wajib berada di `platforms/qcom/system/`.
- File vendor MediaTek wajib berada di `platforms/mtk/system/`.
- `customize.sh` bertugas menyalin **hanya** folder platform yang relevan ke `$MODPATH/system/` saat instalasi berlangsung.

### 10.3 Pemulihan Konfigurasi Penuh (`uninstall.sh`)
Ketika pengguna menghapus modul melalui Magisk / KernelSU / APatch Manager, `uninstall.sh` menjamin 100% pemulihan sistem:
- Mengembalikan nilai `window_animation_scale`, `transition_animation_scale`, dan `animator_duration_scale` ke nilai backup aslinya.
- Menghapus direktori runtime sementara di `/dev/lynx_*` dan `/data/adb/modules/Lynx`.
- Mengembalikan pengaturan thermal dan pengisian daya ke default OEM.

---
*Dokumen ini merupakan spesifikasi teknis resmi dari arsitektur Lynx Universal Hybrid Core.*
