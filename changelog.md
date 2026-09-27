# Lynx [Codename: Deity] 3.0.8
Released on: 2026-09-28
> **Versi ini** memperbarui durasi **Live Benchmark OSD di Floating Game HUD** menjadi **1 Menit (60 detik)** penuh, memungkinkan analisis kestabilan frametime jangka panjang, deteksi thermal throttling progresif, serta perataan pacing yang lebih representatif saat gameplay intensif.

## 🚀 Fitur Baru & Peningkatan (3.0.8)

### 1. ⏱️ 1-Minute Live Benchmark pada Floating Game HUD
- **Standar Durasi 1 Menit (60 Detik)**: Tombol benchmark instan di Expanded Game HUD kini disetel ke durasi 60 detik (sebelumnya 10 detik).
- **Pemantauan Pacing Jangka Panjang**: Pengujian 60 detik menangkap siklus gameplay realistis (rotasi peta, pertempuran, burst efek partikel) serta efek kenaikan suhu terhadap kestabilan frame delivery.
- **Hitung Mundur Real-Time**: Tampilan tombol HUD menampilkan indikator live `Merekam Pacing... (60s)` hingga `(0s)`.
- **Benchmark Studio Dialog Synchronization**: Di dalam aplikasi utama Lynx Companion, opsi default juga disesuaikan ke 1 Menit (60s) dengan pilihan rentang `10s`, `30s`, `1 Min`, dan `2 Min`.

---

# Lynx [Codename: Deity] 3.0.7
Released on: 2026-09-28
> **Versi ini** menghadirkan **Live Hardware Benchmark Studio & Frame Pacing Profiler**, pembaruan ergonomi Floating Game HUD (Frame Time langsung di Mini Pill), penguncian hardware fixed OPP GPU, bypass limit table Mali, dan isolasi SMP Affinity IRQ CPU.

## 🚀 Fitur Baru & Peningkatan (3.0.7)

### 1. ⚡ Live Hardware Benchmark & Frame Pacing Studio
- **Presisi Hardware Presentation Latency**: Menggunakan ekstraksi timestamp hardware SurfaceFlinger (`dumpsys SurfaceFlinger --latency`) untuk mengukur waktu sesungguhnya saat frame ditampilkan ke panel layar.
- **Metrik Diagnostik Komprehensif**:
  - Average FPS & Median Frametime (ms)
  - 1% Low FPS & 0.1% Low FPS
  - Frametime Jitter / Standard Deviation (ms)
  - Peak / Min Latency & Max Spike (ms)
  - Janky Frames Count & Percentage (>33.3ms)
  - Hardware Telemetry (CPU Avg, GPU Avg & Load, Suhu Baterai, Konsumsi Daya Watt)
- **Interactive Canvas Frametime Chart**: Visualisasi kurva latensi per frame secara real-time dengan garis panduan 16.6ms (60 FPS) dan 33.3ms (30 FPS).
- **Akses Fleksibel**: Dapat dijalankan dari Dashboard (Tab 0), Tools (Tab 3), serta tombol Quick Benchmark (10s) langsung di Floating Game HUD tanpa meninggalkan game.

### 2. 🎮 Ergonomi HUD OSD — Frame Time pada Mini Pill
- Menggantikan label 120Hz pada Mini Pill HUD dengan nilai **Live Frame Time (ms)** (misal `23.8ms`) yang berdampingan langsung dengan FPS, memberikan informasi instan tentang konsistensi rendering.
- Nilai Refresh Rate panel (Hz) tetap ditampilkan dengan rapi pada grid telemetri di mode Expanded HUD.

