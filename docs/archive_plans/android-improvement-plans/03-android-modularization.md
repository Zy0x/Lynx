# Plan 3 — Modularisasi Android per fitur

Status: rencana lengkap, belum dimulai. Dikerjakan setelah Plan 1 (slider) dan Plan 2 (performa render) diterima.
Inventaris diperbarui: 12 Oktober 2026 (setelah RC26).

---

## 1. Masalah yang diselesaikan

Lebih dari 75% kode aplikasi ada di 9 file. Akibatnya satu perubahan kecil memaksa membuka file belasan ribu baris, diff sulit direview, dan konflik antar-fitur mudah terjadi.

| File | Baris | Isi yang bercampur |
| :--- | ---: | :--- |
| `data/LynxRepository.kt` | 14.914 | ±415 fungsi: root, config, telemetri, CPU, GPU, baterai, charging, thermal, memori, jaringan, ADB, deep tunables, skrip shell tertanam (±1.500 baris di `deployWatcherScripts`), benchmark, FKM |
| `ui/LynxComponents.kt` | 8.863 | Design system (token, `LynxCard`, `LynxSwitch`, `LynxSlider`, tweak sheet) + kartu CPU, GPU, display, baterai, kernel, recovery |
| `ui/LynxTuningSubscreens.kt` | 5.225 | 7 subscreen kategori (CPU, GPU, thermal, memori, baterai, jaringan, sistem) beserta seluruh kartunya |
| `ui/LynxViewModel.kt` | 5.132 | ±280 fungsi aksi semua fitur + polling telemetri + intent guard |
| `ui/LynxCpuComponents.kt` | 4.170 | 15 komponen CPU besar (beberapa >400 baris) |
| `ui/MainActivity.kt` | 3.088 | `MainDashboard` sendiri ±2.220 baris: 4 tab, dialog app rule, dock, kartu tools |
| `ui/LynxGpuDisplayComponents.kt` | 2.789 | Kartu warna, HUD, lab, info GPU, per-app graphics |
| `service/LynxFloatingHudService.kt` | 2.046 | Service + 7 gaya overlay HUD |
| `data/LynxModels.kt` | 1.254 | ±70 model semua domain + `LynxUiState` (±160 field datar) |

Temuan teknis tambahan yang memengaruhi rencana:
- **317 pemanggilan `Shell.cmd` langsung** di repository; belum ada eksekutor root terpusat untuk sanitasi/logging.
- **Belum ada `src/test`**; parser (batterystats, top, scheduler, telemetri) belum punya tes.
- **ProGuard keep rule berbasis package** (`com.noir.lynx.data.**`, `ui.**`, dll.). Package baru wajib ditambahkan, atau model JSON/serialization rusak di build release.
- **Manifest dan tool memakai `.ui.MainActivity`**, `.service.*`, `.receiver.*`. Kelas-kelas ini **tidak dipindah package**.

Ukuran file adalah gejala keterikatan; bukan bukti langsung penyebab lag. Tujuannya kemudahan pengelolaan tanpa mengubah perilaku.

---

## 2. Aturan ukuran dan pembagian (wajib)

| Aturan | Batas |
| :--- | :--- |
| Ukuran file Kotlin, target | ≤ 400 baris |
| Ukuran file Kotlin, batas keras untuk file baru/hasil ekstraksi | ≤ 700 baris |
| Satu fungsi `@Composable` | ≤ 250 baris; jika lebih, pecah ke sub-composable `private` di file yang sama atau file `*Sections.kt` |
| Satu fungsi repository/parser | ≤ 150 baris; parser dipisah dari I/O |
| Isi file | 1 kartu/layar publik utama + helper `private`-nya. Nama file = nama simbol publik utama |
| Skrip shell tertanam (>100 baris) | Pindah ke file Kotlin `*Script.kt` khusus (tahap awal), opsional ke `assets/scripts/` (tahap akhir) |
| Tabel metadata statis (mis. `resolveFriendlyMetadata`) | File data tersendiri `*Catalog.kt` |

Pengecualian dicatat di manifest dengan alasan. Jumlah baris bukan satu-satunya kriteria selesai (lihat §8).

---

## 3. Struktur tujuan

Tetap dalam modul Gradle `:app`. Modul Gradle terpisah hanya dipertimbangkan setelah struktur package stabil dan manfaat build terukur.

