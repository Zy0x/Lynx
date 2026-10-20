# ⚡ LYNX [Codename: Deity] — Universal Hybrid Root Optimizer
> **Versi Modul**: 7.0.0-RC123 | **Author**: ɴᴏɪʀ | **Platform**: Android 10 – 15 (KernelSU / APatch / Magisk)

---

## 📌 Dokumentasi & Cetak Biru Arsitektur
- 📚 **[Dokumentasi Spesifikasi Teknis Lengkap (`docs/`)](./docs/README.md)**
- 📱 **[Cetak Biru Arsitektur Modular Per-Halaman (`docs/architecture/`)](./docs/architecture/README.md)**
- 📖 **[Panduan Pengguna (Bahasa Indonesia)](./docs/userguides/UserGuide-ID.md) | [User Guide (English)](./docs/userguides/UserGuide-EN.md)**
- 🔬 **[Arsip Pengujian & Benchmarks Lapangan](./docs/benchmarks/)**

---

## 📌 Ringkasan Proyek
**Lynx Universal** adalah modul optimasi kernel hibrida modern tingkat lanjut yang menggabungkan kapabilitas Qualcomm Snapdragon dan MediaTek (Dimensity & Helio) dalam satu paket terpadu. Dibangun di atas fondasi **Arsitektur Modular** dan **Mesin Native Rust Berkecepatan Tinggi (`lynxd`)**, Lynx menyatukan deteksi chipset anti-spoofing tingkat kernel, telemetri real-time bebas latency, WebUI modern, dan Aplikasi Native Android Companion (**Lynx Kernel Manager** / Kotlin Jetpack Compose) dengan sinkronisasi reaktif dua arah.

---

## ⚡ Spektrum Profil Performa (5 Mode)

| Profil | Deskripsi & Target Penggunaan | Karakteristik CPU / GPU | Batas Termal | Pengisian Daya (Charging) |
| :--- | :--- | :--- | :--- | :--- |
| **Auto (AI)** | Mode adaptif cerdas (default harian) | Otomatis beralih ke *Performance* saat game terbuka (0ms latency, 3s cooldown buffer) | Proteksi OEM aktif | Standar Dinamis |
| **Balance** | Efisiensi baterai & stabilitas harian | EAS Schedutil / PPM seimbang, *idle downclock* aktif | Proteksi OEM aktif | Standar OEM |
| **Performance** | Gaming kompetitif & beban kerja berat | Kunci frekuensi menengah-atas, prioritas *SurfaceFlinger*, rate limits agresif | Pembatasan moderat | Kontrol termal (1500–2000mA) |
| **Extreme** 🔥 | Sesi gaming tanpa kompromi & benchmark | Kunci clock CPU & GPU maksimum, uclamp 100%, devfreq latency 0 | **Bypass Penuh** (Zone disable, override 150°C) | **Bypass Charging** / Unrestricted |
| **Powersave** 🔋 | Daya tahan baterai maksimum | Skala frekuensi minimum, efisiensi kerja latar belakang | Proteksi OEM aktif | Pengisian hemat daya |

> [!WARNING]
> Mode **Extreme** menonaktifkan seluruh perlindungan termal OEM. **Sangat disarankan menggunakan pendingin eksternal (phone cooler)** guna menjaga kestabilan perangkat dan kesehatan baterai.

---

## 🏛️ Arsitektur & Keunggulan Utama: Modular & Rust-First

1. **Mesin Native Rust Berdaya Tinggi (`native/lynxd`)**:
   - **Zero-IO Telemetry Fast-Path**: Menggantikan subshell lambat dengan in-process kernel polling di Rust (<0.02ms parsing, <0.001% CPU duty cycle).
   - **Autonomous Watchdog & Keep-Alive**: Loop adaptif 2500ms hemat baterai, bebas wakelock (mengizinkan Deep Sleep C-States 100%), dan proteksi Doze Android.
   - **Sysfs Transaction Engine**: Penulisan node atomik dengan proteksi rollback, mencegah race conditions dan kernel panic.

2. **Arsitektur Modular Per-Fitur (Anti-Monolitik)**:
   - Kode aplikasi dan modul dipecah secara ketat per-domain tanggung jawab (`feature/battery`, `feature/cpu`, `feature/gpu`, `feature/display`, `feature/thermal`, `feature/network`, dll.).
   - Setiap fitur memiliki batas isolasi tegas antara UI Composable, State Slice, Controller, dan Repository domain, memudahkan audit dan pengembangan terfokus.

3. **Hardware Abstraction Layer (HAL) Kebal Spoofer**:
   - `platforms/qcom/`: Optimalisasi Adreno KGSL, CPU-boost input boost suppression, WALT fast ramp, dan DRM perf mode.
   - `platforms/mtk/`: Penonaktifan logging modem CCCI, penekanan GED KPI, penonaktifan pembatasan MediaTek Syslimiter, dan Perf PMU.
   - `platforms/generic/`: Fallback CPUFreq standar Linux tanpa intervensi vendor berisiko.
   - Deteksi berbasis *Hardware Ground Truth* (DTB kernel Linux & node GPU `/dev/kgsl-3d0` vs `/dev/ged`), kebal terhadap manipulasi `getprop` atau modul spoofer.

4. **State Engine Terpusat Berbasis JSON (`config.json`)**:
   - Rust daemon (`lynxd`), Lynx Kernel Manager APK, WebUI, dan CLI berkomunikasi secara instan melalui file state reaktif yang terintegrasi.

---

## 📱 Cara Penggunaan & Kontrol Modul

### 1. Antarmuka Web (WebUI)
- Pengguna **KernelSU** & **APatch**: Buka WebUI langsung melalui tombol antarmuka web pada manajer modul.
- Pengguna **Magisk**: Akses melalui browser lokal atau jalankan micro-daemon lokal yang disediakan.

### 2. Aplikasi Native Android Companion (`LynxKernelManager`)
- Pasang berkas `LynxKernelManager.apk` (v3.0.91) yang tersedia di root repositori atau rilis.
- Menyediakan kendali penuh seluruh subsistem dengan tampilan Cyberpunk OLED Dark, telemetri reaktif, manajemen baterai, dan responsivitas sentuhan haptic.

### 3. Command Line Interface (CLI) via Termux / Root Shell
```bash
# Menampilkan status subsistem dan telemetri native Rust daemon
su -c lynxd status

# Kontrol ADB Wireless Keep-Alive & Port Lock (Rust Engine)
su -c lynxd adb status
su -c lynxd adb enable 5555

# Menampilkan ringkasan status modul dan profil aktif via Lxcore
su -c Lxcore status

# Beralih profil performa (auto | balance | performance | extreme | powersave)
su -c Lxcore profile auto
su -c Lxcore profile extreme

# Mengatur konfigurasi state secara spesifik
su -c Lxcore state set uclamp.game_min_ratio 85 val
su -c Lxcore state set network.wifi_ping_stabilizer true bool

# Menjalankan pemeliharaan memori dan cache (CCleaner)
su -c Lxcore ccleaner

# Menjalankan pemeliharaan database SQLite & storage FSTRIM
su -c Lxcore maintenance

# Mengekspor berkas diagnostik lengkap 1-klik (.zip di /sdcard/Lynx/)
su -c Lxcore log export

# Menu TUI Interaktif Terminal
su -c lynx
```

---

## 🛡️ Lisensi & Hak Cipta
Dikembangkan oleh **ɴᴏɪʀ** dengan kontribusi komunitas performa Android.
Dilarang keras menyalin atau mengemas ulang modul ini tanpa atribusi resmi.