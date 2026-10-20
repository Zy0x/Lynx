# 🤖 AGENTS.MD — Architectural Blueprint & Development Guidelines
> **Proyek**: Lynx Universal (Hybrid Qualcomm Snapdragon & MediaTek Architecture)  
> **Status**: Development & Local Staging  
> **Otoritas**: Master Document untuk Antigravity AI Coding Agent & Pengembang  

---

## 📌 1. Visi & Identitas Proyek

### 1.1 Visi Utama
Menyatukan **Lynx** (berbasis Qualcomm Snapdragon) dan **Chimera** (berbasis MediaTek) menjadi **satu modul root terpadu (Unified Hybrid Module)** yang cerdas, efisien, aman, dan berdaya guna tinggi. Pengguna cukup mengunduh satu berkas modul ZIP yang sama, dan sistem akan secara otomatis mengidentifikasi arsitektur chipset perangkat, mengonfigurasi parameter kernel yang tepat, serta menyediakan kontrol penuh, tuning mendalam, dan pemantauan real-time terutama melalui **Aplikasi Native Android (`LynxCompanion` / Jetpack Compose)** sebagai **prioritas utama pengembangan, inovasi UI/UX, dan laboratorium eksperimen perangkat**, dengan antarmuka web modern (**WebUI** di `webroot/`) dipertahankan sebagai antarmuka pendamping sekunder (*secondary fallback*).

### 1.2 Konvensi Branding & Metadata Modul (`module.prop`)
Untuk menjaga kompatibilitas ekosistem OTA update, riwayat modul, dan transparansi informasi pengguna:
- **Module ID**: Tetap menggunakan `id=Lynx` (tidak diubah agar tidak memutus kompatibilitas manajer modul Magisk/KernelSU/APatch).
- **Module Name Dinamis**:
  - Pada perangkat Qualcomm: `name=Lʏɴx - Deity (Qualcomm)`
  - Pada perangkat MediaTek: `name=Lʏɴx - Deity (MediaTek)`
- **Author**: `ɴᴏɪʀ` (dengan atribusi kontributor pada dokumentasi).
- **Deskripsi Dinamis**: Diperbarui secara *real-time* oleh daemon pengontrol sesuai profil aktif (contoh: `[ ⚡ Auto (AI) Mode ]`, `[ 🚀 Performance Mode ]`, `[ ⚖️ Balance Mode ]`, `[ 🔥 Extreme Mode ]`).
- **Tata Kelola Versi V7 & Siklus RC Tanpa Batas (Uncapped RC1000+)**:
  - Versi modul dikunci pada **Generasi V7** (format: `7.0.0-RC<N>`).
  - Nomor Release Candidate (`RC`) berjalan secara kontinu, berurutan, dan diizinkan melampaui angka 1000 (`RC1000+`).
  - **Dilarang Keras** menaikkan versi basis dari V7 ke V8 tanpa perintah eksplisit dan tertulis langsung dari pengguna.

---

## 🏛️ 2. Cetak Biru Arsitektur Direktori (Codebase Hierarchy)

Kode modul dipisahkan secara tegas antara **Logika Universal (Core)** dan **Hardware Abstraction Layer (HAL / Platforms)** untuk mencegah tabrakan mount (*Magic Mount collision*) yang dapat memicu *bootloop*.

```
Lynx/
├── AGENTS.md                    # Dokumen ini (Master Guidelines)
├── module.prop                  # Template metadata modul
├── customize.sh                 # Otak instalasi, deteksi SoC, & conditional file injector
├── library.sh                   # Pustaka fungsi instalasi, root check, & helper umum
├── post-fs-data.sh              # Early boot script (sebelum zygote berjalan)
├── service.sh                   # Late start service (router inisialisasi boot utama)
├── sepolicy.rule                # Aturan SELinux terpadu (QCOM & MTK)
├── uninstall.sh                 # Skrip restorasi sistem saat modul dihapus
│
├── native/                      # 🦀 MESIN UTAMA NATIVE RUST (High-Performance Engine)
│   └── lynxd/                   # Rust Daemon Source (aarch64-linux-android)
│       ├── Cargo.toml           # Dependensi & profil rilis strip LTO
│       └── src/                 # Modul-modul Rust (subsystems, sysfs, hardware, daemon)
│
├── LynxCompanion/               # 📱 APLIKASI NATIVE ANDROID (Modular Kotlin Jetpack Compose)
│   ├── app/src/main/java/com/noir/lynx/
│   │   ├── core/                # Lapisan bersama: RootShell, Config, Telemetry, Sysfs
│   │   ├── designsystem/        # UI token, komponen kartu, slider aman, switch, sparkline
│   │   └── feature/             # Domain terisolasi per-fitur (battery, cpu, gpu, display, dll.)
│   └── build.gradle.kts         # Build config & ProGuard rules
│
├── core/                        # 🌐 LOGIKA UNIVERSAL MODUL (SoC-Agnostic Shell Fallbacks)
│   ├── Smart-AI.sh              # Daemon pemantau aplikasi aktif (foreground window watcher)
│   ├── applist_perf.txt         # Basis data paket aplikasi/game pemicu mode performa
│   ├── Charging-Controller.sh   # Pengontrol arus pengisian daya, bypass, & AutoCut
│   ├── CCleaner.sh              # Skrip pembersih cache, tombstones, dan log sistem
│   └── cron/                    # Konfigurasi crond harian (23:59 execution)
│
├── platforms/                   # ⚡ HARDWARE ABSTRACTION LAYER (HAL)
│   ├── qcom/                    # Komponen Khusus Qualcomm Snapdragon
│   │   ├── perf.sh              # Boost Adreno GPU, KGSL, Devfreq bus, & Schedutil boost
│   │   ├── balance.sh           # Pemulihan frekuensi, idle clock, & power efficient wq
│   │   ├── thermal.sh           # Override thermal engine generic Qualcomm & vendor HAL
│   │   ├── system.prop          # Properti Android spesifik Qualcomm (QTI gaming, touchboost)
│   │   └── system/              # Berkas overlay vendor Qualcomm (HANYA di-mount jika QCOM)
│   │       └── vendor/          # Dummy thermal-engine, msm_irqbalance, WCNSS bonding
│   │
│   └── mtk/                     # Komponen Khusus MediaTek (Diadaptasi dari Chimera)
│       ├── perf.sh              # Boost MTK PPM, CCI, cpufreq_power_mode 3, & GED GPU
│       ├── balance.sh           # Reset MTK power mode & PPM balance table
│       ├── thermal.sh           # MediaTek Thermal Policy override (.tp policy & conf)
│       ├── system.prop          # Properti Android spesifik MediaTek (MTK driver, GED)
│       └── system/              # Berkas overlay vendor MediaTek (HANYA di-mount jika MTK)
│           └── vendor/          # .tp/thermal.conf, firmware/wifi.cfg, Mali EGL configs
│
├── webroot/                     # 🌐 ANTARMUKA WEB SEKUNDER (WebUI Secondary Fallback / KSU WebView)
│   ├── index.html               # Antarmuka SPA Modern (Mobile-First, Dark Theme, Touch-First)
│   ├── app.js / script.js       # Logika interaksi & ksu.exec() bridge
│   ├── style.css                # Sistem token desain modern & responsif
│   └── script.sh                # Helper eksekusi cepat dari WebUI
│
├── system/bin/                  # Utilitas Eksekusi CLI & Biner Kompilasi
│   ├── lynxd                    # Biner Native Rust Daemon (Kompilasi dari native/lynxd)
│   ├── lynx                     # TUI Menu interaktif (Termux secondary fallback)
│   ├── Lxcore                   # Modular CLI tool untuk kontrol skrip
│   └── sqlite3                  # Database optimization binary
│
└── archive/                     # 📦 ARSIP & REFERENSI (TIDAK di-flash)
    ├── packages/                # Berkas ZIP rilis mentah
    ├── Chimera/                 # Ekstraksi modul MediaTek Chimera untuk acuan kode
    └── device_reference/        # Sampel & referensi nyata (Infinix X698 Helio G96 & Redmi Note 7 SD660)
```

---

## 🔍 3. Matriks Deteksi SoC Anti-Spoofing (`detect_soc`)

Banyak gamer dan power user menggunakan modul **SoC Spoofer** (seperti *Game Unlocker 90/120 FPS, GLTools, MagiskHide Props Config, atau Device Faker*) yang memanipulasi `ro.soc.manufacturer` atau `ro.board.platform` (misalnya ponsel MediaTek di-spoof agar terbaca sebagai Snapdragon 8 Gen 2).

> [!CAUTION]
> **Larangan Keras**: Dilarang mengandalkan `getprop` sebagai penentu utama arsitektur! Jika ponsel MediaTek di-spoof menjadi Snapdragon dan modul mempercayai `getprop`, modul akan memasang berkas vendor Qualcomm yang memicu **Bootloop atau GPU Crash**.

### Protokol Deteksi Bertingkat Anti-Spoofing:
1. **Tingkat 1: Hardware Ground Truth (Mutlak & Kebal Spoofer)**
   Modul spoofer user-space **tidak akan pernah bisa memalsukan driver fisik kernel Linux** maupun *Device Tree Blob (DTB)* yang dimuat saat cold-boot:
   - **Device Tree Kernel**: Periksa string vendor di `/sys/firmware/devicetree/base/compatible` atau `/proc/device-tree/compatible`.
   - **Device Node Karakter GPU**:
     - Qualcomm: Keberadaan `/dev/kgsl-3d0` dan sysfs `/sys/class/kgsl`
     - MediaTek: Keberadaan `/dev/mali0` / `/dev/ged`, sysfs `/proc/ged`, atau `/proc/ppm`
   - **SoC Physical Driver**: Keberadaan `/sys/devices/soc0` (Qualcomm).

2. **Tingkat 2: Verifikasi & Deteksi Spoofer (Spoof-Awareness Alert)**
   - Jika Hardware Kernel = **MediaTek**, namun `getprop` melaporkan **Qualcomm**:
     - Sistem mencatat: `SPOOF_DETECTED=true`
     - Sistem **mengabaikan nilai `getprop`** dan secara paksa mengunci target ke arsitektur **MediaTek**.
     - Tampilkan peringatan visual di terminal, Native Companion App, dan WebUI:
       `⚠️ Terdeteksi SoC Spoofer aktif! Mengunci engine ke MediaTek demi keamanan hardware.`

3. **Tingkat 3: System Properties (Hanya Digunakan Jika Node Kernel Terproteksi/Tertutup)**
   - Periksa `ro.soc.manufacturer`, `ro.board.platform`, dan `ro.hardware` hanya jika Tingkat 1 tidak memberikan kesimpulan pasti.

4. **Tingkat 4: Fallback Aman (Generic Linux)**
   - Jika kedua arsitektur tidak teridentifikasi secara sahih, modul beralih ke mode **Generic Linux CPUFreq**, tanpa menyentuh properti vendor atau memasang berkas overlay berbahaya.

