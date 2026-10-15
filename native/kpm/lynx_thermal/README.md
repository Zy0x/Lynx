# ⚡ Lynx TPCB Thermal Unthrottle KPM (KernelPatch Module)

> **Modul Kernel Native untuk Bypass Throttling Suhu PCB (TPCB 45°C) pada Pengisian Daya Cepat MediaTek / Transsion (Helio G96 / MTK DV2)**  
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

## 🔬 2. Solusi Teknis: KernelPatch Module (KPM)
Modul ini memanfaatkan framework **KernelPatch (APatch)** untuk melakukan *Inline Hooking* di tingkat Ring-0 kernel memory:
1. **Dynamic Symbol Resolution**: Menggunakan `kallsyms_lookup_name_by_suffix` untuk menemukan fungsi `__dv2_check_thermal_level` tanpa ketergantungan pada source code kernel OEM.
2. **Safe Clamping (Level 0 Override)**: Saat `__dv2_check_thermal_level` mengembalikan Level > 0 (throttled), modul meng-override nilai balik (`args->ret = 0`), menjaga pengisian daya tetap berada di Level 0 (Unthrottled 33W–45W penuh).
3. **Hardware Safety Guardrail**:
   - Jika suhu fisik PCB masih berada di bawah **`52°C`**, modul mengizinkan pengisian daya puncak.
   - Jika suhu PCB menyentuh ambang batas darurat **`52°C`** (`HARD_MAX_SAFE_PCB_TEMP`), modul secara otomatis menghentikan override dan membiarkan proteksi asli pabrikan mengambil alih untuk mencegah kerusakan fisik sirkuit.

---

## 🛠️ 3. Kompilasi KPM
Modul dikompilasi menggunakan Android NDK LLVM Clang:
```powershell
.\native\kpm\lynx_thermal\build_kpm.ps1
```
Hasil kompilasi berupa file biner relocatable ELF:
`native/kpm/lynx_thermal/build/lynx_thermal.kpm` (~5.5 KB).

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
