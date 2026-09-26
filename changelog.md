# Lynx [Codename: Deity] 3.0.1
Released on: 2026-09-26
> **Versi ini** membawa ekspansi masif Deep Hardware Scanner dengan lebih dari 450 node tuneable hardware terdeteksi secara dinamis.

## ✨ Fitur Baru & Peningkatan (3.0.1)

### 6. Deep Hardware Scanner Diperluas (450+ Hardware Nodes)
- **Tambahan subsistem yang sebelumnya tidak terpindai:**
  - **MediaTek FPSGO/FBT/FSTB**: Engine frame-pacing utama MediaTek (/sys/kernel/fpsgo/common/*, /fbt/*, /fstb/*) kini tampil sebagai kontrol toggle tertunable.
  - **GED HAL Layer**: Kontrol langsung GPU driver level (/sys/kernel/ged/hal/*) untuk custom boost freq, DVFS margin, loading step.
  - **Core Control (Qualcomm)**: Manajemen hotplug core CPU performa Qualcomm (cpu*/core_ctl/*).
  - **EAS & HPS**: Energy Aware Scheduling enable node dan MediaTek CPU Hotplug System.
  - **PPM Master Switch & Mode**: Kontrol on/off seluruh engine PPM MediaTek (/proc/ppm/enabled, /proc/ppm/mode).
  - **Charging Lanjutan**: Tambahan node ast_charge, astcharge_mode, 	hermal_limit, system_temp_level, wireless_boost untuk Samsung/Xiaomi/POCO.
  - **LMK & Process Reclaim**: /sys/module/lowmemorykiller/parameters/* dan /sys/module/process_reclaim/parameters/* untuk manajemen memori lebih halus.
  - **Sound Control**: /sys/kernel/sound_control/* dan /sys/class/misc/soundcontrol/* untuk kernel audio tuning.
  - **Vibrator Amplitude**: /sys/devices/virtual/timed_output/vibrator/amp untuk kontrol kekuatan getaran.
  - **LRU Gen**: Seluruh node Multi-Gen LRU (/sys/kernel/mm/lru_gen/*) bukan hanya enabled.
  - **Adreno Extended**: orce_no_nap, gpuclk, min/max_clock_mhz, pwrscale, dan max_gpuclk.
  - **Kernel Misc**: pid_max, 	imer_migration, andomize_va_space, perf_cpu_time_max_percent.
- **Smart Value Normalization**: Status string ppm is enabled/disabled, bt_idleprefer_enable:1 kini otomatis dinormalisasi ke nilai boolean 0/1.
- **Smart Skipping**: Node non-tuneable (info dumps, statistik, BQ ID, tabel) kini difilter otomatis sehingga tidak muncul sebagai toggle palsu.
- **Performa Scan**: 453 node terdeteksi dalam 80ms (dari sebelumnya 440 node dalam 130ms).
- **Metadata Baru**: 20+ entri metadata friendly ditambahkan untuk GED HAL, FPSGO, Core Control, PPM, HPS, EAS, ASLR.

### 7. Sinkronisasi Shell Script & Kotlin
- core/lib/deep_inspector.sh dan uildStandaloneDeepScanScript() di LynxRepository.kt kini **100% sinkron** — jalur hardware, filter, normalisasi nilai, dan kategorisasi identik persis antara shell module dan aplikasi native.

---

# Lynx [Codename: Deity] 3.0.0
Released on: 2026-09-25
> **Nama Aplikasi**: Lynx | **Codename**: Deity | **Versi**: 3.0.0  
> Rilis ini menghadirkan perombakan total (*major architectural overhaul*), penyatuan arsitektur hibrida Qualcomm & MediaTek, pembersihan redundansi kode warisan, standarisasi murni POSIX, integrasi biner BusyBox selektif (Feravolt & Brutal), antarmuka WebUI modern, serta Aplikasi Native Android Companion (Kotlin + Jetpack Compose) dengan sinkronisasi 2 arah real-time.

---

## âœ¨ Fitur Baru & Peningkatan (3.0.0)

### 1. Spektrum 5 Mode & AI Adaptif (Smart-AI Core v5.5)
- **5 Profil Performa Utama**:
  - `auto`: Deteksi game adaptif secara otomatis (*Zero Latency 0ms entry, 3s cooldown buffer, deep sleep suspend blocker*).
  - `balance`: Profil harian hemat daya berbasis EAS, frekuensi seimbang, dan *power-efficient workqueues*.
  - `performance`: Kunci frekuensi menengah-atas, elevasi prioritas *SurfaceFlinger/HW Composer*, dan pembatasan throttling moderat.
  - `extreme`: Frekuensi CPU & GPU maksimum, *unrestricted full thermal bypass* (trip points 150Â°C), dan pengisian daya bebas batas (khusus dengan *phone cooler* eksternal).
  - `powersave`: Penghemat baterai ekstrem dengan batas frekuensi efisien dan penonaktifan boost agresif.
- **Safe First-Boot (Dormant Mode)**: Modul menginisialisasi dalam status standby aman tanpa modifikasi jam ekstrem sebelum dikonfigurasi melalui WebUI atau Aplikasi Companion.

### 2. Segmentasi HAL Perangkat Keras (Qualcomm vs MediaTek)
- **Pemisahan Sysfs Eksklusif**:
  - `platforms/qcom/sysfs.sh`: Penekanan overhead diagnostik Adreno KGSL, peredam jitter CPU-Boost input frequency, aktivasi Qualcomm WALT (*Window Assisted Load Tracking*), dan DRM perf mode.
  - `platforms/mtk/sysfs.sh`: Penonaktifan logging modem CCCI, penekanan tracing GED KPI, pembatalan pembatasan FPS MediaTek Syslimiter (60/90/120/144), dan pengoptimalan Perf PMU.
  - `platforms/generic/sysfs.sh`: Penanganan fallback aman untuk chipset arsitektur generik.
- **Anti-Spoofing Detection Engine & Hardware Fingerprinting (`hw_probe.sh`)**:
  - Verifikasi bertingkat melalui driver kernel fisik dan Device Tree Blob (`/sys/firmware/devicetree/base/model`, `/proc/device-tree/compatible`, `/dev/kgsl-3d0`, `/dev/mali0`, `/proc/ppm`) yang kebal 100% terhadap manipulasi spoofing *Device Faker*, *GLTools*, maupun *Game Unlocker*.
  - Mengabaikan manipulasi `getprop` di user-space dan secara otomatis mengunci eksekusi ke arsitektur hardware kernel yang sesungguhnya.
  - **Dynamic Targeted Sub-Profiles**:
    - **MediaTek**: ARM Mali-G57 (`gpu_mali_g57.sh`), Mali Valhall Gen-2 G68/G77/G78 (`gpu_mali_valhall2.sh`), ARM Immortalis flagship (`gpu_immortalis.sh`), dan tuning khusus perangkat Infinix X698 / MT6781 (`infinix_x698.sh`).
    - **Qualcomm**: Adreno 600 series (`gpu_adreno_600.sh`), Adreno 700 GMU tuning (`gpu_adreno_700.sh`), dan adaptasi Xiaomi/Poco/Redmi (`xiaomi_qcom.sh`).

### 3. Modularisasi Subsistem Universal (POSIX Standard)
- **`core/lib/sched_features.sh`**:
  - Penyesuaian flags scheduler kernel berlatensi rendah (`NO_GENTLE_FAIR_SLEEPERS`, `START_DEBIT`, `LB_BIAS`, dll.).
  - Injeksi otomatis 23 pustaka mesin game utama (*Unity, Unreal Engine, Genshin Impact, Asphalt, Flutter*) ke `/proc/sys/kernel/sched_lib_name`.
- **`core/lib/network.sh`**:
  - Penemuan otomatis algoritma TCP Congestion Control terbaik (`bbrv3` âž” `bbr` âž” `westwood` âž” `cubic`).
  - Implementasi *Fair Queueing Controlled Delay* (`fq_codel`) untuk eliminasi *bufferbloat*.
  - Pemblokiran *Wi-Fi Power Save* saat bermain game untuk mencegah lonjakan latensi (ping spikes).
- **`core/lib/state.sh` & `config.json`**:
  - Manajemen state terpadu berbasis JSON dengan dukungan notasi titik (*dot-notation*, e.g. `uclamp.game_min_ratio`).
  - Pembaruan atomik bebas race condition yang menjaga konsistensi state di seluruh antarmuka.

### 4. Tombol Aksi KernelSU & APatch Aman (`action.sh`)
- Menghapus total perintah penghapusan `dalvik-cache` usang yang berisiko memicu *soft reboot*.
- Menggantikan dengan pemeliharaan aman via `CCleaner.sh`: pemadatan memori RAM fisik kontigu (*memory compaction*), pembersihan crash dump/tombstones/ANR, dan pemangkasan blok penyimpanan (*fstrim*).

### 5. Aplikasi Native Android Companion (`LynxCompanion`)
- Dibangun menggunakan **Kotlin + Jetpack Compose + Material 3** dengan estetika *Cyberpunk Deep Dark OLED*.
- Eksekusi root berlatensi rendah via `libsu`.
- Sinkronisasi dua arah (*Two-Way Sync*) secara instan dengan `config.json` modul via `FileObserver` dan `inotify`.

---

# Arsip Rilis Sebelumnya (v3.0 Beta)
Released on: 2025-05-11
> Rilis awal pengenalan mesin logika Lxcore dan dukungan Android 14/15 Beta.

