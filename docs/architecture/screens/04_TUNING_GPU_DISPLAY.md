# 📐 Screen Architecture 04: GPU & Display Subscreen (`gpu`)
> **Scope**: GPU Master Tuner (Min/Max OPP), Adreno KGSL & MTK GED Tunables, Display Refresh Rate, TouchBoost Multi-OEM, & Color Management  
> **Source Files**: `GpuSubscreen.kt`, `GpuMasterTunerCard.kt`, `DisplayRefreshRateTouchCard.kt`, `GraphicsDriverHwuiCard.kt`, `GraphicsMemoryPipelineSection.kt`, `SurfaceFlingerPhaseCard.kt`, `ColorManagementCard.kt`, `PanelTuningCard.kt`, `TouchPrecisionCalibrationCard.kt`, `GpuProfilesAndHardwareCard.kt`, `GraphicsPipelineProbe.kt`, `TouchIrqShellBuilder.kt`  

---

## 1. Identitas Komponen & Tanggung Jawab
Sub-halaman GPU & Display mengontrol subsistem grafis dan panel output visual. Halaman ini memisahkan secara tegas antara domain frekuensi clock fisik (Master Tuner), abstraksi driver kernel (Adreno KGSL / MediaTek GED), compositor Android HWUI, laju penyegaran layar (Refresh Rate Hz), responsivitas layar sentuh (TouchBoost), dan kalibrasi warna hardware (ColorMatrix / KCAL).

---

## 2. Struktur Visual & Komposisi UI
1. **GPU Master Tuner Card (`GpuMasterTunerCard`)**:
   - Menampilkan model GPU fisik terdeteksi (misal: `Adreno 640` / `Mali-G68 MC4`).
   - Waveform grafik real-time beban GPU (Bezier waveform sparkline).
   - **Min GPU Frequency Picker**: Frekuensi batas bawah GPU dalam MHz.
   - **Max GPU Frequency Picker**: Frekuensi batas atas GPU dalam MHz.
   - **GPU Clock Lock Toggle**: Mengunci frekuensi GPU pada satu clock tetap (untuk benchmarking).
2. **Adaptive Hardware Engine Card (`GpuProfilesAndHardwareCard`)**:
   - Menjadi *Single Source of Truth* untuk parameter kernel driver grafis:
     - **Qualcomm Adreno**: KGSL bus split, force bus on, devfreq governor (`msm-adreno-tz`, `simple_ondemand`), idle timer.
     - **MediaTek Mali / GED**: GED GPU boost, FPSGO frame tracking, boosting policy `ged_boost_enable`, CCI limits.
3. **Graphics Driver & HWUI Pipeline Card (`GraphicsDriverHwuiCard`, `GraphicsMemoryPipelineSection`, `SurfaceFlingerPhaseCard`)**:
   - **Runtime Capability Gating (`GraphicsPipelineProbe.kt`)**: Memeriksa dukungan biner `resetprop`, simbol `ro.hwui.*` pada `libhwui.so`, backend `skiavk`/`skiagraphite`, ketersediaan paket ANGLE & pengaturan global `angle_gl_driver_*`, serta properti SurfaceFlinger/EGL sebelum menampilkan kontrol ke UI. Kontrol yang tidak didukung kernel/ROM otomatis disembunyikan agar bebas opsi palsu (*zero gimmick*).
   - Pilihan backend rendering global & per-aplikasi menggunakan `angle_gl_driver_all_angle` + pasangan AOSP `angle_gl_driver_selection_pkgs` / `angle_gl_driver_selection_values` (bukan key non-standar).
   - Cache HWUI (`ro.hwui.{texture,layer,path}_cache_size`) & `ro.input.resampling` diterapkan secara atomik via `resetprop` karena bersifat `ro.*`.
   - RenderThread priority booster (`setpriority -20`), `debug.sf.latch_unsignaled`, `debug.sf.disable_backpressure`, dan `debug.sf.early_phase_offset_ns`.
4. **Display Refresh Rate & Touch Card (`DisplayRefreshRateTouchCard`)**:
   - Pemilih Laju Penyegaran: `60 Hz`, `90 Hz`, `120 Hz`, `144 Hz` (bergantung panel).
   - **Auto Dynamic Refresh Rate Toggle**: Mengizinkan OS menurunkan refresh rate saat layar diam.
   - **Multi-OEM TouchBoost Toggle**: Peningkatan polling rate sentuhan berdasarkan node sysfs OEM yang benar-benar ada di kernel.
5. **Color Management & Panel Calibration (`ColorManagementCard`, `PanelTuningCard`)**:
   - SurfaceFlinger ColorMatrix 1015 *column-major* via `su 1000` (Preset `D65 Natural IPS`, `IPS Vivid`, `Akurat`, `Gaming`, `Cinema`, `Membaca`).
   - KCAL RGB Hardware Matrix (jika kernel mendukung interface `/sys/devices/platform/kcal_ctrl.0/kcal`).
   - True Panel CABC (`0x55`) & ESD Polling Watchdog (tampil dinamis hanya jika node sysfs/procfs panel terdeteksi). Fitur gimmick/non-berdampak seperti HBM palsu dan MTK PQ dilepas pada Fase 1 Universalization.