```
com.noir.lynx/
├── LynxApp.kt
├── ui/                         # TIDAK dipindah: MainActivity (manifest) + shell aplikasi
│   ├── MainActivity.kt         # Activity saja: setContent, lifecycle → ViewModel (≤ 200)
│   ├── LynxViewModel.kt        # Koordinator tipis: init, lifecycle, delegasi ke controller
│   └── shell/
│       ├── LynxAppContent.kt   # Loading / Error / Dashboard switch
│       ├── MainDashboard.kt    # Scaffold + tab host saja
│       ├── FloatingDock.kt     # Dock kapsul + vignette
│       ├── TabRoutes.kt        # Tab 0..3 → route fitur
│       └── StatusScreens.kt    # LoadingScreen, ErrorScreen
│
├── core/                       # Lapisan bersama, tanpa UI fitur
│   ├── root/                   # RootShell (wrapper Shell.cmd), ShellQuote/sanitasi, hasil terstruktur
│   ├── config/                 # ConfigPaths, StateStore (readState/parse*/writeStateKey), JSON parser state
│   ├── telemetry/              # TelemetrySource (lynxd), TelemetryMapper, TelemetryHub (pemilik tunggal polling)
│   ├── capability/             # Pindahan hardware/*CapabilityDetector, RootCapabilityChecker, scanKernelCapabilities
│   ├── sysfs/                  # NodeWriter (sudah ada), NodeReader, WriteVerifier
│   ├── apps/                   # Resolver label/ikon aplikasi, readInstalledApps/AppInfos
│   └── format/                 # formatDurationMs, formatCleanDuration, format frekuensi/suhu
│
├── designsystem/               # Murni UI, tanpa akses root/repository
│   ├── theme/                  # Token warna/tipografi (Material 3 Expressive tokens)
│   ├── card/                   # LynxCard, ResetHeaderButton, StatusBadge, SocChip, UnsupportedBadge
│   ├── controls/               # LynxSwitch, LynxSlider (aman), LynxActionButton
│   ├── tweak/                  # LynxTweakTile, LynxDualTweakTile, LynxTweakSheet, LynxDualTweakSheet, LynxSwitchInfoSheet, model Tweak*
│   ├── chart/                  # Bezier sparkline bersama, frametime chart, grid/gradient helper
│   └── icon/                   # AppIconImage, ProcessIcon, BatteryConsumerIcon
│
├── feature/
│   ├── dashboard/              # Tab 0: header, theme picker, telemetry bento, profile grid, kartu ringkas
│   ├── profile/                # ProfileGrid/BentoTile, setProfile, mapping profil → target cluster
│   ├── cpu/                    # cluster, governor, scheduler, cpusets, cpuidle, platform engine, control center, monitor
│   ├── gpu/                    # quick profiles, master tuner, adaptive hardware, HWUI, info dashboard
│   ├── display/                # refresh rate & touch, color management, kalibrasi (RGB/KCAL/HBM), per-app graphics
│   ├── thermal/                # mode thermal, bypass, zona sensor, custom temp limit
│   ├── memory/                 # ZRAM, VM/dirty ratio, KSM, LMK, entropy, I/O scheduler
│   ├── battery/                # info & statistik, discharge chart, app drain, charging, health
│   ├── network/                # TCP congestion, Wi-Fi ping stabilizer, ADB keep-alive
│   ├── audio/                  # sound control/gain, audio MMAP
│   ├── system/                 # SELinux, printk, wakelock/doze, custom rules, custom scripts, dmesg
│   ├── tunables/               # deep sysfs scan/inspect, katalog metadata, skrip standalone
│   ├── automation/             # app profile rules, applist_perf, automation service control
│   ├── kernel/                 # flasher AnyKernel3, boot backup, reset stock, voltage, install modul
│   ├── benchmark/              # benchmark studio + lab recording/sesi (menggabungkan package lab/)
│   └── hud/                    # komponen overlay HUD (service tetap di service/)
│
├── service/ receiver/          # TIDAK dipindah (manifest). HUD service mendelegasikan UI ke feature/hud
└── safety/ engine/ kernel/ profiles/ sync/ display/ lab/   # package lama: dipertahankan, dipindah bertahap bila jelas pemiliknya
```

### 3.1 Struktur standar di dalam satu fitur

Contoh `feature/battery/` (pola yang sama untuk fitur lain; folder tidak dibuat jika kosong):

