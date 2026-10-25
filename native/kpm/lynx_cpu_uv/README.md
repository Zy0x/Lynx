# ⚡ Lynx Comprehensive CPU, CCI & SoC VCore Undervolt Engine KPM (`lynx_cpu_uv` v2.2)

> **Modul Kernel Native Terpadu untuk Undervolting Tingkat Ring-0 (MediaTek Helio G96 & MT6358 PMIC)**  
> **Arsitektur**: ARM64 (`aarch64`)  
> **Framework**: KernelPatch / APatch  
> **Target SoC**: MediaTek Helio G96 (`mt6781`) / MT6358 Low-Level Buck Converters

---

## 📌 1. Domain Daya yang Dioptimasi
Modul v2.2 ini mencakup **3 subsistem komputasi inti**:

1. **Big Core Cluster (2× Cortex-A76 @ 2.05 GHz)** — `vproc12`:
   * Memotong voltase puncak dari **`943.75 mV` ke `925.00 mV`** (`-18.75 mV` / 3 steps).
2. **Little Core Cluster (6× Cortex-A55 @ 2.00 GHz) & CCI Bus (1.40 GHz)** — `vproc11`:
   * Memotong voltase puncak dari **`962.50 mV` ke `943.75 mV`** (`-18.75 mV` / 3 steps).
   * Mendinginkan 6 inti core Little sekaligus dan bus interkoneksi L3 Cache.
3. **SoC VCore & LPDDR4X Memory Bus (4266 MT/s)** — `vcore`:
   * Memotong voltase VCore sebesar **`-12.5 mV`** (`-2 langkah PMIC` @ 6.25 mV) pada kecepatan DRAM puncak.
   * Mengurangi panas internal pengontrol RAM dan bus DMA grafis.

---

## 🔬 2. Hook Kernel Tingkat Ring-0
* `mt6358_upmu_set_rg_buck_vproc11_vosel(val)`: Mencegat register selektor PMIC untuk Little Cores & CCI Bus.
* `mt6358_upmu_set_rg_buck_vproc12_vosel(val)`: Mencegat register selektor PMIC untuk Big Cores.
* `mt6358_upmu_set_rg_buck_vcore_vosel(val)`: Mencegat register selektor PMIC untuk SoC VCore / LPDDR4X.
* `regulator_set_voltage(reg, min_uV, max_uV)`: Fallback generik Linux regulator framework (dilengkapi *Anti-Double Undervolt Guardrail*).

---

## 🛡️ 3. Safety Guardrails & Floor Protection
* **Safety Floor Voltage**: Terkunci minimal **`0.575 V` (`575.000 uV`)** untuk CPU (`vproc11` / `vproc12`) dan **`0.650 V` (`650.000 uV`)** untuk SoC VCore.
* **Anti-Double Undervolt Shield**: Jika hook register tingkat rendah PMIC aktif, interceptor regulator tingkat atas otomatis dinonaktifkan agar tidak terjadi pemotongan ganda.

---

## 🛠️ 4. Kontrol Runtime (Supercall KPM_CTL0)
- `reset` / `stock` : Kembalikan semua voltase ke default pabrik (0 mV offset).
- `uv=-12.5` : Mode sangat konservatif (-12.5 mV / 2 steps).
- `uv=-18.75` : Mode rekomendasi default (-18.75 mV / 3 steps CPU/CCI, -12.5 mV VCore).
- `uv=-25` : Mode seimbang tinggi (-25.0 mV / 4 steps CPU/CCI, -12.5 mV VCore).
- `uv=-31.25` : Mode agresif (-31.25 mV / 5 steps CPU/CCI, -12.5 mV VCore).