---

## 3. State ViewModel & Aliran Data
- `uiState.gpuAvailableFrequencies`: Daftar frekuensi OPP GPU dalam satuan MHz.
- `uiState.gpuMinFreq` & `uiState.gpuMaxFreq`: Nilai frekuensi terpilih saat ini.
- `uiState.currentRefreshRate`: Nilai Hz layar aktif (baca dari `DisplayPipelineReader`).
- `viewModel.setGpuMinFreq(freqMHz)`: Menerapkan frekuensi minimum GPU.
- `viewModel.setGpuMaxFreq(freqMHz)`: Menerapkan frekuensi maksimum GPU.
- `viewModel.setDisplayRefreshRate(hz)`: Mengirim perintah mode SurfaceFlinger via IPC.

---

## 4. Pemetaan Node Kernel & Sysfs Terkait
| Kontrol / Parameter | Jalur Sysfs Qualcomm Adreno | Jalur Sysfs MediaTek Mali |
| :--- | :--- | :--- |
| **Available GPU Freqs** | `/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies` | `/proc/gpufreq/gpufreq_opp_dump` atau `/proc/gpufreq/gpufreq_var_dump` |
| **Min GPU Clock** | `/sys/class/kgsl/kgsl-3d0/min_pwrlevel` (dikonversi dari MHz) | `/proc/gpufreq/gpufreq_limited_thermal_low_level` |
| **Max GPU Clock** | `/sys/class/kgsl/kgsl-3d0/max_pwrlevel` (dikonversi dari MHz) | `/proc/gpufreq/gpufreq_limited_power_low_level` |
| **GPU Governor** | `/sys/class/kgsl/kgsl-3d0/devfreq/governor` | Dikontrol internal via driver GED / DVFSRC |
| **Force Bus Active** | `/sys/class/kgsl/kgsl-3d0/bus_split` & `/sys/class/kgsl/kgsl-3d0/force_bus_on` | `/proc/ged/hal/custom_upbound_gpu_freq` |
| **GED Boost Enable** | *N/A (Khusus MediaTek)* | `/sys/module/ged/parameters/ged_boost_enable` |
| **Display Refresh Rate**| `service call SurfaceFlinger 1035 i32 <mode>` | `service call SurfaceFlinger 1035 i32 <mode>` |
| **Hardware KCAL RGB** | `/sys/devices/platform/kcal_ctrl.0/kcal` | *Bergantung pada driver panel kustom OEM* |
| **Touch Precision & IRQ Affinity** | Dynamic `TouchIrqShellBuilder` (deteksi `/sys/devices/system/cpu/cpufreq/policy*` cluster tertinggi + `/proc/interrupts` touch/SPI/I2C + Edge Back Guard & `resetprop ro.input.resampling`) | Dinamis Multi-OEM (`TouchIrqShellBuilder` + node panel sentuh vendor yang terverifikasi & `/proc/perfmgr/tchbst/kernel/tb_*` jika tersedia) |

- **6. Kalibrasi Presisi Sentuh & Pelindung Tepian (`TouchPrecisionCalibrationCard.kt`, `TouchPrecisionSections.kt`, `LynxTouchPrecisionRepository.kt`, `TouchIrqShellBuilder.kt`)**:
  - Kontrol sepenuhnya *capability-gated*: slider `micro_gain`, `edge_guard`, dan `SPI` hanya aktif bila node kernel driver panel sentuh terverifikasi ada; pada perangkat lain tetap menyediakan **Edge Back Guard**, `resetprop ro.input.resampling`, dan penyematan **IRQ + kthread Sentuh Dinamis** ke cluster core performa tertinggi secara universal across Qualcomm/MediaTek/Exynos/Tensor.
  - Properti mati tanpa efek AOSP (`view.touch_slop`, `debug.touch.frequency.boost`, `windowsmgr.max_events_per_second`, `persist.sys.touch.latency`, `debug.hwui.blob_cache_size_mb`, `debug.hwui.compiler_threads`, `debug.sf.max_acquired_buffers`) telah dihapus bersih.
  - Terintegrasi ke `native/lynxd` (`TouchPrecisionController` + `irq_topology.rs`) serta `LynxBootReceiver` standalone.

---

## 5. Tata Kelola Domain & Failsafe
1. **Pemisahan Tanggung Jawab Domain (Domain Boundary Mandate)**:
   - `GpuMasterTunerCard` dilarang menyentuh governor atau bypass termal.
   - Seluruh toggle driver kernel hanya berada di `GpuProfilesAndHardwareCard`.
2. **Qualcomm Power Level Inversion Guard**:
   - Pada Adreno KGSL, `pwrlevel 0` merepresentasikan frekuensi tertinggi (Max Clock), sedangkan `pwrlevel N` adalah frekuensi terendah. Adapter `QualcommAdrenoAdapter.kt` secara otomatis memetakan MHz ke indeks level daya secara terbalik guna mencegah kesalahan fatal saat penulisan ke sysfs.
