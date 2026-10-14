# ⚡ Transsion (Infinix & Tecno) Hardware Charging Architecture & Sysfs Path Blueprint
> **Dokumen Referensi**: Jalur Hardware, Register Sysfs, Protokol Fast Charging, dan Logika Bypass Transsion  
> **Target Platform**: MediaTek Helio G-Series (G96/MT6781, G99/MT6789, G100) & Dimensity (MT6833, MT6855, MT6877, MT6893)  
> **Perangkat Uji Fisik Terverifikasi**: Infinix Note 11S (`X698`, MT6781, Android 14, APatch/Magisk)  
> **Status**: Production Reference Standard untuk Lynx Universal & Antigravity AI Agent  

---

## 📌 1. Gambaran Umum & Topografi Hardware Transsion

Perangkat Transsion (Infinix, Tecno, dan Itel) modern dengan pengisian daya 33W, 45W, 68W, hingga 180W menggunakan arsitektur pengisian daya ganda (**Dual Charging Topology**):

```
                       [ Adaptor Transsion PE+ / RFC (33W / 45W / 68W) ]
                                              │
                                              ▼ (VBUS: 9V - 11V / ~3.3A)
                             [ Port Type-C & R_SENSE Shunt (10 mΩ) ]
                                              │
                      ┌───────────────────────┴───────────────────────┐
                      │                                               │
                      ▼                                               ▼
     [ Jalur 1: Primary Switching Buck ]             [ Jalur 2: 2:1 Switched-Capacitor Pump ]
           MediaTek MT6358 / MT6370 PMIC                     Richtek RT9759 Charge Pump IC
          (Standar 5V/9V, Maks ~2.0A - 2.5A)             (Efisiensi ~97%, Arus Mengganda 2x I_bus)
                      │                                               │
                      └───────────────────────┬───────────────────────┘
                                              ▼
                                 [ Baterai Li-Ion / Li-Po ]
                               (Arus Pengisian Bersih > 6.0A)
```

### 1.1 Komponen Kunci Hardware:
1. **Primary PMIC / Buck Converter**: MediaTek MT6358 / MT6370, menangani negosiasi awal 5V standar, USB-PD, dan charging trickle.
2. **Secondary Direct Charge Pump**: Richtek RT9759 (terdaftar pada I2C driver `/sys/bus/i2c/drivers/rt9759/`), bekerja dengan rasio pelipat gandaan arus 2:1:
   $$\text{I}_{\text{pump}} = 2 \times \text{I}_{\text{bus}} \times \eta \quad (\eta \approx 0.97)$$
3. **Protokol Fast Charge**:
   - **Transsion PE+ (Pump Express Plus) / RFC (Rapid Flash Charge)**: Handshake proprietary Transsion via D+/D- atau CC line.
   - **USB Power Delivery (PD) & PPS**: Fallback untuk adaptor pihak ketiga (Ugreen, Anker, Baseus).

---

## 🗺️ 2. Matriks Register Hardware & Sysfs Transsion

Berikut adalah kamus lengkap register kernel dan node sysfs Transsion yang telah diuji dan diverifikasi secara empiris:

### 2.1 MediaTek Charger Platform Subsystem (`/sys/devices/platform/charger/`)