```
feature/battery/
├── BatteryRoute.kt             # Entry composable subscreen + lifecycle layar (ChargingScreenLifecycle)
├── BatteryState.kt             # Slice state fitur (pengganti field battery* di LynxUiState)
├── BatteryController.kt        # Aksi fitur (pindahan dari LynxViewModel), memakai scope ViewModel
├── data/
│   ├── BatteryRepository.kt    # readBatteryDetails, readBatteryHealth, calibrate, cable benchmark
│   ├── BatteryStatsReader.kt   # I/O dumpsys batterystats
│   ├── BatteryStatsParser.kt   # Parser murni → dapat dites tanpa perangkat
│   ├── ChargingRepository.kt   # applyChargingMode, extreme lock, fast charge, setter proteksi
│   └── ExtremeChargingScript.kt# buildUniversalExtremeChargingScript (teks skrip saja)
└── ui/
    ├── info/                   # BatteryInformationContent, BatteryRuntimeCard, BatteryHardwareCard,
    │                           # RealDischargeStepHistoryCard (+ DischargeChart.kt), AppDrainRow
    ├── charging/               # FastChargingCard, BatteryProtectionCard, ChargingSparklineWaveform
    ├── health/                 # BatteryHealthStatsCard
    └── dialogs/                # CustomCurrentLimitDialog, BatteryDetailBottomSheet
```

Aturan ketergantungan:
- `designsystem` → tidak bergantung pada `feature`, `core/root`, atau repository.
- `feature/X/ui` → hanya `designsystem`, `feature/X/*State`, dan callback. Tidak memanggil repository langsung.
- `feature/X` → boleh memakai `core/*`. Antar-fitur hanya lewat `core` atau shell; **tidak saling import UI fitur lain**.
- Semua eksekusi root lewat `core/root/RootShell` untuk kode yang sudah dipindah. Sisa `Shell.cmd` langsung dihapus bertahap per domain.

### 3.2 Batas domain GPU & display (agents.md §7.2)

Setiap domain = satu file, satu pemilik. Tidak ada gateway tweak duplikat.

| Domain | File tujuan |
| :--- | :--- |
| Macro preset | `feature/gpu/ui/GpuQuickProfilesCard.kt` |
| Clock OPP & telemetri | `feature/gpu/ui/GpuMasterTunerCard.kt` (+ `GpuLoadWaveform.kt`, `GpuFrequencyPickerSheet.kt`) |
| Kernel HAL sysfs (SSOT) | `feature/gpu/ui/AdaptiveGpuHardwareCard.kt` + `feature/gpu/data/GpuHardwareGateway.kt` (pembungkus `hardware/gpu/GpuBackendAdapter`) |
| Compositor & render | `feature/gpu/ui/GraphicsDriverHwuiCard.kt` + `feature/gpu/data/GraphicsHwuiRepository.kt` |
| Per-app graphics | `feature/display/ui/PerAppGraphicsHubCard.kt` + `feature/display/data/PerAppGraphicsRepository.kt` |
| Display output | `feature/display/ui/DisplayRefreshRateTouchCard.kt` + `feature/display/data/RefreshRateRepository.kt` |
| Kalibrasi warna | `feature/display/ui/ColorManagementCard.kt`, `DisplayCalibrationCard.kt` + `feature/display/data/ColorRepository.kt` |
| Diagnostik read-only (Tab 2) | `feature/gpu/ui/info/*` (GpuHardwareInfoDashboard, GpuSiliconIdentityCard, DisplayTelemetryCard, CompositorPipelineCard, KernelNodesInspectorCard). Tanpa kontrol tulis |

---

## 4. Peta pemindahan simbol (manifest awal)

Nomor baris mengacu kondisi RC26 dan diverifikasi ulang di Tahap 0. Pemindahan dilakukan dengan **cut-paste tanpa perubahan logika**; perubahan logika dilakukan di commit terpisah.

### 4.1 `LynxRepository.kt` → `core/*` dan `feature/*/data`

