# 📐 Screen Architecture 01: Main Dashboard (Tab 0)
> **Scope**: Primary Monitoring Landing Page, Live Hardware Telemetry, CPU Core Matrix, Quick Profiles Switcher, & SoC Detection  
> **Source Files**: `MainDashboard.kt`, `DashboardTabContent.kt`, `TelemetryDashboardCards.kt`, `ProfileGrid.kt`, `QuickProfileSwitcherCard.kt`, `SocChip.kt`  

---

## 1. Identitas Komponen & Tanggung Jawab
Halaman Dashboard adalah beranda utama aplikasi yang memberikan ringkasan status kesehatan perangkat keras secara instan. Pengguna dapat langsung melihat beban kerja prosesor per-core, frekuensi GPU real-time, temperatur baterai dan arus pengisian/pengosongan (mA), sisa RAM terpakai, status modul daemon `lynxd`, serta mengganti profil performa secara instan (1-click).

---

## 2. Struktur Visual & Komposisi UI
1. **Header SoC & Engine Status (`DashboardHeaderItem`)**:
   - Menampilkan Chipset terverifikasi (`Qualcomm Snapdragon` / `MediaTek Dimensity/Helio` / `Generic`).
   - Badge Status Daemon: `lynxd Active` (hijau), `Dormant / Standby` (amber), atau `Standalone Root` (biru).
   - Chip Deteksi Spoofer: Jika terdeteksi spoofer aktif, chip menampilkan peringatan anti-spoofing merah.
2. **Quick Profile Switcher Card (`QuickProfileSwitcherCard`)**:
   - Grid 5 profil performa makro:
     - `Auto (AI)`: Mode adaptif cerdas (0ms masuk game, 3s jeda hysteresis saat keluar).
     - `Balance`: Mode seimbang harian (EAS Schedutil / PPM Cluster balanced, idle clock aktif).
     - `Performance`: Kunci frekuensi clock tinggi, devfreq boost, touchboost aktif.
     - `Extreme`: Kunci frekuensi maksimum (CPU + GPU), bypass batas termal (memerlukan cooler eksternal).
     - `Powersave`: Mode hemat daya agresif, downclock cluster besar, disable non-essential bus.
3. **Hardware Telemetry Gauges (`TelemetryDashboardCards`)**:
   - **GPU Metric Card**: Frekuensi real-time (MHz) + Persentase Beban Render GPU (Busy %).
   - **Battery Telemetry Card**: Temperatur baterai (°C) + Level Baterai (%) + Arus Pengisian/Pemakaian (mA).
   - **RAM Usage Bar**: Memori aktif terpakai vs Total Kapasitas Fisik (MB).
4. **CPU Cores Real-Time Frequency Matrix (`CoresGrid`)**:
   - Menampilkan frekuensi real-time dari Core 0 hingga Core 7 (atau 8-core+).
   - Indikator warna dinamis sesuai persentase load (Cyan -> Hijau -> Amber -> Merah).

---

## 3. State ViewModel & Aliran Data Reaktif
- `uiState.state`: Objek `LynxState` berisi `active_profile`, `soc_type`, `governor`, `battery_temp`, `ram_used`, dll.
- `uiState.cpuFrequencies`: Array integer frekuensi saat ini per-core (diperbarui setiap interval polling telemetri ~1000ms saat foreground).
- `uiState.gpuFreq`: Frekuensi clock GPU aktif dalam MHz.
- `uiState.gpuBusy`: Nilai load GPU (0–100%).
- `uiState.batteryCurrent`: Nilai arus pengisian (positif) atau pengosongan (negatif) dalam satuan mA.

---

## 4. Pemetaan Node Kernel & Sysfs Terkait
| Parameter Telemetri | Path Sysfs Qualcomm | Path Sysfs MediaTek | Fallback Generic |
| :--- | :--- | :--- | :--- |
| **CPU Core 0-7 Freq** | `/sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq` | `/sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq` | `/sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_cur_freq` |
| **GPU Clock** | `/sys/class/kgsl/kgsl-3d0/gpuclk` atau `/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq` | `/proc/gpufreq/gpufreq_var_dump` atau `/sys/class/misc/mali0/device/cur_freq` | `/sys/class/devfreq/*/cur_freq` |
| **GPU Busy Load** | `/sys/class/kgsl/kgsl-3d0/gpubusy` (dihitung: busy/total) | `/proc/ged/hal/gpu_utilization` | Node devfreq util / statis |
| **Baterai Temp** | `/sys/class/power_supply/battery/temp` (skala 0.1°C) | `/sys/class/power_supply/battery/temp` | `/sys/class/power_supply/battery/batt_temp` |
| **Arus Baterai (mA)**| `/sys/class/power_supply/battery/current_now` | `/sys/class/power_supply/battery/current_now` | `/sys/class/power_supply/battery/BatteryAverageCurrent` |
| **Status Profil** | `/data/adb/modules/Lynx/config.json` | `/data/adb/modules/Lynx/config.json` | Memori sesi internal app |

---

## 5. Batas Keamanan & Error Boundary
1. **Peringatan Dialog Mode Extreme**:
   - Jika pengguna menekan tombol profil `Extreme`, sistem memicu modal dialog peringatan (`showExtremeDialog = true`).
   - Mode Extreme tidak akan diterapkan sebelum pengguna mengonfirmasi secara sadar dengan tombol teks merah "Aktifkan Extreme".
2. **Foreground-Only Telemetry Polling**:
   - Polling telemetri hanya berjalan saat `isAppForeground = true`.
   - Saat aplikasi masuk ke latar belakang (`onStop()`), loop telemetri dijeda penuh guna menghemat baterai dan menjaga *deep sleep*.
