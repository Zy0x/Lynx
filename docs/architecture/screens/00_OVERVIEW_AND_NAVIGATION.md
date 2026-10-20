# 📐 Screen Architecture 00: Overview & Navigation Framework
> **Scope**: Root App Shell, Navigation Flow, Dual-Mode Root Detection, Theme Tokens, & Hardware Back-Stack  
> **Source Files**: `MainActivity.kt`, `LynxAppShell.kt`, `LynxBottomNavigationDock.kt`, `LynxThemeTokens.kt`  

---

## 1. Identitas & Tanggung Jawab Modul
Modul navigasi adalah tulang punggung antarmuka Lynx Companion. Modul ini bertanggung jawab atas inisialisasi lingkungan root, status layar aktif (foreground/background), pemeliharaan stack navigasi virtual, rendering edge-to-edge OLED tanpa gangguan bilah sistem Android, dan penyediaan umpan balik visual (*toast feedback overlay*).

---

## 2. Struktur Komponen & Siklus Hidup UI
- **Activity Host**: `MainActivity` (menonaktifkan penyesuaian dekor sistem dengan `WindowCompat.setDecorFitsSystemWindows(window, false)`).
- **Touch Fix Injection**: `VelocityTrackerAddPointsFix = true` (mengoreksi algoritma fling pada Compose 1.6+ agar sentuhan batched tidak mewarisi perpindahan acak).
- **Dual-Mode Root Handshake**:
  1. `libsu` menginisialisasi root shell (`Shell.getShell()`).
  2. Memeriksa keberadaan modul Lynx di `/data/adb/modules/Lynx/module.prop`.
  3. Membaca `config.json` dan memuat baseline konfigurasi kernel.
  4. Jika modul root tidak terdeteksi, aplikasi beralih secara anggun ke **Mode Standalone Root** (memungkinkan kontrol langsung sysfs tanpa fitur background daemon).

---

## 3. Sistem Token Desain & Tema (Cyberpunk Deep OLED)
- **Background Utama**: `BgDeepOled` (`#070709`) — Hitam pekat sejati untuk efisiensi daya panel OLED/AMOLED.
- **Warna Kartu (Card Surface)**: `BgCard` (`#111116`) dengan border stroke halus (`#1d1d26`).
- **Aksen Semantik**:
  - `AccentCyan` (`#00f0ff`): Mode Auto / AI, status normal, kontrol telemetri.
  - `AccentGreen` (`#00ff66`): Mode Balance, pengisian aman, efisiensi baterai.
  - `AccentPurple` (`#bf00ff`): Mode High Performance, tuning clock tinggi.
  - `AccentRed` (`#ff003c`): Mode Extreme Unrestricted, bypass termal, peringatan kritis.
  - `AccentAmber` (`#ffb700`): Mode Powersave, throttling warning, status charging lambat.

---

## 4. Alur Navigasi Virtual & Back-Stack
Aplikasi menggunakan **Dock Navigasi Virtual Melayang (Floating Dock)** dengan 3 segmen utama:
- **Tab 0**: Dashboard (Beranda Telemetri & Profil)
- **Tab 1**: Tuning Hub (Kategori Mesin Perangkat Keras)
  - Mendukung navigasi sub-halaman: `selectedCategory` (`cpu`, `gpu`, `charging`, `memory`, `thermal`, `network`, `audio`, `system`, `profiles`, `hud`).
- **Tab 2 & 3**: Tools & Flasher Hub
  - Sub-kategori: `battery_lab`, `benchmark`, `flasher`, `system_diag`, `preferences`.

```
                    ┌────────────────────────┐
                    │      MainActivity      │
                    └───────────┬────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        ▼                       ▼                       ▼
   [ Tab 0: Dashboard ]   [ Tab 1: Tuning ]      [ Tab 2/3: Tools ]
   • Telemetry Gauges     • Category Selector    • Category Selector
   • Quick Profiles       • Subscreen Host       • Subscreen Host
   • Quick SoC Specs             │                       │
                                 ├──> CPU Subscreen      ├──> Battery Lab
                                 ├──> GPU Subscreen      ├──> Benchmark Studio
                                 ├──> Battery Subscreen  ├──> Kernel Flasher
                                 ├──> Memory Subscreen   ├──> System Diagnostics
                                 ├──> Thermal Subscreen  └──> Preferences
                                 ├──> Net/Audio Subscreen
                                 ├──> System Subscreen
                                 └──> Profiles Hub
```

### Penanganan Tombol Kembali Perangkat Keras (`BackHandler`):
1. Jika pengguna berada di dalam sub-halaman Tab 1 (`selectedCategory != null`), tombol Back akan menutup sub-halaman dan kembali ke menu Tuning Hub utama (`selectedCategory = null`).
2. Jika pengguna berada di dalam sub-halaman Tab 2/3 (`selectedToolsCategory != null`), tombol Back akan kembali ke menu Tools Hub utama (`selectedToolsCategory = null`).
3. Jika berada di beranda tab, tombol Back akan meminimalkan aplikasi ke latar belakang tanpa memutus koneksi telemetri HUD.