| Rentang ±baris | Isi | Tujuan |
| :--- | :--- | :--- |
| 52–136 | root/module check, path config, `writeTextToFileSafely` | `core/root/RootShell.kt`, `core/config/ConfigPaths.kt`, `core/config/SafeFileWriter.kt` |
| 137–493 | `readState`, `parse*`, `writeStateKey`, `setProfile`, maintenance, bug report, CCleaner | `core/config/StateStore.kt`, `core/config/StateParser.kt`, `feature/profile/data/ProfileRepository.kt`, `feature/system/data/MaintenanceRepository.kt` |
| 494–992 | `parseTelemetryJson`, `readTelemetry`, `mapTelemetryTo*`, `readNativeStatus` | `core/telemetry/TelemetrySource.kt`, `TelemetryParser.kt`, `TelemetryMapper.kt`, `NativeStatusReader.kt` |
| 993–1400 | cluster lock registry, `readClusters`, `setClusterFreq/Lock/Gov`, `applyCpuBatchProfile` | `feature/cpu/data/CpuClusterRepository.kt`, `ClusterLockRegistry.kt` |
| 1400–1900 | parking topologi, MTK PPM/CCI/DVFSRC, QCOM devfreq/LLCC, restore bus | `feature/cpu/data/platform/MtkPlatformTuner.kt`, `QcomPlatformTuner.kt`, `BusRestore.kt` |
| 1901–2052 | `readTopGraphicsProcesses` | `feature/gpu/data/GraphicsProcessReader.kt` |
| 2052–2536 | `readGpuInfo` (±480) | `feature/gpu/data/GpuInfoReader.kt` (+ pecah probe per vendor) |
| 2537–3025 | setter GPU/Mali/Adreno/FPSGO/SurfaceFlinger, `applyGpuProfile` | `feature/gpu/data/GpuControlRepository.kt`, `GpuProfileApplier.kt` |
| 3026–3157 | KSM, I/O scheduler | `feature/memory/data/KsmRepository.kt`, `IoSchedulerRepository.kt` |
| 3158–3215 | TCP congestion | `feature/network/data/TcpRepository.kt` |
| 3216–3413 | governor tunables & preset | `feature/cpu/data/GovernorTunablesRepository.kt` |
| 3414–3432, 5179–5214, 5407–5567, 12048–12109 | LMK, ZRAM, VM/dirty, entropy | `feature/memory/data/LmkRepository.kt`, `ZramRepository.kt`, `VmRepository.kt`, `EntropyRepository.kt` |
| 3433–3518, 11649–11729, 12223–12288 | boot backup, flasher, install modul, voltage, dmesg, reset stock | `feature/kernel/data/BootPartitionRepository.kt`, `KernelFlasher.kt`, `VoltageRepository.kt`, `DmesgRepository.kt`, `StockResetRepository.kt` |
| 3519–3597, 9295–9496, 11042–11118 | applist_perf, app rules, automation start/stop | `feature/automation/data/AppRulesRepository.kt`, `ApplistPerfStore.kt`, `AutomationController.kt` |
| 3598–3657, 5568–5625 | wakelock monitor & blocker, doze | `feature/system/data/WakelockRepository.kt` |
| 3658–4486 | charging (±830), termasuk skrip ±316 baris | `feature/battery/data/ChargingRepository.kt`, `ExtremeChargingScript.kt` |
| 4487–4558 | `scanKernelCapabilities` | `core/capability/KernelCapabilityScanner.kt` |
| 4559–4647, 5215–5265, 5012–5032 | refresh rate, mode didukung, DC dimming | `feature/display/data/RefreshRateRepository.kt` |
| 4648–4839, 4971–5011 | HWUI, shader cache, SF early phase, game driver, MSAA, OEM shield | `feature/gpu/data/GraphicsHwuiRepository.kt` |
| 4840–4970 | per-app graphics rules | `feature/display/data/PerAppGraphicsRepository.kt` |
| 5033–5143, 11823–11963 | capabilities grafis, pipeline, color profile, KCAL/HBM | `feature/display/data/ColorRepository.kt`, `DisplayCalibrationRepository.kt` |
| 5144–5161 | lab recording/sesi | `feature/benchmark/data/LabRepository.kt` |
| 5162–5178 | SoC override | `core/capability/SocOverrideStore.kt` |
| 5266–5332, 5759–5946 | thermal zones, thermal mode/stable/safety engine, custom limit | `feature/thermal/data/ThermalZoneReader.kt`, `ThermalEngineRepository.kt` |
| 5333–5374, 12110–12222 | custom rules, custom scripts | `feature/system/data/CustomRulesRepository.kt`, `CustomScriptsRepository.kt` |
| 5375–5406 | app resolver | `core/apps/AppInfoResolver.kt` |
| 5626–5658 | SELinux, printk | `feature/system/data/KernelLogModeRepository.kt` |
| 5659–5697 | touchboost, Wi-Fi ping, audio MMAP | `feature/display/data/TouchRepository.kt`, `feature/network/data/WifiRepository.kt`, `feature/audio/data/AudioRepository.kt` |
| 5698–5736 | ADB keep-alive | `feature/network/data/AdbRepository.kt` |
| 5737–5758 | Joyose neutralizer | `feature/thermal/data/OemNeutralizerRepository.kt` |
| 5947–5965 | overclock | `feature/cpu/data/OverclockRepository.kt` |
| 5966–6347 | stat loads, stream monitor, top process (parser ±300) | `feature/cpu/data/CpuLoadSampler.kt`, `CpuMonitorStream.kt`, `TopProcessReader.kt`, `TopOutputParser.kt` |
| 6348–6726 | SoC name, topologi, micro-arch, silicon details | `feature/cpu/data/CpuTopologyReader.kt`, `ArmMicroArch.kt` |
| 6727–6890 | core info & hotplug | `feature/cpu/data/CpuCoreRepository.kt` |
| 6891–7960 | battery details, batterystats (±560), app drain, kalibrasi, cable benchmark | `feature/battery/data/BatteryRepository.kt`, `BatteryStatsReader.kt`, `BatteryStatsParser.kt`, `AppDrainResolver.kt` |
| 7961–8279 | deep tunables cache/scan/inspect/parse | `feature/tunables/data/DeepTunablesRepository.kt`, `DeepTunableJsonParser.kt` |
| 8280–8960 | `resolveFriendlyMetadata` (±680, tabel statis) | `feature/tunables/data/TunableMetadataCatalog.kt` (dipecah per kategori bila >700) |
| 8961–9294 | skrip deep scan/inspect standalone | `feature/tunables/data/StandaloneScanScripts.kt` |
| 9473–9496 | izin overlay & usage stats | `core/root/PermissionGrants.kt` |
| 9497–11041 | `deployWatcherScripts` (±1.500, skrip mode standalone) | `feature/automation/data/WatcherScripts.kt` (teks skrip) + `WatcherDeployer.kt` (logika) |
| 11119–11384 | telemetri HUD | `feature/hud/data/HudTelemetryReader.kt` |
| 11385–11648 | benchmark, profil saat ini | `feature/benchmark/data/BenchmarkRunner.kt`, `FrameStatsParser.kt`; profil → `feature/profile/data` |
| 11730–11822 | battery health | `feature/battery/data/BatteryHealthReader.kt` |
| 11964–12047 | sound control | `feature/audio/data/SoundControlRepository.kt` |
| 12289–13093 | `readSchedulerInfo` (±800) | `feature/cpu/data/scheduler/SchedulerInfoReader.kt` + parser per backend (EAS/HMP/WALT) |
| 13094–13990 | setter scheduler, PPM, bus/touch/anti-throttle universal, proses, preset | `feature/cpu/data/scheduler/SchedulerRepository.kt`, `SchedulerPresets.kt`, `feature/cpu/data/ProcessControlRepository.kt`, `feature/cpu/data/platform/UniversalBoosts.kt` |
| 13991–14309 | CPU sets | `feature/cpu/data/CpuSetsRepository.kt` |
| 14310–14473 | schedtune & hints | `feature/cpu/data/scheduler/SchedtuneRepository.kt` |
| 14474–14914 | CPU idle, core parking, ARM PLL | `feature/cpu/data/CpuIdleRepository.kt` |

