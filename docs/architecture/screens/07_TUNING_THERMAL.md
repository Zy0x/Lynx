# 📐 Screen Architecture 07: Thermal Studio Subscreen (`thermal`)
> **Scope**: Vendor Thermal Studio, Dynamic Trip Point Offsets, Throttling Neutralization, Thermal Zones Matrix, & OEM Thermal Policy  
> **Source Files**: `ThermalSubscreen.kt`, `VendorThermalStudioCard.kt`, `ThermalHeroCard.kt`, `ThermalCapabilityDetector.kt`, `ThermalProfileManager.kt`  

---

## 1. Identitas Komponen & Tanggung Jawab
Sub-halaman Thermal Studio adalah pusat kendali mitigasi throttling dan manajemen disipasi panas prosesor. Halaman ini menyediakan transparansi visual terhadap seluruh sensor termal fisik pada SoC (CPU, GPU, PMIC, Modem, Skin, Baterai), serta opsi pengendalian kebijakan termal vendor (*vendor thermal-engine / MediaTek .tp policy*) secara aman.

---

## 2. Struktur Visual & Komposisi UI
1. **Thermal Hero Status Card (`ThermalHeroCard`)**:
   - Menampilkan temperatur puncak SoC saat ini (°C) dengan visualisasi cincin gradien.
   - Status Disipasi: `Normal / Sejuk`, `Moderate / Hangat`, atau `Throttling Active / Kritis`.
   - Tombol Aksi Cepat: **Cooling Flush** (mereset cache termal dan mendinginkan frekuensi sesaat).
2. **Vendor Thermal Management Studio (`VendorThermalStudioCard`)**:
   - **Mode Kebijakan Termal (Thermal Profile Presets)**:
     - `Default OEM`: Kebijakan termal asli pabrikan perangkat.
     - `Balanced Gamers`: Menaikkan trip-point throttling sebesar +3°C hingga +5°C secara aman.
     - `Extreme Benchmark`: Menonaktifkan pembatasan termal CPU/GPU (wajib pendingin eksternal).
   - **Vendor Daemon Neutralizer Toggle**:
     - *Qualcomm*: Membatasi atau mematikan daemon `thermal-engine` / `thermald` via overlay dummy.
     - *MediaTek*: Menimpa berkas `.tp/thermal.conf` dan mereset status thermal mitigation policy.
3. **Hardware Thermal Zones Matrix (Live Sensor Grid)**:
   - Menampilkan seluruh `thermal_zone0` hingga `thermal_zoneN` yang aktif.
   - Menampilkan tipe zona termal (misal: `tsens_tz_sensor0`, `battery`, `gpu-thermal`, `cpu-1-0-usr`).
   - Nilai temperatur real-time dalam format desimal (°C).

---

## 3. State ViewModel & Aliran Data
- `uiState.maxSocTemp`: Nilai temperatur tertinggi di antara seluruh zona termal CPU/GPU.
- `uiState.thermalZones`: Daftar objek sensor termal berisi ID zona, tipe sensor, dan temperatur terkini.
- `uiState.activeThermalProfile`: Nama profil termal aktif (`oem`, `balanced`, `extreme`).
- `viewModel.setThermalProfile(profileName)`: Menerapkan offset trip point ke kernel.
- `viewModel.toggleThermalNeutralizer(enabled)`: Mengalihkan status intervensi daemon vendor.

---

## 4. Pemetaan Node Kernel & Sysfs Terkait
| Item Termal | Jalur Sysfs Qualcomm | Jalur Sysfs MediaTek |
| :--- | :--- | :--- |
| **Daftar Zona Termal** | `/sys/class/thermal/thermal_zone*/type` | `/sys/class/thermal/thermal_zone*/type` |
| **Temperatur Sensor** | `/sys/class/thermal/thermal_zone*/temp` (skala m°C / 1000) | `/sys/class/thermal/thermal_zone*/temp` |
| **Trip Point Temp** | `/sys/class/thermal/thermal_zone*/trip_point_*_temp` | `/sys/class/thermal/thermal_zone*/trip_point_*_temp` |
| **Cooling Device State** | `/sys/class/thermal/cooling_device*/cur_state` | `/sys/class/thermal/cooling_device*/cur_state` |
| **Vendor Qualcomm Config**| `/vendor/etc/thermal-engine.conf` | *N/A* |
| **Vendor MediaTek Config**| *N/A* | `/vendor/etc/.tp/thermal.conf` |

---

## 5. Protokol Keamanan & Pencegahan Kerusakan Perangkat Keras
1. **Trip Point Offset Ceiling (+8°C Max)**:
   - Penyetelan trip point offset dibatasi secara keras oleh kode Kotlin maksimal **+8.0°C** di atas batas aman pabrikan, mencegah komponen semikonduktor mencapai suhu leleh solder (solder reflow point > 105°C).
2. **Emergency Kernel Critical Trip Unmodifiable**:
   - Trip point berjenis `critical` pada thermal core Linux tidak pernah dimodifikasi atau dimatikan oleh Lynx. Jika suhu mencapai ambang batas kritis silikon kernel (biasanya 105°C – 110°C), kernel Linux akan tetap mengeksekusi *Emergency Thermal Shutdown* demi mencegah kerusakan fisik permanen.