```bash
# Standar implementasi fungsi deteksi SoC Anti-Spoofing
detect_soc() {
    TARGET_SOC="generic"
    IS_SPOOFED=false

    # 1. Cek Ground Truth Hardware Kernel (DTB & GPU/SoC Driver)
    local dt_compat=""
    [ -f "/sys/firmware/devicetree/base/compatible" ] && dt_compat=$(cat /sys/firmware/devicetree/base/compatible 2>/dev/null | tr '[:upper:]' '[:lower:]')
    [ -z "$dt_compat" ] && [ -f "/proc/device-tree/compatible" ] && dt_compat=$(cat /proc/device-tree/compatible 2>/dev/null | tr '[:upper:]' '[:lower:]')

    if [[ "$dt_compat" == *"mediatek"* ]] || [ -d "/proc/ppm" ] || [ -d "/proc/ged" ] || [ -c "/dev/ged" ]; then
        TARGET_SOC="mtk"
    elif [[ "$dt_compat" == *"qcom"* ]] || [ -d "/sys/class/kgsl" ] || [ -c "/dev/kgsl-3d0" ] || [ -d "/sys/devices/soc0" ]; then
        TARGET_SOC="qcom"
    fi

    # 2. Cek apakah ada manipulasi / Spoofer pada System Properties
    local prop_manuf=$(getprop ro.soc.manufacturer | tr '[:upper:]' '[:lower:]')
    local prop_plat=$(getprop ro.board.platform | tr '[:upper:]' '[:lower:]')

    if [ "$TARGET_SOC" = "mtk" ] && [[ "$prop_manuf" == *"qualcomm"* || "$prop_plat" =~ ^(sm|sdm|msm|kona|taro) ]]; then
        IS_SPOOFED=true
        ui_print "⚠️ Terdeteksi SoC Spoofer: $prop_manuf ($prop_plat)"
        ui_print "🛡️ Hardware Asli Terverifikasi: MediaTek (Dimensity/Helio)"
        ui_print "✅ Lynx mengunci instalasi ke MediaTek Engine demi keamanan!"
    elif [ "$TARGET_SOC" = "qcom" ] && [[ "$prop_manuf" == *"mediatek"* || "$prop_plat" =~ ^mt ]]; then
        IS_SPOOFED=true
        ui_print "⚠️ Terdeteksi SoC Spoofer: $prop_manuf ($prop_plat)"
        ui_print "🛡️ Hardware Asli Terverifikasi: Qualcomm Snapdragon"
        ui_print "✅ Lynx mengunci instalasi ke Qualcomm Engine demi keamanan!"
    fi

    # 3. Fallback ke System Properties jika hardware node tidak terdeteksi (sangat jarang)
    if [ "$TARGET_SOC" = "generic" ]; then
        if [[ "$prop_manuf" == *"mediatek"* ]] || [[ "$prop_plat" =~ ^mt ]]; then
            TARGET_SOC="mtk"
        elif [[ "$prop_manuf" == *"qualcomm"* ]] || [[ "$prop_plat" =~ ^(sm|sdm|msm|kona|taro) ]]; then
            TARGET_SOC="qcom"
        fi
    fi
}
```


---

## 🧠 4. Arsitektur Mesin Cerdas AI Core (`Smart-AI.sh` — Adaptive Engine)

Daemon pemantau aplikasi aktif (`core/Smart-AI.sh`) adalah jantung kecerdasan modul. Versi lawas menggunakan *naive polling* (`dumpsys window` setiap 0.5s tanpa debounce) yang rentan terhadap **I/O race condition**, *micro-stutter*, dan boros baterai (*deep sleep blocker*).

AI Core wajib menerapkan standar **Adaptive Intelligent Engine** berikut:

### 4.1 Mekanisme Debounce / Hysteresis Filter (Solusi Anti-Race Condition)
- **Masalah**: Pengguna membuka/menutup game secara cepat, membuka floating chat WhatsApp, atau melihat recent apps selama 1 detik, memicu eksekusi `AI_performance.sh` dan `AI_balance.sh` bolak-balik secara simultan.
- **Standar Solusi**:
  - **Masuk Game**: Trigger mode `performance` langsung seketika (**Zero Latency / 0 ms**).
  - **Keluar Game**: **Dilarang langsung beralih ke `balance`**. Sistem wajib menahan status di mode `performance` selama buffer waktu pendinginan (*cooldown timer*) **3 hingga 5 detik**.
  - Jika dalam jendela 3 detik game aktif kembali (misalnya hanya membalas notifikasi singkat), mode performa tetap dipertahankan tanpa gangguan frekuensi (*stutter-free*).
  - Peralihan ke `balance` hanya dieksekusi jika pengguna telah meninggalkan game lebih dari 3 detik berturut-turut.

### 4.2 Atomic Concurrency Lock (Mutex Guard)
- **Standar Solusi**:
  - Dilarang membiarkan dua proses shell modifikasi sysfs berjalan bersamaan.
  - Setiap pergantian mode wajib menggunakan *lockfile* atomik di RAM (`/dev/lynx_mode.lock`).
  - Jika skrip performa sedang menulis parameter kernel, permintaan baru wajib membatalkan (*kill*) eksekusi usang secara bersih sebelum mengeksekusi parameter baru, mencegah *I/O kernel choke*.

### 4.3 Screen-State Power-Awareness & Deep Sleep Shield
- **Masalah**: Polling terus berjalan saat ponsel terkunci di saku, mencegah CPU masuk ke status *Deep Sleep*.
- **Standar Solusi**:
  - AI wajib memverifikasi status layar ponsel (via `dumpsys power` atau node DRM panel):
    ```bash
    is_screen_on() {
        dumpsys power | grep -q "mHoldingDisplaySuspendBlocker=true"
    }
    ```
  - **Saat Layar Mati (Screen OFF)**: Loop polling dihentikan total dan beralih ke mode hemat daya (`sleep 15` s/d `sleep 30`), membiarkan kernel masuk ke status tidur lelap (*C-States*) demi efisiensi baterai 100%.
  - **Saat Layar Menyala (Screen ON)**: Daemon seketika bangun dan kembali memantau aplikasi aktif.

### 4.4 Ultra-Fast Foreground Query (Sub-30ms)
- Menggantikan eksekusi berat `dumpsys window` dengan metode parsing terfokus yang 10x lebih ringan:
  ```bash
  get_top_app() {
      dumpsys activity activities | grep -m1 "topResumedActivity" | awk '{print $3}' | cut -d'/' -f1
  }
  ```

---

## 🛡️ 5. Protokol Keamanan Hardware & Mitigasi Bootloop (Non-Negotiable)

1. **Conditional File Injection Wajib**:
   - Dilarang keras meletakkan berkas statis vendor (`system/vendor/...`) di root installer ZIP.
   - Berkas vendor harus berada di `platforms/qcom/system/` atau `platforms/mtk/system/`.
   - `customize.sh` bertugas menyalin **hanya** folder platform yang relevan ke `$MODPATH/system/`.
2. **Validasi Keberadaan Node Sebelum Tulis**:
   - Dilarang menulis perintah `echo "value" > /path/node` secara membabi buta.
   - Setiap operasi penulisan wajib dibungkus dalam pengecekan:
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
3. **Reversibilitas Penuh pada `uninstall.sh`**:
   - Setiap modifikasi pada setelan global Android (`window_animation_scale`, properti keamanan, dan setelan baterai) wajib dicadangkan dan dikembalikan ke kondisi aslinya saat modul di-uninstall.

---

## ⚡ 6. Spektrum Mode: Dari Safe Balanced Hingga Extreme Unrestricted

Modul harus menyediakan spektrum profil performa yang fleksibel untuk berbagai kebutuhan pengguna:

| Mode | Target Penggunaan | Perilaku CPU / GPU | Batas Termal | Pengisian Daya (Charging) |
| :--- | :--- | :--- | :--- | :--- |
| **Balance (Default)** | Harian & Media | Schedutil / PPM Balanced, idle downclock aktif | Proteksi OEM Aktif | Standar Aman OEM |
| **Aggressive** | Multitasking Cepat | Frekuensi minimal dinaikkan, RAM purge aktif | Proteksi OEM Aktif | Fast Charge Standar |
| **High Performance** | Gaming Stabil | Kunci frekuensi menengah-atas, governor performance saat game | Pembatasan throttling moderat | Limit arus 1500–2000mA (Thermal control) |
| **Extreme (Unrestricted)** | Benchmark & Hardcore Gaming | Kunci frekuensi maksimum (CPU + GPU), disable PPM / Devfreq latency 0 | **Bypass Penuh** (Thermal zones disabled, override trip points 150°C) | **Unrestricted Charge / Bypass Charging** (hingga 4500mA jika kabel & charger mendukung) |

> [!WARNING]
> Mode **Extreme (Unrestricted)** wajib dilengkapi *disclaimer* visual yang jelas di Aplikasi Native Companion (dan WebUI) bahwa mode ini diperuntukkan bagi pengguna tingkat lanjut dengan pendingin eksternal (*phone cooler*) guna menghindari degradasi baterai dan komponen SoC.

---

## 🎨 7. Standar Antarmuka: Prioritas Utama Native Android App (`LynxCompanion`) & Tier Sekunder WebUI (`webroot/`)

### 7.1 Paradigma "Native-First" — LynxCompanion Sebagai Episentrum Utama Pengembangan
Aplikasi **Native Android Companion (`LynxCompanion` / `LynxKernelManager.apk`)** adalah **prioritas tertinggi dan episentrum utama** dari seluruh siklus perancangan, eksperimen fitur baru, validasi tuning kernel/hardware, inovasi UI/UX, serta telemetri real-time Lynx Universal.

- **Hierarki & Peran Antarmuka Proyek**:
  1. **Tier 1 (Utama & Prioritas Mutlak)**: **Aplikasi Native Android (`LynxCompanion`)**. Seluruh fitur baru, inovasi kontrol kernel, desain kartu Bento/Material 3, animasi responsif, logika interaksi, dan eksperimen performa di perangkat fisik wajib diimplementasikan, diuji, dan dimatangkan di aplikasi native terlebih dahulu.
  2. **Tier 2 (Sekunder & Fallback)**: **WebUI (`webroot/`)**. Berfungsi murni sebagai antarmuka pendamping/cadangan bagi manajer modul berbasis WebView (KernelSU/APatch WebUI). Perubahan atau sinkronisasi fitur ke WebUI hanya dilakukan setelah implementasi di native Android stabil dan terbukti valid di perangkat fisik.

### 7.2 Ketentuan Teknis & Desain Native Android (Kotlin Jetpack Compose)
Aplikasi native dibangun menggunakan **Kotlin + Jetpack Compose + Material 3** dengan standar:
- **Mobile-First & Touch-First Presisi**:
  - Ukuran target sentuh tombol, switch, dan slider minimal **48×48 dp** (bebas miss-touch).
  - Tampilan stabil pada berbagai rasio layar non-reguler (1080×2400, 1080×2460, 20:9, 21:9) hingga resolusi tablet dan layar lipat.
  - Bebas dari ketergantungan hover-only; seluruh interaksi dirancang untuk sentuhan tangan satu jari yang ergonomis.
- **Estetika Cyberpunk OLED Dark & Readability First**:
  - Skema warna: **Modern Cyberpunk / Deep Dark Theme** (latar belakang OLED `#0a0a0a` / `#121212`, kartu kontras `#1a1a1a`, aksen visual Cyan `#00ffaa` atau Biru Modern `#0099ff`, serta aksen status semantik).
  - Kontras teks tinggi dan konsisten, tidak melelahkan mata.
  - Tipografi semantik (*system-ui, sans-serif*) dengan hierarki visual yang tegas dan bersih.
- **Kinerja 60/120 FPS & Efisiensi Compose**:
  - Optimasi recomposition (state stabil/immutable, `derivedStateOf`, remembered lambdas) guna mencegah jank saat scroll atau pembaruan telemetri live.
  - Animasi transisi halus dan fungsional dengan durasi **150–300 ms** tanpa memicu lonjakan Garbage Collection (GC).
- **Akses Root Universal (`libsu`)**:
  - Eksekusi cepat melalui pustaka resmi `com.github.topjohnwu.libsu:core` pada KernelSU, APatch, dan Magisk.
  - Pemuatan data asinkron berbasis Kotlin Coroutines / Flow tanpa memblokir Main Thread (UI Thread).

### 7.3 Ketentuan WebUI (`webroot/` — Secondary Fallback Interface)
Sebagai antarmuka sekunder, WebUI tetap mematuhi keselarasan visual dengan aplikasi native:
- Mengikuti token desain Cyberpunk Dark yang selaras dengan tema native.
- Eksekusi asinkron via `ksu.exec(cmd)` dengan error boundary tangguh dan sanitasi input.
- Sinkronisasi status dua arah berbasis `config.json` agar konsisten dengan status native app.