`LynxRepository` tetap ada sebagai **facade sementara**: setiap fungsi lama menjadi satu baris delegasi ke kelas baru, sehingga caller tidak berubah sekaligus. Facade dihapus per domain setelah semua caller pindah (§6, Tahap 6).

### 4.2 UI → `designsystem/*` dan `feature/*/ui`

| Sumber | Simbol | Tujuan |
| :--- | :--- | :--- |
| `LynxComponents.kt` 50–1610 | token, `LynxCard`, `LynxSwitch`, `LynxSlider`, tweak tile/sheet, switch info sheet | `designsystem/theme`, `card`, `controls`, `tweak` (1 file per komponen) |
| `LynxComponents.kt` 1384–1610 | `LynxSchedulerPresetSheet` | `feature/cpu/ui/scheduler/SchedulerPresetSheet.kt` |
| `LynxComponents.kt` 1611–1897 | `ProfileGrid`, `ProfileBentoTile`, `LynxActionButton`, `StatusBadge`, `SocChip` | `feature/profile/ui/*`, `designsystem/controls`, `designsystem/card` |
| `LynxComponents.kt` 1898–2560 | telemetry gauges/memory/topology, `CpuCoreBentoTile` | `feature/dashboard/ui/telemetry/*` |
| `LynxComponents.kt` 2563–3932 | `CpuClusterTunerCard` (±1.260) + helper tunable | `feature/cpu/ui/cluster/CpuClusterTunerCard.kt`, `ClusterFrequencyPills.kt`, `SteppedFrequencyGrid.kt`, `QuickTunablesSheet.kt`, `TunableMeta.kt` |
| `LynxComponents.kt` 3933–4134, 4248–4376 | `FlasherCard`, `BootBackupCard`, `VoltageControlCard` | `feature/kernel/ui/*` |
| `LynxComponents.kt` 4135–4247 | `StandaloneModuleBanner`, `UnsupportedBadge` | `feature/dashboard/ui`, `designsystem/card` |
| `LynxComponents.kt` 4381–5552 | GPU master tuner, quick profiles, adaptive hardware | `feature/gpu/ui/*` (lihat §3.2) |
| `LynxComponents.kt` 5553–6268 | HWUI, refresh rate & touch, kalibrasi display | `feature/gpu/ui`, `feature/display/ui` |
| `LynxComponents.kt` 6269–6841 | sound, battery health, memory entropy, custom script, dmesg | `feature/audio/ui`, `feature/battery/ui/health`, `feature/memory/ui`, `feature/system/ui` |
| `LynxComponents.kt` 6842–7044 | `TuningCategory`, `SubsystemCategoryCard`, `TuningBentoCard` | `ui/shell/tuning/TuningHub.kt`, `TuningCategory.kt` |
| `LynxComponents.kt` 7045–8862 | CPU sets, CPU idle, platform engine, control center, recovery sheet, protected tasks | `feature/cpu/ui/cpusets`, `cpuidle`, `platform`, `control/*` |
| `LynxTuningSubscreens.kt` | `TuningCpuCategory` … `TuningSystemCategory`, `DeepTunableItemCard` | `feature/<kategori>/<Kategori>Route.kt` + kartu masing-masing di `ui/` |
| `LynxTuningSubscreens.kt` 1723–3755 | seluruh baterai/charging (±2.030) | `feature/battery/ui/*` (pilot, §3.1) |
| `LynxTuningSubscreens.kt` 3756–4126 | jaringan & audio, kartu ADB | `feature/network/ui/NetworkRoute.kt`, `AdbKeepAliveCard.kt`, `TcpCongestionCard.kt`; `feature/audio/ui` |
| `LynxTuningSubscreens.kt` 4127–5225 | app rules, deep tunables, node tester | `feature/automation/ui/*`, `feature/tunables/ui/*` |
| `LynxCpuComponents.kt` | 15 komponen CPU | `feature/cpu/ui/{dashboard,cluster,scheduler,cpuidle,cpusets,monitor,process,topology,developer}/` |
| `LynxGpuDisplayComponents.kt` | warna, HUD card, lab, info GPU, per-app | `feature/display/ui`, `feature/hud/ui/FloatingGameHudCard.kt`, `feature/benchmark/ui/lab/*`, `feature/gpu/ui/info/*` |
| `MainActivity.kt` 133–372 | `LynxAppContent`, loading/error, `AppIconImage`, fling | `ui/shell/*`, `designsystem/icon`, `ui/shell/FluidFling.kt` (perilaku fling tidak diubah) |
| `MainActivity.kt` 442–835 | dialog tambah/edit app rule | `feature/automation/ui/AppRuleEditorDialog.kt` |
| `MainActivity.kt` 836–1441 | Tab 0 dashboard | `feature/dashboard/DashboardRoute.kt` + kartu |
| `MainActivity.kt` 1442–1749 | Tab 1 hub + subpage sticky header | `ui/shell/tuning/TuningHubRoute.kt`, `TuningSubpageHost.kt` |
| `MainActivity.kt` 1750–2429 | Tab 2/3 tools (SELinux, rules, thermal zones, wakelock, capability, script, dmesg) | `feature/system/SystemToolsRoute.kt`, `feature/kernel/KernelToolsRoute.kt` + kartu |
| `MainActivity.kt` 2430–2596 | vignette, dock | `ui/shell/FloatingDock.kt` |
| `MainActivity.kt` 2597–3088 | benchmark studio | `feature/benchmark/ui/BenchmarkStudioDialog.kt`, `FrametimeCanvasChart.kt`, `BenchmarkMetricTile.kt` |
| `LynxFloatingHudService.kt` 424–2046 | 7 gaya HUD + helper | `feature/hud/ui/styles/Hud*.kt`, `feature/hud/ui/HudSharedComponents.kt`; service tinggal lifecycle/window (≤ 450) |

