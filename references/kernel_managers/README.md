# 📚 LYNX KERNEL MANAGER (LKM) — REFERENCE ARCHITECTURE LIBRARY

Repositori ini menyimpan salinan kode sumber dan kajian arsitektur kernel manager terkemuka sebagai acuan pengembangan **Lynx Kernel Manager (LKM)**:

```
references/kernel_managers/
├── smartpack/            # SmartPack-Kernel-Manager (Open Source Java/Kotlin)
│   └── app/src/main/java/com/smartpack/kernelmanager/utils/kernel/
│       ├── cpu/          # CPUFreq.java, CPUTimes.java, MSMPerformance.java
│       ├── gpu/          # GPUFreq.java, AdrenoIdler.java, DevfreqBoost.java
│       ├── battery/      # Battery.java (BLX charge limit, fast charge, current_now)
│       ├── io/           # IO.java (Schedulers, read-ahead buffer)
│       └── vm/           # ZRAM.java, VM.java, ZSwap.java
│
├── rvkernel_manager/     # RvKernel-Manager (Modern Material 3 Expressive & Jetpack Compose)
│   └── app/src/main/java/com/rve/rvkernelmanager/
│       ├── ui/
│       │   ├── home/     # HomeScreen.kt, HomeViewModel.kt
│       │   ├── soc/      # SoCScreen.kt (124 KB UI: Cluster Sliders, GPU Clock, Freq Bars)
│       │   ├── kernelParameter/ # KernelParameterScreen.kt (UCLAMP, BORE, TCP, ZRAM)
│       │   └── battery/  # BatteryScreen.kt (Charge levels, voltage, temperature)
│       └── utils/
│           ├── SoCUtils.kt     # Cluster topology detection, CPU policy nodes
│           ├── KernelUtils.kt  # Sched features, sysfs read/write helpers
│           └── BatteryUtils.kt # Power supply node readers
│
└── kernel_adiutor/       # Kernel Adiutor (Classic Android Sysfs Foundations)
    └── app/src/main/java/com/grarak/kerneladiutor/utils/kernel/
```

---

## 🎯 Intisari Logika Universal & Pola Implementasi untuk LKM

### 1. Deteksi Topologi Cluster CPU (Universal Topology Discovery)
Diadaptasi dari `SoCUtils.kt` dan `CPUFreq.java`:
- Perangkat Android modern menggunakan `policy` clusters di `/sys/devices/system/cpu/cpufreq/`:
  - `policy0`: Little / Efficiency Cores (Cortex-A55 / A510 / A520)
  - `policy3` / `policy4`: Big / Performance Cores (Cortex-A76 / A78 / A715 / A720)
  - `policy6` / `policy7`: Prime / Gold+ Cores (Cortex-X1 / X2 / X3 / X4)
- **Node Pembacaan**:
  - `scaling_cur_freq` (Frekuensi aktif real-time)
  - `scaling_min_freq` & `scaling_max_freq` (Batas frekuensi)
  - `scaling_available_frequencies` (Daftar clock yang valid)
  - `scaling_governor` & `scaling_available_governors` (Governor aktif & opsi yang didukung)

### 2. Pengontrol Frekuensi GPU (Adreno KGSL & MediaTek GED)
Diadaptasi dari `GPUFreq.java`:
- **Qualcomm Adreno**:
  - Cur Freq: `/sys/class/kgsl/kgsl-3d0/gpuclk`
  - Max Freq: `/sys/class/kgsl/kgsl-3d0/max_gpuclk`
  - Min Freq: `/sys/class/kgsl/kgsl-3d0/devfreq/min_freq`
  - Avail Freqs: `/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies`
  - Throttling & Powerlevels: `/sys/class/kgsl/kgsl-3d0/throttling`, `min_pwrlevel`
- **MediaTek Mali / Dimensity**:
  - OPP Dump: `/proc/gpufreq/gpufreq_opp_dump`
  - Current Opp: `/proc/gpufreq/gpufreq_opp_freq`
  - GED Clamping: `/sys/module/ged/parameters/gpu_cust_upbound_freq`, `/sys/kernel/ged/hal/custom_upbound_gpu_freq`

### 3. ZRAM & Virtual Memory Control
Diadaptasi dari `ZRAM.java` & `KernelUtils.kt`:
- Size Reset Sequence:
  `swapoff /dev/block/zram0` ➔ `echo 1 > /sys/block/zram0/reset` ➔ `echo <bytes> > /sys/block/zram0/disksize` ➔ `mkswap /dev/block/zram0` ➔ `swapon /dev/block/zram0 -p 5`
- Algorithm Discovery:
  Membaca `/sys/block/zram0/comp_algorithm` dan mengambil algoritma bertanda kurung siku `[lz4]` atau mengubahnya dengan menulis target algoritma ke node tersebut.

### 4. Baterai & Arus Pengisian Daya
Diadaptasi dari `Battery.java` & `BatteryUtils.kt`:
- Temp: `/sys/class/power_supply/battery/temp` (dalam decicelsius, bagi 10 untuk °C)
- Current: `/sys/class/power_supply/battery/current_now` (dalam microamperes $\mu\text{A}$)
- Voltage: `/sys/class/power_supply/battery/voltage_now` (dalam microvolts $\mu\text{V}$)
- Control: `/sys/class/power_supply/battery/charging_enabled` (0 = pause charging, 1 = resume)