### 7.4 Tata Kelola Batas Domain & Single Source of Truth (GPU & Display Tuning)
Untuk mencegah komplikasi dan redundansi kontrol di masa mendatang, seluruh pengembang dan AI Agent **WAJIB** mematuhi pembagian domain tanggung jawab berikut (berlaku langsung pada kartu Compose di `LynxCompanion` maupun komponen WebUI):
1. **Domain Macro Preset (`GpuQuickProfilesCard`)**: Murni 1-click harmonized presets (Battery, Balanced, Esports, Extreme).
2. **Domain Clock OPP & Telemetri (`GpuMasterTunerCard`)**: Murni bertanggung jawab atas frekuensi clock fisik (Min/Max MHz picker), lock clock, grafik real-time load bezier waveform, dan proses render aktif. **DILARANG** menambahkan kontrol kernel sysfs, toggle governor, atau bypass termal di kartu ini.
3. **Domain Kernel Hardware Abstraction (`AdaptiveGpuHardwareCard`)**: Menjadi **satu-satunya Single Source of Truth** untuk seluruh parameter sysfs driver GPU (Adreno KGSL, MTK GED/FPSGO, ARM Mali Kbase, Generic Linux Devfreq). Wajib diverifikasi via permission probe (`VERIFIED_WORKING` vs `READ_ONLY_LOCKED`) dan transaction write verification. Dilarang menduplikasi tweak ini di kartu lain.
4. **Domain Compositor & Render Engine (`GraphicsDriverHwuiCard`)**: Mengontrol global Android HWUI, Updatable Game Driver, SurfaceFlinger latching, dan shader cache.
5. **Domain Per-App Graphics Hub (`PerAppGraphicsHubCard`)**: Mengontrol preferensi driver grafis dan refresh rate per-paket aplikasi.
6. **Domain Display Hardware Output (`DisplayRefreshRateTouchCard`)**: Mengontrol SurfaceFlinger HWC physical mode switching, Auto dynamic mode, TouchBoost multi-OEM, dan DC Dimming.
7. **Domain Kalibrasi Warna (`ColorManagementCard`)**: Mengontrol SurfaceFlinger ColorMatrix 1015 universal, D65 White Point, KCAL, dan HBM.
8. **Domain Diagnostik Murni Tab 2 (`GpuHardwareInfoDashboard`)**: Berstatus **Murni Read-Only (Spec & Telemetry)**. Dilarang mengekspos tombol interaktif pengubah pengaturan sistem di Tab 2.

---

## 📦 8. Alur Interaksi Penginstal (Installer UX) & First-Boot Safety

Instalasi modul di Magisk, KernelSU, maupun APatch menerapkan protokol **Semi-Otomatis Cerdas & Dormant First-Boot**:

1. **Deteksi Otomatis & Konfirmasi Tombol Volume**:
   - `customize.sh` menjalankan deteksi hardware anti-spoofing (`detect_soc`).
   - Terminal instalasi menampilkan konfirmasi interaktif:
     `[?] Terdeteksi Chipset: <Nama SoC>. Apakah benar? [Vol +: YA] [Vol -: PILIH MANUAL]`
   - Jika pengguna menekan `Vol +`: Sistem melanjutkan dengan platform terdeteksi.
   - Jika `Vol -`: Sistem menampilkan menu pemilihan manual (1. Qualcomm, 2. MediaTek, 3. Generic).
2. **Preservasi Aset Platform Cadangan**:
   - Berkas vendor platform terpilih disalin ke `$MODPATH/system/`.
   - Folder platform mentah (`platforms/qcom/`, `platforms/mtk/`, `platforms/generic/`) tetap disimpan di `$MODPATH/platforms/` (<150 KB). Hal ini memungkinkan pengguna beralih arsitektur langsung via Native APK (`LynxCompanion`) atau WebUI jika terjadi kesalahan tanpa perlu flash ulang di recovery.
   - Beralih platform via UI wajib dilindungi dengan dialog peringatan keras (*Critical Danger Warning Modal*).
3. **Status Aman Awal (Dormant / Standby First-Boot)**:
   - Modul diinstal dalam status `"active_profile": "dormant"` pada `config.json`.
   - Modul **DILARANG** langsung menerapkan clock ekstrem saat boot pertama sebelum pengguna membuka Native APK (`LynxCompanion`) atau WebUI untuk memilih profil dan konfigurasi yang diinginkan.

---

## 🛠️ 9. Protokol Kerja AI Agent (Rules of Engagement)

Setiap AI Agent yang bekerja pada repositori ini **WAJIB** mematuhi pedoman operasional berikut:

1. **Mandat "Native-First" — Seluruh Eksperimen, Uji Coba, & Inovasi Terpusat pada Native Android App**:
   - **Target Utama Laboratorium & Eksperimen**: Seluruh uji coba fitur baru, tuning kernel/sysfs (CPU, GPU, Battery, Charging, Display, I/O, dsb.), perancangan kartu UI Bento/Material 3, dan pengujian performa WAJIB difokuskan serta divalidasi langsung pada **Aplikasi Native Android (`LynxCompanion` / `LynxKernelManager.apk`)** di perangkat uji aktif (**Infinix X698**) terlebih dahulu.
   - **Status WebUI Sekunder / Follower**: WebUI (`webroot/`) berstatus murni sebagai antarmuka pendamping sekunder. Agen DILARANG mendahulukan atau mengalihkan fokus pengujian ke WebUI sebelum implementasi di native Android selesai, stabil, dan terbukti bekerja optimal di perangkat nyata, kecuali bila ada instruksi khusus tertulis dari pengguna.
   - **Siklus Iterasi Uji Coba Standar**:
     1. Terapkan logika/fitur pada kode Kotlin (`LynxCompanion/app/...`) atau modul Rust (`native/lynxd/...`).
     2. Kompilasi APK varian Release (`tools/build_apk.ps1` atau `./gradlew :app:assembleRelease`).
     3. Pasang langsung ke perangkat uji aktif Infinix X698 via ADB (`adb -s <X698_SERIAL> install -r LynxKernelManager.apk`).
     4. Jalankan pengujian fungsionalitas, rendering UI Compose, dan respon hardware langsung pada perangkat fisik.
2. **Mandat Commit & Push Otomatis Pasca-Perubahan (Rule 16 Compliant)**:
   - Setelah setiap siklus perubahan, perbaikan, atau pengujian selesai divalidasi pada *local working tree*, agen **WAJIB** secara langsung melakukan `git commit` dan `git push` ke repositori GitHub tanpa menunggu perintah tambahan, dengan pesan commit yang deskriptif dan mencantumkan konteks perubahan (mematuhi Global Rule 16).
3. **Pengujian Nyata via ADB & Dynamic Connection Protocol (Dual-Device Laboratory Matrix)**:
   - Laboratorium pengembangan memiliki **2 perangkat fisik** dengan otoritas yang **berbeda secara mutlak**:
     1. **Perangkat Uji Utama (Active Read-Write Test Device)**: **Infinix Note 11S (`X698`, MediaTek Helio G96 / `mt6781`, Android 14, Magisk Root)** pada port tetap **`5555`**. Satu-satunya perangkat yang diizinkan menerima instalasi APK uji, penulisan sysfs live, dan penyimpanan berkas di `/storage/emulated/0/Debug/`.
     2. **Perangkat Referensi & Sampel (Strict Read-Only Daily Driver — ZERO-TOUCH)**: **Xiaomi Redmi Note 7 (`lavender`, Qualcomm Snapdragon 660 / `sdm660`, Rooted)** via Wireless ADB (Port **`5555`**) atau Kabel USB. Perangkat krusial *daily driver* pengguna yang **HANYA** boleh dibaca (`cat`, `ls`, `find`, `getprop`) via stdout ADB ke PC lokal dan **HARAM** dimodifikasi sedikit pun (lihat Sub-bab 9.3).
   - **Protokol Koneksi Dinamis (Anti-Stuck & Hardware Identity Interlock)**: Alamat IP perangkat bersifat dinamis karena pengguna berpindah jaringan Wi-Fi/Hotspot (namun port Wireless ADB **SELALU 5555**).
   - **Aturan Eksekusi Agen**:
     - Sebelum menjalankan pengujian ADB pertama kali atau jika koneksi terputus, agen **WAJIB** menjalankan utilitas auto-discovery:
       - Untuk pengujian aktif (Infinix X698): `.\tools\connect_device.cmd` atau `$target = & .\tools\connect_device.ps1 -Quiet`
       - Untuk pengambilan sampel referensi Read-Only (Redmi Note 7): `.\tools\connect_device.cmd -Role reference` atau `$refTarget = & .\tools\connect_device.ps1 -Role reference -Quiet`
     - Perintah ini secara otomatis mendeteksi perangkat melalui 4 tingkat verifikasi bertingkat + **Hardware Identity Interlock**:
       1. Verifikasi koneksi `adb devices` yang sudah aktif (memvalidasi `ro.product.device` & `ro.board.platform`),
       2. mDNS Zero-Config Discovery (`adb mdns services`),
       3. Cache IP terakhir (`tools/.last_device_ip` untuk Test Device, `tools/.last_reference_ip` untuk Reference Device),
       4. Pemindaian paralel multi-subnet kecepatan tinggi (.NET Async Sockets, <1 detik di semua subnet lokal aktif).
     - **Selalu Gunakan Flag Target (`-s <SERIAL>`)**: Karena host PC dapat memiliki beberapa perangkat/emulator sekaligus, seluruh perintah adb wajib menyertakan flag serial perangkat target (contoh: `adb -s 192.168.1.5:5555 shell ...`).
     - **Dilarang Keras**: Menebak IP secara acak, melakukan perulangan `Test-Connection` lambat, atau menyimpulkan daemon adbd mati sebelum menjalankan utilitas `connect_device`.
     - **Larangan Mengaktifkan Mode Rotasi Layar Otomatis (Anti Auto-Rotate Policy)**:
       - Saat menghubungkan perangkat via ADB (`connect_device`) maupun selama sesi otomasi/pengujian perangkat berlangsung, agen **DILARANG KERAS** mengaktifkan mode rotasi layar otomatis (`settings put system accelerometer_rotation 1` atau memodifikasi orientasi layar `user_rotation`), **KECUALI jika memang sangat diperlukan** untuk validasi skenario pengujian spesifik (misalnya pengujian layout landscape tertentu).
       - Pertahankan preferensi rotasi bawaan milik pengguna tanpa intervensi sepihak. Jika dalam kondisi darurat orientasi sempat diubah untuk pengujian, agen **WAJIB** segera mengembalikan setelan rotasi ke kondisi semula begitu pengujian selesai.
4. **Lokasi Penyimpanan Berkas Pengujian & Protokol Zero-Trace / Pembersihan Seketika (Syarat Mutlak — Non-Negotiable)**:
   - **Lokasi Tunggal yang Diizinkan**: Setiap kali melakukan remote testing, inspeksi, dump hierarki UI (`uiautomator dump`), tangkapan layar (`screencap`), log uji, maupun berkas sementara pada perangkat uji aktif (**Infinix X698**), seluruh berkas luaran **WAJIB** diletakkan secara terisolasi di folder:
     `/storage/emulated/0/Debug/` (alias `/sdcard/Debug/`).
   - **Larangan Keras Penulisan Liar**:
     - **DILARANG KERAS** meletakkan berkas di luar folder tersebut, terutama langsung di root internal storage (`/sdcard/`, `/storage/emulated/0/`).
     - **DILARANG KERAS** menumpuk atau meninggalkan berkas dump, gambar uji, skrip sementara, maupun log di folder temp sistem (`/data/local/tmp/`).
   - **Protokol Pembersihan Seketika (Immediate Post-Pull Zero-Trace Cleanup — SYARAT MUTLAK)**:
     - Begitu berkas selesai ditarik ke PC lokal/folder artifact melalui perintah `adb pull`, agen **WAJIB SEKETIKA ITU JUGA** menghapus berkas-berkas tersebut dari perangkat target tanpa menunda:
       ```bash
       # Pola standar penarikan dan pembersihan instan:
       adb -s <SERIAL> pull /sdcard/Debug/screen_sample.png ./local_path/
       adb -s <SERIAL> shell "rm -f /sdcard/Debug/screen_sample.png"
       ```
     - Jika rangkaian pengujian selesai, agen **WAJIB** memastikan direktori `/sdcard/Debug/`, root `/sdcard/`, dan `/data/local/tmp/` benar-benar bersih dari seluruh artefak uji:
       ```bash
       adb -s <SERIAL> shell "rm -rf /sdcard/Debug/* /data/local/tmp/*.png /data/local/tmp/*.xml 2>/dev/null"
       ```
     - **Toleransi Nol Terhadap Sampah Sisa Pengujian**: Tidak boleh ada satu pun berkas `.png`, `.xml`, `.log`, atau temporary artifacts yang tertinggal di `/sdcard/Debug/`, di root `/sdcard/`, maupun di `/data/local/tmp/`. Perangkat fisik pengguna wajib selalu dalam kondisi higienis dan bebas dari residu pengujian otomatis AI.
   - Sebelum mengeksekusi penulisan atau penyimpanan berkas pada **Infinix X698**, selalu pastikan folder penampung sementara telah dibuat:
     `mkdir -p /storage/emulated/0/Debug 2>/dev/null`
   - ⚠️ **Pengecualian Mutlak untuk Redmi Note 7 (`lavender`)**: Dilarang membuat folder `/storage/emulated/0/Debug/` atau menulis berkas apapun di Redmi Note 7! Seluruh sampel dari Redmi Note 7 wajib dialirkan via stdout ADB langsung ke PC lokal di `archive/device_reference/redmi_note_7_sdm660/`.