### 4.3 `LynxViewModel.kt` dan `LynxModels.kt`

| Sumber | Tujuan |
| :--- | :--- |
| Intent guard (`merge*WithIntent`, `recordStateMutation`) | `ui/state/MutationIntentGuard.kt` (satu instance bersama; controller memakai, bukan menyalin) |
| `startTelemetryPolling`, file observer, periodic sync | `core/telemetry/TelemetryHub.kt`, `ui/state/StateSync.kt` |
| Aksi per bagian (CPU, profil, charging, thermal, ADB, GPU, HWUI, warna, KSM, I/O, TCP, LMK, app rules, wakelock, flasher, VM, scheduler, cpusets, cpuidle, deep tunables, HUD, FKM, benchmark, control center) | `feature/<fitur>/<Fitur>Controller.kt` |
| `initializeApp` (±250 baris memuat semua fitur) | dipecah: tiap controller punya `suspend fun load()`; ViewModel memanggil berurutan/paralel dengan urutan sama seperti sekarang |
| Model domain di `LynxModels.kt` | `feature/<fitur>/model/*.kt` (mis. `BatteryModels.kt`, `CpuModels.kt`, `GpuModels.kt`, `SchedulerModels.kt`) |
| `LynxUiState` (±160 field) | dipecah menjadi slice: `BatteryState`, `CpuState`, `GpuState`, `DisplayState`, `ThermalState`, `MemoryState`, `NetworkState`, `SystemState`, `HudState`, `BenchmarkState`, `ShellState` (tab, dialog, pesan) |