### 3. 🔥 Peak GPU & IRQ Performance Clamping
- **Hardware Fixed OPP Lock**: Menulis frekuensi puncak KHz ke `/proc/gpufreq/gpufreq_opp_freq` untuk mengunci register OPP GPU Mali di frekuensi tertinggi.
- **Bypass Limit Table GPU**: Menonaktifkan seluruh pembatas thermal, battery OC, battery low, dan governor policy pada `/proc/gpufreq/gpufreq_limit_table`.
- **Mali DVFS Disable**: Mematikan DVFS kbase driver di mode Extreme (`/proc/mali/dvfs_enable = 0`) agar GPU tidak pernah mengalami micro-downclock.
- **Mali GPU IRQ Affinity Isolation**: Mengalihkan interrupt handler Mali ke CPU 0–5 (`0x3F`), mengisolasi Big Cores (Cortex-A78) 100% untuk komputasi render thread game engine.
- **VFS Cache Pressure Tuning (`vm.vfs_cache_pressure = 40`)**: Mencegah pembuangan cache direktori dan file tekstur di RAM selama transisi scene dan rotasi 3D.

---

# Lynx [Codename: Deity] 3.0.6
Released on: 2026-09-28
> **Versi ini** menghadirkan **Frametime & Sustained FPS Optimization Suite** untuk Extreme dan Performance mode — meminimalisir frame delivery jitter, menghilangkan micro-stuttering pada 3D open-world gaming, serta mengoptimalkan SurfaceFlinger buffer latching, MediaTek FPSGO Ultra Rescue, dan Linux CFS low-latency scheduling.

## 🚀 Fitur Baru & Peningkatan (3.0.6)

### 1. ⏱️ SurfaceFlinger & Frame Pacing Pipeline Optimization
- **`debug.sf.latch_unsignaled = 1`**: Mengizinkan SurfaceFlinger melakukan latch render buffer seketika setelah GPU selesai me-rasterize tanpa harus menunggu sinyal fence siklus VSYNC berikutnya, memangkas display input lag 1–2 frame dan menghilangkan frame drop artificial.
- **`debug.sf.enable_gl_backpressure = 0` & `disable_backpressure = 1`**: Menghilangkan backpressure pipeline GPU yang sering kali memblokir RenderThread game saat rendering frame kompleks.
- **SkiaGL Threaded Backend & Phase Offsets**: Mengaktifkan `debug.renderengine.backend skiaglthreaded` bersama offset fase mikrodetik (`early_phase_offset_ns 500000`, `early_gl_phase_offset_ns 3000000`) untuk penyerahan frame sub-millisecond.

### 2. ⚡ MediaTek FPSGO Ultra Rescue & GED Anti-Stutter Tuning
- **GPU Idle Elimination (`gpu_idle = 0`)**: Mematikan state idle mikro pada Mali GPU di mode Extreme agar shader core tidak mengalami siklus tidur-bangun antar draw call yang memicu spike frametime.
- **FPSGO Ultra Rescue (`ultra_rescue = 1`)**: Mengaktifkan MediaTek Ultra Rescue untuk mendeteksi frame time overrun secara sub-millisecond dan menginjeksi CPU boost seketika sebelum frame drop terjadi.
- **Light Loading Policy Bypass (`light_loading_policy = 0`)**: Mencegah penurunan clock otomatis saat transisi adegan/menu di dalam game.
- **Hardware MDP Composition Offloading (`ged_force_mdp_enable = 1`)**: Mengalihkan komposisi overlay ke MediaTek Display Processor, menghemat ALU execution unit GPU Mali untuk 3D rendering murni.
- **GED Top-App PID Prioritization (`gx_top_app_pid`)**: Menginjeksi PID game aktif secara dinamis ke driver GED untuk prioritas antrean command stream GPU tertinggi.

### 3. 🧠 Linux CFS Scheduler Low-Latency Preemption
- **Scheduling Latency Epoch 4ms (`sched_latency_ns = 4000000`, `sched_min_granularity_ns = 500000`)**: Memperketat jendela penjadwalan CPU dari default 10ms menjadi 4ms, memastikan thread game diprioritaskan tanpa tertunda oleh background worker.
- **Instant Core Migration (`sched_migration_cost_ns = 50000`)**: Menurunkan ambang migrasi dari 200µs ke 50µs agar thread rendering game segera berpindah ke cluster Big (A76) seketika ada beban rendering.
- **Thread Spawning Acceleration (`sched_child_runs_first = 1`)**: Thread worker yang di-spawn oleh game engine (seperti Unity Job System `Job.Worker` atau Unreal Engine worker) langsung dieksekusi seketika.
- **C-State Aware Packing Disable (`sched_cstate_aware = 0`)**: Mematikan pemadatan tugas ke core hemat daya pada Extreme mode.

