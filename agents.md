# 🤖 AGENTS.MD — Architectural Blueprint & Development Guidelines
> **Proyek**: Lynx Universal (Hybrid Qualcomm Snapdragon & MediaTek Architecture)  
> **Status**: Development & Local Staging  
> **Otoritas**: Master Document untuk Antigravity AI Coding Agent & Pengembang  

---

## 📌 1. Visi & Identitas Proyek

### 1.1 Visi Utama
Menyatukan **Lynx** (berbasis Qualcomm Snapdragon) dan **Chimera** (berbasis MediaTek) menjadi **satu modul root terpadu (Unified Hybrid Module)** yang cerdas, efisien, aman, dan berdaya guna tinggi. Pengguna cukup mengunduh satu berkas modul ZIP yang sama, dan sistem akan secara otomatis mengidentifikasi arsitektur chipset perangkat, mengonfigurasi parameter kernel yang tepat, serta menyediakan kontrol penuh melalui antarmuka web modern (**WebUI**).

### 1.2 Konvensi Branding & Metadata Modul (`module.prop`)
Untuk menjaga kompatibilitas ekosistem OTA update, riwayat modul, dan transparansi informasi pengguna:
- **Module ID**: Tetap menggunakan `id=Lynx` (tidak diubah agar tidak memutus kompatibilitas manajer modul Magisk/KernelSU/APatch).
- **Module Name Dinamis**:
  - Pada perangkat Qualcomm: `name=Lʏɴx - Deity (Qualcomm)`
  - Pada perangkat MediaTek: `name=Lʏɴx - Deity (MediaTek)`
- **Author**: `ɴᴏɪʀ` (dengan atribusi kontributor pada dokumentasi).
- **Deskripsi Dinamis**: Diperbarui secara *real-time* oleh daemon pengontrol sesuai profil aktif (contoh: `[ ⚡ Auto (AI) Mode ]`, `[ 🚀 Performance Mode ]`, `[ ⚖️ Balance Mode ]`, `[ 🔥 Extreme Mode ]`).

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
├── webroot/                     # 🌐 ANTARMUKA WEB (WebUI Dashboard Pasca-Instalasi)
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
     - Tampilkan peringatan visual di terminal dan WebUI:
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
> Mode **Extreme (Unrestricted)** wajib dilengkapi *disclaimer* visual yang jelas di WebUI bahwa mode ini diperuntukkan bagi pengguna tingkat lanjut dengan pendingin eksternal (*phone cooler*) guna menghindari degradasi baterai dan komponen SoC.

---

## 🎨 7. Standar Desain & Interaksi WebUI (`webroot/`)

WebUI adalah **antarmuka kontrol utama** pasca-instalasi bagi pengguna (diakses via KernelSU Manager / APatch WebUI, dengan roadmap masa depan ke aplikasi native Android). WebUI wajib mematuhi standar desain tingkat profesional:

### 7.1 Ketentuan Teknis UI/UX
- **Mobile-First & Touch-First**:
  - Ukuran target sentuh tombol dan elemen interaktif minimal **48×48 px** (bebas miss-click).
  - Tampilan stabil pada berbagai rasio layar non-reguler (1080×2400, 1080×2460, 20:9, 21:9) hingga resolusi tablet dan layar lipat.
  - Bebas dari ketergantungan *hover-only* (seluruh interaksi berbasis sentuhan).
- **Estetika & Keterbacaan (Readability First)**:
  - Skema warna: **Modern Cyberpunk / Deep Dark Theme** (latar belakang OLED `#0a0a0a` / `#121212`, kartu kontras `#1a1a1a`, aksen visual Cyan `#00ffaa` atau Biru Modern `#0099ff`).
  - Kontras teks tinggi dan konsisten, tidak melelahkan mata.
  - Tipografi semantik (*system-ui, sans-serif*) dengan hierarki informasi yang tegas.
- **Interaksi & Animasi Ringan**:
  - Animasi transisi halus dan fungsional dengan durasi **150–300 ms** (tidak membebani WebView).
  - Feedback visual instan saat tombol ditekan (indikator loading, pesan status, atau toast visual).
- **Integrasi Backend KernelSU (`ksu.exec`)**:
  - Semua eksekusi shell dilakukan melalui antarmuka `ksu.exec(cmd)` secara asinkron (`async/await`).
  - Penanganan error (*error boundary*) yang tangguh jika perintah gagal dieksekusi.
  - Sanitasi input untuk mencegah injeksi perintah shell yang merusak sistem.