Model yang diserialisasi atau dibaca dari JSON **tidak diubah nama field-nya**. Jika model pindah package, tambahkan keep rule package baru lebih dulu (§5.4).

---

## 5. Model state dan kepemilikan

### 5.1 Satu pemilik per job dan per state hardware
- **Polling telemetri hanya di `TelemetryHub`** (dipanggil dari `LynxViewModel`). Controller fitur berlangganan hasil, tidak membuka loop sendiri.
- Polling khusus layar (monitor CPU stream, statistik baterai) dimiliki route layar tersebut dan berhenti saat layar keluar, sama seperti sekarang.
- Setiap node/parameter hardware hanya ditulis oleh satu repository. Daftar pemilik dicatat di manifest Tahap 0.

### 5.2 Tetap satu `ViewModel` pada awalnya
`LynxViewModel` dipertahankan sebagai koordinator agar tidak terjadi banyak ViewModel yang masing-masing polling atau menulis node yang sama. Isinya menjadi tipis:

```kotlin
class LynxViewModel : ViewModel() {
    val battery = BatteryController(viewModelScope, telemetryHub, intentGuard)
    val network = NetworkController(viewModelScope, intentGuard)
    // ...
}
```

Pemecahan menjadi beberapa `ViewModel` hanya dipertimbangkan setelah semua fitur memakai controller dan kepemilikan job terbukti tidak tumpang tindih.

### 5.3 Migrasi `LynxUiState` bertahap
1. Fitur yang sedang dimigrasi mendapat `StateFlow<XState>` sendiri di controller-nya.
2. Field lama fitur tersebut dihapus dari `LynxUiState` pada commit yang sama setelah semua pembaca pindah.
3. Route fitur hanya menerima slice-nya (`state: BatteryState`, callback aksi). Recomposition layar lain tidak terpicu oleh perubahan fitur ini.

### 5.4 Keamanan build release
- Tambahkan ke `proguard-rules.pro` sebelum memindah kelas apa pun: `-keep class com.noir.lynx.core.** { *; }`, `com.noir.lynx.feature.**`, `com.noir.lynx.designsystem.**` (persempit setelah audit model yang benar-benar diserialisasi).
- `MainActivity`, service, receiver, dan tile tetap di package lama.
- Setiap tahap divalidasi dengan build **release** (R8 aktif), bukan hanya debug.

---

## 6. Tahapan dan urutan fitur

Satu tahap = satu kandidat APK yang diuji dan diterima sebelum lanjut.

