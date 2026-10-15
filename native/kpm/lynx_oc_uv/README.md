# ⚡ Lynx Adaptive Undervolt (UV) & Overclock (OC) Engine KPM

> **Modul Kernel Native untuk Undervolting & Overclocking Dinamis Tingkat Ring-0 (MediaTek & ARM64 Linux Kernel 4.14+)**  
> **Arsitektur**: ARM64 (`aarch64`)  
> **Framework**: KernelPatch / APatch  
> **Target SoC**: MediaTek Helio G96 (`mt6781`) / MT6358 PMIC & Linux Regulators

---

## 📌 1. Latar Belakang & Prinsip Fisika
Sesuai hukum daya dinamis semikonduktor:
$$\mathbf{P = C \cdot V^2 \cdot f}$$
Konsumsi daya dan disipasi panas berbanding lurus secara **kuadratik** dengan voltase suplai ($V^2$). 
Pabrikan memberikan voltase bawaan yang sengaja ditinggikan (*voltage guardband*) agar semua unit chip (termasuk kualitas silikon terendah) tetap menyala.

Modul ini mengimplementasikan **Adaptive Undervolt & Overclock** langsung di tingkat driver regulator hardware kernel Linux (`regulator_set_voltage` & `ged_dvfs_vcore`).

---

## 🔬 2. Pemetaan Power Rails & Fungsi Kernel yang Di-Hook

### A. Power Rails Hardware Terverifikasi (MT6358 PMIC)
- **`vproc11`**: Little Cores CPU (Cortex-A55, default ~837.5 mV)
- **`vproc12`**: Big Cores CPU (Cortex-A76, default ~968.75 mV)
- **`vgpu`**: ARM Mali-G57 GPU (OPP 00–48: 600 mV s/d 850 mV)
- **`vsram_proc11` / `vsram_proc12`**: SRAM L1/L2 Cache CPU
- **`vsram_gpu`**: SRAM GPU (850 mV s/d 950 mV)
- **`vcore`**: SoC Core Logic (700 mV)

### B. Inline Hooks Kernel
1. **`regulator_set_voltage(struct regulator *reg, int min_uV, int max_uV)`**:
   Mencegat setiap penulisan voltase CPU/GPU dan mengurangkan nilai offset undervolt yang dipilih (misal `-25.000 uV` / `-25 mV`).
2. **`ged_dvfs_vcore(int vcore)`**:
   Mengatur undervolt pada kalkulasi voltase dinamis GPU Mali MediaTek.
3. **`dev_pm_opp_set_rate(struct device *dev, unsigned long freq)`**:
   Menghilangkan latensi transisi OPP saat gaming/mode performa aktif.

---

## 🛡️ 3. Safety Guardrails (Proteksi Kestabilan)
- **CPU Safety Floor**: Dibatasi minimal **`700 mV` (700.000 uV)**. Modul tidak akan pernah menurunkan voltase di bawah ambang batas ini agar tidak terjadi *freezing* atau *kernel panic*.
- **GPU Safety Floor**: Dibatasi minimal **`550 mV` (550.000 uV)**.
- **Dynamic Reconfiguration**: Dapat diatur langsung secara *runtime* tanpa perlu reboot.

---

## 🛠️ 4. Kompilasi & Biner
Modul dikompilasi menggunakan Android NDK Clang (`aarch64-linux-android`):
```powershell
.\native\kpm\lynx_oc_uv\build_kpm.ps1
```
Hasil biner: **`native/kpm/lynx_oc_uv/build/lynx_oc_uv.kpm`** (~7.6 KB).

---

## 📲 5. Cara Penggunaan di APatch
1. Salin berkas `lynx_oc_uv.kpm` ke ponsel (`/sdcard/Download/lynx_oc_uv.kpm`).
2. Buka aplikasi **APatch Manager**.
3. **Pilihan 1 (Load Dinamis)**: Muat `lynx_oc_uv.kpm` via menu KPM.
4. **Pilihan 2 (Embed)**: Tambal `boot.img` dengan fitur "Embed KPM" agar modul langsung aktif saat ponsel dinyalakan.
5. Periksa status via terminal:
   ```bash
   su -c "dmesg | grep -i LynxOC_UV"
   ```
   Log verifikasi:
   `[LynxOC_UV] Lynx Adaptive OC & UV Engine successfully loaded!`