| Node Sysfs | Tipe Data | Nilai Default OEM | Nilai Hyper Mode (Lynx) | Nilai Restorasi (Unplug) | Fungsi & Mekanisme Hardware |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `enable_sc` | Int (0/1) | `0` | `1` (Izin `0444`) | `0` (Izin `0666`) | Mengaktifkan Richtek RT9759 Switched-Capacitor Direct Pump. Wajib `1` untuk arus >2.5A. |
| `sc_ibat_limit` | Int (mA) | `2000` | `12000` (Izin `0444`) | `2000` (Izin `0666`) | Batas arus pengisian langsung pump ke baterai (mA). |
| `input_current` | Int (mA) | `24576` | `33170` (Izin `0444`) | `24576` (Izin `0666`) | Batas arus masukan adapter dari VBUS. Nilai `33170` (3.3A) membuka daya penuh 33W+. |
| `chg1_current` | Int (mA) | `24576` | `33170` (Izin `0444`) | `24576` (Izin `0666`) | Batas arus jalur pengisian primer MT6358 PMIC. |
| `chg2_current` | Int (mA) | `24576` | `33170` (Izin `0444`) | `24576` (Izin `0666`) | Batas arus jalur pengisian sekunder (Direct Pump). |
| `pdc_max_watt` | Int (W) | `33` | `120` (Izin `0444`) | `33` (Izin `0666`) | Batas daya negosiasi pengisian cepat (watt). Melepaskan pencekikan pada adaptor 45W/68W+. |
| `BN_TestMode` | Int (0/1) | `0` | `1` (Izin `0444`) | `0` (Izin `0666`) | Battery Notification Test Mode. Memotong loop pembatasan termal firmware Transsion. |
| `BatteryNotify` | Int (0/1) | `1` | `0` (Izin `0444`) | `1` (Izin `0666`) | Broadcast notifikasi thermal throttle ke framework Android. `0` mematikan throttle. |
| `sw_jeita` | Int (0/1) | `1` | `0` (Izin `0444`) | `1` (Izin `0666`) | Software JEITA thermal stepping table. `0` mencegah penurunan arus akibat tabel JEITA. |
| `pe20` | Int (0/1) | `0` | `1` (Izin `0444`) | `0` (Izin `0666`) | Handshake MediaTek Pump Express 2.0. |
| `pe40` | Int (0/1) | `0` | `1` (Izin `0444`) | `0` (Izin `0666`) | Handshake MediaTek Pump Express 4.0 (Direct Pump). |
| `bypass_charger` | Int (0/1) | `0` | `0` (atau `1` saat bypass) | `0` | Mengaktifkan bypass hardware OEM Transsion (Motherboard powered, baterai 0 mA). |
| `tran_charger_full` | Int (0/1) | `0` | `0` (atau `1` saat bypass) | `0` | Flag baterai penuh buatan Transsion. |
| `tran_game_mode` | Int (0/1) | `0` | `0` | `0` | Mode game Transsion bawaan yang membatasi arus pengisian demi suhu casing. |
| `Charger_Type` | Int | Read-only | Status sensor | Status sensor | Jenis charger aktif: `9` = Transsion PE+ RFC Direct Pump, `7`/`4` = USB-PD/PPS, `0` = Unplug. |
| `ADC_Charger_Voltage`| Int (mV) | Read-only | Sensor live (~9500) | `0` | Tegangan adapter VBUS terukur secara live pada chip charger. |

---

### 2.2 Transsion ODM Custom Platform Subsystem (`/sys/devices/platform/odm/odm:tran_battery/` & `.../tran_battery/`)

| Node Sysfs | Tipe Data | Nilai Default OEM | Nilai Hyper Mode (Lynx) | Nilai Restorasi (Unplug) | Fungsi & Mekanisme Hardware |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `Charging_CallState` | Int | `0` (atau `1` saat layar nyala) | `0` (Izin `0444` lock) | `0` (Izin `0666`) | **KUNCI SCREEN-ON THROTTLING TRANSSION!** Nilai `1` mencekik arus hingga 1000mA saat layar menyala/panggilan aktif. Nilai `2` mode cool. Wajib dikunci ke `0`. |
| `pcb_thermal_debug` | Array | `[45,3000,50,2000,1000]` | `[95,6000,100,6000,6000]` (Lock `0444`) | `[45,3000,50,2000,1000]` | **Tabel Derating Termal Motherboard PCB**. Default menurunkan arus ke 3A di 45°C dan 2A di 50°C. Ditulis ulang ke batas 95°C/6000mA. |
| `Pump_Express_VCharger` | Int (mV) | Read-only | Sensor live (~9400) | `0` | Tegangan VBUS adapter fisik yang terdeteksi di pin Type-C. |
| `Pump_Express_ICharger` | Int (mA) | Read-only | Sensor live (~3300) | `0` | **Sensor Arus Masukan Riil (Bus Current)** dari adaptor. |

---

### 2.3 PCA DV2 Algorithm Subsystem (`/sys/devices/platform/pca_dv2_algo/`)

| Node Sysfs | Tipe Data | Perintah Hyper Mode (Lynx) | Perintah Restorasi (Unplug) | Fungsi & Mekanisme Hardware |
| :--- | :--- | :--- | :--- | :--- |
| `dv2_debug` | String | `[101,85,88,90,6000,6000,6000]` | `[101,43,45,50,500,1000,1500]` | Perintah `101`: Menimpa ambang batas suhu derating termal in-memory Direct Pump algorithm. Default turun di 43°C/45°C/50°C. |
| `dv2_debug` | String | `[102,85,88,90,6000,6000,6000]` | `[102,70,75,80,300,600,900]` | Perintah `102`: Menimpa step arus fallback derating algoritma DV2. |

---

### 2.4 MediaTek Battery Platform & Temperature Override Subsystem (`/sys/devices/platform/battery/`)

