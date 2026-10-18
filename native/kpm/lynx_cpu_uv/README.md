# ⚡ Lynx Comprehensive CPU, CCI & SoC VCore Undervolt Engine KPM (`lynx_cpu_uv` v2.0)

> **Modul Kernel Native Terpadu untuk Undervolting Tingkat Ring-0 (MediaTek Helio G96 & MT6358 PMIC)**  
> **Arsitektur**: ARM64 (`aarch64`)  
> **Framework**: KernelPatch / APatch  
> **Target SoC**: MediaTek Helio G96 (`mt6781`) / MT6358 Low-Level Buck Converters

---

## 📌 1. Domain Daya yang Dioptimasi
Modul v2.0 ini mencakup **3 subsistem komputasi inti**:

1. **Big Core Cluster (2× Cortex-A76 @ 2.05 GHz)** — `vproc12`:
   * Memotong voltase puncak dari **`943.75 mV` ke `912.50 mV`** (`-31.25 mV`).
2. **Little Core Cluster (6× Cortex-A55 @ 2.00 GHz) & CCI Bus (1.40 GHz)** — `vproc11`:
   * Memotong voltase dari **`968.75 mV` ke `943.75 mV`** (`-25.0 mV`).
   * Mendinginkan 6 inti core Little sekaligus dan bus interkoneksi L3 Cache.
3. **SoC VCore & LPDDR4X Memory Bus (4266 MT/s)** — `vcore`:
   * Memotong voltase VCore sebesar **`-12.5 mV`** (`-2 langkah PMIC`) pada kecepatan DRAM puncak.
   * Mengurangi panas internal pengontrol RAM dan bus DMA grafis.

---

## 🔬 2. Hook Kernel Tingkat Ring-0
* `mt6358_upmu_set_rg_buck_vproc11_vosel(val)`: Mencegat register selektor PMIC untuk Little Cores & CCI Bus.
* `mt6358_upmu_set_rg_buck_vproc12_vosel(val)`: Mencegat register selektor PMIC untuk Big Cores.
* `mt6358_upmu_set_rg_buck_vcore_vosel(val)`: Mencegat register selektor PMIC untuk SoC VCore / LPDDR4X.
* `regulator_set_voltage(reg, min_uV, max_uV)`: Fallback generik Linux regulator framework.

---

## 🛠️ 3. Kontrol Runtime (Supercall KPM_CTL0)
- `reset` : Kembalikan semua voltase ke default pabrik (0 mV offset).
- `uv=-12.5` : Mode sangat konservatif (-12.5 mV).
- `uv=-25` : Mode rekomendasi default (-25.0 mV CPU/CCI, -12.5 mV VCore).
- `uv=-31.25` : Mode seimbang tinggi (-31.25 mV CPU/CCI, -12.5 mV VCore).
- `uv=-37.5` : Mode agresif (-37.5 mV CPU/CCI, -18.75 mV VCore).
