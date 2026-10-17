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
- Offset default: **`-12.5 mV`** (Sweet spot terverifikasi: stabil 100%, dingin, performa maksimal).
- Safety floor: **`550 mV`** (mencegah driver GPU crash).

---

## 🔬 2. Hook Kernel Terverifikasi
1. **`regulator_set_voltage(reg, min_uV, max_uV)`**:
   Mencegat rail daya `vgpu` dan `vsram_gpu` untuk memotong nilai target voltase sebesar offset yang aktif.
2. **`ged_dvfs_vcore(int vcore)`**:
   Memodulasi kalkulasi voltase dinamis MediaTek GED (GPU Energy Demand).

---

## 🛠️ 3. Kontrol Runtime (Supercall KPM_CTL0)
Dapat dikendalikan langsung tanpa reboot:
- `reset` / `stock` : Kembalikan voltase GPU ke default pabrik (0 mV).
- `uv=-12.5` / `sweetspot` : Mode sweet spot (-12.5 mV).
- `uv=-25` : Mode rekomendasi (-25.0 mV).
- `uv=-37.5` : Mode agresif (-37.5 mV).
- `uv=-50` : Mode ekstrem (-50.0 mV).
- `vsram=off` / `core_only` : Hanya undervolt core logika GPU (`vgpu`), SRAM (`vsram_gpu`) dibiarkan pada voltase stock pabrik demi stabilitas maksimal 100% (Default).
- `vsram=on` / `dual_rail` : Undervolt kedua rail secara simultan (`vgpu` + `vsram_gpu`).
