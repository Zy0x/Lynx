# 📱 Lynx Companion — UI Architecture & Screen Specifications
> **Episentrum Utama Pengembangan**: Native Android Application (`com.noir.lynx`)  
> **Teknologi**: Kotlin 1.9.22 + Jetpack Compose + Material 3 Expressive + `libsu` Universal Root  
> **Standar**: Modular Per-Screen Architectural Blueprint & Sysfs Configuration Mapping  

---

## 🏛️ 1. Ikhtisar Arsitektur Antarmuka (Interface Topology)

Aplikasi **Lynx Companion** (`LynxKernelManager.apk`) dibangun dengan paradigma **Native-First & Touch-First Presisi**. Seluruh fungsionalitas kernel, pengontrol daya, telemetri real-time, dan tuning perangkat keras disajikan melalui antarmuka modular bertingkat yang transparan, aman, dan berkinerja tinggi (60/120 FPS tanpa jank).

```
                             ┌──────────────────────────────────────┐
                             │       MainActivity (Root Shell)      │
                             │  • Dual-Mode Root Probe (libsu)      │
                             │  • Edge-to-Edge OLED Luxury Dark     │
                             │  • BackHandler Virtual Navigation    │
                             └──────────────────┬───────────────────┘
                                                │
                          ┌─────────────────────┴─────────────────────┐
                          ▼                                           ▼
             [ Mode Standalone / Generic ]                 [ Mode Lynx Universal Module ]
             • CPU / GPU / Storage Standard                • IPC Daemon lynxd Active
             • Direct sysfs root writes                    • Config State Synchronizer
             • Kernel diagnostics fallback                 • Hyper Charging & Thermal bypass
                          │                                           │
                          └─────────────────────┬─────────────────────┘
                                                │
                     ┌──────────────────────────┴──────────────────────────┐
                     │          Bottom Navigation Virtual Dock             │
                     │  [Tab 0] Dashboard (Live Gauges & Profiles)        │
                     │  [Tab 1] Tuning Hub (Categorized Hardware Engines) │
                     │  [Tab 2/3] Tools Hub (Battery Lab & Diagnostics)   │
                     └──────────────────────────┬──────────────────────────┘
                                                │
    ┌────────────────────────┬──────────────────┴──┬────────────────────────┬────────────────────────┐
    ▼                        ▼                     ▼                        ▼                        ▼
[CPU Subscreen]       [GPU & Display]      [Battery & Charge]       [Memory & Storage]      [Thermal Studio]
• Clusters OPP        • Master Tuner OPP   • Hyper Charging         • MountX Tri-Tier       • Trip Offsets
• Governors & Sched   • KGSL / GED Driver  • Bypass AutoCut         • I/O Schedulers        • OEM Neutralize
• Task Shields        • Refresh & Touch    • Discharge Waveform     • ZRAM & Swappiness     • Sensor Matrix
```

---

## 📑 2. Daftar Spesifikasi Modular Per-Halaman (`docs/architecture/screens/`)

Setiap dokumen di bawah ini membedah secara mendalam struktur komponen Jetpack Compose, state ViewModel, parameter konfigurasi yang diekspos ke pengguna, pemetaan node kernel/sysfs fisik (Qualcomm Snapdragon vs MediaTek vs Generic), serta mekanisme mitigasi kegagalan (*failsafe & rollback*):