5. **Kompatibilitas Shell Android**:
   - Seluruh skrip shell (`.sh`) wajib kompatibel dengan shell standar Android (`/system/bin/sh` / Toybox / BusyBox ash).
   - Hindari dependensi bash-eksklusif yang tidak didukung secara *native* oleh shell Android murni.
6. **Dokumentasi & Integritas Kode**:
   - Pertahankan struktur komentar yang informatif pada setiap skrip.
   - Catat setiap perubahan arsitektur pada dokumentasi dan hindari paparan data sensitif.
7. **Larangan Keras Menginstall / Mem-flash Modul Tanpa Perintah Eksplisit (Strict No-Install Policy)**:
   - **DILARANG KERAS** menginstall, mem-flash berkas modul ZIP (`Lynx-Deity-*.zip`), memodifikasi folder modul root (`/data/adb/modules/Lynx/`), atau mengeksekusi installer modul ke perangkat target pengguna via ADB maupun shell root, **KECUALI** jika pengguna secara EKSPLISIT memberikan instruksi tertulis untuk menginstallnya.
   - Pengujian aplikasi (Native Companion APK `LynxKernelManager.apk`) dan inspeksi sistem harus mampu berjalan secara independen dan aman dengan graceful fallback tanpa mengasumsikan modul root telah terpasang di sistem.
8. **Kewajiban Mutlak Build APK Versi Release (Strict Release-Only Build Policy)**:
   - **Wajib Selalu Varian Release**: Seluruh proses kompilasi, build, pengujian, dan pemasangan APK Lynx Companion (`LynxKernelManager.apk`) **WAJIB SELALU** menggunakan varian **Release** (`assembleRelease` / `:app:assembleRelease`), **BUKAN** varian Debug!
   - **Alasan Teknis & Kinerja**: Varian Release mengaktifkan optimasi penuh R8, ProGuard shrinking, dead-code elimination, dan resource minification (menghasilkan ukuran APK ramping ~2.8 MB dibanding ~19.5 MB pada Debug, 7x lebih hemat memori). Ini memastikan setiap pengujian mencerminkan stabilitas dan performa produksi nyata serta mencegah bug/crash tersembunyi akibat minifikasi R8.
   - **Larangan Keras Varian Debug**: Dilarang keras mengompilasi atau memasang varian Debug (`assembleDebug`), **KECUALI** jika pengguna secara EKSPLISIT menginstruksikan varian debug atau kondisi troubleshooting darurat yang sangat memerlukan debug symbols.
   - **Penyelarasan Utilitas Otomatis**: Skrip otomatisasi build `tools/build_apk.ps1` secara baku mengompilasi varian Release (`assembleRelease`) dan menyalin hasil `app-release.apk` langsung ke root `LynxKernelManager.apk`.

### 9.1 Protokol Pengujian Aplikasi Tanpa Modul (Standalone Root Testing Protocol — Khusus Infinix X698)
Untuk memverifikasi stabilitas dan fungsionalitas Lynx Companion pada **Infinix X698** tanpa memodifikasi sistem root:
1. **Instalasi Non-Invasif**: APK dipasang via ADB (`adb -s <X698_SERIAL> install -r LynxKernelManager.apk`) tanpa mem-flash atau menyentuh `/data/adb/modules/Lynx/`.
2. **Deteksi Status**: Aplikasi mendeteksi `isModuleInstalled = false` dan menampilkan badge status `Standalone Root Mode` (warna oranye) serta banner informatif di Tab Engine tanpa popup mengganggu.
3. **Penyimpanan Konfigurasi Mandiri**: Dalam mode standalone, setelan disimpan di data lokal aplikasi (`/data/user/0/com.noir.lynx/files/config.json`). Jika modul diinstal di masa depan, data akan otomatis disinkronisasi ke `/data/adb/modules/Lynx/config.json`.
4. **Kontrol Hardware Langsung (Direct Sysfs Execution)**:
   - CPU: Frekuensi min/max, governor, dan preset schedutil diaplikasikan langsung ke `/sys/devices/system/cpu/cpufreq/`.
   - GPU: Live clock dan boost level diaplikasikan langsung via driver GPU vendor (Mali GED / Adreno devfreq).
   - Storage I/O: Pemilihan scheduler diaplikasikan langsung ke `/sys/block/*/queue/scheduler`.
   - VM & Jaringan: Pembebasan RAM cache (drop caches) dan TCP congestion control via `/proc/sys/`.
   - Layar: Kunci refresh rate panel diaplikasikan via Android system settings.
5. **Dormant Background Daemons**: Fitur otomasi latar belakang yang bergantung pada modul (seperti Smart-AI foreground watcher dan Charging AutoCut) berstatus *Standby/Non-Aktif*.
6. **Penyimpanan Berkas Uji & Protokol Zero-Trace**: Seluruh berkas diagnostik, log, dump UI, dan tangkapan layar pengujian disimpan secara eksklusif di `/storage/emulated/0/Debug/` dan **WAJIB LANGSUNG DIHAPUS** seketika setelah ditarik (`adb pull`), tanpa pernah meninggalkan jejak di internal storage maupun `/data/local/tmp/`.

### 9.2 Tata Kelola Versi V7 & Siklus Release Candidate Tanpa Batas (Uncapped RC1000+ Protocol)

Sesuai instruksi dan ketetapan mutlak pemilik proyek, arsitektur penomoran versi Lynx Universal menerapkan aturan:

1. **Penguncian Generasi V7 (Strict Ban on V7 to V8 Transition)**:
   - Versi modul dan rilis resmi **DIKUNCI PADA GENERASI V7** (format: `7.0.0-RC<N>`).
   - **DILARANG KERAS** mengubah, memperbarui, atau menaikkan versi basis dari **V7 ke V8** (misalnya menjadi `8.0.0`, `v8.0.0`, `8.0.0-RC1`, atau variasi generasi berikutnya), **KECUALI** jika pengguna secara EKSPLISIT dan TERTULIS memberikan perintah langsung untuk migrasi ke V8.
   - Selama tidak ada instruksi eksplisit tersebut, seluruh siklus rilis dan iterasi perbaikan wajib tetap berada di bawah payung generasi V7 (`7.0.0-RC...`).

2. **Siklus Release Candidate Tak Terbatas (Uncapped RC Beyond 1000)**:
   - Penomoran Release Candidate (`RC`) berjalan secara kontinu, berurutan, bertahap, dan **dibiarkan melebihi 1000** (`7.0.0-RC94`, `7.0.0-RC95`, ..., `7.0.0-RC999`, `7.0.0-RC1000`, `7.0.0-RC1001`, dst.).
   - Agen AI, pengembang, maupun skrip build otomatis **DILARANG** membatasi (*capping*), memotong, mereset kembali ke angka kecil, atau melompatkan nomor versi ke versi major baru hanya karena angka RC mencapai atau melewati 100, 999, atau 1000.
   - Setiap rilis baru meningkatkan nomor RC secara linear (`RC<N+1>`), dengan `versionCode` pada `module.prop` yang ikut bertambah selaras.

3. **Sinkronisasi Metadata Versi & Changelog**:
   - Setiap rilis atau pembaruan yang mengubah kode/fitur wajib menyelaraskan metadata:
     - `module.prop`: `version=7.0.0-RC<N>`, `versionCode=<N>`
     - `changelog.md`: `# Lynx Universal [...] 7.0.0-RC<N>`
     - `LynxCompanion/app/build.gradle.kts`: `versionName` dan `versionCode` disesuaikan secara proporsional.
   - Penulisan commit message mengikuti format konvensional dengan mencantumkan tag versi V7 yang relevan (contoh: `feat(...): ... (v7.0.0-RC95)`).

### 9.3 Matriks Perangkat Laboratorium & Protokol Proteksi Mutlak Redmi Note 7 (Strict Read-Only Daily Driver)

> [!CAUTION]
> **PERINGATAN KRITIS TINGKAT TERTINGGI (ZERO-TOLERANCE DAILY DRIVER PROTECTION)**:
> Perangkat **Xiaomi Redmi Note 7 (`lavender` — Qualcomm Snapdragon 660 / `sdm660`)** adalah perangkat utama (*vital daily driver*) milik pengguna. Kesalahan sekecil apapun yang mengubah parameter sistem, memodifikasi berkas, atau mengganggu stabilitas pada Redmi Note 7 **TIDAK DAPAT DITOLERANSI**.

| Atribut Perangkat | Perangkat 1: Infinix Note 11S (`X698`) | Perangkat 2: Xiaomi Redmi Note 7 (`lavender`) |
| :--- | :--- | :--- |
| **SoC / Arsitektur** | MediaTek Helio G96 (`mt6781`) | Qualcomm Snapdragon 660 (`sdm660`) |
| **Metode Koneksi** | Wireless ADB (Port `5555`) | Wireless ADB (Port `5555`) & Kabel USB |
| **Status Root** | Magisk Root (`su`) | Rooted (`su` tersedia untuk baca node terproteksi) |
| **Peran & Otoritas** | **Active Read-Write Test Device** | **🔒 STRICT READ-ONLY REFERENCE (ZERO-TOUCH)** |
| **Instalasi APK / UI Test** | ✅ Diizinkan (`adb install -r`) | ❌ **DILARANG KERAS** |
| **Modifikasi Sysfs / Prop** | ✅ Diizinkan (Pengujian Tweak) | ❌ **DILARANG KERAS** |
| **Penulisan File di Device** | ✅ Diizinkan hanya di `/sdcard/Debug/` (Wajib langsung dibersihkan pasca-pull) | ❌ **DILARANG KERAS (0 Byte Written)** |
| **Lokasi Simpan Sampel** | `/storage/emulated/0/Debug/` (Device) | `archive/device_reference/redmi_note_7_sdm660/` (PC Lokal) |

#### Aturan Mutlak Interaksi dengan Redmi Note 7 (`lavender` / `sdm660`):
1. **Hanya untuk Pengambilan Sampel & Referensi Arsitektur Snapdragon (`qcom`)**:
   - Redmi Note 7 digunakan **murni sebagai sumber observasi pasif** guna mempelajari jalur sysfs/procfs nyata pada kernel Qualcomm Snapdragon (seperti `/sys/class/kgsl/kgsl-3d0/`, `/sys/class/devfreq/`, `/sys/devices/system/cpu/cpufreq/`, `/sys/class/qcom-battery/`, `/sys/class/power_supply/`, `/sys/class/thermal/`, dan `getprop`).
2. **Daftar Perintah yang DILARANG KERAS pada Redmi Note 7 (Zero-Write Policy)**:
   - **Dilarang mengubah nilai kernel/sistem**: Haram menjalankan `echo ... > ...`, `printf ... > ...`, `tee`, `sysctl -w`, `setprop`, `settings put`, `cmd`, `service call`, atau `content insert/update/delete`.
   - **Dilarang mengubah izin/atribut file**: Haram menjalankan `chmod`, `chown`, `chgrp`, `chcon`, `restorecon`, `mount`, `umount`, `remount`.
   - **Dilarang menulis/menghapus berkas di storage perangkat**: Haram menjalankan `mkdir`, `touch`, `rm`, `mv`, `cp`, `dd`, `tar`, `unzip`, `adb push`, termasuk **DILARANG** membuat folder `/storage/emulated/0/Debug/` atau `/data/local/tmp/` di Redmi Note 7.
   - **Dilarang mengintervensi proses atau paket**: Haram menjalankan `adb install`, `adb uninstall`, `pm`, `am`, `kill`, `killall`, `pkill`, `stop`, `start`, `reboot`, `input keyevent/tap/swipe`.
