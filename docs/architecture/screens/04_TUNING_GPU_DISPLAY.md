# 📐 Screen Architecture 04: GPU & Display Subscreen (`gpu`)
> **Scope**: GPU Master Tuner (Min/Max OPP), Adreno KGSL & MTK GED Tunables, Display Refresh Rate, TouchBoost Multi-OEM, & Color Management  
> **Source Files**: `GpuSubscreen.kt`, `GpuMasterTunerCard.kt`, `DisplayRefreshRateTouchCard.kt`, `GraphicsDriverHwuiCard.kt`, `ColorManagementCard.kt`, `GpuProfilesAndHardwareCard.kt`  

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
3. **Graphics Driver & HWUI Pipeline Card (`GraphicsDriverHwuiCard`)**:
   - Pilihan driver rendering per-aplikasi (System Graphics Driver vs Game Driver).
   - RenderThread priority booster (`setpriority -20`).
   - SurfaceFlinger latching optimization.
4. **Display Refresh Rate & Touch Card (`DisplayRefreshRateTouchCard`)**:
   - Pemilih Laju Penyegaran: `60 Hz`, `90 Hz`, `120 Hz`, `144 Hz` (bergantung panel).
   - **Auto Dynamic Refresh Rate Toggle**: Mengizinkan OS menurunkan refresh rate saat layar diam.
   - **Multi-OEM TouchBoost Toggle**: Peningkatan polling rate sentuhan (Xiaomi, OnePlus, Realme, Infinix).
5. **Color Management & Panel Calibration (`ColorManagementCard`)**:
   - SurfaceFlinger ColorMatrix 1015 (Universal SDR -> D65 White Point).
   - KCAL RGB Hardware Matrix (jika kernel mendukung interface `/sys/devices/platform/kcal_ctrl.0/kcal`).
   - High Brightness Mode (HBM) sunlight booster.

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

---

## 5. Tata Kelola Domain & Failsafe
1. **Pemisahan Tanggung Jawab Domain (Domain Boundary Mandate)**:
   - `GpuMasterTunerCard` dilarang menyentuh governor atau bypass termal.
   - Seluruh toggle driver kernel hanya berada di `GpuProfilesAndHardwareCard`.
2. **Qualcomm Power Level Inversion Guard**:
   - Pada Adreno KGSL, `pwrlevel 0` merepresentasikan frekuensi tertinggi (Max Clock), sedangkan `pwrlevel N` adalah frekuensi terendah. Adapter `QualcommAdrenoAdapter.kt` secara otomatis memetakan MHz ke indeks level daya secara terbalik guna mencegah kesalahan fatal saat penulisan ke sysfs.
