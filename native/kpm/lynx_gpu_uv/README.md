# 🎮 Lynx Dedicated GPU Undervolt Engine KPM (`lynx_gpu_uv`)

> **Modul Kernel Native Terisolasi Khusus GPU Undervolting Tingkat Ring-0 (ARM Mali & Adreno)**  
> **Arsitektur**: ARM64 (`aarch64`)  
> **Framework**: KernelPatch / APatch  
> **Target SoC**: MediaTek (Helio / Dimensity) Mali GPU & Qualcomm Adreno GPU

---

## 📌 1. Tujuan & Tanggung Jawab Modul
Modul ini bertugas secara **eksklusif** menangani undervolting pada domain prosesor grafis (GPU) tanpa mencampuri logika CPU.

- Mengurangi panas saat GPU rendering beban tinggi (gaming).
- Menjaga stabilitas FPS tanpa throttling.
- Strategi Default (Adaptive Tiered Curve):
  - **Peak Frequencies** ($\ge 840\text{ mV}$, 930–950 MHz): **`-12.5 mV`** (Margin stabilitas silicon timing ketat).
  - **Mid & Low Frequencies** ($< 840\text{ mV}$, 300–920 MHz): **`-25.0 mV`** (Menghemat daya & menekan panas maksimal).
- Safety floor: **`550 mV`** (mencegah driver GPU crash).

---

## 🔬 2. Hook Kernel Terverifikasi
1. **`regulator_set_voltage(reg, min_uV, max_uV)`**:
   Mencegat rail daya `vgpu` dan `vsram_gpu` untuk memotong nilai target voltase secara presisi sesuai kurva tingkatan.
2. **`ged_dvfs_vcore(int vcore)`**:
   Memodulasi kalkulasi voltase dinamis MediaTek GED (GPU Energy Demand).

---

## 🛠️ 3. Kontrol Runtime (Supercall KPM_CTL0)
Dapat dikendalikan langsung tanpa reboot:
- `tiered` / `auto` : Mode Tiered adaptif (Puncak -12.5 mV | Mid/Low -25.0 mV) [Default].
- `flat12.5` / `uv=-12.5` : Mode potong -12.5 mV rata untuk seluruh tabel OPP.
- `flat25` / `uv=-25` : Mode potong -25.0 mV rata untuk seluruh tabel OPP.
- `reset` / `stock` : Kembalikan voltase GPU ke default pabrik (0 mV).
- `vsram=off` / `core_only` : Hanya undervolt core logika GPU (`vgpu`), SRAM (`vsram_gpu`) dibiarkan pada voltase stock pabrik demi stabilitas maksimal 100% (Default).
- `vsram=on` / `dual_rail` : Undervolt kedua rail secara simultan (`vgpu` + `vsram_gpu`).