3. **Daftar Perintah yang DIIZINKAN pada Redmi Note 7 (Passive Stdout Read-Only)**:
   - Hanya perintah pembacaan pasif tanpa efek samping: `cat`, `ls`, `find`, `grep`, `awk`, `sed` (tanpa `-i`), `head`, `tail`, `stat`, `id`, `uname -a`, `getprop` (tanpa argumen tulis), dan `dumpsys` (tanpa flag pengubah status).
   - Penggunaan `su -c "<perintah_baca>"` diizinkan **HANYA** untuk membaca (`cat` / `ls` / `find`) node sysfs/procfs yang memiliki permission terbatas (misal `0400` atau `0600` milik root) dan wajib diverifikasi bahwa string perintah di dalam `su -c` tidak mengandung operator redirect tulis (`>`, `>>`) atau perintah mutasi.
   - Seluruh hasil pembacaan harus langsung di-redirect ke file di **PC Lokal** (contoh: `adb -s <LAVENDER_SERIAL> shell "su -c 'cat /sys/class/kgsl/kgsl-3d0/gpu_available_frequencies'" > archive/device_reference/redmi_note_7_sdm660/gpu_freqs.txt`).
4. **Protokol Verifikasi Identitas Sebelum Eksekusi (Hardware Identity Interlock)**:
   - Sebelum mengeksekusi skrip pengujian (Read-Write) apapun via ADB, agen **WAJIB** memastikan target bukan Redmi Note 7 (`ro.product.device != lavender` dan `ro.board.platform != sdm660`).
   - Skrip `tools/connect_device.ps1` telah dilengkapi pengaman otomatis (*Hardware Identity Interlock*): pemanggilan standar `.\tools\connect_device.cmd` hanya akan mengembalikan serial perangkat uji **Infinix X698** dan secara otomatis mengecualikan **Redmi Note 7** dari target pengujian tulis.

### 9.4 Mandat Kebersihan Repositori Mutlak & Pencegahan Bloat (Zero-Bloat & Strict Git Hygiene)

Untuk menjaga repositori tetap padat, esensial, ringan, dan cepat di-clone, seluruh AI Agent dan pengembang **WAJIB MEMATUHI ATURAN KEBERSIHAN MUTLAK** berikut:

1. **Larangan Keras Men-Track Berkas Biner Rilis & Artefak Kompilasi**:
   - **DILARANG KERAS** melakukan `git add` atau meng-commit berkas `.zip`, `.apk`, `.aab`, `.kpm`, `.jar`, `.o`, `.bin`, `.so`, dan `dist/*` ke dalam riwayat Git!
   - Seluruh berkas rilis final (`dist/*.zip`, `LynxKernelManager.apk`) hanya diunggah ke platform hosting rilis (GitHub Releases), BUKAN disimpan di dalam Git tree.
2. **Larangan Keras Men-Track Tangkapan Layar, UI Dumps, & Lockfiles**:
   - Dilarang keras meng-commit gambar tangkapan layar (`*.png`, `*.jpg`, `*.webp`), dump hierarki UI (`dump*.xml`), lockfiles (`*.lock`), dan direktori `test-results/`.
   - File-file ini wajib selalu masuk dalam `.gitignore` dan langsung dibersihkan dari disk lokal pasca-analisis.
3. **Isolasi Skrip Pengujian Satu Kali Pakai (One-Off Probes)**:
   - Skrip tes ad-hoc, dump DTB, inspeksi kallsyms, atau pencarian string kernel sementara dilarang berserakan di folder `tools/` atau root.
   - Skrip pengujian sementara wajib diisolasi di `tools/probes_archive/` atau `tests/live_probes_archive/` yang secara baku di-ignore oleh Git, sehingga folder `tools/` murni berisi utilitas packaging (`pack_module.ps1`), verifikasi shell (`verify_zero_shell.ps1`), dan skrip helper produksi aktif.
4. **Audit Sebelum Commit**:
   - Sebelum mengeksekusi `git add` dan `git commit`, agen **WAJIB** menjalankan `git status -s` untuk memverifikasi bahwa tidak ada file biner, screenshot, atau dump yang tidak sengaja ter-stage.

---

### 9.5 Mandat Dokumentasi Desain Arsitektur Per Halaman / Sub-Halaman (`docs/architecture/screens/`)

Untuk menjamin kejelasan teknis, transparansi penuh, dan kemudahan audit proyek:

1. **Kewajiban Dokumentasi Modular Per-Halaman**:
   - Setiap halaman utama, sub-halaman (Tuning Subscreens), Bottom Sheet kompleks, dan Floating Overlay Service pada `LynxCompanion` **WAJIB** memiliki dokumen spesifikasi arsitektur terpisah di `docs/architecture/screens/`.
   - **Dilarang Menyatukan Dokumentasi Menjadi Satu File Monolitik**: Struktur dokumen wajib modular per-domain halaman guna menjaga kerapian dan transparansi.
2. **Standar Struktur Wajib Setiap Dokumen Halaman**:
   Setiap berkas di `docs/architecture/screens/` wajib memuat 5 pilar informasi:
   - **Pilar 1: Identitas & Struktur UI Compose**: File path Kotlin, komponen layout kartu Bento/Material 3, dialog, dan sheet yang digunakan.
   - **Pilar 2: State ViewModel & Aliran Reaktif**: StateFlow yang diamati, interval polling telemetri, dan method mutasi state.
   - **Pilar 3: Parameter & Konfigurasi Detail**: Seluruh slider, toggle, chip, dan menu dropdown yang diekspos ke pengguna, lengkap dengan nilai default, batas min/max, dan step increment.
   - **Pilar 4: Pemetaan Node Kernel & Sysfs Fisik (Ground Truth Mapping)**: Jalur sysfs/procfs nyata yang dimanipulasi di kernel, dengan percabangan arsitektur tegas (Qualcomm Adreno vs MediaTek GED/PPM vs Generic Linux fallback).
   - **Pilar 5: Protokol Keamanan & Error Boundary**: Validasi slider, verifikasi transaksi pasca-tulis (`VERIFIED_WORKING` vs `READ_ONLY_LOCKED`), perlindungan thermal clamp, dan mitigasi soft-reboot.
3. **Kewajiban Sinkronisasi Saat Menambah Fitur Baru**:
   - Jika agen menambahkan layar baru, sub-halaman baru, atau toggle konfigurasi baru pada `LynxCompanion`, agen **WAJIB** memperbarui atau membuat berkas dokumen terkait di `docs/architecture/screens/` sebelum menyelesaikan task.

---

### 9.6 Kepatuhan Mutlak & Disiplin Eksekusi pada Setiap Chat & Perintah (Strict Compliance Mandate)

1. Seluruh pedoman di dalam `AGENTS.md` ini berstatus **HUKUM TERTINGGI PROYEK (NON-NEGOTIABLE)**.
2. Agen AI **WAJIB** membaca dan mematuhi aturan ini secara konsisten pada setiap chat, perintah, dan interaksi tanpa mengabaikan prinsip Native-First, proteksi Redmi Note 7 (Zero-Touch), Zero-Trace debugging di Infinix X698, serta kebersihan repositori Git.
3. Kepatuhan terhadap aturan ini dievaluasi secara otomatis pada setiap iterasi pengembangan.

---

## 🧠 10. "Kecerdasan Tingkat Tinggi" — Protokol Penemuan Kapabilitas Dinamis

Sesuai motto dan visi proyek, Lynx mengusung **Zero Hardcoding Guarantee**. Modul dilarang berasumsi bahwa sebuah fitur kernel (misal `zstd`, `bfq`, `schedutil`) tersedia di semua perangkat.

Setiap subsistem wajib menggunakan **Universal Dynamic Capability Discovery**:

1. **ZRAM Compression Algorithm**:
   - Mem-parsing string `/sys/block/zram0/comp_algorithm` (contoh output kernel: `lzo [lz4] lz4hc zstd`).
   - Rantai Fallback Dinamis:
     - Target Rasio Tinggi: `zstd` ➔ `lz4hc` ➔ `lz4` ➔ `lzo-rle` ➔ `lzo`.
     - Target Latensi Rendah / Hemat Daya: `lz4` ➔ `lzo-rle` ➔ `lzo`.
   - Jika target algoritma tidak ada di output kernel, sistem otomatis mengambil algoritma tertinggi berikutnya dan mencatatnya ke log fallback.
2. **Storage I/O Scheduler**:
   - Mem-parsing `/sys/block/*/queue/scheduler` secara langsung.
   - Rantai prioritas: `none` (UFS) ➔ `mq-deadline` ➔ `bfq` ➔ `cfq`.
3. **TCP Congestion Control**:
   - Mem-parsing `/proc/sys/net/ipv4/tcp_available_congestion_control`.
   - Rantai prioritas: `bbr` ➔ `cubic` ➔ `westwood` ➔ `reno`.
4. **Governor CPU & Devfreq**:
   - Mem-parsing `scaling_available_governors` sebelum melakukan peralihan. Jika kernel kustom membawa governor sendiri (`blu_schedutil`, `sched_pixel`), pertahankan governor tersebut dan sesuaikan tunables-nya.
5. **Kapasitas RAM Fisik**:
   - Membaca `MemTotal` dari `/proc/meminfo` secara real-time untuk menentukan ukuran ZRAM (RAM $\le$ 4 GB: 50% ZRAM LZ4; RAM $\ge$ 8 GB: 35-50% ZRAM ZSTD/LZ4).

---

## 📱 11. Arsitektur Native APK Companion (`LynxCompanion`) & Two-Way Sync Engine

Aplikasi **Native Android Companion (`LynxCompanion`)** adalah **pusat kendali dan fokus utama pengembangan** Lynx Universal, yang menghubungkan pengguna secara langsung dengan parameter kernel dan mesin Rust secara instan, dengan WebUI bertindak sebagai antarmuka pendamping yang sinkron 2 arah secara real-time:

1. **Teknologi Native**:
   - Bahasa: **Kotlin + Jetpack Compose + Material 3** dengan estetika Cyberpunk OLED Dark (`#0a0a0a`, `#00ffaa`, `#0099ff`).
   - Akses Root Universal: Menggunakan pustaka resmi `com.github.topjohnwu.libsu:core` untuk mengeksekusi shell root secara instan pada KernelSU, APatch, Magisk, dan root generik.
2. **State Engine Terpusat Berbasis JSON (`config.json`)**:
   - Lokasi data: `/data/adb/modules/Lynx/config.json`.
   - WebUI membaca dan menulis ke `config.json` via `ksu.exec` atau micro-daemon HTTP.
   - Native APK membaca dan menulis ke `config.json` via `libsu` Shell.
   - **Real-Time 2-Way Sync**: Native APK menggunakan `FileObserver` (Linux inotify), sedangkan daemon latar belakang modul menggunakan `inotifyd`. Setiap perubahan slider/toggle di salah satu antarmuka seketika memperbarui tampilan di antarmuka lainnya tanpa jeda!

---

## 📊 12. Ultra-Detailed Logging Engine & 1-Click Diagnostics

Untuk mencegah "log kosong" dan kehilangan jejak eksekusi:

1. **Pencatatan Log Terstruktur**:
   - `logs/execution.log`: Mencatat setiap penulisan sysfs, nilai sebelum, nilai sesudah, dan exit code perintah.
   - `logs/daemon.log`: Mencatat event foreground window switch, debounce timer, dan screen-off suspend blocker.
   - `logs/fallback.log`: Mencatat setiap kejadian fallback kapabilitas (misal saat kernel tidak mendukung `zstd` dan turun ke `lz4`).
   - Rotasi otomatis: Log dibatasi maksimal 512 KB per berkas dan dirotasi otomatis untuk mencegah memori penuh.
