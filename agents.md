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
├── agents.md                    # Dokumen ini (Master Guidelines)
├── module.prop                  # Template metadata modul
├── customize.sh                 # Otak instalasi, deteksi SoC, & conditional file injector
├── library.sh                   # Pustaka fungsi instalasi, root check, & helper umum
├── post-fs-data.sh              # Early boot script (sebelum zygote berjalan)
├── service.sh                   # Late start service (router inisialisasi boot utama)
├── sepolicy.rule                # Aturan SELinux terpadu (QCOM & MTK)
├── uninstall.sh                 # Skrip restorasi sistem saat modul dihapus
├── Toast.apk                    # Utilitas visual notifikasi toast
│
├── core/                        # 🌐 LOGIKA UNIVERSAL (SoC-Agnostic)
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
├── webroot/                     # 🌐 ANTARMUKA UTAMA (WebUI Dashboard Pasca-Instalasi)
│   ├── index.html               # Antarmuka SPA Modern (Mobile-First, Dark Theme, Touch-First)
│   ├── app.js / script.js       # Logika interaksi & ksu.exec() bridge
│   ├── style.css                # Sistem token desain modern & responsif
│   └── script.sh                # Helper eksekusi cepat dari WebUI
│
├── system/bin/                  # Utilitas Eksekusi CLI & Helper Modul
│   ├── lynx                     # TUI Menu interaktif (Termux secondary fallback)
│   ├── Lxcore                   # Modular CLI tool untuk kontrol skrip
│   └── sqlite3                  # Database optimization binary
│
└── archive/                     # 📦 ARSIP & REFERENSI (TIDAK di-flash)
    ├── packages/                # Berkas ZIP rilis mentah (Lynx Beta 3, Chimera, dll.)
    ├── Chimera/                 # Ekstraksi modul MediaTek Chimera untuk acuan kode
    └── device_reference/        # Catatan runtime nyata dari perangkat uji Infinix X698
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
2. **Pengujian Nyata via ADB**:
   - Perangkat pengujian aktif tersedia di jaringan lokal: **Infinix X698 (MediaTek Dimensity 920, Android 14, Magisk Root)** pada alamat `192.168.1.5:5555`.
   - Gunakan koneksi ADB ini untuk memverifikasi jalur sysfs, menguji sintaks shell di lingkungan Android nyata, dan memastikan modul berjalan sempurna.
3. **Lokasi Penyimpanan Berkas Pengujian & Debugging di Perangkat (Mandatory)**:
   - Setiap kali melakukan remote testing, inspeksi, atau penulisan berkas uji ke perangkat target via ADB, seluruh berkas luaran (seperti hasil screenshot `.png`, dump UI `.xml`, log tes, berkas sementara, maupun binary pengujian) **WAJIB** diletakkan terpusat di direktori:
     `/storage/emulated/0/Debug/` (atau alias `/sdcard/Debug/`).
   - **Dilarang Keras** meletakkan berkas di luar folder tersebut, termasuk di root storage (`/sdcard/`, `/storage/emulated/0/`) maupun menumpuk berkas di folder temp sistem (`/data/local/tmp/`), karena menyebabkan penyimpanan berantakan dan sulit dibersihkan oleh pengguna.
   - Sebelum mengeksekusi penulisan atau penyimpanan berkas, selalu pastikan folder telah dibuat:
     `mkdir -p /storage/emulated/0/Debug 2>/dev/null`
4. **Kompatibilitas Shell Android**:
   - Seluruh skrip shell (`.sh`) wajib kompatibel dengan shell standar Android (`/system/bin/sh` / Toybox / BusyBox ash).
   - Hindari dependensi bash-eksklusif yang tidak didukung secara *native* oleh shell Android murni.
5. **Dokumentasi & Integritas Kode**:
   - Pertahankan struktur komentar yang informatif pada setiap skrip.
   - Catat setiap perubahan arsitektur pada dokumentasi dan hindari paparan data sensitif.
6. **Larangan Keras Menginstall / Mem-flash Modul Tanpa Perintah Eksplisit (Strict No-Install Policy)**:
   - **DILARANG KERAS** menginstall, mem-flash berkas modul ZIP (`Lynx-Deity-*.zip`), memodifikasi folder modul root (`/data/adb/modules/Lynx/`), atau mengeksekusi installer modul ke perangkat target pengguna via ADB maupun shell root, **KECUALI** jika pengguna secara EKSPLISIT memberikan instruksi tertulis untuk menginstallnya.
   - Pengujian aplikasi (Native Companion APK `LynxKernelManager.apk`) dan inspeksi sistem harus mampu berjalan secara independen dan aman dengan graceful fallback tanpa mengasumsikan modul root telah terpasang di sistem.

### 9.1 Protokol Pengujian Aplikasi Tanpa Modul (Standalone Root Testing Protocol)
Untuk memverifikasi stabilitas dan fungsionalitas Lynx Companion tanpa memodifikasi sistem root:
1. **Instalasi Non-Invasif**: APK dipasang via ADB (`adb install -r LynxKernelManager.apk`) tanpa mem-flash atau menyentuh `/data/adb/modules/Lynx/`.
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
*Dokumen ini sah sebagai panduan teknis dan acuan implementasi resmi proyek Lynx Universal.*

