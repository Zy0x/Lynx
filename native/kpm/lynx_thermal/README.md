# ⚡ Lynx TPCB Thermal Unthrottle KPM v2.0 (KernelPatch Module)

> **Modul Kernel Native untuk Bypass Throttling Suhu PCB (TPCB 45°C) & Multi-Domain Thermal Gating pada Pengisian Daya Cepat MediaTek / Transsion (Helio G96 / MTK DV2)**  
> **Arsitektur**: ARM64 (`aarch64`)  
> **Framework**: KernelPatch / APatch  
> **Target Kernel**: Android Linux 4.14+ (Infinix X698 / Helio G96)

---

## 📌 1. Latar Belakang & Masalah
Pada ponsel Infinix/Tecno dengan pengisian cepat 33W/45W/68W+, kernel Linux menjalankan thread background bernama `dv2_algo_task`.
Ketika sensor NTC motherboard mendeteksi suhu PCB mencapai **45.0°C**, kernel mengeksekusi `__dv2_check_thermal_level` dan menaikkan status termal ke **Level 7**.
Dampaknya:
- Arus dari adapter dipotong paksa dari 3.3A–4.0A menjadi 1.500 mA.
- Daya pengisian anjlok dari 33W–45W menjadi ~10 Watt.
- Penulisan ke sysfs `/sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug` ditolak oleh kernel (`Permission denied`) karena fungsi `store` dinonaktifkan pada kernel komersial.

---

## 🔬 2. Solusi Teknis: KernelPatch Module (KPM v2.0)
Modul ini memanfaatkan framework **KernelPatch (APatch)** untuk melakukan *Inline Hooking* di tingkat Ring-0 kernel memory:
1. **Dynamic Symbol Resolution**: Menggunakan `kallsyms_lookup_name` untuk menemukan fungsi `__dv2_check_thermal_level`, `store_BN_TestMode`, dan `dv2_stop_algo`.
2. **Domain-Aware Safety Thresholds**:
   - `tbat` (Baterai): Batas aman ketat **`45°C`** (mencegah degradasi elektrokimia & lithium plating).
   - `tpcb` (Motherboard): Dinaikkan hingga **`58°C`** (komponen PCB aman beroperasi hingga 60°C).
   - `tdvchg_master` (RT9759 IC): Dinaikkan hingga **`68°C`** (silicon OTP pabrikan adalah 85°C).
   - `tta` (Travel Adapter / Kabel): Dinaikkan hingga **`65°C`** (spesifikasi adapter Transsion derating pada 70°C).
   - `tusb` (Port Type-C): Batas aman **`52°C`**.
3. **Hyper-Charging Auto-Gating & Fail-Safe Disconnect Restoration**:
   - Hook pada `store_BN_TestMode`: Ketika pengguna mengaktifkan mode Hyper di Lynx, `BN_TestMode=1` memicu KPM unthrottle aktif. Saat beralih ke mode Standard/Cool/Eco, KPM otomatis kembali ke proteksi OEM (`g_hyper_enabled=0`).
   - Hook pada `dv2_stop_algo`: Ketika kabel charger dicabut, KPM seketika menonaktifkan seluruh unthrottle override (`g_hyper_enabled=0`), mencegah arus bocor, baterai panas, atau anomali kernel saat ponsel digunakan normal tanpa charger.

---

## 🛠️ 3. Kompilasi KPM
Modul dikompilasi menggunakan Android NDK LLVM Clang:
```powershell
.\native\kpm\lynx_thermal\build_kpm.ps1
```
Hasil kompilasi berupa file biner relocatable ELF:
`native/kpm/lynx_thermal/build/lynx_thermal.kpm` (~10 KB).

---

## 📲 4. Cara Penggunaan di APatch
1. Salin berkas `lynx_thermal.kpm` ke penyimpanan internal ponsel (misal `/sdcard/Download/lynx_thermal.kpm`).
2. Buka aplikasi **APatch Manager**.
3. **Opsi 1 (Load Dinamis)**: Pilih menu KPM ➔ Muat `lynx_thermal.kpm`.
4. **Opsi 2 (Embed ke Boot Image)**:
   - Pilih Tambal Boot Image (Patch boot.img).
   - Tekan tombol **"Embed KPM"** dan pilih `lynx_thermal.kpm`.
   - Pasang hasil tambalan ke boot partition.
5. Periksa log kernel melalui `dmesg | grep -i LynxKPM` untuk memverifikasi bahwa hook telah aktif.