### 7.2 Tata Kelola Batas Domain & Single Source of Truth (GPU & Display Tuning)
Untuk mencegah komplikasi dan redundansi kontrol di masa mendatang, seluruh pengembang dan AI Agent **WAJIB** mematuhi pembagian domain tanggung jawab berikut:
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
   - Folder platform mentah (`platforms/qcom/`, `platforms/mtk/`, `platforms/generic/`) tetap disimpan di `$MODPATH/platforms/` (<150 KB). Hal ini memungkinkan pengguna beralih arsitektur langsung via WebUI atau Native APK jika terjadi kesalahan tanpa perlu flash ulang di recovery.
   - Beralih platform via UI wajib dilindungi dengan dialog peringatan keras (*Critical Danger Warning Modal*).
3. **Status Aman Awal (Dormant / Standby First-Boot)**:
   - Modul diinstal dalam status `"active_profile": "dormant"` pada `config.json`.
   - Modul **DILARANG** langsung menerapkan clock ekstrem saat boot pertama sebelum pengguna membuka WebUI atau Native APK untuk memilih profil dan konfigurasi yang diinginkan.

---

## 🛠️ 9. Protokol Kerja AI Agent (Rules of Engagement)

Setiap AI Agent yang bekerja pada repositori ini **WAJIB** mematuhi pedoman operasional berikut:

1. **Local-First Staging (No Unsolicited Commits)**:
   - Dilarang melakukan `git commit` atau `git push` kecuali jika pengguna secara eksplisit memberikan perintah untuk commit/push.
   - Seluruh perubahan, restrukturisasi, dan pengujian file harus dilakukan pada *local working tree*.
2. **Pengujian Nyata via ADB & Dynamic Connection Protocol (Dual-Device Laboratory Matrix)**:
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
3. **Lokasi Penyimpanan Berkas Pengujian & Debugging di Perangkat (Khusus Infinix X698 — Mandatory)**:
   - Setiap kali melakukan remote testing, inspeksi, atau penulisan berkas uji ke perangkat uji aktif (**Infinix X698**) via ADB, seluruh berkas luaran (seperti hasil screenshot `.png`, dump UI `.xml`, log tes, berkas sementara, maupun binary pengujian) **WAJIB** diletakkan terpusat di direktori:
     `/storage/emulated/0/Debug/` (atau alias `/sdcard/Debug/`).
   - **Dilarang Keras** meletakkan berkas di luar folder tersebut, termasuk di root storage (`/sdcard/`, `/storage/emulated/0/`) maupun menumpuk berkas di folder temp sistem (`/data/local/tmp/`), karena menyebabkan penyimpanan berantakan dan sulit dibersihkan oleh pengguna.
   - Sebelum mengeksekusi penulisan atau penyimpanan berkas pada **Infinix X698**, selalu pastikan folder telah dibuat:
     `mkdir -p /storage/emulated/0/Debug 2>/dev/null`
   - ⚠️ **Pengecualian Mutlak untuk Redmi Note 7 (`lavender`)**: Dilarang membuat folder `/storage/emulated/0/Debug/` atau menulis berkas apapun di Redmi Note 7! Seluruh sampel dari Redmi Note 7 wajib dialirkan via stdout ADB langsung ke PC lokal di `archive/device_reference/redmi_note_7_sdm660/`.
4. **Kompatibilitas Shell Android**:
   - Seluruh skrip shell (`.sh`) wajib kompatibel dengan shell standar Android (`/system/bin/sh` / Toybox / BusyBox ash).
   - Hindari dependensi bash-eksklusif yang tidak didukung secara *native* oleh shell Android murni.
5. **Dokumentasi & Integritas Kode**:
   - Pertahankan struktur komentar yang informatif pada setiap skrip.
   - Catat setiap perubahan arsitektur pada dokumentasi dan hindari paparan data sensitif.
