# 📐 Screen Architecture 03: CPU Tuning Subscreen (`cpu`)
> **Scope**: CPU Dynamic Clusters, Frequency OPP Sliders, Governors Picker, Schedutil Tunables, CPU Sets, Task Shield, Topology, & Process Inspector  
> **Source Files**: `CpuSubscreen.kt`, `CpuClusterTunerCard.kt`, `CpuSetsTaskShieldCard.kt`, `CpuUnifiedSystemCard.kt`, `CpuTopologyAndVerification.kt`, `ProcessInspectorBottomSheet.kt`, `GovernorPickerBottomSheet.kt`, `FrequencyPickerBottomSheet.kt`  

---

## 1. Identitas Komponen & Tanggung Jawab
Sub-halaman CPU adalah ruang kendali komprehensif untuk subsistem pemrosesan utama perangkat. Halaman ini menyediakan kontrol granular atas frekuensi minimum dan maksimum per-cluster (LITTLE, MID, BIG/PRIME), pemilihan governor kernel, penyetelan parameter schedutil, isolasi task scheduler (*cgroups cpuset*), serta inspektur proses real-time (*Process Inspector*).

---

## 2. Struktur Visual & Komposisi UI
1. **CPU Overview & Unified Cluster Card (`CpuUnifiedSystemCard`)**:
   - Menampilkan arsitektur prosesor (misal: `Octa-Core Kryo / Cortex`), governor aktif saat ini, dan status *Energy Aware Scheduling (EAS)*.
2. **Dynamic CPU Cluster Cards (`CpuClusterTunerCard`)**:
   - Satu kartu untuk setiap cluster fisik yang terdeteksi (Cluster 0: Little Cores, Cluster 1: Big Cores, Cluster 2: Prime Core jika ada).
   - Menampilkan rentang core (misal: Core 0–3, Core 4–7).
   - **Governor Chip**: Menampilkan governor aktif (misal: `schedutil`, `performance`, `powersave`, `walt`). Mengetuk chip membuka `GovernorPickerBottomSheet`.
   - **Min Frequency Slider (`SafeSlider`)**: Memilih frekuensi batas bawah cluster (MHz).
   - **Max Frequency Slider (`SafeSlider`)**: Memilih frekuensi batas atas cluster (MHz).
   - **Schedutil Tunables Sheet (`SchedutilPresetsCard`)**: Mengonfigurasi parameter schedutil (`up_rate_limit_us`, `down_rate_limit_us`, `hispeed_freq`, `hispeed_load`, dll.).
3. **CPU Sets & Task Shield (`CpuSetsTaskShieldCard`)**:
   - Pemisahan alokasi core Linux cgroups untuk kategori proses:
     - `Top-App`: Core yang dialokasikan untuk aplikasi aktif di layar.
     - `Foreground`: Core untuk layanan foreground.
     - `Background`: Core terbatas (biasanya hanya Little Cores) untuk layanan latar belakang.
     - `System-Background`: Core untuk daemon OS.
4. **Process Inspector Bottom Sheet (`ProcessInspectorBottomSheet`)**:
   - Memindai daftar proses aktif (`top -b -n 1 -m 15`), menampilkan PID, nama proses, beban CPU %, dan tombol untuk memprioritaskan atau membatasi core proses.

---

## 3. State ViewModel & Aliran Data
- `uiState.cpuClusters`: Daftar objek `CpuCluster` yang mendefinisikan ID cluster, daftar core, frekuensi min/max saat ini, frekuensi min/max hardware yang diizinkan, governor aktif, dan daftar governor yang tersedia.
- `viewModel.setCpuClusterMinFreq(clusterId, freqKHz)`: Menerapkan frekuensi minimum.
- `viewModel.setCpuClusterMaxFreq(clusterId, freqKHz)`: Menerapkan frekuensi maksimum.
- `viewModel.setCpuClusterGovernor(clusterId, governor)`: Menerapkan governor ke cluster terkait.
- `viewModel.applyCpuSet(setName, cpus)`: Menulis alokasi core ke node cpuset cgroup.

---

## 4. Pemetaan Node Kernel & Sysfs Terkait
| Kontrol / Parameter | Lokasi Sysfs Fisik (Linux Universal) | Keterangan & Batas Nilai |
| :--- | :--- | :--- |
| **Available Frequencies** | `/sys/devices/system/cpu/cpu*/cpufreq/scaling_available_frequencies` | Daftar OPP MHz yang valid dari kernel |
| **Available Governors** | `/sys/devices/system/cpu/cpu*/cpufreq/scaling_available_governors` | Governor yang dikompilasi di kernel |
| **Min Frequency** | `/sys/devices/system/cpu/cpu*/cpufreq/scaling_min_freq` | Nilai dalam satuan kHz |
| **Max Frequency** | `/sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq` | Nilai dalam satuan kHz |
| **Active Governor** | `/sys/devices/system/cpu/cpu*/cpufreq/scaling_governor` | String nama governor |
| **Schedutil Rate Limits** | `/sys/devices/system/cpu/cpufreq/schedutil/up_rate_limit_us`<br>`/sys/devices/system/cpu/cpufreq/schedutil/down_rate_limit_us` | Jeda waktu transisi frekuensi dalam mikrodetik |
| **CPUSets (Top-App)** | `/dev/cpuset/top-app/cpus` | String rentang core, contoh: `0-7` |
| **CPUSets (Background)** | `/dev/cpuset/background/cpus` | Dibatasi ke Little Cores, contoh: `0-3` |
| **MediaTek PPM DVFS** | `/proc/ppm/policy/cur_pstate` atau `/proc/ppm/policy_status` | Khusus MediaTek Platform HAL |

---

## 5. Validasi Keamanan Slider (SafeSlider Guard)
- **Aturan Relasi Min <= Max**: Penggeser frekuensi minimum tidak dapat ditarik melampaui nilai maksimum saat ini, dan penggeser frekuensi maksimum tidak dapat ditarik di bawah nilai minimum.
- **Strict Table Snapping**: Nilai slider otomatis mengunci (*snap*) pada frekuensi fisik yang benar-benar tercantum dalam `scaling_available_frequencies`. Dilarang menulis frekuensi arbitrer yang dapat memicu *hang*.
- **Transaction Verification**: Setiap penulisan node diverifikasi dengan membaca ulang nilai dari sysfs. Jika kernel menolak penulisan (karena read-only atau intervensi vendor HAL), status ditandai `READ_ONLY_LOCKED` dan UI mengembalikan indikator ke nilai kernel asli.