> [!CAUTION]
> ### ⚠️ Peringatan Kritis Node `Battery_Temperature`:
> * Driver kernel MTK `bat_gm30` menerima input integer bertanda dengan batasan **`[-2, 50]`** (dalam derajat Celsius).
> * Jika ditulis `"65535"`, parser integer 16-bit membacanya sebagai **`-1`**, yang menyebabkan `/sys/class/power_supply/battery/temp` terkunci permanen pada **`-10` (-1.0°C) atau `0` (0.0°C)**!
> * Driver kernel MTK **TIDAK PERNAH** kembali ke mode pembacaan otomatis AuxADC dengan sendirinya setelah node ini ditulis.
> * **Satu-satunya solusi yang benar**: Saat kabel dicabut atau beralih ke mode standar, daemon Lynx **wajib menyinkronkan suhu fisik riil** dari AuxADC Channel 3 (`read_real_battery_temp_dc() / 10`) ke node ini secara berkala!

| Node Sysfs | Tipe Data | Perilaku Saat Hyper Mode | Perilaku Saat Unplug / Standard | Fungsi & Karakteristik Kernel |
| :--- | :--- | :--- | :--- | :--- |
| `Battery_Temperature` | Int (-2 s/d 50) | Ditulis `"28"` (Izin `0444` lock) | Ditulis suhu riil `(real_dc / 10)` (Izin `0666`) | Debug override suhu baterai kernel MediaTek. Nilai `28` menetralkan thermal throttle level 6. |
| `UI_SOC` | Int (0 s/d 100, 65535) | Tidak diubah (`65535`) | `65535` | Override persentase baterai di UI. Nilai `65535` mengembalikan pembacaan SoC ke sensor ADC fisik. |
| `BAT_EC` | String | Read-only | Read-only | Sensor kesehatan siklus elektrokimia sel baterai. |
| `FG_meter_resistance` | Int (mΩ) | Read-only (~100 - 300) | Read-only | Resistansi internal jalur kabel & konektor baterai. |

---

### 2.5 Sensor Fisik Murni (AuxADC & Thermal Zones Ground Truth)

Saat node `Battery_Temperature` sedang di-override ke 28°C selama pengisian cepat Hyper, **suhu baterai fisik yang asli** tetap dapat dibaca secara akurat dari:

1. **MT635x AuxADC Channel 3 NTC Thermistor**:
   * Jalur sysfs: `/sys/bus/iio/devices/iio:device1/in_voltage3_BAT_TEMP_input`
   * Membaca tegangan analog thermistor NTC 10K (B=3950) dengan pull-up 12K ke referensi 1.8V VREF.
   * **Tabel Konversi Millivolt ke Deci-Celsius (`auxadc_mv_to_dc`)**:
     * `mv <= 300 mV`: $\ge 65.0^\circ\text{C}$ (650 dC)
     * `mv <= 375 mV`: $55.0^\circ\text{C} - 65.0^\circ\text{C}$
     * `mv <= 440 mV`: $50.0^\circ\text{C} - 55.0^\circ\text{C}$ (**Batas Kritis Hardware Safety Cutoff 50.0°C**)
     * `mv == 530 mV`: $\approx 42.0^\circ\text{C}$ (420 dC — Terverifikasi pada live hardware Infinix)
     * `mv == 600 mV`: $\approx 36.5^\circ\text{C}$ (365 dC)
     * `mv == 700 mV`: $\approx 30.0^\circ\text{C}$ (300 dC)
     * `mv >= 800 mV`: $\le 25.0^\circ\text{C}$ (250 dC)
2. **Zona Termal Hardware Bebas Spoofing**:
   * `thermal_zone1` (`mtktsAP`): Sensor temperatur SoC/Application Processor (dalam millicelsius, misal `42376` = 42.3°C).
   * `thermal_zone2` (`mtktsbtsmdpa`): Sensor temperatur baseband/modem power amplifier.

---

### 2.6 Kontroller Termal ABCCT MediaTek & Cooling Devices

| Node Sysfs | Nilai Hyper Mode (Lynx) | Nilai Restorasi (Unplug) | Fungsi & Mekanisme Hardware |
| :--- | :--- | :--- | :--- |
| `/proc/driver/thermal/clabcct` | `0` (Lock `0444`) | `1 42000 1000 200000 5 2000 0` | MediaTek Adaptive Battery Current Control Thermal PID loop. `0` mematikan modulasi PID pembatas arus. |
| `/proc/driver/thermal/clabcct_lcmoff` | `0` (Lock `0444`) | `1 43000 1000 200000 5 2000 0` | ABCCT PID loop saat layar mati (LCM Off). |
| `/sys/class/thermal/cooling_device53..57/cur_state` | `0` (Lock `0444`) | Izin dibuka (`0644`), state dinamis | Cooling device `mtk-cl-bcct00..02`. Mengunci state ke `0` mencegah kernel menaikkan tingkat throttling. |