| No | Modul Dokumentasi | Deskripsi & Cakupan Halaman | Komponen Kunci Compose |
| :---: | :--- | :--- | :--- |
| **00** | [`00_OVERVIEW_AND_NAVIGATION.md`](./screens/00_OVERVIEW_AND_NAVIGATION.md) | Fondasi AppShell, Navigasi Virtual Dock, Haptic Feedback, Token Desain OLED, dan Dual-Mode Root Detection. | `MainActivity`, `LynxAppShell`, `LynxBottomNavigationDock` |
| **01** | [`01_DASHBOARD.md`](./screens/01_DASHBOARD.md) | Tab 0: Beranda pemantauan real-time, CPU Core Frequency Matrix, GPU Clock/Load, Battery Telemetry, dan Quick Profile Switcher. | `MainDashboard`, `DashboardTabContent`, `TelemetryDashboardCards` |
| **02** | [`02_PROFILES_HUB.md`](./screens/02_PROFILES_HUB.md) | Sub-halaman Profil: Manajemen Profil Game/Aplikasi Per-Package, Auto-Switch Rules Engine, dan Katalog Profil Kernel. | `TuningProfilesCategory`, `ProfileGrid`, `AppProfileRule` |
| **03** | [`03_TUNING_CPU.md`](./screens/03_TUNING_CPU.md) | Sub-halaman CPU: Kontrol frekuensi cluster Min/Max MHz, Governor Picker, Schedutil Tunables, CPU Sets, Task Shield, dan Process Inspector. | `CpuSubscreen`, `CpuClusterTunerCard`, `CpuSetsTaskShieldCard` |
| **04** | [`04_TUNING_GPU_DISPLAY.md`](./screens/04_TUNING_GPU_DISPLAY.md) | Sub-halaman GPU & Layar: GPU Master Tuner (Min/Max MHz), KGSL/GED hardware tunables, Refresh Rate, TouchBoost, dan ColorMatrix D65/KCAL. | `GpuSubscreen`, `GpuMasterTunerCard`, `DisplayRefreshRateTouchCard` |
| **05** | [`05_TUNING_BATTERY_CHARGING.md`](./screens/05_TUNING_BATTERY_CHARGING.md) | Sub-halaman Baterai: Pengontrol Hyper Charge, Bypass Charging, AutoCut 46°C Safety Clamp, Health Stats, dan Riwayat Discharge Step. | `BatterySubscreen`, `FastChargingControlCard`, `BatteryProtectionCard` |
| **06** | [`06_TUNING_MEMORY_STORAGE.md`](./screens/06_TUNING_MEMORY_STORAGE.md) | Sub-halaman Memori: MountX Dynamic Tri-Tier Optimization, I/O Schedulers, Readahead Buffers, ZRAM / Swappiness, dan VM Compaction. | `MemorySubscreen`, `MountXStorageCard`, `MemoryEntropyCard` |
| **07** | [`07_TUNING_THERMAL.md`](./screens/07_TUNING_THERMAL.md) | Sub-halaman Termal: Vendor Thermal Studio, Dynamic Trip Point Offsets, Throttling Neutralization, dan Sensor Thermal Matrix. | `ThermalSubscreen`, `VendorThermalStudioCard`, `ThermalHeroCard` |
| **08** | [`08_TUNING_NETWORK_AUDIO.md`](./screens/08_TUNING_NETWORK_AUDIO.md) | Sub-halaman Jaringan & Audio: Wi-Fi Low-Latency Mode, TCP BBR/Cubic, ALSA Sound Gain Booster, Hi-Res MMAP, dan Codec Decoders. | `NetworkSubscreen`, `AudioSubscreen`, `SoundControlCard`, `MediaDecoderCard` |
| **09** | [`09_TUNING_SYSTEM.md`](./screens/09_TUNING_SYSTEM.md) | Sub-halaman Sistem: Pengelola Skrip Kustom Shell, Deep Kernel Tunables Store, Dmesg Viewer, dan Audit Status SELinux. | `SystemSubscreen`, `CustomScriptManagerCard`, `DeepTunableItemCard` |
| **10** | [`10_TOOLS_AND_DIAGNOSTICS.md`](./screens/10_TOOLS_AND_DIAGNOSTICS.md) | Tab 2 & 3: Battery Degradation Lab, Benchmark Studio, AnyKernel3 Flasher, Boot Backup/Restore, SQLite WAL TRIM, dan Preferensi. | `ToolsTabContent`, `BatteryLabSubscreen`, `BenchmarkStudioComponents` |
| **11** | [`11_FLOATING_HUD_SERVICE.md`](./screens/11_FLOATING_HUD_SERVICE.md) | Layanan Latar Belakang: Floating Telemetry HUD Overlay, Edge Drawer Quick Tools, Quick Settings Tiles, dan Broadcast Receivers. | `LynxFloatingHudService`, `FloatingHudContent`, `LynxProfileReceiver` |

---

## 🔒 3. Prinsip Integritas Konfigurasi & Keamanan Transaksi
1. **Single Source of Truth**: Seluruh konfigurasi pengguna disimpan secara atomik pada `/data/adb/modules/Lynx/config.json`.
2. **Transaction Probe & Verify**: Setiap penulisan node wajib memverifikasi izin tulis (`chmod 644`), mengeksekusi penulisan nilai, lalu membaca ulang node untuk memastikan nilai berhasil diadopsi oleh kernel (`VERIFIED_WORKING` vs `READ_ONLY_LOCKED`).
3. **Safety Fallback**: Slider frekuensi dan voltase dibatasi oleh tabel frekuensi fisik kernel (`scaling_available_frequencies`) yang dideteksi secara dinamis saat runtime, mencegah crash atau *kernel panic*.