2. **1-Click Bug Report Export**:
   - Tombol "Export Bug Report" di Native Companion APK (dan WebUI) memanggil `Lxcore log export`.
   - Menghasilkan berkas terkompresi `/sdcard/Lynx/lynx_diagnostic_<timestamp>.zip` berisi:
     - `config.json` aktif.
     - Ringkasan hardware (`dts`, GPU ID, OPP tables).
     - Cuplikan `dmesg` dan `logcat`.
     - Seluruh riwayat berkas log modul.

---

## 🔍 13. Granular Profile Verification & Debugging Engine (Unit-Level Auditing)

Untuk memastikan seluruh konfigurasi profil (`extreme`, `performance`, `balance`, `powersave`, `auto`) benar-benar bekerja 100% dan tidak mengalami silent fallback atau reversion oleh daemon vendor:

1. **Prinsip Verifikasi Satuan Konfigurasi (Unit-Level Auditing)**:
   - Dilarang hanya memverifikasi status keluar skrip (`exit 0`).
   - Setiap tweak individual (satuan node/baris config) wajib diaudit nilai nyatanya langsung dari kernel sysfs/procfs/settings setelah diaplikasikan.
2. **Arsitektur Mesin Verifikasi (`core/lib/verify_profile.sh`)**:
   - Membaca arsitektur hardware asli secara mandiri (MediaTek vs Qualcomm vs Generic).
   - Menguji seluruh parameter: CPU Governor, Min/Max Freq, Schedutil Rate Limits, Workqueue, SoC Engines (MediaTek PPM/GED/Mali/FPSGO; Qualcomm KGSL/Devfreq/CoreControl), CPUSet, UCLAMP, VM, CFS, I/O, Display Refresh Rate, Thermal Zones, dan pembekuan Throttler OEM.
3. **Status Klasifikasi Hasil Audit**:
   - `[PASS]` / `VERIFIED`: Nilai runtime hardware persis cocok dengan target spesifikasi profil.
   - `[CLAMP]`: Nilai diadaptasi secara sah oleh driver hardware ke step OPP terdekat.
   - `[FALLBACK]`: Nilai tidak berubah atau direvert oleh daemon OEM (e.g. PowerHAL).
   - `[ERROR]`: Node gagal diakses karena pembatasan permission/SELinux.
   - `[SKIP]`: Node tidak didukung oleh kernel/SoC perangkat saat ini.
4. **Alur Pelaporan & Integrasi**:
   - Menghasilkan ringkasan dan skor kesehatan profil (`Health Score %`).
   - Menyimpan laporan terstruktur ke `/data/adb/lynx/profile_audit.json` dan `/data/adb/lynx/profile_audit.log`.
   - Otomatis mencadangkan log ke folder debug lokal `/storage/emulated/0/Debug/` (Rule 9.3).
   - Dapat dipanggil via CLI: `Lxcore verify [profile]` atau `apply_profile.sh <profile> --verify`.
   - Diintegrasikan ke Companion App via `LynxRepository.verifyProfile()` dan `readProfileAuditJson()`.

---

## 🏛️ 14. Mandat Arsitektur Modular & Manajemen Ukuran Berkas (Strict Modularity & Anti-Monolith Policy)

Untuk mencegah degradasi keterbacaan, konflik git skala besar, dan kompleksitas kode yang berlebihan (*spaghetti codebase*), seluruh pengembang dan AI Agent **WAJIB** mematuhi pedoman ukuran dan modularisasi berkas berikut:

### 14.1 Batas Ukuran Berkas & Fungsi (File & Function Size Budgets)
1. **Target Ukuran Berkas**: Setiap berkas sumber (`.kt`, `.rs`) ditargetkan berukuran **$\le$ 400 baris**.
2. **Batas Keras Berkas Baru / Hasil Ekstraksi**: Dilarang keras membuat berkas baru atau hasil pemecahan refactor yang melebihi **700 baris**.
3. **Larangan Menambah Beban File Monolitik**: Dilarang menambahkan baris kode baru ke berkas monolitik lama (`LynxRepository.kt`, `LynxComponents.kt`, `LynxViewModel.kt`, `LynxTuningSubscreens.kt`, `MainActivity.kt`). Setiap penambahan fitur baru wajib diekstraksi atau dibuat ke dalam berkas terisolasi sesuai domain fiturnya.
4. **Batas Fungsi `@Composable`**: Satu fungsi Composable dibatasi maksimal **250 baris**. Jika komponen antarmuka membutuhkan kode lebih panjang, wajib dipecah menjadi sub-composable `private` dalam berkas yang sama atau modul partisi terpisah (`*Sections.kt` / `*Cards.kt`).
5. **Prinsip Satu Simbol Utama per Berkas (One Public Component per File)**: Setiap kartu visual publik atau subscreen diletakkan pada berkas tersendiri dengan penamaan berkas identik dengan nama simbol utamanya (contoh: `GpuMasterTunerCard.kt`, `BatteryRuntimeCard.kt`).
6. **Audit Batas Baris Wajib**: Semua berkas sumber Kotlin yang dipelihara manual di LynxCompanion/app/src/main/java/ wajib dipertahankan **di bawah 700 baris** (maksimal 699). Target normal tetap **≤400 baris** sesuai butir 1. Sebelum menyelesaikan setiap modularisasi/perubahan, hitung baris seluruh berkas .kt; jika ada yang mencapai 700 baris atau lebih, pecah berdasarkan domain yang kohesif. Build dan pengujian final aplikasi wajib memakai varian **release** (`:app:assembleRelease` dan `:app:testReleaseUnitTest`) agar minifikasi dan shrinking ikut diverifikasi; gunakan debug hanya bila pengguna meminta secara eksplisit.

### 14.2 Arsitektur Terisolasi per Fitur (`feature/<nama_fitur>/`)
Struktur kode aplikasi Android (`LynxCompanion`) diatur secara ketat per domain fitur dengan pola baku:
- **`Route`**: Entry point composable untuk navigasi layar (contoh: `BatteryRoute.kt`).
- **`State`**: Slice state terisolasi khusus fitur tersebut (mengeliminasi penumpukan field datar di `LynxUiState`).
- **`Controller`**: Handler aksi bisnis dan intent guard coroutine (pindahan dari `LynxViewModel`).
- **`data/`**: Repository domain dan parser murni (contoh: `BatteryRepository.kt`, `BatteryStatsParser.kt`).
- **`ui/`**: Kartu visual murni tanpa akses root atau I/O langsung.
- **Larangan Impor Silang UI**: Fitur dilarang saling mengimpor Composable fitur lain secara langsung. Komunikasi lintas fitur hanya diperbolehkan melalui `core/` atau shell navigasi pusat.

### 14.3 Pemisahan Parser Murni dari I/O (Testable Contract)
Seluruh fungsi parsing teks kernel (`dumpsys batterystats`, `top`, `/proc/stat`, time_in_state, scheduler) wajib dipisahkan dari logika eksekusi I/O shell. Parser murni menerima input `String` dan menghasilkan objek data terstruktur, sehingga dapat diverifikasi via Unit Test (`src/test/`) tanpa ketergantungan perangkat keras.

---

## 🦀 15. Mandat Mesin Native Rust (`lynxd`) — Rust-First Computation & Subshell Ban

Sistem eksekusi Lynx menempatkan biner native Rust (`native/lynxd/`) sebagai **jantung utama pemrosesan kernel**, bukan sekadar utilitas pendamping:

### 15.1 Prinsip Rust-First untuk Beban Komputasi & Sysfs I/O
1. **Delegasi Penuh ke Rust**: Seluruh operasi yang membutuhkan parsing teks besar di kernel, query status frekuensi tinggi, batching sysfs multi-node, mitigasi Doze, penguncian atomic lockfile, dan background watchdog **WAJIB** dijalankan oleh `lynxd`.
2. **Lapisan Kotlin Hanya Presentation Layer**: Aplikasi Android Kotlin Jetpack Compose berfungsi murni sebagai antarmuka pengguna yang reaktif, mengonsumsi data terstruktur berformat JSON dari `lynxd` melalui memory cache atau eksekusi cepat.

### 15.2 Larangan Keras Perulangan Subshell di Layer Android (`Subshell Polling Ban`)
1. **Larangan Fork Berulang**: Dilarang keras membuat perulangan polling di Kotlin menggunakan `Shell.cmd(...)` berulang-ulang untuk membaca sensor atau status sysfs.
2. **Dampak Negatif yang Dihindari**: Fork proses `sh`/`su` baru setiap beberapa detik memicu *CPU spikes*, membanjiri logcat, menyebabkan *Garbage Collection (GC) thrashing*, menghasilkan *micro-stutter / dropped frames* pada UI Compose, dan menguras baterai perangkat.
3. **Standar Zero-IO Fast-Path**: Seluruh telemetri berkala wajib memanfaatkan fast-path in-process read yang disediakan `lynxd` (eksekusi sub-0.02ms, CPU duty cycle <0.001%).

### 15.3 Protokol Daya Hemat & Deep Sleep Shield (Zero Persistent Wakelocks)
1. **Watchdog Adaptif Non-Blocking**: Loop pemantauan latar belakang di Rust (seperti `lynxd adb watchdog`) wajib menggunakan jeda adaptif minimal **2500ms** tanpa menahan Android Partial Wakelock.
2. **Jaminan C-States 100%**: Saat layar padam (*Screen OFF*), kernel Linux harus diizinkan sepenuhnya memasuki status tidur lelap (*Deep Sleep C-States*). Loop pemantauan tidak boleh mencegah CPU masuk suspend.

### 15.4 Standar Kompilasi & Penempatan Biner Rust
1. **Target Toolchain**: Biner native selalu dikompilasi dengan arsitektur 64-bit Android:
   ```powershell
   cargo build --release --target aarch64-linux-android
   ```
2. **Lokasi Biner Produksi**: Biner hasil kompilasi disalin langsung ke path modul:
   `system/bin/lynxd` (dengan izin berkas `0755` / `rwxr-xr-x`).
3. **Auto-Resurrection Supervised**: Setiap pemanggilan query status subsistem (misal `lynxd adb status`) wajib menyertakan verifikasi liveness; jika proses watchdog terbunuh oleh Android OOM killer, daemon secara otomatis membangkitkan proses tersebut kembali di latar belakang.

---

## 🌐 16. Mandat Localization Produk (`id` & `en` — No Hardcoded UI Text)

Seluruh teks yang ditampilkan kepada pengguna wajib dapat diterjemahkan. Bahasa rilis awal yang didukung penuh adalah Bahasa Indonesia (`id`) dan English (`en`); struktur katalog harus memungkinkan penambahan locale lain tanpa mengubah alur fitur.

### 16.1 Cakupan Teks yang Wajib Dilokalkan
1. **Companion Android**: Seluruh teks Compose, menu, dialog, snackbar/toast, status/error/success, notifikasi, Quick Settings tile, label aksesibilitas (`contentDescription`/semantics), placeholder, dan teks yang dibentuk dari nilai dinamis.
2. **WebUI**: Teks HTML statis dan dinamis, label/tombol, judul, placeholder, pesan console/toast, atribut `title`/`aria-*`, dan setiap teks yang diubah oleh JavaScript.
3. **Installer, CLI, dan skrip**: Semua prompt, `ui_print`, bantuan, status, peringatan, dan pesan error yang terlihat pengguna. Teks teknis dalam log internal yang tidak ditampilkan sebagai UI boleh tetap memakai format diagnostik teknis.
4. Dokumentasi teknis, identifier internal, nama properti kernel, perintah, nama paket, enum/protokol, kode, angka, dan satuan standar bukan teks UI dan tidak diterjemahkan.
5. **Metadata parameter**: Nama tampilan, kategori, deskripsi, rekomendasi, label opsi, dan petunjuk `#` yang disediakan Lynx wajib dilokalkan di Companion dan WebUI. Path sysfs/procfs, nama node mentah, nilai kernel, dan komentar kernel yang tidak dikenal tetap diperlakukan sebagai data teknis.