### 4. 🎯 Dynamic Game Process & Thread Renicing
- Pada saat game aktif terdeteksi oleh watcher/daemon, seluruh thread proses game (`UnityMain`, `UnityGfxDeviceW`, `Job.Worker`, `RenderThread`) secara otomatis di-renice ke tingkat prioritas tertinggi Linux (`nice -20`), diberikan I/O Real-Time (`ionice -c 1 -n 0`), dan dialokasikan ke `top-app` cpuset.

### 5. 🗄️ Storage I/O Read-Ahead 2048 KB (Zero Stutter World Streaming)
- Meningkatkan buffer UFS/MMC `read_ahead_kb` ke 2048 KB dan `nr_requests` ke 512 untuk mengeliminasi hitching saat streaming tekstur dan model 3D di game open-world.

---

# Lynx [Codename: Deity] 3.0.5

## 🐛 Critical Bug Fixes (3.0.5)

### 1. ⚡ Fix GPU Extreme Mode — OPP Index vs KHz Mismatch
- **Root Cause**: Node `custom_upbound_gpu_freq` dan `gpufreq_opp_freq` di MTK GED HAL menggunakan **OPP Index** (0 = frekuensi tertinggi, 48 = terendah), bukan nilai KHz. Script lama menulis nilai `950000` (KHz) ke node index ini, sehingga GED menginterpretasinya sebagai "index 950000" yang tidak valid dan mem-fallback ke index 48 (= 300 MHz minimum).
- **Fix**: Tulis `0` (index 0 = OPP tertinggi) ke `custom_upbound_gpu_freq` dan `gpufreq_opp_freq`. Nilai KHz tetap digunakan di node `gpu_cust_upbound_freq` dan `gpu_cust_boost_freq` dari `/sys/module/ged/`.
- **Tambahan**: Aktifkan `gpufreq_fixed_freq_volt` dengan nilai puncak OPP untuk memastikan GPU terkunci di 950 MHz pada mode Extreme.

### 2. 🔒 Fix CPU Extreme Mode — MTK PPM Index vs KHz Mismatch
- **Root Cause**: Node `hard_userlimit_min_cpu_freq` dan `hard_userlimit_max_cpu_freq` di PPM (MediaTek Power Policy Manager) juga menggunakan **OPP Index** bukan KHz. Script lama mengirim nilai KHz mentah yang salah diinterpretasikan oleh PPM.
- **Fix**: Tulis `<cluster_id> 0` (index 0 = frekuensi puncak) untuk extreme, dan index proporsional untuk performance.

### 3. 🚀 Fix CPU Extreme — Governor Switch ke `performance`
- **Root Cause**: `mtkpower@1.0-service` (MTK Power HAL daemon) terus me-reset `scaling_min_freq` ke nilai defaultnya, menimpa nilai yang ditulis oleh `apply_profile.sh`.
- **Fix**: Mode Extreme kini menggunakan CPU governor **`performance`** (bukan `schedutil`), yang secara otomatis memaksa CPU berjalan di `scaling_max_freq` tanpa bergantung pada `scaling_min_freq`. Ini secara efektif mem-bypass interferensi mtkpower HAL.

### ✅ Hasil Terverifikasi (Infinix X698, Dimensity 920)
- CPU policy0 (A55): governor `performance`, freq **2000 MHz** ✅
- CPU policy6 (A76): governor `performance`, freq **2050 MHz** (saat load) ✅
- GPU: fixed_freq **950 MHz** enabled via `gpufreq_fixed_freq_volt` ✅

---

# Lynx [Codename: Deity] 3.0.4
Released on: 2026-09-28
> **Versi ini** menghadirkan **Audit & Hardening Total Seluruh Profil Hardware (18 Vektor Subsistem)** untuk Extreme, Performance, Balance, dan Powersave, menjamin **Zero Missing Tweaks & 100% Parity** antara Standalone Root Mode dan Magisk/KernelSU Module Mode, resolusi Unity FPS Uncap read permission collision, serta sinkronisasi per-app profile targeting secara komprehensif.