| Tahap | Isi | Keluaran |
| :--- | :--- | :--- |
| **0. Audit & alat** | Verifikasi ulang rentang baris; daftar caller tiap fungsi repository; daftar pemilik job/node; tambah keep rule package baru; buat `tools/check_kotlin_size.ps1` (lapor file > 700 baris dan composable > 250 baris, mode laporan saja); buat `src/test` dengan JUnit | `docs/android-improvement-plans/03a-modularization-manifest.md` |
| **1. Fondasi** | `core/root/RootShell` (wrapper tipis `Shell.cmd`, perilaku identik), `core/config`, `core/format`; `designsystem/*` dipindah dari `LynxComponents.kt` (pure move) | `LynxComponents.kt` turun ±1.600 baris |
| **2. Pilot: Baterai** | `feature/battery` lengkap sesuai §3.1: data, parser + tes `BatteryStatsParser`, controller, state slice, UI | Satu fitur sepenuhnya terpisah; `LynxTuningSubscreens.kt` turun ±2.000 baris |
| **3. Shell aplikasi** | Pecah `MainDashboard`: tab host, dock, dialog app rule, benchmark studio, tools tab | `MainActivity.kt` ≤ 200 baris |
| **4. Fitur ringan** | Network (+ADB), audio, system tools, thermal, memory | Pola terbukti di fitur kecil |
| **5. Fitur berat** | GPU & display (ikuti §3.2), lalu CPU (cluster → scheduler → cpusets → cpuidle → platform → control center → monitor), lalu tunables, automation, kernel, benchmark, HUD | Seluruh file sumber lama ≤ 700 baris atau dihapus |
| **6. Pembersihan** | Hapus delegasi facade yang sudah tidak dipakai; ganti sisa `Shell.cmd` langsung dengan `RootShell`; aktifkan `check_kotlin_size.ps1` sebagai pemeriksaan wajib untuk file baru | Facade `LynxRepository` hilang atau tinggal entry kompatibilitas kecil |

Aturan per tahap:
- Pemindahan simbol dan perubahan logika tidak dicampur dalam commit yang sama.
- Tidak ada rename massal, upgrade dependency, atau perubahan format konfigurasi bersamaan dengan pemindahan.
- Fix sentuhan yang sudah diterima (velocity tracker, native fling, perlindungan slider, keyed battery items) dipindah apa adanya.

---

## 7. Pengujian

| Lapisan | Pengujian |
| :--- | :--- |
| Setiap commit pemindahan | Build debug + **release**, lint, diff review (pastikan hanya pindah), `check_kotlin_size.ps1` |
| Parser murni | Unit test dengan fixture teks nyata dari X698 (batterystats, top, telemetri lynxd, scheduler). Fixture disimpan di `src/test/resources`, data pribadi dihapus |
| Repository hardware | Tes kontrak memakai fake `RootShell` untuk deteksi kapabilitas dan verifikasi tulis, sebelum uji perangkat |
| UI fitur | Smoke test layar yang dipindah: buka/keluar, Back, tab aktif tersimpan, rotasi/perubahan konfigurasi, dialog/sheet |
| Lifecycle | Pastikan polling tidak ganda (log jumlah job aktif), stream berhenti saat layar keluar, preview/commit slider tetap |
| Mode standalone | Uji tanpa modul terpasang; jalur modul memakai fixture/fake. Tidak mem-flash modul sebagai syarat refactor |
| Perangkat | Hanya X698 via discovery resmi + serial. Redmi Note 7 tidak dipakai untuk uji tulis |
| Penerimaan | Skenario scroll yang sudah diterima diulang; hasil sentuhan jari pengguna dicatat terpisah dari hasil remote |

---

## 8. Kriteria selesai per fitur

- File fitur mengikuti aturan §2 (pengecualian tercatat).
- State fitur ada di slice sendiri; tidak ada field fitur tersisa di `LynxUiState`.
- Tidak ada polling atau gateway tulis duplikat; pemilik job/node sesuai manifest.
- UI fitur tidak memanggil repository atau `Shell.cmd` langsung.
- Build release, tes, dan smoke test lulus; tidak ada regresi perilaku atau hasil ukur.

---

## 9. Rollback dan pelaporan

- Cadangan per fitur (bukan sekali di awal) di `archive/local_backups/modularization_<fitur>_<tanggal>/`: APK, diff, hash.
- Manifest mencatat file ditambah/dipindah/diubah per tahap, sehingga rollback satu fitur tidak menghapus pekerjaan lain.
- Format data tersimpan dipertahankan. Jika perubahan format diperlukan, jadikan tahap migrasi sendiri dengan backup dan tes.
- Laporan per tahap: peta arsitektur terbaru, tabel ukuran file sebelum/sesudah, daftar fitur selesai/tersisa, APK yang diterima, dan hasil pengujian.