### 16.2 Sumber Teks dan Preferensi Bahasa
1. **Jangan menambahkan literal bahasa manusia langsung ke UI baru**. Android Compose wajib mengambil teks dari Android string resources (`values-id/strings.xml` dan `values-en/strings.xml`) menggunakan `stringResource`; WebUI wajib memakai token stabil di katalog `webroot/locale-data.js`; output shell/CLI wajib memakai helper locale bersama (`core/locale.sh`).
2. Android menyediakan katalog `id` dan `en` dengan key yang sama; `values/strings.xml` tetap menjadi fallback Indonesian untuk locale yang tidak didukung. WebUI memilih dari locale browser dengan aturan yang sama.
3. Companion menyediakan pilihan bahasa manual yang mengikuti locale sistem secara default dan menyimpan override lokal pada preferensi aplikasi. WebUI menyediakan pilihan manual dan menyimpan override pada browser (local storage); preferensi kedua antarmuka tetap independen.
4. Output installer/CLI/skrip mengikuti locale sistem Android; pada native CLI, baca `persist.sys.locale`, lalu `ro.product.locale`, dan gunakan `LANG` hanya sebagai fallback. Jangan menyimpan preferensi bahasa UI ke `config.json` modul atau mencampurnya dengan konfigurasi kernel.
5. Semua komentar kode, docstring, catatan inline, dan komentar pada template skrip yang dibuat atau dipelihara di repositori wajib ditulis dalam English, termasuk komentar `#`, `//`, `/* */`, XML/HTML, shell, Kotlin Gradle, TOML, YAML, INI, CFG, dan SQL. Komentar menjelaskan implementasi untuk pengembang dan tidak diterjemahkan ke katalog UI; teks yang tampil atau dapat diedit pengguna tetap wajib dilokalkan.
6. Teks berparameter wajib memakai placeholder bernomor/format yang aman pada katalog. Teks berjumlah wajib memakai plural resources/aturan plural; jangan merangkai kalimat terjemahan dari potongan kata.

### 16.3 Audit, Validator, dan Kriteria Selesai
1. Perubahan UI harus memperbarui seluruh katalog locale sekaligus. Key yang hilang, key yatim, atau fallback tersembunyi tidak diperbolehkan.
2. **Metadata harus lengkap di setiap permukaan**: setiap nama, kategori, deskripsi, rekomendasi, label opsi, bantuan, dan komentar `#` yang berasal dari katalog tunable wajib ada pada katalog Android dan WebUI. Generator WebUI wajib mengimpor `tools/tunable_metadata_translations.json`; validator wajib membandingkan semua entri metadata dengan sumber, teks Indonesian, teks English, dan placeholder. Metadata yang menyertakan nilai runtime harus memakai pola terparameter yang mempertahankan nilai tersebut.
3. **Semua bentuk teks UI wajib diaudit**: teks tombol, subteks, deskripsi, judul, hint, placeholder, state/badge dari setiap cabang `if/else` atau `when`, output dinamis, dialog, toast, HTML yang dibuat melalui `innerHTML`, DOM `textContent`, atribut aksesibilitas, handler inline HTML, dan teks JavaScript yang dirangkai dari parameter. Katalog harus menyimpan kalimat utuh; pola runtime hanya boleh digunakan bila nilai dinamis memang diperlukan dan seluruh kalimat pada kedua bahasa sudah tersedia. Generator dan validator wajib memeriksa cabang teks UI, termasuk nilai yang disalurkan melalui model atau parameter komponen. Nilai model yang dipakai ulang sebagai label visual dan `contentDescription` harus diterjemahkan pada kedua jalur; status notifikasi/overlay serta ringkasan hasil skrip yang dirangkai oleh helper juga harus memakai resource locale, bukan nama mode atau fragmen kalimat hardcoded. Pisahkan label buatan aplikasi dari stdout/stderr mentah agar output perintah pengguna tidak diterjemahkan secara keliru.
4. Validator harus memeriksa key sumber dan katalog `id`/`en`, placeholder termasuk `$name`, `${name}` dan placeholder format, jumlah capture group pola Compose dinamis terhadap placeholder resource, metadata tunable di kedua UI, string Compose statis/dinamis, HTML dan JavaScript statis/dinamis termasuk `innerText`, `document.title`, `setAttribute`, dan text node, atribut aksesibilitas, judul/deskripsi kanal notifikasi beserta teks dan aksinya, keluaran shell/CLI yang dilokalkan, serta komentar kode termasuk komentar baris penuh dan komentar inline. Audit template Compose tidak boleh bergantung pada pencarian kata berbahasa Indonesia saja: label English yang diikuti angka, nilai runtime, atau parameter juga harus diwajibkan memiliki pola locale. Paritas key saja tidak cukup: teks English harus benar-benar English dan tidak boleh menyalin terjemahan Indonesian secara keliru.
5. Jalankan `python tools/validate_locales.py` untuk pemeriksaan otomatis. Audit statis tambahan harus mencari literal bahasa manusia yang tertinggal pada seluruh permukaan produk; tinjau hasilnya agar identifier dan nilai kernel tidak salah dianggap sebagai UI. Jangan melemahkan aturan validator untuk meloloskan teks produk yang belum diterjemahkan.
6. Verifikasi kedua bahasa pada locale sistem/browser `id`, `en`, serta locale lain yang memakai fallback Indonesian; verifikasi pula override manual tetap tersimpan setelah aplikasi/halaman dibuka ulang.
7. Periksa placeholder, plural, pesan sukses/error, dialog, notifikasi/tile, label aksesibilitas, installer, dan CLI dalam kedua bahasa. Sebelum menyelesaikan perubahan localization, jalankan unit test dan build aplikasi varian **Release** sesuai kebijakan repo; jangan menaikkan versi.
8. **Runtime format validation**: Literal percent signs in Android formatted resources must use `%%` (for example `%1$s%%`). Catalog parity alone cannot catch Java Formatter crashes. Run `LocaleFormatCatalogTest` in the Release unit-test suite and reject unescaped percent signs in the locale validator. Keep generator input templates and generated resources consistent.

---

## 17. Shader Compilation and Measured Storage Tuning

1. **Preserve shader caches**: Do not add blanket shader/pipeline cache deletion to cleaners, RAM cleanup, performance profiles, boot tasks, or game launch hooks. Cache removal can force expensive recompilation. Any future recovery feature must target an explicitly selected, stopped application, warn about recompilation, verify failures, and never report success unconditionally.
2. **Keep ART and shaders separate**: `cmd package compile` / dexopt compiles Android bytecode, not game GPU shaders. Never label ART compilation as shader warmup or claim that it activates anti-stutter. Generic root commands cannot replace an engine-specific shader warmup integration.
3. **Measure before tuning**: Capture process/thread CPU deltas, CPU affinity and policy limits, storage deltas, I/O pressure, temperature, and visible progress before attributing shader compilation delays to storage. Already-maximal CPU clocks and idle storage do not justify larger I/O queues or thermal overrides.
4. **Adaptive default I/O policy**: Following the user's explicit request, production profiles and `lynxd io apply` share `hardware/storage.rs`. Identify SD versus MMC through the kernel card type and UFS/NVMe/USB through controller ancestry; never infer media from block numbering, device model, system properties, or a mount path. Cap excessive SD read-ahead at 256 KiB while preserving smaller/disabled values. Preserve eMMC, UFS, NVMe, USB, unknown, virtual, and loop queue settings until separately measured evidence supports another policy. The ceiling is a conservative SD policy, not a universal measured optimum. Do not force scheduler, queue depth, affinity, F2FS remounts/durability options, global writeback, MMCQD priority, or TCP changes as part of this I/O default. Measurements and limitations are in `docs/GENSHIN_MOUNTX_IO_TRIAL_20261014.md`; the original device-scoped lab helper remains `tools/genshin_sd_io_profile.sh`.
5. **Cache diagnostics are partial**: An empty generic scan cannot establish that an application has no shader cache. Do not count packaged shader inputs as compiled pipeline caches or imply that scanning precompiles shaders. New guidance, labels, statuses, and plural summaries must follow the locale rules above.
6. **Validate sustained Extreme performance**: Inspect active MediaTek PPM policy limits and actual CPU/GPU clocks before adding boosts. A maximum GPU floor can coexist with a thermally capped CPU and worse frame pacing. Compare supported OPP candidates on the same scene after heat soak; retain only measured device-specific results, never turn one X698 result into a universal clock constant or disable thermal/battery protections to make a benchmark pass. The controlled results and rejected candidates are recorded in `docs/GENSHIN_EXTREME_TRIAL_20261015.md` and `docs/X698_GENSHIN_TUNING_20261015.json`; these records are not automatically loaded production presets.
7. **Keep live trials comparable and reversible**: Serialize collectors and mutations with a shared lock; save boot-bound recovery metadata before writes, verify readbacks, and restore each candidate after measurement. Record scene/user-input changes, temperatures, policy ceilings, trace errors, and instrumentation overhead. Exclude overlapping collectors and label uncontrolled gameplay separately. SurfaceFlinger presentation intervals and ready-to-present timing are not direct GPU execution time or touch-to-photon latency.
8. **Single I/O policy and truthful status**: Every profile, default command, legacy MountX action, and verification path must use the shared media policy. Resolve mounted backing devices through mountinfo major:minor, sysfs partitions and slave relationships; skip ambiguous multi-device mappings and never fall back to a guessed block device. Save pre-write read-ahead with boot ID, card CID, major:minor and canonical path; reject mismatched recovery records and preserve later external edits. Report actual queue readbacks and verified write failures. A detected worker PID, large read-ahead, or nonzero dirty-byte limit is not proof of optimization or a realtime priority. Dormant first boot remains dormant; changing defaults does not authorize deployment or module installation. Do not claim universal throughput or gameplay gains from a single SD measurement.

## 18. Video Playback and Memory Failure Diagnostics

1. **Separate decoder crashes from GPU tuning**: Obtain codec names, media metrics, service PID changes, and tombstone backtraces before changing rendering properties. A crash in `libcodec2_soft_avcdec.so` is a software H.264 decoder failure; raising GPU clocks or dropping caches is not a demonstrated repair. Compare a bounded synthetic clip and the user's same failing clip; synthetic success alone does not validate the real clip.
2. **Distinguish restart types**: Record kernel boot ID and uptime together with `system_server` and zygote identities. A new framework/zygote PID with the same boot ID is a framework restart, even if the previous kernel boot reason still says `kernel_panic`. Do not attribute a new event to an old boot reason.
3. **Inspect memory owners and VMA limits**: Report `MemAvailable`, PSI, ZRAM logical usage versus physical compressed usage, per-process swap/native heap, VMA count, and `vm.max_map_count`. An allocator `NO MEMORY` abort near the VMA limit is not proof that physical RAM is exhausted. Raising a limit or enlarging swap does not cure unbounded allocations; the stack that failed allocation does not by itself identify the original leak.
4. **Preserve live swap and crash evidence**: Never use swapoff, ZRAM reset/resize, or hot-remove as a cleanup shortcut while ZRAM contains live pages. Native runtime reconfiguration must fail closed on unreadable usage, reject in-use devices, check command/write failures, and stop before reset if swapoff fails. Kotlin must delegate to the guarded native path without an unchecked shell fallback. Reading `zram-control/hot_add` creates a device; it is not a harmless probe. Memory cleaning must preserve tombstones, ANRs, shader caches, app data, and live process allocations, and report accepted requests without claiming a native leak was cleaned.
5. **Keep codec workarounds scoped and reversible**: The X698 trial in `docs/VIDEO_MEMORY_AUDIT_20261015.md` verified a temporary Codec2-to-OMX fallback for the user's failing Telegram video. It is not a universal media optimization. The user subsequently authorized professional controls with optional boot application: keep factory defaults at `media.decoder_mode=system` and `media.decoder_apply_on_boot=false`; only an explicit user selection enables boot persistence. Keep media choices independent of performance profiles. Disabling Codec2 can remove formats such as AV1 or change audio paths. Back up original nonpersistent properties per boot, account for the cached codec list in the media player service, verify property readbacks and active decoders, and restore owned settings on request/uninstall. Restart only the identified media player service after confirmation or an explicitly enabled boot policy, and defer when telephony/audio indicates a call or the state is unknown; never restart zygote/system_server for decoder selection.
6. **Expose real capabilities, not fabricated decoders**: Companion enumerates Android MediaCodecList; native/WebUI XML entries must be labeled declarations, not verified working implementations. Global OMX/Codec2 rankings are preferences and cannot force a named decoder inside every app. Show actual active component names and the distinction between property verification and playback success. Gate unsupported profiles on implementation/property and codec-candidate probes. Do not imply that FFmpeg/VLC or additional codec binaries are installed. Media UI belongs in Tools, outside GPU clock/tunable cards; use `tools/generate_media_decoder_locales.py` and the reviewed ID/EN media catalog for every label, warning, accessibility control, plural and dynamic message. Media actions use an exclusive create lock as well as the shared mode lock; a killed operation's stale volatile lock fails closed until reboot, rather than racing an automatic lock deletion.