## ✨ Fitur Baru & Peningkatan (3.0.4)

### 1. ⚡ Master Hardware Profile Applicator (`apply_profile.sh` — 18 Subsystem Vectors)
- **Audit Menyeluruh 18 Vektor Subsistem**: Melakukan audit dan harmonisasi komprehensif pada seluruh profil hardware (`extreme`, `performance`, `balance`, `powersave`, `auto`):
  - **CPU Core & Governors**: Kunci frekuensi min/max presisi, governor selection (`performance`, `schedutil`), dan pemulihan dynamic range.
  - **Schedutil Rate Limits**: `up_rate_limit_us` 0µs untuk lompatan frekuensi seketika pada mode gaming/extreme, dan ramp-down terukur pada mode seimbang/hemat daya.
  - **MediaTek Deep Tuning**: Aktivasi `Performance(Sports) mode` pada `/proc/cpufreq/cpufreq_power_mode`, penguncian PPM userlimit table (`hard_userlimit_min_cpu_freq`), dan CCI boost.
  - **GPU Acceleration**: Boost DVFS margin 99% pada Mali GED (`/proc/ged/hal/custom_margin`) dan devfreq boost max pada Qualcomm Adreno (`/sys/class/kgsl/kgsl-3d0`).
  - **UCLAMP & CPUSET**: Task clamping top-app min 100% untuk prioritas frame rendering maksimal pada CPU cluster Big/Prime.
  - **Virtual Memory & Storage I/O**: Swappiness teroptimasi (60 extreme, 70 perf, 80 balance, 100 powersave), MGLRU boost, dan peningkatan I/O read-ahead (2048KB).
  - **OEM Throttler Neutralizer**: Membekukan daemon termal OEM (`thermal-engine`, `mi_thermald`, `thermalloadalg`) via sinyal `SIGSTOP` pada mode extreme/gaming dan memulihkan via `SIGCONT` pada mode balance.
  - **Display, Wi-Fi & Audio**: TouchBoost touch response, low-latency Wi-Fi gaming packet stack, dan AAudio MMAP FastMixer low-latency.
  - **Process Priority & Lib Injection**: Renice `-20` pada `surfaceflinger` dan HWComposer, serta injeksi `sched_lib_name` untuk library game populer (Unity, Unreal Engine).

### 2. 🛡️ Unity FPS Uncap Read Collision Resolution
- **Pre-Flight Permission Recovery**: Memperbaiki isu di mana teknik bypass limit FPS Unity (`chmod 000` pada `cpuinfo_max_freq` dan `cpu_capacity`) menyebabkan pembacaan frekuensi kosong pada pergantian profil berikutnya.
- **Dynamic Fallback Parsing**: Menambahkan pemulihan izin otomatis (`chmod 444`) sebelum pembacaan sysfs dan mekanisme fallback cerdas ke `scaling_available_frequencies`.

### 3. 🎯 Full Parity: Standalone Root Mode & Module Mode
- **Zero Gap Between Modes**: Sinkronisasi penuh antara implementasi Kotlin di `LynxRepository.kt` dan skrip shell root `/data/adb/lynx/apply_profile.sh`.
- Pengguna Standalone Root Mode kini menikmati seluruh 18 tweak subsistem yang setara 100% dengan pengguna modul terpasang di Magisk/KernelSU.

### 4. 📱 Dynamic Per-App Profile Targeting pada Watcher Daemon
- Watcher daemon mandiri (`lynx_watcher.sh` & `Smart-AI.sh`) kini mengekstrak target profil dinamis langsung dari `app_rules.tsv` dan memanggil `apply_profile.sh` dengan parameter profil spesifik per aplikasi yang sedang aktif di latar depan.

---

# Lynx [Codename: Deity] 3.0.3
Released on: 2026-09-27
> **Versi ini** menghadirkan arsitektur **Dual-Engine Per-App Automation** dengan daemon latar belakang root mandiri (*Unkillable Standalone Root Watcher*) yang beroperasi penuh di latar belakang tanpa bergantung pada antarmuka aplikasi, kebal pembunuhan proses Android, serta pemulihan otomatis pasca-reboot.

