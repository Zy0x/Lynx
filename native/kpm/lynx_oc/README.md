# 🚀 Lynx Dedicated Hardware Overclock Engine KPM (`lynx_oc`)

> **Modul Kernel Native Terpadu untuk Overclocking Dinamis & Anti-Throttle Ring-0 (MediaTek & ARM Mali-G57 / Cortex-A76)**  
> **Arsitektur**: ARM64 (`aarch64`)  
> **Framework**: KernelPatch / APatch  
> **Target SoC**: MediaTek Helio G96 (`mt6781`) / Dimensity

---

## 📌 1. Fitur Utama Modul Overclock (Target Awal: 960 MHz)
1. **GPU Overclock Boost (Mali-G57 @ 960 MHz)**:
   * Mencegat `mt_gpufreq_target` dan `ged_dvfs_boost_gpu_freq`.
   * Memodifikasi langsung pointer live OPP table di memori kernel (`live_gpufreq_opp_table[0]`):
     - `freq = 960000 kHz` (960 MHz, +10 MHz over stock 950 MHz).
     - `vgpu = 85625` (856.25 mV, +6.25 mV headroom tegangan silikon).
     - `vsram = 95625` (956.25 mV, +6.25 mV SRAM stability rail).
2. **CPU Big Core Peak Lock (2× Cortex-A76)**:
   * Mencegat `cpufreq_driver_target` dan `dev_pm_opp_set_rate`.
   * Mengunci frekuensi Big Core secara konsisten di **`2.05 GHz`** saat beban gaming tinggi.
3. **Safety Fallback Interlock**:
   * Mendukung perintah darurat `reset` / `oc=0` untuk mengembalikan seluruh clock ke default pabrik seketika (950 MHz @ 850 mV).

---

## 🛠️ 2. Kontrol Runtime (Supercall KPM_CTL0)
- `gpu=960` atau `oc=960` : Set GPU Overclock ke 960 MHz (+6.25mV headroom).
- `gpu=1000` atau `oc=1000` : Set GPU Overclock ke 1000 MHz (+30mV headroom).
- `reset` atau `stock` : Kembalikan ke frekuensi bawaan (Stock 950 MHz @ 850 mV).

---

## 📲 3. Cara Penggunaan di APatch
1. Salin berkas `lynx_oc.kpm` ke penyimpanan internal ponsel (`/sdcard/Download/lynx_oc.kpm`).
2. Buka aplikasi **APatch Manager ➔ KPM**.
3. Muat berkas `lynx_oc.kpm`.
4. Periksa log kernel melalui `dmesg | grep -i LynxOC` dan verifikasi frekuensi aktif di `/proc/gpufreq/gpufreq_opp_freq`.