## 19. Release Validation and Foreground Isolation

1. Build and install only Release artifacts on the identity-verified X698 device; preserve existing version metadata and user configuration. Record artifact checksums and verify installed files. Back up replaced module components under the authorized Debug directory before a live update.
2. UI automation must check the foreground package and Companion process identity before dumps, taps, and swipes. Abort when Lynx loses focus; do not inspect or interact with another application's contents. Wait for initialization before asserting that navigation exists. Resume after a repair using a separate evidence directory.
3. Empty logcat output does not establish that no crash occurred. Check process continuity, `dumpsys activity exit-info`, and the application's DropBox crash entry when the process disappears. Keep the exact Release mapping and tests associated with the artifact being inspected.
4. Navigation, catalog/format checks, backend readbacks, guarded rejection tests, real decoder playback, and boot persistence are separate claims. Report each verified scope; never label destructive kernel flashing, uninstall, reboot, unsupported hardware, or unexercised controls as tested merely because the page opens.
5. **WebUI (Secondary) regression tests**: While the Native Companion App is the primary development, experimentation, and test target, whenever secondary WebUI updates or parity synchronizations are performed, regression tests must cover locale round trips and saved overrides, first rendering over loading placeholders, and Linux CPU lists containing spaces, commas, and ranges. Keep fixtures read-only and distinguish captured-data browser checks from live hardware mutations.

---

## 🔬 20. Panduan Pembuatan, Kompilasi & Penyuntikan KernelPatch Module (KPM)

### 20.1 Filosofi Anti-Hardcode: Mengapa KPM Digunakan
Arsitektur Lynx Universal menganut prinsip mutlak **Zero-Hardcode**. Kode inti daemon (`lynxd`) dan aplikasi Android (`LynxCompanion`) bersifat universal untuk seluruh keluarga chipset Qualcomm Snapdragon dan MediaTek (Dimensity & Helio). 
* Setiap silikon fisik memiliki karakteristik unik (*Silicon Lottery*), batas tabel frekuensi (OPP) OEM yang berbeda, serta toleransi voltase (*voltage margin*) yang bervariasi.
* **Dilarang keras melakukan hardcode angka frekuensi atau voltase khusus satu perangkat (misalnya 940 MHz @ 825 mV) ke dalam logika inti translator `lynxd`**.
* Seluruh eksperimen hardware tingkat rendah, modifikasi OPP memori kernel, dynamic undervolt (UV), dan *inline kernel hook* didelegasikan secara modular ke dalam **KernelPatch Module (KPM)**.

### 20.2 Kebutuhan Alat & Lingkungan Kompilasi (Build Toolchain)
Modul KPM dikompilasi menggunakan **Android NDK Clang (LLVM)** dengan standar *freestanding* tanpa dependensi `libc` userspace:

1. **Toolchain Compiler**:
   - Clang Android NDK (misalnya: `toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe` atau binary Linux).
   - Target Arsitektur: `aarch64-linux-android`.
2. **Direktori Header Wajib**:
   - `tools/KernelPatch/kernel/include` (`kpmodule.h`, `hook.h`, `kallsyms.h`, `log.h`)
   - `tools/KernelPatch/kernel/patch/include`
   - `tools/KernelPatch/kernel/linux/include`
   - `tools/KernelPatch/kernel/linux/arch/arm64/include`
3. **Langkah 1: Kompilasi Objek ELF (`.o`)**:
   ```powershell
   clang -target aarch64-linux-android `
         -c -O2 `
         -fno-pic `
         -fno-builtin `
         -ffreestanding `
         -Wall `
         -I tools/KernelPatch/kernel/include `
         -I tools/KernelPatch/kernel/patch/include `
         -I tools/KernelPatch/kernel/linux/include `
         -I tools/KernelPatch/kernel/linux/arch/arm64/include `
         -o module.o source.c
   ```
4. **Langkah 2: Link Relocatable KPM Binary (`.kpm`)**:
   ```powershell
   clang -target aarch64-linux-android `
         -no-pie `
         -Wl,-r `
         -nostdlib `
         -o module.kpm module.o
   ```

### 20.3 Struktur Anatomi Kode Sumber C (`source.c`)
Setiap KPM wajib mendefinisikan metadata, fungsi inisialisasi, fungsi terminasi, dan antarmuka kontrol userspace:
```c
/* SPDX-License-Identifier: GPL-2.0-or-later */
#include <log.h>
#include <compiler.h>
#include <kpmodule.h>
#include <hook.h>
#include <kallsyms.h>
#include <linux/printk.h>

KPM_NAME("lynx_nama_modul");
KPM_VERSION("1.0.0");
KPM_LICENSE("GPL v2");
KPM_AUTHOR("ɴᴏɪʀ");
KPM_DESCRIPTION("Deskripsi fungsi modul KPM");

#define LOG_TAG "[LynxKPM] "

// Handler kontrol dinamis dari userspace (CLI / Daemon)
static long lynx_kpm_control0(const char *args, char *__user out_msg, int outlen) {
    if (!args) return 0;
    logkd(LOG_TAG "Control command received: %s\n", args);

    if (strstr(args, "mode=extreme")) {
        // Aktifkan profil hardware maksimal
    } else if (strstr(args, "mode=balance") || strstr(args, "reset")) {
        // Kembalikan ke profil seimbang / hemat daya
    }
    return 0;
}

// Inisialisasi saat KPM dimuat
static long lynx_kpm_init(const char *args, const char *event, void *__user reserved) {
    logkd(LOG_TAG "Initializing Lynx KPM...\n");
    // Hook target simbol via kallsyms_lookup_name & hook_wrap
    return 0;
}

// Terminasi saat KPM di-unload
static long lynx_kpm_exit(void *__user reserved) {
    logkd(LOG_TAG "Unloading Lynx KPM...\n");
    // Lepas seluruh hook kernel via unhook
    return 0;
}

KPM_INIT(lynx_kpm_init);
KPM_CTL0(lynx_kpm_control0);
KPM_EXIT(lynx_kpm_exit);
```

### 20.4 Protokol Keamanan & Otentikasi Supercall (Injeksi CLI Otomatis)

> [!CAUTION]
> **Penyebab Utama Error Injeksi CLI (`errno=1: Operation not permitted / -EPERM`)**:  
> KernelPatch mengalihkan `syscall 45` (`__NR3264_truncate`) sebagai kanal supercall. Di dalam kernel (`tools/KernelPatch/lkm/supercall/supercall.c`), fungsi `kp_supercall_handler` memverifikasi identitas pemanggil:
> ```c
> bool manager = kp_is_manager_uid(uid);
> if (!manager && !kp_supercall_allowed_for_su(cmd)) return -EPERM;
> ```
> Seluruh perintah manipulasi modul KPM (`SUPERCALL_KPM_LOAD`, `UNLOAD`, `CONTROL`, `LIST`) **HANYA DIIZINKAN** dieksekusi oleh **UID APatch Manager App** (`me.bmax.apatch`). Pemanggilan langsung dari akun `root` (`uid 0`) atau user lain akan selalu diblokir oleh kernel dengan error `-EPERM`.

#### Protokol Otomasi Injeksi Melalui CLI / ADB:
1. **Identifikasi Dinamis UID APatch Manager**:
   ```bash
   APATCH_UID=$(pm list packages -U | grep "package:me.bmax.apatch " | awk -F'uid:' '{print $2}')
   # Contoh pada perangkat uji Infinix X698: UID adalah 10683 (u0_a683)
   ```
2. **Kompilasi Alat Diagnostik (`tools/kpm_diag.c`)**:
   Utilitas `kpm_diag` dikompilasi menggunakan target `aarch64-linux-android` dengan formula command 64-bit KernelPatch:
   ```c
   // Format: ((long)version_code << 32) | (0x1158 << 16) | subcmd
   // Pastikan version_code cocok dengan versi kernel aktif (misal 0x000d03 untuk d03)
   static long make_cmd(int subcmd) {
       return ((long)0x000d03 << 32) | (0x1158 << 16) | subcmd;
   }
   ```
   - Kode Sub-perintah:
     - `SUPERCALL_KPM_LOAD = 0x1020`
     - `SUPERCALL_KPM_UNLOAD = 0x1021`
     - `SUPERCALL_KPM_CONTROL = 0x1022`
     - `SUPERCALL_KPM_LIST = 0x1031`
   - Superkey standar perangkat: `"123456"`.
3. **Eksekusi Perintah di Bawah Konteks Manager UID**:
   Gunakan `su $APATCH_UID sh -c '...'` agar pemanggilan syscall truncate lolos validasi `kp_is_manager_uid`:
   ```bash
   # 1. Menampilkan daftar seluruh modul KPM aktif:
   su $APATCH_UID sh -c '/data/local/tmp/kpm_diag list'

   # 2. Menyuntikkan / Memuat modul KPM ke memori kernel:
   su $APATCH_UID sh -c '/data/local/tmp/kpm_diag load /path/ke/modul.kpm'

   # 3. Mengeluarkan / Unload modul KPM secara bersih:
   su $APATCH_UID sh -c '/data/local/tmp/kpm_diag unload nama_modul'

   # 4. Mengirim instruksi runtime ke handler KPM_CTL0:
   su $APATCH_UID sh -c '/data/local/tmp/kpm_diag ctl nama_modul "mode=extreme"'
   ```

### 20.5 Kebijakan Siklus Daya GPU: Dynamic DVFS vs Extreme Sweet Spot Lock
Modul KPM dan pengontrolan GPU wajib mematuhi aturan manajemen daya berimbang:
1. **Modul Utama GPU Tuning (`lynx_gpu_uv.kpm`)**:
   - Menjadi modul KPM resmi dan utama untuk penyesuaian voltase silikon GPU.
   - Offset default sweet spot resmi terverifikasi: **`-12.5 mV`** (`-12500 µV`).
   - Pada frekuensi tertinggi (OPP 0: 950 MHz di Helio G96 MT6781), voltase turun dari standar pabrik 850.0 mV menjadi **837.5 mV**, terbukti 100% stabil, dingin, dan bebas watchdog timeout / GPU freeze.
2. **Saat Mode Extreme Aktif**:
   - Sistem Lynx mengunci frekuensi GPU ke frekuensi kapabilitas silikon maksimum (950 MHz via `/proc/gpufreq/gpufreq_opp_freq`) dengan voltase sweet spot 837.5 mV untuk mencegah *frame drop* dan memaksimalkan stabilitas gaming berat (seperti Genshin Impact).
3. **Saat di Luar Mode Extreme (Balanced, Powersave, Normal, Standby)**:
   - **DILARANG KERAS** membiarkan kuncian frekuensi GPU tetap aktif secara statis!
   - Penguncian OPP wajib dilepaskan seketika (`echo 0 > /proc/gpufreq/gpufreq_opp_freq`) sehingga kernel driver GPU beralih penuh ke **Dynamic DVFS** murni.
   - Saat perangkat dalam kondisi idle / tanpa beban grafis 3D berat, frekuensi GPU otomatis turun (*downclock*) ke 0 MHz / 300 MHz dengan tegangan turun ke batas minimal (~600 mV), menjaga baterai tetap hemat dan suhu perangkat dingin.
   - Modul Undervolt (`lynx_gpu_uv.kpm`) tetap dibiarkan aktif di latar belakang untuk memberikan pengurangan tegangan `-12.5 mV` secara proporsional di seluruh jenjang frekuensi dinamis.

---

*Dokumen ini sah sebagai panduan teknis dan acuan implementasi resmi proyek Lynx Universal.*