6. **Larangan Keras Menginstall / Mem-flash Modul Tanpa Perintah Eksplisit (Strict No-Install Policy)**:
   - **DILARANG KERAS** menginstall, mem-flash berkas modul ZIP (`Lynx-Deity-*.zip`), memodifikasi folder modul root (`/data/adb/modules/Lynx/`), atau mengeksekusi installer modul ke perangkat target pengguna via ADB maupun shell root, **KECUALI** jika pengguna secara EKSPLISIT memberikan instruksi tertulis untuk menginstallnya.
   - Pengujian aplikasi (Native Companion APK `LynxKernelManager.apk`) dan inspeksi sistem harus mampu berjalan secara independen dan aman dengan graceful fallback tanpa mengasumsikan modul root telah terpasang di sistem.
7. **Kewajiban Mutlak Build APK Versi Release (Strict Release-Only Build Policy)**:
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
6. **Penyimpanan Berkas Uji & Tanpa Reboot**: Seluruh berkas diagnostik, log, dan tangkapan layar pengujian disimpan secara eksklusif di `/storage/emulated/0/Debug/` tanpa me-reboot perangkat pengguna.

### 9.2 Freeze Versioning — Penghentian Kenaikan Versi Otomatis (Mandatory)
Sesuai arahan pengguna, siklus penomoran versi Lynx Universal saat ini **DIBEKUKAN (FROZEN)** pada versi:
- **Module Version**: `3.0.9`
- **Version Code**: `20261002`

**Ketentuan Operasional Agent**:
1. **Dilarang Menaikkan Nomor Versi**: Agen dilarang keras mengubah atau menaikkan nomor versi pada berkas apapun (`module.prop`, `LynxCompanion/app/build.gradle.kts`, `changelog.md`, maupun WebUI), kecuali jika pengguna memberikan instruksi eksplisit tertulis untuk menaikkan versi.
2. **Commit & Push Saja**: Seluruh perbaikan bug, penyempurnaan fitur, refactoring, atau tuning hardware tetap diuji dan di-commit ke Git serta di-push ke GitHub tanpa mengubah versi (version bump).

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
| **Penulisan File di Device** | ✅ Diizinkan hanya di `/sdcard/Debug/` | ❌ **DILARANG KERAS (0 Byte Written)** |
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

## 📱 11. Arsitektur Native APK Companion (`app/`) & Two-Way Sync Engine

Lynx Universal tidak hanya bergantung pada WebUI, melainkan menyediakan **Aplikasi Android Native Companion** (`app/`) yang sinkron 2 arah secara real-time:

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
   - Tombol "Export Bug Report" di WebUI dan Native APK memanggil `Lxcore log export`.
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
4. Output installer/CLI/skrip mengikuti locale sistem Android; jangan menyimpan preferensi bahasa UI ke `config.json` modul atau mencampurnya dengan konfigurasi kernel.
5. Semua komentar kode, docstring, catatan inline, dan komentar pada template skrip yang dibuat atau dipelihara di repositori wajib ditulis dalam English. Komentar menjelaskan implementasi untuk pengembang dan tidak diterjemahkan ke katalog UI; teks yang tampil atau dapat diedit pengguna tetap wajib dilokalkan.
6. Teks berparameter wajib memakai placeholder bernomor/format yang aman pada katalog. Teks berjumlah wajib memakai plural resources/aturan plural; jangan merangkai kalimat terjemahan dari potongan kata.

### 16.3 Audit, Validator, dan Kriteria Selesai
1. Perubahan UI harus memperbarui seluruh katalog locale sekaligus. Key yang hilang, key yatim, atau fallback tersembunyi tidak diperbolehkan.
2. Jalankan `python tools/validate_locales.py` untuk memeriksa kesetaraan key Android dan WebUI, placeholder, teks HTML statis dan template dinamis, metadata parameter, label aksesibilitas, serta komentar kode non-English pada sumber produk. Audit statis juga harus mencari literal bahasa manusia yang tertinggal pada UI; tinjau hasilnya agar identifier dan nilai kernel tidak salah dianggap sebagai UI.
3. Verifikasi kedua bahasa pada locale sistem/browser `id`, `en`, serta locale lain yang memakai fallback Indonesian; verifikasi pula override manual tetap tersimpan setelah aplikasi/halaman dibuka ulang.
4. Periksa placeholder, plural, pesan sukses/error, dialog, notifikasi/tile, label aksesibilitas, installer, dan CLI dalam kedua bahasa. Sebelum menyelesaikan perubahan localization, jalankan unit test dan build aplikasi varian **Release** sesuai kebijakan repo; jangan menaikkan versi.

---
*Dokumen ini sah sebagai panduan teknis dan acuan implementasi resmi proyek Lynx Universal.*