## ✨ Fitur Baru & Peningkatan (3.0.3)

### 1. 🛡️ Unkillable Standalone Root Watcher Daemon (`lynx_watcher.sh`)
- **Proses Root Mandiri (UID 0)**: Watcher berjalan terpisah dari proses Android zygote/JVM di bawah `init` (PPID 1), menjadikannya kebal terhadap pembersihan Recent Apps, pembatasan baterai OEM (XOS/MIUI/ColorOS), maupun `am force-stop`.
- **Eksekusi 0ms Instan**: Memantau aplikasi aktif dengan latensi sub-30ms via `dumpsys activity activities` (`topResumedActivity`) dan menerapkan profil hardware (CPU Schedutil 0µs ramp-up, GPU GED/Adreno boost, Display 120Hz, dan Floating HUD) secara seketika saat aplikasi dibuka.
- **Hysteresis Cooldown Buffer (4 Detik)**: Mempertahankan mode performa selama 4 detik saat berpindah aplikasi singkat (membuka notifikasi, floating chat, atau menu recent) sebelum secara halus mengembalikan frekuensi dan governor ke mode seimbang.
- **Deep Sleep Shield**: Otomatis menghentikan loop polling saat layar mati (`mHoldingDisplaySuspendBlocker=false`), mengembalikan profil hemat daya, dan mengizinkan CPU masuk ke C-States penuh tanpa menguras baterai standby.

### 2. ⚡ Dual-Engine Architecture & Task-Swipe Immunity
- **Perlindungan Task Removal**: Penambahan `android:stopWithTask="false"` pada manifest dan override `onTaskRemoved()` pada `LynxAppAutomationService` untuk memastikan kelangsungan hidup daemon saat pengguna menyapu aplikasi dari daftar tugas terkini.
- **Deteksi Status Persisten**: Logika pengecekan switch UI kini memverifikasi langsung keberadaan proses daemon root aktif melalui `isAppAutomationRunning()`, sehingga switch selalu akurat dalam posisi aktif meskipun aplikasi baru saja dibuka kembali.