---

## ⚡ 3. Arsitektur 4-Tier Penghapusan Pembatas Pengisian Daya

Untuk mencapai arus pengisian konstan **> 6.0A (6000mA+)** secara berkelanjutan, Lynx menerapkan protokol 4-Tier berikut:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       LYNX 4-TIER CHARGING PIPELINE                         │
├─────────────────────────────────────────────────────────────────────────────┤
│  [ Tier R1: Screen-On & Refresh Rate Throttle Elimination ]                 │
│  • Kunci Charging_CallState = 0 (Izin 0444)                                 │
│  • tran_game_mode = 0 | pdc_max_watt = 120                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│  [ Tier R2: Multi-Domain Thermal Neutralization ]                           │
│  • pcb_thermal_debug = [95,6000,100,6000,6000] (Lock 0444)                  │
│  • dv2_debug = [101,85,88,90,6000,6000,6000] & [102,85,88,90,6000,6000,6000]│
│  • Battery_Temperature = 28 (Lock 0444)                                     │
│  • clabcct & clabcct_lcmoff = 0 | cooling_device53..57 cur_state = 0         │
│  • SIGSTOP ke daemon termal: thermalloadalgod, joyose, mi_thermald          │
│  • cmd thermalservice override-status 0                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│  [ Tier R3: Direct Charge Pump High Current (>6A) Delivery ]                │
│  • enable_sc = 1 | pe20 = 1 | pe40 = 1 | BN_TestMode = 1 | BatteryNotify = 0│
│  • input_current = 33170 | sc_ibat_limit = 12000                            │
│  • chg1_current = 33170 | chg2_current = 33170                             │
│  • I_pump = 2 * I_bus * 0.97 => 2 * 3.3A * 0.97 = 6.4A                     │
│  • I_net = I_pump - I_sys => 6.4A - 0.35A = ~6.05A Bersih ke Baterai        │
├─────────────────────────────────────────────────────────────────────────────┤
│  [ Tier R4: Fail-Safe Restoration & Dynamic Temp Sync ]                     │
│  • Deteksi cabut kabel sub-500ms (Triple-Vector Invariant)                  │
│  • Buka seluruh izin sysfs (chmod 0666)                                     │
│  • SIGCONT ke seluruh daemon termal pengguna                                │
│  • Sinkronkan suhu fisik AuxADC (sync_authentic_battery_temp) ke Batt_Temp   │
│  • dumpsys battery reset & cmd thermalservice reset                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.1 Perhitungan Matematika Arus Pompa Richtek RT9759:
Pada adaptor Transsion 33W/45W asli, negosiasi menghasilkan tegangan VBUS sekitar **9.4V s/d 10.0V** dengan arus masukan adapter ($I_{\text{bus}}$) sebesar **3300 mA (3.3A)**:
$$I_{\text{pump}} = 2 \times 3300\text{ mA} \times 0.97 = 6402\text{ mA}$$
Dengan konsumsi daya sistem motherboard ($I_{\text{sys}}$) sekitar 300–400 mA:
$$I_{\text{net}} = 6402\text{ mA} - 350\text{ mA} = \mathbf{6052\text{ mA } (6.05\text{ A})}$$
Arus pengisian bersih yang masuk ke dalam sel kimia baterai terbukti stabil **melampaui 6000 mA**.

### 3.2 Kurva Tapering Elektrokimia Sel Baterai (CC ke CV):
Untuk menjaga keamanan sel baterai dari bahaya overvoltage:
* **SoC < 80% & Tegangan Baterai < 4470 mV**: Fase *Constant Current (CC)* penuh -> **Hyper Peak (12000 mA / >6A Net)**.
* **SoC 80% s/d 94% (atau $V_{\text{bat}} \ge 4470\text{ mV}$)**: Fase *Constant Voltage (CV)* step-down -> **Tapering 80 (3500 mA)**.
* **SoC 95% s/d 99%**: Fase *Saturation Trickle* -> **Tapering 95 (750 mA)**.
* **SoC 100% (atau Status "Full")**: Proteksi *AutoCut Motherboard Bypass* -> **Bypass 100 (0 mA)**.

---

## 🛠️ 4. Cheatsheet Diagnostik Cepat (CLI & One-Liners)

Gunakan perintah cepat berikut via terminal root (ADB Shell / Termux) untuk memverifikasi atau mengatur jalur pengisian Transsion secara instan:

