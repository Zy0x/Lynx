# 📐 Screen Architecture 05: Battery & Charging Subscreen (`charging`)
> **Scope**: Hyper Charge Current Control, Bypass Charging (Idle Mode), AutoCut 46.0°C Emergency Thermal Clamp, Health Stats, & Discharge Step History  
> **Source Files**: `BatterySubscreen.kt`, `FastChargingControlCard.kt`, `BatteryProtectionCard.kt`, `BatteryHealthStatsCard.kt`, `RealDischargeStepHistoryCard.kt`, `ChargingHeroDisplayCard.kt`, `ChargingRepository.kt`  

---

## 1. Identitas Komponen & Tanggung Jawab
Sub-halaman Battery & Charging mengatur siklus daya, laju pengisian, perlindungan panas, dan efisiensi baterai. Halaman ini bertugas menyediakan kontrol arus masuk fleksibel (hingga 4500mA pada charger yang mendukung), pengisian daya bypass (*bypass charging*) saat bermain game guna mencegah pemanasan baterai, serta menerapkan **Batas Termal Darurat Mutlak 46.0°C** demi keselamatan fisik sel lithium-ion.

---

## 2. Struktur Visual & Komposisi UI
1. **Charging Hero Display Card (`ChargingHeroDisplayCard`)**:
   - Menampilkan status daya real-time: `Discharging`, `Fast Charging`, `Bypass Mode`, atau `Full`.
   - Persentase baterai besar dengan indikator animasi pengisian.
   - Grafik riwayat laju arus pengisian/pengosongan (Sparkline Waveform mA).
2. **Fast Charging & Current Control Card (`FastChargingControlCard`)**:
   - **Preset Laju Pengisian**:
     - `Eco / Safe` (1000–1500 mA): Arus dingin untuk pengisian semalaman.
     - `Standard Fast` (2000–3000 mA): Standar OEM harian.
     - `Hyper Charge / Max Input` (3500–4500 mA): Memaksimalkan negosiasi USB-PD / QuickCharge.
   - **Custom Current Limit Dialog (`CustomCurrentLimitDialog`)**: Memungkinkan pengguna memasukkan nilai batas arus mA kustom secara aman.
3. **Battery Protection & Bypass Card (`BatteryProtectionCard`)**:
   - **Bypass Charging (Idle Mode) Toggle**: Memotong aliran arus ke sel baterai saat charger terhubung dan ponsel digunakan bermain game, sehingga daya dialirkan langsung ke motherboard (mencegah degradasi sel akibat panas ganda).
   - **AutoCut Charging Toggle**: Menghentikan pengisian otomatis saat baterai mencapai persentase tertentu (misal: 80% atau 90%).
   - **Emergency Hardware Clamp Indicator**: Menginformasikan status proteksi termal 46.0°C.
4. **Battery Health & Discharge History (`RealDischargeStepHistoryCard`)**:
   - Menampilkan siklus baterai (`cycle_count`), kapasitas desain vs kapasitas aktual terukur (mAh), estimasi umur sel (*Health %*), dan diagram step penurunan daya.

---

## 3. State ViewModel & Aliran Data
- `uiState.batteryLevel`: Persentase level baterai (0–100%).
- `uiState.batteryTemp`: Temperatur baterai terukur (°C).
- `uiState.batteryCurrent`: Arus instan dalam mA.
- `uiState.isChargingBypassEnabled`: Status aktif/non-aktif bypass charging.
- `uiState.fastChargePreset`: Preset arus aktif (`eco`, `standard`, `hyper`, `custom`).
- `viewModel.setFastChargeLimit(mA)`: Mengirim instruksi pembatasan arus ke modul daemon kernel.
- `viewModel.toggleBypassCharging(enabled)`: Mengaktifkan node bypass PMIC.

---

## 4. Pemetaan Node Kernel & Sysfs Terkait
| Kontrol Pengisian | Node Sysfs Qualcomm / Standard | Node Sysfs MediaTek / Transsion |
| :--- | :--- | :--- |
| **Input Current Limit** | `/sys/class/power_supply/battery/current_max`<br>`/sys/class/power_supply/usb/current_max` | `/sys/class/power_supply/charger/input_current_limit`<br>`/sys/devices/platform/charger/input_current` |
| **Fast Charge Toggle** | `/sys/class/power_supply/battery/fast_charge` | `/sys/devices/platform/battery/fast_chg_mode` |
| **Bypass Charging Mode**| `/sys/class/power_supply/battery/charging_enabled` (tulis `0`)<br>`/sys/class/power_supply/battery/battery_charging_enabled` | `/sys/devices/platform/charger/charging_bypass`<br>`/sys/class/power_supply/battery/device/enable_charging` |
| **Hardware Temp Probe** | `/sys/class/power_supply/battery/temp` | `/sys/class/power_supply/battery/batt_temp` |
| **Battery Cycle Count** | `/sys/class/power_supply/battery/cycle_count` | `/sys/class/power_supply/battery/cycle_count` |

---

## 5. Protokol Keselamatan Termal Darurat (Hard Ceiling 46.0°C)
> [!IMPORTANT]
> **Aturan Mutlak Keselamatan Perangkat Keras**:
> Apabila sensor termal baterai mencapai angka **46.0°C**, modul daemon `lynxd` dan `Charging-Controller.sh` secara otomatis dan tanpa syarat akan:
> 1. Memutus mode Hyper Charge.
> 2. Menurunkan arus masuk ke batas aman darurat (**500 mA**).
> 3. Menampilkan notifikasi peringatan kritis di layar pengguna.
> 4. Mode pengisian daya cepat hanya dapat diaktifkan kembali secara otomatis setelah temperatur turun di bawah ambang pendinginan aman (**41.0°C**).