### 3. 🔄 Cold-Boot Auto-Recovery (`LynxBootReceiver`)
- **Penanganan Boot Android**: Menambahkan `LynxBootReceiver` yang mendengarkan sinyal `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, dan `QUICKBOOT_POWERON`.
- Jika otomasi aktif sebelum restart, sistem secara otomatis membangkitkan Root Watcher Daemon dan layanan notifikasi saat perangkat dinyalakan kembali tanpa perlu membuka aplikasi Lynx secara manual.

### 4. 🚀 Format Pemetaan Kecepatan Tinggi (`app_rules.tsv`)
- Mengompilasi aturan per-aplikasi ke format pemetaan TSV terindeks pipa (`packageName|targetProfile|targetRefreshRate|autoFloatingHud|appName`) secara otomatis di `/data/adb/lynx/app_rules.tsv` untuk evaluasi shell berkecepatan sub-milidetik tanpa overhead parsing JSON.

---

# Lynx [Codename: Deity] 3.0.2
Released on: 2026-09-27
> **Versi ini** menghadirkan pembaruan besar pada manajemen Penjadwal Kernel & Gubernur (CFS/EAS), Otomasi Profil Per-Aplikasi & Game Picker UI cerdas, sinkronisasi penuh WebUI KernelSU/APatch, serta panduan arsitektur penyediaan driver hardware kustom.

## ✨ Fitur Baru & Peningkatan (3.0.2)

### 1. 🎮 Otomasi Profil Per-Aplikasi & Visual Game Picker UI
- **Visual App/Game Picker Dialog**: Pemilih aplikasi dan game visual terpadu dengan pencarian instan, filter kategori cerdas (Semua, 🎮 Game, ⚡ Belum Dikonfigurasi), serta deteksi otomatis tag kategori game.
- **Konfigurasi Profil Per-Aplikasi**: Pengguna dapat mengunci profil performa khusus (Extreme, Performa, Balance, Hemat), memaksakan refresh rate panel (60Hz, 90Hz, 120Hz), serta mengaktifkan overlay Floating Game HUD secara otomatis saat aplikasi dibuka.
- **Sinkronisasi Multi-Level**: Sinkronisasi instan dua arah ke `/data/adb/lynx/applist_perf.txt` dan `app_rules.json` (serta `$MODULE_DIR/core/` saat modul terpasang), memicu respon 0ms boost pada daemon AI Core.

### 2. ⚡ Penjadwal Kernel & Gubernur Deep Tunables (CFS / EAS / BORE)
- **Deteksi Jujur BORE Engine**: Memeriksa keberadaan node `/proc/sys/kernel/sched_bore` secara dinamis dan menampilkan badge status jujur (`CFS / EAS` dengan `BORE: UNSUPPORTED` pada kernel stock).
- **3 Preset Penjadwal Cepat**:
  - `⚡ Gaming`: Menyetel `sched_latency_ns` ke 4ms, `up_rate_limit_us` ke 0µs (lompatan instan tanpa jeda), dan `down_rate_limit_us` ke 5ms.
  - `⚖️ Balanced`: Pemulihan nilai seimbang AOSP (`sched_latency_ns` 10ms, `up_rate_limit_us` 500µs, `down_rate_limit_us` 20ms).
  - `🔋 Battery Saver`: Penjadwalan hemat daya (`sched_latency_ns` 20ms, `up_rate_limit_us` 4000µs).
- **Slider Parameter Presisi**: Kontrol langsung untuk CFS Latency, Min Granularity, Wakeup Granularity, Task Migration Cost, Migration Queue Limit (`nr_migrate`), dan tombol Child Process Runs First.

### 3. 🌐 Sinkronisasi Penuh WebUI KernelSU / APatch (`webroot/`)
- **Tuner Virtual Memory (VM) Terpadu**: Diperluas dengan 3 tombol preset cepat (`⚡ Gaming`, `⚖️ Balanced`, `🔋 Battery`) dan 7 slider sysfs lengkap (`dirty_ratio`, `dirty_background_ratio`, `vfs_cache_pressure`, `swappiness`, `dirty_expire_centisecs`, `dirty_writeback_centisecs`, `stat_interval`).
- **Dynamic TCP Congestion Picker**: Membaca algoritma TCP aktif dan tersedia langsung dari kernel (`/proc/sys/net/ipv4/tcp_available_congestion_control`) dan me-render tombol dinamis.
- **Wake Guard & Aggressive Doze**: Menampilkan status nyata driver Boeffla Wakelock Blocker serta switch Aggressive Doze (`dumpsys deviceidle force-idle`) untuk memangkas konsumsi baterai standby.

### 4. 📖 Panduan Penyediaan Driver Hardware Kustom
- Penambahan dokumen teknis resmi [`docs/14_CUSTOM_KERNEL_DRIVER_DEVELOPMENT_GUIDE.md`](./docs/14_CUSTOM_KERNEL_DRIVER_DEVELOPMENT_GUIDE.md) yang menjelaskan arsitektur kernel Ring 0 vs Ring 3, penyediaan patch driver Sound Control ALSA codec, driver VDD voltage table untuk undervolt/overvolt, penambahan frekuensi overclocking pada DTS OPP table MediaTek/Qualcomm, serta alur kompilasi dan pengemasan AnyKernel3 1-klik.

---

# Lynx [Codename: Deity] 3.0.1
Released on: 2026-09-26
> **Versi ini** membawa ekspansi masif Deep Hardware Scanner dengan lebih dari 450 node tuneable hardware terdeteksi secara dinamis.

## ✨ Fitur Baru & Peningkatan (3.0.1)

### 1. Deep Hardware Scanner Diperluas (450+ Hardware Nodes)
- **Tambahan subsistem yang sebelumnya tidak terpindai:**
  - **MediaTek FPSGO/FBT/FSTB**: Engine frame-pacing utama MediaTek (`/sys/kernel/fpsgo/common/*`, `/fbt/*`, `/fstb/*`) kini tampil sebagai kontrol toggle tertunable.
  - **GED HAL Layer**: Kontrol langsung GPU driver level (`/sys/kernel/ged/hal/*`) untuk custom boost freq, DVFS margin, loading step.
  - **Core Control (Qualcomm)**: Manajemen hotplug core CPU performa Qualcomm (`cpu*/core_ctl/*`).
  - **EAS & HPS**: Energy Aware Scheduling enable node dan MediaTek CPU Hotplug System.
  - **PPM Master Switch & Mode**: Kontrol on/off seluruh engine PPM MediaTek (`/proc/ppm/enabled`, `/proc/ppm/mode`).
  - **Charging Lanjutan**: Tambahan node `fast_charge`, `fastcharge_mode`, `thermal_limit`, `system_temp_level`, `wireless_boost` untuk Samsung/Xiaomi/POCO.
  - **LMK & Process Reclaim**: `/sys/module/lowmemorykiller/parameters/*` dan `/sys/module/process_reclaim/parameters/*` untuk manajemen memori lebih halus.
  - **Sound Control**: `/sys/kernel/sound_control/*` dan `/sys/class/misc/soundcontrol/*` untuk kernel audio tuning.
  - **Vibrator Amplitude**: `/sys/devices/virtual/timed_output/vibrator/amp` untuk kontrol kekuatan getaran.
  - **LRU Gen**: Seluruh node Multi-Gen LRU (`/sys/kernel/mm/lru_gen/*`) bukan hanya enabled.
  - **Adreno Extended**: `force_no_nap`, `gpuclk`, `min/max_clock_mhz`, `pwrscale`, dan `max_gpuclk`.
  - **Kernel Misc**: `pid_max`, `timer_migration`, `randomize_va_space`, `perf_cpu_time_max_percent`.
- **Smart Value Normalization**: Status string `ppm is enabled/disabled`, `fbt_idleprefer_enable:1` kini otomatis dinormalisasi ke nilai boolean 0/1.
- **Smart Skipping**: Node non-tuneable (info dumps, statistik, BQ ID, tabel) kini difilter otomatis sehingga tidak muncul sebagai toggle palsu.
- **Performa Scan**: 453 node terdeteksi dalam 80ms (dari sebelumnya 440 node dalam 130ms).
- **Metadata Baru**: 20+ entri metadata friendly ditambahkan untuk GED HAL, FPSGO, Core Control, PPM, HPS, EAS, ASLR.

### 2. Sinkronisasi Shell Script & Kotlin
- `core/lib/deep_inspector.sh` dan `buildStandaloneDeepScanScript()` di `LynxRepository.kt` kini **100% sinkron** — jalur hardware, filter, normalisasi nilai, dan kategorisasi identik persis antara shell module dan aplikasi native.

---

# Lynx [Codename: Deity] 3.0.0
Released on: 2026-09-25
> **Nama Aplikasi**: Lynx | **Codename**: Deity | **Versi**: 3.0.0  
> Rilis ini menghadirkan perombakan total (*major architectural overhaul*), penyatuan arsitektur hibrida Qualcomm & MediaTek, pembersihan redundansi kode warisan, standarisasi murni POSIX, integrasi biner BusyBox selektif (Feravolt & Brutal), antarmuka WebUI modern, serta Aplikasi Native Android Companion (Kotlin + Jetpack Compose) dengan sinkronisasi 2 arah real-time.

---

## ✨ Fitur Baru & Peningkatan (3.0.0)

### 1. Spektrum 5 Mode & AI Adaptif (Smart-AI Core v5.5)
- **5 Profil Performa Utama**:
  - `auto`: Deteksi game adaptif secara otomatis (*Zero Latency 0ms entry, 3s cooldown buffer, deep sleep suspend blocker*).
  - `balance`: Profil harian hemat daya berbasis EAS, frekuensi seimbang, dan *power-efficient workqueues*.
  - `performance`: Kunci frekuensi menengah-atas, elevasi prioritas *SurfaceFlinger/HW Composer*, dan pembatasan throttling moderat.
  - `extreme`: Frekuensi CPU & GPU maksimum, *unrestricted full thermal bypass* (trip points 150°C), dan pengisian daya bebas batas (khusus dengan *phone cooler* eksternal).
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
  - Penemuan otomatis algoritma TCP Congestion Control terbaik (`bbrv3` ➔ `bbr` ➔ `westwood` ➔ `cubic`).
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
