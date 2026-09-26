# ⚡ LYNX [Codename: Deity] — Universal Hybrid Root Optimizer
> **Versi**: 3.0.0 | **Author**: ɴᴏɪʀ | **Platform**: Android (KernelSU / APatch / Magisk)

---

## 📌 Ringkasan Proyek
**Lynx Universal** adalah modul optimasi kernel hibrida tingkat lanjut yang menggabungkan kapabilitas Qualcomm Snapdragon dan MediaTek (Dimensity & Helio) dalam satu paket terpadu. Dilengkapi dengan deteksi chipset anti-spoofing otomatis, mesin adaptif AI (*Smart-AI Core*), WebUI modern berbasis touch-first, dan Aplikasi Native Android Companion (Kotlin + Jetpack Compose) dengan sinkronisasi 2 arah *real-time*.

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

## 🏛️ Arsitektur & Keunggulan Utama

1. **Hardware Abstraction Layer (HAL)**:
   - `platforms/qcom/`: Optimalisasi Adreno KGSL, CPU-boost input boost suppression, WALT fast ramp, and DRM perf mode.
   - `platforms/mtk/`: Penonaktifan logging modem CCCI, penekanan GED KPI, penonaktifan pembatasan MediaTek Syslimiter, dan Perf PMU.
   - `platforms/generic/`: Fallback CPUFreq standar Linux tanpa intervensi vendor berisiko.
2. **Universal Kernel Subsystems (`core/lib/`)**:
   - `sched_features.sh`: Low-latency scheduler flags (`NO_GENTLE_FAIR_SLEEPERS`, `START_DEBIT`) dan prioritas otomatis untuk 23 mesin game ternama.
   - `network.sh`: Dynamic TCP discovery (`bbr` / `cubic`), FQ-CoDel queue discipline, dan Wi-Fi low latency gaming lock.
   - `uclamp.sh`, `ram.sh`, `display_touch.sh`, `audio_latency.sh`, `oem_neutralizer.sh`.
3. **State Engine Terpusat Berbasis JSON (`config.json`)**:
   - WebUI, Native Companion App, CLI (`Lxcore`), dan daemon latar belakang berkomunikasi melalui `config.json` yang sinkron secara real-time via `inotify`.
4. **Keamanan Tanpa Kompromi**:
   - Bebas dari operasi penghapusan `dalvik-cache` berbahaya di runtime.
   - Semua operasi penulisan dilindungi wrapper validasi node `write_node()` yang mencegah kernel panic.

---

## 📱 Cara Penggunaan & Kontrol Modul

### 1. Antarmuka Web (WebUI)
- Pengguna **KernelSU** & **APatch**: Buka WebUI langsung melalui tombol antarmuka web pada manajer modul.
- Pengguna **Magisk**: Akses melalui browser lokal atau jalankan micro-daemon lokal yang disediakan.

### 2. Aplikasi Native Android Companion (`LynxCompanion`)
- Pasang berkas `Lynx-3.0.0.apk` yang tersedia di direktori rilis atau flashable ZIP.
- Menyediakan kendali penuh seluruh subsistem dengan tampilan Cyberpunk OLED Dark dan responsivitas sentuhan haptic.

### 3. Command Line Interface (CLI) via Termux / Root Shell
```bash
# Menampilkan ringkasan status modul dan profil aktif
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