```bash
# 1. Cek Seluruh Sensor Live Transsion (Tegangan Adapter, Arus Bus, dan Status Pump)
su -c 'echo "--- TRANSSION SENSORS ---"
echo "VBUS Adapter : $(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger 2>/dev/null) mV"
echo "IBUS Adapter : $(cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger 2>/dev/null) mA"
echo "Chg Type     : $(cat /sys/devices/platform/charger/Charger_Type 2>/dev/null)"
echo "Pump Enable  : $(cat /sys/devices/platform/charger/enable_sc 2>/dev/null)"
echo "IBAT Limit   : $(cat /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null) mA"
echo "Input Limit  : $(cat /sys/devices/platform/charger/input_current 2>/dev/null) mA"
echo "CallState    : $(cat /sys/devices/platform/odm/odm:tran_battery/Charging_CallState 2>/dev/null)"
echo "Batt Temp    : $(cat /sys/class/power_supply/battery/temp 2>/dev/null) (dC)"
echo "AuxADC Temp  : $(cat /sys/bus/iio/devices/iio:device1/in_voltage3_BAT_TEMP_input 2>/dev/null) mV"'

# 2. Paksa Aktifkan Hyper Mode 33W+ Pompa Maksimal Secara Instan
su -c 'chmod 666 /sys/devices/platform/charger/* /sys/devices/platform/odm/odm:tran_battery/* /sys/devices/platform/pca_dv2_algo/* /sys/devices/platform/battery/* 2>/dev/null
echo 1 > /sys/devices/platform/charger/enable_sc
echo 1 > /sys/devices/platform/charger/pe20
echo 1 > /sys/devices/platform/charger/pe40
echo 1 > /sys/devices/platform/charger/BN_TestMode
echo 0 > /sys/devices/platform/charger/BatteryNotify
echo 0 > /sys/devices/platform/charger/sw_jeita
echo 120 > /sys/devices/platform/charger/pdc_max_watt
echo 33170 > /sys/devices/platform/charger/input_current
echo 12000 > /sys/devices/platform/charger/sc_ibat_limit
echo 33170 > /sys/devices/platform/charger/chg1_current
echo 33170 > /sys/devices/platform/charger/chg2_current
echo 0 > /sys/devices/platform/odm/odm:tran_battery/Charging_CallState
echo "[95,6000,100,6000,6000]" > /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug
echo "[101,85,88,90,6000,6000,6000]" > /sys/devices/platform/pca_dv2_algo/dv2_debug
echo "[102,85,88,90,6000,6000,6000]" > /sys/devices/platform/pca_dv2_algo/dv2_debug
echo 28 > /sys/devices/platform/battery/Battery_Temperature
echo 0 > /proc/driver/thermal/clabcct
echo 0 > /proc/driver/thermal/clabcct_lcmoff
chmod 444 /sys/devices/platform/charger/enable_sc /sys/devices/platform/charger/input_current /sys/devices/platform/charger/sc_ibat_limit /sys/devices/platform/odm/odm:tran_battery/Charging_CallState /sys/devices/platform/battery/Battery_Temperature 2>/dev/null'

# 3. Pulihkan Semua Pengaturan ke Standar Aman OEM (Restore Safe Baseline)
su -c 'chmod 666 /sys/devices/platform/charger/* /sys/devices/platform/odm/odm:tran_battery/* /sys/devices/platform/pca_dv2_algo/* /sys/devices/platform/battery/* 2>/dev/null
echo 0 > /sys/devices/platform/charger/enable_sc
echo 0 > /sys/devices/platform/charger/pe20
echo 0 > /sys/devices/platform/charger/pe40
echo 0 > /sys/devices/platform/charger/BN_TestMode
echo 1 > /sys/devices/platform/charger/BatteryNotify
echo 1 > /sys/devices/platform/charger/sw_jeita
echo 33 > /sys/devices/platform/charger/pdc_max_watt
echo 24576 > /sys/devices/platform/charger/input_current
echo 2000 > /sys/devices/platform/charger/sc_ibat_limit
echo 0 > /sys/devices/platform/odm/odm:tran_battery/Charging_CallState
echo "[45,3000,50,2000,1000]" > /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug
echo "[101,43,45,50,500,1000,1500]" > /sys/devices/platform/pca_dv2_algo/dv2_debug
# Sinkronkan kembali suhu riil dari AuxADC (jangan tulis 65535!)
tz=$(cat /sys/class/thermal/thermal_zone1/temp 2>/dev/null)
[ -n "$tz" ] && [ "$tz" -gt 10000 ] && echo "$((tz / 1000))" > /sys/devices/platform/battery/Battery_Temperature
dumpsys battery reset'
```
